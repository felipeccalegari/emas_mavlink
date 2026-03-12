package embedded.mas.bridges.javard;

import java.io.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import arduino.NRJ;
import embedded.mas.bridges.jacamo.EmbeddedAction;
import embedded.mas.bridges.jacamo.IPhysicalInterface;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

// MAVLink
import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.*;
import io.dronefleet.mavlink.minimal.*;
import io.dronefleet.mavlink.util.EnumValue;

public class NRJ4EmbeddedMas extends NRJ implements IPhysicalInterface {

    /* ============================================================
     * Legacy framed serial (kept available)
     * ============================================================ */
    private final String preamble = "==";
    private final String startMessage = "::";
    private final String endMessage = "--";

    /* ============================================================
     * MAVLink IDs
     * ============================================================ */
    private final int systemId = 200;      // Jason (GCS) sysid
    private final int componentId = 50;    // Jason (GCS) component
    private final int targetSystem = 1;    // vehicle sysid
    private final int targetComponent = 1; // vehicle component

    /* ============================================================
     * MAVLink TX (serialize + send to serial)
     * ============================================================ */
    private boolean mavTxInit = false;
    private MavlinkConnection mavTxConn;
    private ByteArrayOutputStream mavTxOut;
    private final Object mavTxLock = new Object();

    /* ============================================================
     * MAVLink RX (read from serial)
     * ============================================================ */
    private MavlinkConnection mavRxConn;

    /* ============================================================
     * State
     * ============================================================ */
    private boolean connected = false;

    /* ============================================================
     * Heartbeat (Jason acts like GCS)
     * ============================================================ */
    private volatile boolean heartbeatRunning = false;
    private Thread heartbeatThread;

    /* ============================================================
     * Mission buffer + mission busy guard (critical to not break missions)
     * ============================================================ */
    private volatile boolean missionBusy = false;

    private static class MissionWp {
        final double latDeg;
        final double lonDeg;
        final float altM;
        final boolean isTakeoff;
        MissionWp(double latDeg, double lonDeg, float altM, boolean isTakeoff) {
            this.latDeg = latDeg;
            this.lonDeg = lonDeg;
            this.altM = altM;
            this.isTakeoff = isTakeoff;
        }
    }
    private final List<MissionWp> missionBuffer = new ArrayList<>();

    /* ============================================================
     * Dialects for dynamic payload lookup
     * ============================================================ */
    private static final String[] DIALECT_PACKAGES = new String[] {
            "io.dronefleet.mavlink.common",
            "io.dronefleet.mavlink.minimal",
            "io.dronefleet.mavlink.ardupilotmega",
            "io.dronefleet.mavlink.uavionix"
    };

    /* ============================================================
     * Ctor
     * ============================================================ */
    public NRJ4EmbeddedMas(String port, int baud)
            throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException {
        super(port, baud);
        openConnection();
    }

    /* ============================================================
     * IPhysicalInterface
     * ============================================================ */
    @Override
    public String read() {
        return serialRead();
    }

