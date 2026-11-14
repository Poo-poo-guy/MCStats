package mcstats.client.animation;

/** Exact critically damped spring. Retargeting preserves velocity and position. */
public final class Spring {
    private double value, velocity;
    public Spring(double initial) {
        if (!Double.isFinite(initial)) throw new IllegalArgumentException("initial");
        value = initial;
    }
    public double update(double target, double seconds, double settlingSeconds, boolean reducedMotion) {
        if (!Double.isFinite(target) || !Double.isFinite(seconds) || seconds < 0
                || !Double.isFinite(settlingSeconds) || settlingSeconds <= 0) throw new IllegalArgumentException("spring input");
        if (reducedMotion) { value = target; velocity = 0; return value; }
        double omega = 6 / settlingSeconds, offset = value - target;
        double coefficient = velocity + omega * offset, decay = Math.exp(-omega * seconds);
        value = target + (offset + coefficient * seconds) * decay;
        velocity = (velocity - omega * coefficient * seconds) * decay;
        return value;
    }
    public double value() { return value; }
}
