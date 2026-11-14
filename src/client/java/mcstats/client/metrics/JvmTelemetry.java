package mcstats.client.metrics;

import java.lang.management.ManagementFactory;

/** Low-rate, worker-thread telemetry; NaN/-1 explicitly mean unavailable. */
public final class JvmTelemetry {
    private final java.lang.management.MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final java.util.List<java.lang.management.GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    private final java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    public record Snapshot(long heapUsed, long heapMax, long gcCount, long gcMillis, double processCpu, double systemCpu) {}
    public Snapshot sample() {
        var heap = memory.getHeapMemoryUsage();
        long count = 0, millis = 0;
        boolean counts = !collectors.isEmpty(), times = counts;
        for (var gc : collectors) {
            long c = gc.getCollectionCount(), t = gc.getCollectionTime();
            counts &= c >= 0; times &= t >= 0;
            if (c >= 0) count += c; if (t >= 0) millis += t;
        }
        double process = Double.NaN, system = Double.NaN;
        if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
            process = available(extended.getProcessCpuLoad()); system = available(extended.getCpuLoad());
        }
        return new Snapshot(heap.getUsed(), heap.getMax(), counts ? count : -1, times ? millis : -1, process, system);
    }
    private static double available(double x) { return x >= 0 && x <= 1 ? x : Double.NaN; }
}