    @Override
    public boolean write(String s) {
        if (s == null || s.trim().isEmpty()) return false;

        try {
            processMavlinkCommand(s.trim());
            return true;
        } catch (Exception e) {
            // fallback to legacy serialWrite, but don't crash
            try {
                serialWrite(s);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    @Override
    public boolean openConnection() {
        if (!connected) {
            connected = super.openConnection();
            if (connected) {
                try { ensureMavlinkTx(); } catch (Exception ignored) {}
                try { initMavlinkRx(); } catch (Exception ignored) {}
                startGcsHeartbeat();
            }
        }
        return connected;
    }

    @Override
    public void closeConnection() {
        heartbeatRunning = false;
        super.closeConnection();
        connected = false;
    }

    @Override
    public void execEmbeddedAction(EmbeddedAction action) {
        // not used here
    }

    /* ============================================================
     * MAVLink TX
     * ============================================================ */
    private void ensureMavlinkTx() throws IOException {
        synchronized (mavTxLock) {
            if (mavTxInit) return;
            mavTxOut = new ByteArrayOutputStream();
            mavTxConn = MavlinkConnection.create(new PipedInputStream(), mavTxOut);
            mavTxInit = true;
            System.out.println("[NRJ] MAVLink TX ready.");
        }
    }

    private void sendMavlink(Object payload) throws IOException {
        ensureMavlinkTx();

        final byte[] bytes;
        synchronized (mavTxLock) {
            mavTxOut.reset();
            mavTxConn.send2(systemId, componentId, payload);
            bytes = mavTxOut.toByteArray();
        }

        if (bytes.length == 0) return;

        comPort.getOutputStream().write(bytes);
        comPort.getOutputStream().flush();
    }

    /* ============================================================
     * MAVLink RX init
     * ============================================================ */
    private void initMavlinkRx() {
        if (mavRxConn != null) return;
        try {
            InputStream is = comPort.getInputStream();
            mavRxConn = MavlinkConnection.create(is, new ByteArrayOutputStream());
        } catch (Exception ignored) {
            // leave null; serialRead will retry
        }
    }

    /* ============================================================
     * GCS Heartbeat
     * ============================================================ */
    private void startGcsHeartbeat() {
        if (heartbeatRunning) return;
        heartbeatRunning = true;

        heartbeatThread = new Thread(() -> {
            try {
                while (heartbeatRunning) {
                    Heartbeat hb = Heartbeat.builder()
                            .type(MavType.MAV_TYPE_GCS)
                            .autopilot(MavAutopilot.MAV_AUTOPILOT_INVALID)
                            .baseMode(EnumValue.of(MavModeFlag.MAV_MODE_FLAG_CUSTOM_MODE_ENABLED))
                            .customMode(0)
                            .systemStatus(MavState.MAV_STATE_ACTIVE)
                            .build();
                    sendMavlink(hb);
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) { }
        });

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        System.out.println("[GCS] Heartbeat started.");
    }

    /* ============================================================
     * SERIAL READ
     *   - DO NOT consume MAVLink RX during mission upload (critical)
     *   - Otherwise: try MAVLink telemetry → JSON perceptions
     *   - If nothing: try legacy framed serial
     * ============================================================ */
    @Override
    public String serialRead() {
        try {
            if (missionBusy) {
                // prevent mission protocol messages (MISSION_REQUEST/ACK) from being eaten
                return "";
            }

            String mav = readMavlinkTelemetryNonBlocking();
            if (mav != null && !mav.isEmpty()) return mav;

            // optional: if you still use framed serial elsewhere
            // return readFramedSerial();
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String readMavlinkTelemetryNonBlocking() {
        try {
            if (mavRxConn == null) initMavlinkRx();
            if (mavRxConn == null) return "";

            // Avoid hard blocking when nothing is there
            InputStream is = comPort.getInputStream();
            if (is.available() <= 0) return "";

            MavlinkMessage<?> msg = mavRxConn.next(); // may block briefly until a full frame is read
            if (msg == null) return "";

            Object payload = msg.getPayload();
            if (payload == null) return "";

            return mavPayloadToJson(payload);
        } catch (Exception e) {
            return "";
        }
    }

    /* ============================================================
     * MAVLink payload → JSON perception
     *  Output format: {"<msgname_lower>":[v1,v2,...]}
     *  Designed to be accepted by MicrocontrollerMonitor JSON parser
     * ============================================================ */
    private String mavPayloadToJson(Object payload) {
        try {
            Class<?> cls = payload.getClass();
            String beliefName = cls.getSimpleName().toLowerCase(Locale.ROOT);

            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"").append(beliefName).append("\":[");

            boolean first = true;

            // Dronefleet message classes keep MAVLink field order in declaredFields
            Field[] fields = cls.getDeclaredFields();
            for (Field f : fields) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod)) continue;
                if (f.isSynthetic()) continue;

                f.setAccessible(true);
                Object v = f.get(payload);

                // Rule 1: drop byte[]
                if (v instanceof byte[]) continue;
                if (v == null) continue;

                // Rule 3: drop NaN/Infinity (float/double)
                if (v instanceof Float) {
                    float fv = ((Float) v).floatValue();
                    if (Float.isNaN(fv) || Float.isInfinite(fv)) continue;
                } else if (v instanceof Double) {
                    double dv = ((Double) v).doubleValue();
                    if (Double.isNaN(dv) || Double.isInfinite(dv)) continue;
                }

                if (!first) sb.append(",");
                first = false;

                appendJsonValue(sb, v);
            }

            sb.append("]}");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void appendJsonValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
            return;
        }

        // Rule 2: EnumValue
        if (v instanceof EnumValue) {
            EnumValue<?> ev = (EnumValue<?>) v;
            Object entry = ev.entry();
            if (entry != null) {
                sb.append("\"").append(escapeJson(entry.toString())).append("\"");
            } else {
                sb.append(ev.value());
            }
            return;
        }

        // Primitive arrays (float[], int[], etc.)
        Class<?> vc = v.getClass();
        if (vc.isArray()) {
            Class<?> ct = vc.getComponentType();
            if (ct == byte.class) {
                // Rule 1: drop byte[] already handled; but if we get here, emit empty
                sb.append("[]");
                return;
            }

            sb.append("[");
            int n = Array.getLength(v);
            boolean first = true;
            for (int i = 0; i < n; i++) {
                Object elem = Array.get(v, i);
                if (elem == null) continue;

                // Drop NaN/Inf inside arrays too
                if (elem instanceof Float) {
                    float fv = ((Float) elem).floatValue();
                    if (Float.isNaN(fv) || Float.isInfinite(fv)) continue;
                } else if (elem instanceof Double) {
                    double dv = ((Double) elem).doubleValue();
                    if (Double.isNaN(dv) || Double.isInfinite(dv)) continue;
                }

                if (!first) sb.append(",");
                first = false;

                // array elements: numbers/bools unquoted; strings quoted
                if (elem instanceof EnumValue) {
                    EnumValue<?> ev = (EnumValue<?>) elem;
                    Object entry = ev.entry();
                    if (entry != null) sb.append("\"").append(escapeJson(entry.toString())).append("\"");
                    else sb.append(ev.value());
                } else if (elem instanceof String) {
                    sb.append("\"").append(escapeJson((String) elem)).append("\"");
                } else if (elem instanceof Character) {
                    sb.append("\"").append(escapeJson(String.valueOf(elem))).append("\"");
                } else if (elem instanceof Boolean || elem instanceof Number) {
                    sb.append(elem.toString());
                } else {
                    sb.append("\"").append(escapeJson(elem.toString())).append("\"");
                }
            }
            sb.append("]");
            return;
        }

        // Strings
        if (v instanceof String) {
            sb.append("\"").append(escapeJson((String) v)).append("\"");
            return;
        }

        // Numbers / booleans
        if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
            return;
        }

        // Enums
        if (v.getClass().isEnum()) {
            sb.append("\"").append(escapeJson(v.toString())).append("\"");
            return;
        }

        // Fallback
        sb.append("\"").append(escapeJson(v.toString())).append("\"");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b");  break;
                case '\f': out.append("\\f");  break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    /* ============================================================
     * Optional legacy framed serial reader (unchanged behavior)
     * ============================================================ */
    @SuppressWarnings("unused")
    private String readFramedSerial() {
        try {
            if (comPort.getInputStream().available() == 0) return "";

            String s = "";
            String start = "";
            String end = "";

            InputStream in = comPort.getInputStream();
            int data;

            while (!start.equals(preamble)) {
                data = in.read();
                if (data < 0) return "";
                if ((char) data == preamble.charAt(start.length())) start += (char) data;
                else start = "";
            }

            while (!end.equals(endMessage)) {
                data = in.read();
                if (data < 0) return "";
                if ((char) data == endMessage.charAt(end.length())) end += (char) data;
                else { s += end + (char) data; end = ""; }
            }

            String[] strings = s.split(startMessage);
            if (strings.length < 2) return "";
            int number = Integer.parseInt(strings[0]);
            String message = strings[1];

            return (number == message.length()) ? message : "";
        } catch (Exception e) {
            return "";
        }
    }

    /* ============================================================
     * Command parsing NAME(p1,p2,...)
     * ============================================================ */
    private static class ParsedCommand {
        final String name;
        final String[] params;
        ParsedCommand(String name, String[] params) {
            this.name = name;
            this.params = params;
        }
    }

    private ParsedCommand parseCommand(String text) throws Exception {
        text = text.trim();
        Pattern pattern = Pattern.compile("^([A-Z0-9_]+)\\s*\\(([^)]*)\\)\\s*$");
        Matcher matcher = pattern.matcher(text);

        if (!matcher.matches()) {
            // allow bare NAME without params
            if (text.matches("^[A-Z0-9_]+$")) return new ParsedCommand(text, new String[0]);
            throw new Exception("Invalid command format: " + text);
        }

        String name = matcher.group(1).trim();
        String paramsStr = matcher.group(2).trim();

        String[] params;
        if (paramsStr.isEmpty()) params = new String[0];
        else params = paramsStr.split("\\s*,\\s*");

        return new ParsedCommand(name, params);
    }

    private int toInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
    private float toFloat(String s) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return 0f; }
    }

