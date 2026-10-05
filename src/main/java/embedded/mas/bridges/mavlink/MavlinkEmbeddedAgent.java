package embedded.mas.bridges.mavlink;

import embedded.mas.bridges.jacamo.DefaultDevice;
import embedded.mas.bridges.jacamo.EmbeddedAgent;

/** Connects MAVLink perception notifications to Jason's sensing wake-up. */
public abstract class MavlinkEmbeddedAgent extends EmbeddedAgent {
    @Override
    public void initAg() {
        super.initAg();
        for (DefaultDevice device : getDevices()) {
            if (device instanceof PerceptNotificationSource) {
                ((PerceptNotificationSource) device).setPerceptListener(() ->
                        getTS().getAgArch().wakeUpSense());
            }
        }
    }
}
