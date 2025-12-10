package embedded.mas.bridges.javard;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.math.BigInteger;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;

import com.fazecast.jSerialComm.SerialPort;

import arduino.NRJ;
import embedded.mas.bridges.jacamo.EmbeddedAction;
import embedded.mas.bridges.jacamo.IPhysicalInterface;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

// MAVLink / DroneFleet imports
import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.common.CommandLong;
import io.dronefleet.mavlink.common.CommandInt;
import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MavFrame;
import io.dronefleet.mavlink.util.EnumValue;

// Mission-related
import io.dronefleet.mavlink.common.MissionItemInt;
import io.dronefleet.mavlink.common.MissionCount;
import io.dronefleet.mavlink.common.MissionClearAll;
import io.dronefleet.mavlink.common.MissionSetCurrent;
import io.dronefleet.mavlink.common.MavMissionType;

public class NRJ4EmbeddedMas extends NRJ implements IPhysicalInterface {

    private String preamble = "==";
    private String startMessage = "::";
    private String endMessage = "--";
    private boolean connected = false;

    // MAVLink TX serializer
    private boolean mavInit = false;
    private ByteArrayOutputStream mavOut;
    private MavlinkConnection mavTxConn;

    private final int systemId = 200;      // GCS sysid
    private final int componentId = 50;    // GCS component
    private final int targetSystem = 1;    // vehicle sysid
    private final int targetComponent = 1; // vehicle component

    // Dialects for dynamic payload lookup
    private static final String[] DIALECT_PACKAGES = new String[] {
            "io.dronefleet.mavlink.common",
            "io.dronefleet.mavlink.minimal",
            "io.dronefleet.mavlink.ardupilotmega",
            "io.dronefleet.mavlink.uavionix"
    };

    // Mission buffer
    private static class MissionWp {
        final double latDeg;
        final double lonDeg;
        final float  altM;
        final boolean isTakeoff;

        MissionWp(double latDeg, double lonDeg, float altM, boolean isTakeoff) {
            this.latDeg    = latDeg;
            this.lonDeg    = lonDeg;
            this.altM      = altM;
            this.isTakeoff = isTakeoff;
        }
    }

    private final List<MissionWp> missionBuffer = new ArrayList<>();

    public NRJ4EmbeddedMas(String portDescription, int baud_rate)
            throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException {
        super(portDescription, baud_rate);
        this.openConnection();
    }

    @SuppressWarnings("unchecked")
    public String read() {
        return this.serialRead();
    }

    // ================== Parsing NAME(p1,p2,...) ==================

    private static class ParsedCommand {
        String name;
        String[] params;
        ParsedCommand(String n, String[] p) { name = n; params = p; }
    }

    private ParsedCommand parseCommand(String text) throws Exception {
        text = text.trim();
        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile("^([A-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*$");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (!matcher.matches()) {
            throw new Exception("Invalid command format: " + text);
        }

        String name = matcher.group(1).trim();
        String paramsStr = matcher.group(2).trim();
        String[] params;

        if (paramsStr.isEmpty()) {
            params = new String[0];
        } else {
            params = paramsStr.split("\\s*,\\s*");
        }

        return new ParsedCommand(name, params);
    }

    // ================== Basic helpers ==================

    private float toFloat(String s) {
        try { return Float.parseFloat(s); }
        catch (Exception e) { return 0f; }
    }

    private int toInt(String s) {
        try { return Integer.parseInt(s); }
        catch (Exception e) { return 0; }
    }

    private void ensureMavlink() throws IOException {
        if (mavInit) return;
        mavOut = new ByteArrayOutputStream();
        PipedInputStream dummyIn = new PipedInputStream();
        mavTxConn = MavlinkConnection.create(dummyIn, mavOut);
        mavInit = true;
        System.out.println("[NRJ4EmbeddedMas] MAVLink TX serializer initialized.");
    }

    private void sendMavlinkMessage(Object payload) throws IOException {
        ensureMavlink();
        mavOut.reset();
        mavTxConn.send2(systemId, componentId, payload);
        byte[] bytes = mavOut.toByteArray();

        System.out.print("[MAV->SERIAL] ");
        for (byte b : bytes) System.out.printf("%02X ", b);
        System.out.println();

        comPort.getOutputStream().write(bytes);
        comPort.getOutputStream().flush();
    }

