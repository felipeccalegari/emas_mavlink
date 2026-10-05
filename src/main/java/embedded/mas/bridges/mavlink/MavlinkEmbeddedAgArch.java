package embedded.mas.bridges.mavlink;

import java.util.Collection;

import embedded.mas.bridges.jacamo.DefaultDevice;
import embedded.mas.bridges.jacamo.DefaultEmbeddedAgArch;

/** Adds perception wake-ups while preserving the original agent and architecture behavior. */
public class MavlinkEmbeddedAgArch extends DefaultEmbeddedAgArch {
    @Override
    public void setDevices(Collection<DefaultDevice> devices) {
        super.setDevices(devices);
        if (devices != null) {
            for (DefaultDevice device : devices) {
                if (device instanceof PerceptNotificationSource) {
                    ((PerceptNotificationSource) device).setPerceptListener(() ->
                            getTS().getAgArch().wakeUpSense());
                }
            }
        }
    }
}
