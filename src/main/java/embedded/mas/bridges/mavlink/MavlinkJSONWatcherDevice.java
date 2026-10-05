package embedded.mas.bridges.mavlink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import embedded.mas.bridges.jacamo.IPhysicalInterface;
import embedded.mas.bridges.jacamo.JSONWatcherDevice;
import embedded.mas.exception.PerceivingException;
import jason.asSemantics.Unifier;
import jason.asSyntax.Atom;
import jason.asSyntax.Literal;

/** Keeps the newest perception per functor and notifies Jason immediately. */
public class MavlinkJSONWatcherDevice extends JSONWatcherDevice implements PerceptNotificationSource {
    private final Map<String, Literal> latestBeliefs = new LinkedHashMap<String, Literal>();
    private final PerceptNotification notification = new PerceptNotification();

    public MavlinkJSONWatcherDevice(Atom id, IPhysicalInterface microcontroller) {
        super(id, microcontroller, false);
        new MavlinkMicrocontrollerMonitor(this, getMicrocontroller()).start();
    }

    @Override
    public Collection<Literal> getPercepts() throws PerceivingException {
        synchronized (latestBeliefs) {
            Collection<Literal> percepts = new ArrayList<Literal>(latestBeliefs.values());
            latestBeliefs.clear();
            return percepts;
        }
    }

    public void updateLatestBeliefs(Collection<Literal> percepts) {
        if (percepts == null || percepts.isEmpty()) {
            return;
        }
        synchronized (latestBeliefs) {
            for (Literal percept : percepts) {
                if (percept != null) {
                    latestBeliefs.put(percept.getFunctor(), percept);
                }
            }
        }
        notification.fire();
    }

    @Override
    public void setPerceptListener(Runnable listener) {
        notification.setListener(listener);
    }

    @Override
    public boolean execEmbeddedAction(Atom actionName, Object[] args, Unifier un) {
        return MavlinkSerialDevice.sendAction(this, actionName, args);
    }
}