    /* ============================================================
     * MAV_CMD handling
     * ============================================================ */
    private boolean shouldUseCommandInt(String name) {
        return name.equals("MAV_CMD_NAV_WAYPOINT")
                || name.equals("MAV_CMD_NAV_LAND")
                || name.equals("MAV_CMD_NAV_RETURN_TO_LAUNCH");
    }

    private void sendCommandLong(MavCmd command, String[] params) throws IOException {
        float[] p = new float[7];
        for (int i = 0; i < 7; i++) p[i] = (i < params.length) ? toFloat(params[i]) : 0f;

        CommandLong msg = CommandLong.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .command(command)
                .confirmation((short) 0)
                .param1(p[0]).param2(p[1]).param3(p[2]).param4(p[3]).param5(p[4]).param6(p[5]).param7(p[6])
                .build();

        sendMavlink(msg);
    }

    private void sendCommandInt(MavCmd command, String[] params) throws IOException {
        float[] p = new float[7];
        for (int i = 0; i < 7; i++) p[i] = (i < params.length) ? toFloat(params[i]) : 0f;

        CommandInt msg = CommandInt.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .frame(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT)
                .command(command)
                .current((short) 0)
                .autocontinue((short) 0)
                .param1(p[0]).param2(p[1]).param3(p[2]).param4(p[3])
                .x((int) p[4])
                .y((int) p[5])
                .z(p[6])
                .build();

        sendMavlink(msg);
    }

