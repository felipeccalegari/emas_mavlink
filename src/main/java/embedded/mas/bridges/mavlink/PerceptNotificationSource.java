package embedded.mas.bridges.mavlink;

/** A device that can notify its agent when new perceptions are available. */
public interface PerceptNotificationSource {
    void setPerceptListener(Runnable listener);
}
