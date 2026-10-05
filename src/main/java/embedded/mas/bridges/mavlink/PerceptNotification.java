package embedded.mas.bridges.mavlink;

final class PerceptNotification {
    private volatile Runnable listener;

    void setListener(Runnable listener) {
        this.listener = listener;
    }

    void fire() {
        Runnable current = listener;
        if (current != null) {
            current.run();
        }
    }
}