    /* ============================================================
     * Mission buffer (agent-facing)
     * ============================================================ */
    private void handleBufferedMissionItemInt(String[] params) throws Exception {
        if (params.length < 3) {
            throw new Exception("MISSION_ITEM_INT expects at least 3 params: latDeg, lonDeg, altM");
        }

        double latDeg;
        double lonDeg;
        float  altM;

        try {
            latDeg = Double.parseDouble(params[0]);
            lonDeg = Double.parseDouble(params[1]);
            altM   = toFloat(params[2]);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid MISSION_ITEM_INT parameters: " + Arrays.toString(params));
        }

        // PX4 expects the FIRST mission item to be TAKEOFF
        boolean isTakeoff = missionBuffer.isEmpty();

        missionBuffer.add(new MissionWp(latDeg, lonDeg, altM, isTakeoff));

        System.out.println(
            "[MISSION] Buffered WP #" + (missionBuffer.size() - 1) +
            (isTakeoff ? " (TAKEOFF)" : "") +
            " lat=" + latDeg +
            " lon=" + lonDeg +
            " alt=" + altM
        );
    }


    private void startMissionUpload(int firstItem, int lastItem) throws IOException {
        int n = missionBuffer.size();
        if (n <= 0) {
            System.out.println("[MISSION] Buffer empty, nothing to upload.");
            return;
        }

        if (firstItem < 0 || firstItem >= n) firstItem = 0;
        if (lastItem  < 0 || lastItem  >= n) lastItem  = n - 1;

        System.out.println("[MISSION] Uploading " + n + " items (first=" + firstItem + ", last=" + lastItem + ")");

        // 1) MISSION_CLEAR_ALL
        MissionClearAll clear = MissionClearAll.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .build();
        sendMavlink(clear);
        System.out.println("[MISSION] Sent MISSION_CLEAR_ALL");
        sleepQuiet(80);

        // 2) MISSION_COUNT
        MissionCount count = MissionCount.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .count((short) n)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .build();
        sendMavlink(count);
        System.out.println("[MISSION] Sent MISSION_COUNT = " + n);
        sleepQuiet(80);

        // 3) Send each MISSION_ITEM_INT with correct seq/frame/command/targets
        for (int seq = 0; seq < missionBuffer.size(); seq++) {
            MissionWp wp = missionBuffer.get(seq);

            int latE7 = (int) Math.round(wp.latDeg * 1e7);
            int lonE7 = (int) Math.round(wp.lonDeg * 1e7);
            float alt = wp.altM;

            MavCmd cmd = wp.isTakeoff
                    ? MavCmd.MAV_CMD_NAV_TAKEOFF
                    : MavCmd.MAV_CMD_NAV_WAYPOINT;

            MissionItemInt item = MissionItemInt.builder()
                    .targetSystem((short) targetSystem)
                    .targetComponent((short) targetComponent)
                    .seq((short) seq)
                    .frame(EnumValue.of(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT))
                    .command(EnumValue.of(cmd))
                    .current((short) (seq == 0 ? 1 : 0))
                    .autocontinue((short) 1)
                    .param1(0f)        // hold time
                    .param2(1f)        // acceptance radius
                    .param3(0f)        // pass radius
                    .param4(Float.NaN)// yaw
                    .x(latE7)
                    .y(lonE7)
                    .z(alt)
                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .build();

            sendMavlink(item);

            System.out.println(
                "[MISSION] Sent seq=" + seq +
                (wp.isTakeoff ? " TAKEOFF" : " WP") +
                " lat=" + wp.latDeg +
                " lon=" + wp.lonDeg +
                " alt=" + wp.altM
            );

            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

        }


        // 4) MISSION_SET_CURRENT
        MissionSetCurrent setCur = MissionSetCurrent.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .seq((short) firstItem)
                .build();
        sendMavlink(setCur);
        System.out.println("[MISSION] Sent MISSION_SET_CURRENT seq=" + firstItem);
        sleepQuiet(80);

        // 5) MAV_CMD_MISSION_START (optional but works well with PX4)
        CommandLong start = CommandLong.builder()
                .targetSystem((short) targetSystem)
                .targetComponent((short) targetComponent)
                .command(MavCmd.MAV_CMD_MISSION_START)
                .confirmation((short) 0)
                .param1((float) firstItem)
                .param2((float) lastItem)
                .param3(0f).param4(0f).param5(0f).param6(0f).param7(0f)
                .build();
        sendMavlink(start);
        System.out.println("[MISSION] Sent MAV_CMD_MISSION_START first=" + firstItem + " last=" + lastItem);
    }