    // ================== MAV_CMD_* generic handling ==================

    private boolean shouldUseCommandInt(String name) {
        return name.equals("MAV_CMD_NAV_WAYPOINT") ||
               name.equals("MAV_CMD_NAV_LAND") ||
               name.equals("MAV_CMD_NAV_RETURN_TO_LAUNCH");
    }

    private void sendCommandLong(MavCmd command, String[] params) throws IOException {
        float[] p = new float[7];
        for (int i = 0; i < 7; i++) {
            p[i] = (i < params.length) ? toFloat(params[i]) : 0f;
        }

        CommandLong msg = CommandLong.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .command(command)
                .confirmation((short) 0)
                .param1(p[0])
                .param2(p[1])
                .param3(p[2])
                .param4(p[3])
                .param5(p[4])
                .param6(p[5])
                .param7(p[6])
                .build();

        sendMavlinkMessage(msg);
    }

    private void sendCommandInt(MavCmd command, String[] params) throws IOException {
        float[] p = new float[7];
        for (int i = 0; i < 7; i++) {
            p[i] = (i < params.length) ? toFloat(params[i]) : 0f;
        }

        CommandInt msg = CommandInt.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .frame(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT)
                .command(command)
                .current((short) 0)
                .autocontinue((short) 0)
                .param1(p[0])
                .param2(p[1])
                .param3(p[2])
                .param4(p[3])
                .x((int) p[4])
                .y((int) p[5])
                .z(p[6])
                .build();

        sendMavlinkMessage(msg);
    }

    // ================== Dynamic payload lookup ==================

    private Class<?> resolvePayloadClass(String msgName) {
        String className = toClassName(msgName);
        for (String pkg : DIALECT_PACKAGES) {
            try {
                Class<?> cls = Class.forName(pkg + "." + className);
                return cls;
            } catch (ClassNotFoundException e) {
                // try next
            }
        }
        return null;
    }

    private String toClassName(String msgName) {
        String lower = msgName.toLowerCase(Locale.ROOT);
        String[] parts = lower.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private String snakeToCamel(String s) {
        s = s.trim();
        if (s.isEmpty()) return s;
        if (!s.contains("_")) return s.toLowerCase(Locale.ROOT);

        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder();
        sb.append(parts[0].toLowerCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }

    private Map<String, String> parseNamedArgs(String[] tokens) {
        Map<String, String> map = new HashMap<>();
        for (String token : tokens) {
            String t = token.trim();
            int eq = t.indexOf('=');
            if (eq <= 0) continue;
            String key = t.substring(0, eq).trim();
            String val = t.substring(eq + 1).trim();
            map.put(key, val);
        }
        return map;
    }

    private Object buildGenericPayload(String msgName, String[] params) throws Exception {
        Class<?> payloadClass = resolvePayloadClass(msgName);
        if (payloadClass == null) {
            throw new Exception("Unknown MAVLink message: " + msgName);
        }

        Method builderFactory = payloadClass.getMethod("builder");
        Object builder = builderFactory.invoke(null);
        Class<?> builderClass = builder.getClass();

        boolean anyNamed = false;
        for (String p : params) {
            if (p.contains("=")) {
                anyNamed = true;
                break;
            }
        }

        if (anyNamed) {
            Map<String, String> named = parseNamedArgs(params);
            applyNamed(builder, builderClass, payloadClass, named);
        } else {
            applyPositional(builder, builderClass, payloadClass, params);
        }

        Method buildMethod = builderClass.getMethod("build");
        return buildMethod.invoke(builder);
    }

    private void applyNamed(Object builder,
                            Class<?> builderClass,
                            Class<?> payloadClass,
                            Map<String, String> named) throws Exception {
        for (Map.Entry<String, String> e : named.entrySet()) {
            String field = e.getKey();
            String value = e.getValue();
            applyToField(builder, builderClass, field, value);
        }
    }

    private void applyPositional(Object builder,
                                 Class<?> builderClass,
                                 Class<?> payloadClass,
                                 String[] tokens) throws Exception {

        Field[] all = payloadClass.getDeclaredFields();
        List<Field> fields = new ArrayList<>();

        for (Field f : all) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod)) continue;
            if (f.isSynthetic()) continue;
            fields.add(f);
        }

