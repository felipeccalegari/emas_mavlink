package embedded.mas.bridges.mavlink;

import embedded.mas.bridges.jacamo.DefaultDevice;
import embedded.mas.bridges.jacamo.IExternalInterface;
import jason.asSyntax.Atom;

/** Optional perception-notification behavior for MAVLink device subclasses. */
public abstract class MavlinkDefaultDevice extends DefaultDevice implements PerceptNotificationSource {
    private final PerceptNotification notification = new PerceptNotification();

    protected MavlinkDefaultDevice(Atom id, IExternalInterface microcontroller) {
        super(id, microcontroller);
    }

    @Override
    public void setPerceptListener(Runnable listener) {
        notification.setListener(listener);
    }

    protected void notifyPerceptAvailable() {
        notification.fire();
    }
}