    private void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /* ============================================================
     * Generic MAVLink message building via reflection (Dronefleet builders)
     * Supports:
     *  - SET_MODE special case handled elsewhere
     *  - Other messages: NAME(p1,p2,...) positional
     *  - MAV_CMD_* handled separately
     * ============================================================ */
    private Class<?> resolvePayloadClass(String msgName) {
        String className = toClassName(msgName);
        for (String pkg : DIALECT_PACKAGES) {
            try {
                return Class.forName(pkg + "." + className);
            } catch (ClassNotFoundException ignored) { }
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

    private Object buildGenericPayload(String msgName, String[] params) throws Exception {
        Class<?> payloadClass = resolvePayloadClass(msgName);
        if (payloadClass == null) throw new Exception("Unknown MAVLink message: " + msgName);

        Method builderFactory = payloadClass.getMethod("builder");
        Object builder = builderFactory.invoke(null);
        Class<?> builderClass = builder.getClass();

        // Use payload declared field order as positional mapping
        Field[] all = payloadClass.getDeclaredFields();
        List<Field> fields = new ArrayList<>();
        for (Field f : all) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod)) continue;
            if (f.isSynthetic()) continue;
            fields.add(f);
        }

        Method[] methods = builderClass.getMethods();

        for (int i = 0; i < params.length && i < fields.size(); i++) {
            String fieldName = fields.get(i).getName();
            String rawVal = params[i];

            // find a builder method matching fieldName
            Method setter = null;
            for (Method m : methods) {
                if (!m.getName().equals(fieldName)) continue;
                if (m.getParameterCount() != 1) continue;
                setter = m;
                break;
            }
            if (setter == null) {
                // try camelCase variant
                String camel = snakeToCamel(fieldName);
                for (Method m : methods) {
                    if (!m.getName().equals(camel)) continue;
                    if (m.getParameterCount() != 1) continue;
                    setter = m;
                    break;
                }
            }
            if (setter == null) continue;

            Class<?> paramType = setter.getParameterTypes()[0];
            Type genericType = setter.getGenericParameterTypes()[0];

            Object converted = convertStringToType(rawVal, paramType, genericType);
            setter.invoke(builder, converted);
        }

