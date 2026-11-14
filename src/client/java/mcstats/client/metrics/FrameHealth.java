package mcstats.client.metrics;

import java.util.Arrays;

/** Initial scoring model, not a calibrated benchmark. Latency is deliberately excluded. */
public final class FrameHealth {
    public enum Category { POOR, FAIR, GOOD, EXCELLENT }
    public record Result(boolean available, double score, Category category, double consistency,
                         double stutters, double target, double stalls, int stutterCount, int stallCount) {}
    private Category category;
    public Result evaluate(FrameWindow.Snapshot snapshot, int targetFps) {
        if (targetFps < 1 || targetFps > 1000) throw new IllegalArgumentException("target FPS");
        double[] frames = snapshot.frames();
        if (frames.length < 2 || snapshot.seconds() < 1) {
            category = null;
            return new Result(false, Double.NaN, null, 0, 0, 0, 0, 0, 0);
        }
        Arrays.sort(frames);
        double median = frames[frames.length / 2];
        double threshold = Math.max(50, median * 2), severe = Math.max(250, median * 5);
        int stutters = 0, stalls = 0, regular = 0;
        double mean = 0, m2 = 0, stallMillis = 0;
        for (double f : frames) {
            // Disjoint event classes; exceptional frames do not also inflate ordinary-frame variance.
            if (f >= severe) { stalls++; stallMillis += f; }
            else if (f >= threshold) stutters++;
            else { regular++; double d = f - mean; mean += d / regular; m2 += d * (f - mean); }
        }
        double consistency = regular < 2 ? 0 : clamp(1 - Math.sqrt(m2 / regular) / mean / .5);
        double stutterHealth = clamp(1 - stutters / snapshot.seconds() / 2);
        double stallHealth = clamp(1 - stallMillis / (snapshot.seconds() * 1000) / .1);
        double target = clamp((1000 / median) / targetFps);
        double score = 100 * (.35 * consistency + .30 * stutterHealth + .25 * target + .10 * stallHealth);
        int[] thresholds = {50, 75, 90};
        int next = category == null ? 0 : category.ordinal();
        if (category == null) { while (next < 3 && score >= thresholds[next]) next++; }
        else {
            while (next < 3 && score >= thresholds[next] + 2) next++;
            while (next > 0 && score < thresholds[next - 1] - 2) next--;
        }
        category = Category.values()[next];
        return new Result(true, score, category, consistency, stutterHealth, target, stallHealth, stutters, stalls);
    }
    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    public void reset() { category = null; }
}
