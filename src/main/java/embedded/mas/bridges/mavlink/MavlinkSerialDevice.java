package embedded.mas.bridges.mavlink;

import embedded.mas.bridges.jacamo.DefaultDevice;
import embedded.mas.bridges.jacamo.EmbeddedAction;
import embedded.mas.bridges.jacamo.IPhysicalInterface;
import embedded.mas.bridges.jacamo.SerialDevice;
import embedded.mas.bridges.jacamo.SerialEmbeddedAction;
import jason.asSemantics.Unifier;
import jason.asSyntax.Atom;

/** Serial action formatting used by the MAVLink adaptation. */
public class MavlinkSerialDevice extends SerialDevice {
    public MavlinkSerialDevice(Atom id, IPhysicalInterface microcontroller) {
        super(id, microcontroller);
    }

    @Override
    public boolean execEmbeddedAction(Atom actionName, Object[] args, Unifier un) {
        return sendAction(this, actionName, args);
    }

    // JSONWatcherDevice already extends SerialDevice, so its adaptation shares this
    // behavior through this helper rather than trying to inherit two Java classes.
    static boolean sendAction(DefaultDevice device, Atom actionName, Object[] args) {
        try {
            EmbeddedAction action = device.getEmbeddedAction(actionName);
            if (action instanceof SerialEmbeddedAction) {
                String actuationName = ((SerialEmbeddedAction) action).getActuationName().toString();
                String message;
                if (args != null && args.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < args.length; i++) {
                        sb.append(args[i].toString().trim());
                        if (i < args.length - 1) {
                            sb.append(",");
                        }
                    }
                    message = actuationName + "(" + sb + ")";
                } else {
                    message = actuationName;
                }
                return ((IPhysicalInterface) device.getMicrocontroller()).write(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
