package mcstats.client.metrics;

import java.util.Arrays;

/** Render-thread-owned ring; allocation-free writes and time-based expiry. */
public final class FrameWindow {
    public static final long WINDOW_NANOS = 30_000_000_000L;
    private final long[] timestamps;
    private final double[] durations;
    private int start, size;
    private long last = Long.MIN_VALUE;
    public FrameWindow(int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("capacity");
        timestamps = new long[capacity]; durations = new double[capacity];
    }
    public void add(long now, double millis) {
        if (!Double.isFinite(millis) || millis <= 0) return;
        if (last != Long.MIN_VALUE && now <= last) throw new IllegalArgumentException("timestamps must increase");
        last = now; expire(now);
        if (size == durations.length) { start = (start + 1) % durations.length; size--; }
        int index = (start + size++) % durations.length;
        timestamps[index] = now; durations[index] = millis;
    }
    private void expire(long now) {
        while (size > 0 && now - timestamps[start] >= WINDOW_NANOS) {
            start = (start + 1) % durations.length; size--;
        }
    }
    public Snapshot snapshot(long now) {
        expire(now);
        double[] frames = new double[size];
        for (int i = 0; i < size; i++) frames[i] = durations[(start + i) % durations.length];
        double seconds = size == 0 ? 0 : Math.min(30, (now - timestamps[start]) / 1e9 + durations[start] / 1000);
        return new Snapshot(frames, seconds);
    }
    public void clear() { start = size = 0; last = Long.MIN_VALUE; }
    public static final class Snapshot {
        private final double[] frames;
        private final double seconds, averageFps, onePercentLowFps;
        public Snapshot(double[] frames, double seconds) {
            if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("seconds");
            this.frames = frames.clone(); this.seconds=seconds;
            for (double f : frames) if (!Double.isFinite(f) || f <= 0) throw new IllegalArgumentException("duration");
            averageFps=frames.length==0?Double.NaN:1000/Arrays.stream(frames).average().orElseThrow();
            if(frames.length==0)onePercentLowFps=Double.NaN;
            else {
                double[] sorted=frames.clone();Arrays.sort(sorted);
                int count=Math.max(1,(int)Math.ceil(sorted.length*.01));double sum=0;
                for(int i=sorted.length-count;i<sorted.length;i++)sum+=sorted[i];
                onePercentLowFps=1000*count/sum;
            }
        }
        public double[] frames() { return frames.clone(); }
        public double seconds() { return seconds; }
        public int count() { return frames.length; }
        public double averageFps() { return averageFps; }
        public double onePercentLowFps() { return onePercentLowFps; }
    }
}