        Method buildMethod = builderClass.getMethod("build");
        return buildMethod.invoke(builder);
    }

    private String snakeToCamel(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return s;
        if (!s.contains("_")) return s;
        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder();
        sb.append(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object convertToEnum(String raw, Class<?> enumClass) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) return null;

        try {
            int idx = Integer.parseInt(raw.trim());
            if (idx >= 0 && idx < constants.length) return constants[idx];
        } catch (Exception ignored) { }

        String upper = raw.trim().toUpperCase(Locale.ROOT);
        for (Object c : constants) {
            if (((Enum) c).name().equals(upper)) return c;
        }
        return constants[0];
    }

    private Object convertStringToType(String raw, Class<?> targetType, Type genericType) throws Exception {
        raw = raw.trim();

        if (targetType == String.class) return raw;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(raw);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(raw);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(raw);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(raw);
        if (targetType == short.class || targetType == Short.class) return Short.parseShort(raw);
        if (targetType == byte.class || targetType == Byte.class) return Byte.parseByte(raw);

        if (targetType == boolean.class || targetType == Boolean.class) {
            if (raw.equalsIgnoreCase("true") || raw.equals("1")) return true;
            if (raw.equalsIgnoreCase("false") || raw.equals("0")) return false;
            throw new IllegalArgumentException("Cannot parse boolean: " + raw);
        }

        if (targetType == BigInteger.class) return new BigInteger(raw);

        if (targetType.isEnum()) return convertToEnum(raw, targetType);

        if (targetType.getName().equals("io.dronefleet.mavlink.util.EnumValue")) {
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] instanceof Class) {
                    Class<?> enumClass = (Class<?>) args[0];
                    Object enumConst = convertToEnum(raw, enumClass);
                    if (enumConst != null) return EnumValue.of((Enum) enumConst);
                }
            }
            // fallback: numeric
            try {
                int v = Integer.parseInt(raw);
                // cannot build EnumValue without enum type; just return null and let caller skip
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        // last resort: String ctor
        try {
            Constructor<?> c = targetType.getConstructor(String.class);
            return c.newInstance(raw);
        } catch (NoSuchMethodException ignored) { }

        throw new IllegalArgumentException("Cannot convert '" + raw + "' to " + targetType.getName());
    }

    /* ============================================================
     * MAIN DISPATCH (restores your old behavior)
     * ============================================================ */
    private void processMavlinkCommand(String text) throws Exception {
        ParsedCommand cmd = parseCommand(text);
        String name = cmd.name;
        String[] params = cmd.params;

        // Special: SET_MODE is a MAVLink message, not MAV_CMD
        if (name.equals("SET_MODE")) {
            // SET_MODE(target_system, base_mode, custom_mode)
            String targetSys  = params.length > 0 ? params[0] : String.valueOf(targetSystem);
            String baseMode   = params.length > 1 ? params[1] : "1";
            String customMode = params.length > 2 ? params[2] : "0";

            Object payload = buildGenericPayload("SET_MODE", new String[] { targetSys, baseMode, customMode });
            sendMavlink(payload);
            return;
        }

        // Mission buffering
        if (name.equals("MISSION_ITEM_INT")) {
            handleBufferedMissionItemInt(params);
            return;
        }

        // Mission upload + start
        if (name.equals("MAV_CMD_MISSION_START")) {
            int first = (params.length > 0) ? toInt(params[0]) : 0;
            int last  = (params.length > 1) ? toInt(params[1]) : -1;
            startMissionUpload(first, last);
            return;
        }

        // Generic MAV_CMD_*
        if (name.startsWith("MAV_CMD_")) {
            MavCmd command = MavCmd.valueOf(name);
            if (shouldUseCommandInt(name)) sendCommandInt(command, params);
            else sendCommandLong(command, params);
            return;
        }

        // Generic MAVLink message (e.g. SET_POSITION_TARGET_LOCAL_NED)
        Object payload = buildGenericPayload(name, params);
        sendMavlink(payload);
    }
}
