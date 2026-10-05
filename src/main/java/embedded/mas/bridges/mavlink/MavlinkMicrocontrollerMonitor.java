package embedded.mas.bridges.mavlink;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonParsingException;

import embedded.mas.bridges.jacamo.IPhysicalInterface;
import embedded.mas.bridges.javard.MicrocontrollerMonitor;
import embedded.mas.exception.PerceivingException;
import jason.asSyntax.Literal;

/** Reads MAVLink JSON without the original monitor's random inter-read delay. */
public class MavlinkMicrocontrollerMonitor extends MicrocontrollerMonitor {
    private final MavlinkJSONWatcherDevice device;
    private final IPhysicalInterface microcontroller;

    public MavlinkMicrocontrollerMonitor(MavlinkJSONWatcherDevice device, IPhysicalInterface microcontroller) {
        // The base monitor is never started; its legacy list remains unused.
        super(new ArrayList<Collection<Literal>>(), microcontroller);
        this.device = device;
        this.microcontroller = microcontroller;
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            try {
                if (!readAndPublish()) {
                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {
                interrupt();
                return;
            } catch (PerceivingException e) {
                if (e.getMessage() != null) {
                    System.err.println(e.getMessage());
                }
                e.printStackTrace();
            }
        }
    }

    @Override
    public void decode() throws PerceivingException {
        readAndPublish();
    }

    private boolean readAndPublish() throws PerceivingException {
        String json = microcontroller.read();
        if (json == null || json.isEmpty()) {
            return false;
        }
        if (json.equals("Message conversation error")) {
            throw new PerceivingException();
        }

        Collection<Literal> percepts = new ArrayList<Literal>();
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(json.getBytes()))) {
            JsonObject jsonObject = reader.readObject();
            for (String key : jsonObject.keySet()) {
                Object value = jsonObject.get(key);
                String belief = key + "(";
                if (value instanceof JsonArray) {
                    belief += value.toString().replace("[", "").replace("]", "");
                } else {
                    belief += value;
                }
                percepts.add(Literal.parseLiteral(belief + ")"));
            }
        } catch (JsonParsingException e) {
            throw new PerceivingException("Invalid JSON: " + json);
        }
        device.updateLatestBeliefs(percepts);
        return true;
    }
}