        for (int i = 0; i < tokens.length && i < fields.size(); i++) {
            String fieldName = fields.get(i).getName();
            String rawVal = tokens[i];
            applyToField(builder, builderClass, fieldName, rawVal);
        }
    }

    private void applyToField(Object builder,
                              Class<?> builderClass,
                              String fieldName,
                              String rawValue) throws Exception {
        String camel = snakeToCamel(fieldName);
        String[] candidates = new String[] { camel, fieldName };

        Method[] methods = builderClass.getMethods();

        for (String mname : candidates) {
            for (Method m : methods) {
                if (!m.getName().equals(mname)) continue;
                if (m.getParameterCount() != 1) continue;

                Class<?> paramType = m.getParameterTypes()[0];
                Type genericType = m.getGenericParameterTypes()[0];
                try {
                    Object converted = convertStringToType(rawValue, paramType, genericType);
                    m.invoke(builder, converted);
                    return;
                } catch (Exception ex) {
                    // try next overload
                }
            }
        }

        System.err.println("[NRJ4EmbeddedMas] Could not map field '" + fieldName
                + "' with value '" + rawValue + "' on " + builderClass.getSimpleName());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object convertToEnum(String raw, Class<?> enumClass) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) return null;

        try {
            int idx = Integer.parseInt(raw);
            if (idx >= 0 && idx < constants.length) return constants[idx];
        } catch (NumberFormatException ignored) {}

        String upper = raw.toUpperCase(Locale.ROOT);
        for (Object c : constants) {
            if (((Enum)c).name().equals(upper)) return c;
        }
        return constants[0];
    }

    private Object convertStringToType(String raw, Class<?> targetType, Type genericType) throws Exception {
        raw = raw.trim();

        if (targetType == String.class) {
            return raw;
        }

        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(raw);
        }

        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(raw);
        }

        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(raw);
        }

        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(raw);
        }

        if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(raw);
        }

        if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(raw);
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            if (raw.equalsIgnoreCase("true") || raw.equals("1")) return true;
            if (raw.equalsIgnoreCase("false") || raw.equals("0")) return false;
            throw new IllegalArgumentException("Cannot parse boolean: " + raw);
        }

        if (targetType == BigInteger.class) {
            return new BigInteger(raw);
        }

        if (targetType.isEnum()) {
            return convertToEnum(raw, targetType);
        }

        if (targetType.getName().equals("io.dronefleet.mavlink.util.EnumValue")) {
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] instanceof Class) {
                    Class<?> enumClass = (Class<?>) args[0];
                    Object enumConst = convertToEnum(raw, enumClass);
                    if (enumConst != null) {
                        return EnumValue.of((Enum) enumConst);
                    }
                }
            }
            throw new IllegalArgumentException("Cannot convert to EnumValue without generic enum info");
        }

        try {
            java.lang.reflect.Constructor<?> c = targetType.getConstructor(String.class);
            return c.newInstance(raw);
        } catch (NoSuchMethodException nsme) {
            // ignore
        }

        throw new IllegalArgumentException("Cannot convert '" + raw + "' to " + targetType.getName());
    }

    // ================== Mission helpers (agent-facing) ==================

    private void handleBufferedMissionItemInt(String[] params) throws Exception {
        if (params.length < 3) {
            throw new Exception("MISSION_ITEM_INT expects at least 3 params: latDeg, lonDeg, altM");
        }
        double latDeg = Double.parseDouble(params[0]);
        double lonDeg = Double.parseDouble(params[1]);
        float altM    = toFloat(params[2]);

        boolean isTakeoff = missionBuffer.isEmpty(); // first mission_item -> TAKEOFF
        missionBuffer.add(new MissionWp(latDeg, lonDeg, altM, isTakeoff));

        System.out.println("[MISSION] Buffered WP #" + (missionBuffer.size() - 1) +
                (isTakeoff ? " (TAKEOFF)" : "") +
                " latDeg=" + latDeg + " lonDeg=" + lonDeg + " alt=" + altM);
    }


    private void startMissionUpload(int firstItem, int lastItem) throws IOException {
        int n = missionBuffer.size();
        if (n == 0) {
            System.out.println("[MISSION] startMissionUpload: buffer empty.");
            return;
        }

        if (firstItem < 0 || firstItem >= n) firstItem = 0;
        if (lastItem  < 0 || lastItem  >= n) lastItem  = n - 1;

        System.out.println("[MISSION] Uploading " + n +
                " waypoints. first=" + firstItem + " last=" + lastItem);

        MissionClearAll clear = MissionClearAll.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .build();
        sendMavlinkMessage(clear);
        System.out.println("[MISSION] Sent MISSION_CLEAR_ALL.");
        sleepQuiet(100);

        MissionCount count = MissionCount.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .count((short) n)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .build();
        sendMavlinkMessage(count);
        System.out.println("[MISSION] Sent MISSION_COUNT = " + n);
        sleepQuiet(100);

        for (int seq = 0; seq < n; seq++) {
            MissionWp wp = missionBuffer.get(seq);

            int latE7 = (int) Math.round(wp.latDeg * 1e7);
            int lonE7 = (int) Math.round(wp.lonDeg * 1e7);
            float alt = wp.altM;

            // ------------------------------
            // FIX: TAKEOFF for seq 0, WAYPOINT otherwise
            // ------------------------------
            MavCmd cmd = wp.isTakeoff
                    ? MavCmd.MAV_CMD_NAV_TAKEOFF
                    : MavCmd.MAV_CMD_NAV_WAYPOINT;

            MissionItemInt item = MissionItemInt.builder()
                    .targetSystem((short) targetSystem)
                    .targetComponent((short) targetComponent)
                    .seq((short) seq)

                    // ------------------------------
                    // FIX: correct PX4 mission frame
                    // ------------------------------
                    .frame(EnumValue.of(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT_INT))

                    // ------------------------------
                    // FIXED COMMAND (TAKEOFF or WAYPOINT)
                    // ------------------------------
                    .command(EnumValue.of(cmd))

                    // PX4 allowed to start mission at seq 0
                    .current((short) (seq == firstItem ? 1 : 0))
                    .autocontinue((short) 1)

                    // mission parameters
                    .param1(wp.isTakeoff ? 0f : 0f)   // hold time
                    .param2(1f)                       // acceptance radius
                    .param3(0f)                       // pass radius
                    .param4(0f)                       // yaw

                    // position
                    .x(latE7)
                    .y(lonE7)
                    .z(alt)

                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .build();

            sendMavlinkMessage(item);

            System.out.println("[MISSION] Sent MISSION_ITEM_INT seq=" + seq +
                    (wp.isTakeoff ? " (TAKEOFF)" : "") +
                    " latE7=" + latE7 + " lonE7=" + lonE7 + " alt=" + alt);

            sleepQuiet(50);
        }


        MissionSetCurrent setCur = MissionSetCurrent.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .seq((short) firstItem)
                .build();
        sendMavlinkMessage(setCur);
        System.out.println("[MISSION] Sent MISSION_SET_CURRENT seq=" + firstItem);
        sleepQuiet(50);

        CommandLong start = CommandLong.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .command(MavCmd.MAV_CMD_MISSION_START)
                .confirmation((short) 0)
                .param1((float) firstItem)
                .param2((float) lastItem)
                .param3(0f)
                .param4(0f)
                .param5(0f)
                .param6(0f)
                .param7(0f)
                .build();
        sendMavlinkMessage(start);
        System.out.println("[MISSION] Sent MAV_CMD_MISSION_START.");
    }

    private void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // ================== Dispatcher ==================

    private void processMavlinkCommand(String text) throws Exception {
        ParsedCommand cmd = parseCommand(text);
        String name = cmd.name;
        String[] params = cmd.params;

        /* ============================================================
        * SPECIAL CASE: SET_MODE (MESSAGE ID 11 FROM common.xml)
        * ============================================================ */
        if (name.equals("SET_MODE")) {

            // SET_MODE(target_system, base_mode, custom_mode)
            // Fill missing params with defaults.
            String targetSys  = params.length > 0 ? params[0] : String.valueOf(targetSystem);
            String baseMode   = params.length > 1 ? params[1] : "1";   // use custom mode
            String customMode = params.length > 2 ? params[2] : "0";   // PX4 interprets this

            // Rebuild ordered param list to match message fields:
            //   0 → target_system
            //   1 → base_mode
            //   2 → custom_mode
            String[] fixed = new String[] {
                targetSys,
                baseMode,
                customMode
            };

            System.out.println("[SET_MODE] target=" + targetSys +
                            " base=" + baseMode +
                            " custom=" + customMode);

            Object payload = buildGenericPayload("SET_MODE", fixed);
            sendMavlinkMessage(payload);
            return;
        }


        System.out.println();
        System.out.println("Processing: " + name + " with " + params.length + " parameters");

        if ("MISSION_ITEM_INT".equals(name)) {
            handleBufferedMissionItemInt(params);
            return;
        }

        if (name.startsWith("MAV_CMD_")) {
            MavCmd command = MavCmd.valueOf(name);

            if (command == MavCmd.MAV_CMD_MISSION_START) {
                int first = 0;
                int last  = -1;
                if (params.length > 0) first = toInt(params[0]);
                if (params.length > 1) last  = toInt(params[1]);
                startMissionUpload(first, last);
                return;
            }

            if (shouldUseCommandInt(name)) {
                sendCommandInt(command, params);
            } else {
                sendCommandLong(command, params);
            }
            return;
        }

        Object payload = buildGenericPayload(name, params);
        if (payload == null) {
            throw new Exception("Unknown MAVLink message type: " + name);
        }

        sendMavlinkMessage(payload);
    }

    // ================== write() used by agent ==================

    @Override
    public boolean write(String s) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }

        System.out.println("[NRJ WRITE] RAW = [" + s + "]");

        try {
            processMavlinkCommand(s.trim());
            System.out.println("[NRJ WRITE] Processed as MAVLink message.");
            return true;
        } catch (Exception e) {
            System.err.println("[NRJ WRITE] MAVLink processing failed (" + e.getMessage()
                    + "). Sending raw via serialWrite().");
            try {
                serialWrite(s);
                return true;
            } catch (Exception se) {
                System.err.println("[NRJ WRITE] Fallback serialWrite failed: " + se.getMessage());
                return false;
            }
        }
    }

    // ================== Original NRJ serialRead() ==================

    @Override
    public String serialRead() {
        try {
            if (comPort.getInputStream().available() == 0) return "";

            String s = "";
            String start = "";
            String end = "";

            comPort.enableReceiveTimeout(100);

            InputStream in = comPort.getInputStream();
            int data;

            while (!start.equals(preamble)) {
                data = in.read();
                if ((char) data == preamble.charAt(start.length())) {
                    start = start + (char) data;
                } else {
                    start = "";
                }
            }

            while (!end.equals(endMessage)) {
                data = in.read();
                if ((char) data == endMessage.charAt(end.length())) {
                    end = end + (char) data;
                } else {
                    s = s + end + (char) data;
                    end = "";
                }
            }

            System.out.println("lendo " + s);
            String[] strings = s.split(startMessage);
            System.out.println("lendo (2) " + s);
            int number = Integer.parseInt(strings[0]);
            String message = strings[1];

            if (number == message.length()) {
                System.out.println("Leu: " + message);
                return message;
            } else {
                System.out.println("Message conversation error " + message);
                for (int i = 0; i < message.length(); i++) {
                    int teste = message.charAt(i);
                    System.out.println(message.charAt(i) + " - " + teste);
                }
                return "Message conversation error";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "Message conversation error";
        } catch (UnsupportedCommOperationException e) {
            e.printStackTrace();
            return "Message conversation error";
        }
    }

    @Override
    public void execEmbeddedAction(EmbeddedAction action) {
        System.err.println("Method execEmbeddedAction not implemented in " + this.getClass().getName());
    }

    @Override
    public boolean openConnection() {
        if (!connected)
            this.connected = super.openConnection();
        return this.connected;
    }

    @Override
    public void closeConnection() {
        super.closeConnection();
        this.connected = false;
    }
}
