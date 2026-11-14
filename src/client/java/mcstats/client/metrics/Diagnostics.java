package mcstats.client.metrics;

import java.lang.management.ManagementFactory;
import org.slf4j.LoggerFactory;

/** Opt-in development measurements. Never enabled or logged in a normal installation. */
public final class Diagnostics {
    private static final boolean ENABLED=Boolean.getBoolean("mcstats.verify");
    private static final com.sun.management.ThreadMXBean BEAN=ENABLED&&ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean b&&b.isThreadAllocatedMemorySupported()&&b.isThreadAllocatedMemoryEnabled()?b:null;
    private static final long[] nanos=new long[4],bytes=new long[4],counts=new long[4];
    private static long started,frames;
    public static long begin(){return ENABLED?System.nanoTime():0;}
    public static long allocated(){return ENABLED&&BEAN!=null?BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId()):0;}
    public static void end(int kind,long start,long allocated){if(ENABLED){nanos[kind]+=System.nanoTime()-start;bytes[kind]+=allocated()-allocated;counts[kind]++;}}
    public static void completedFrame(int reference){
        if(!ENABLED)return;long now=System.nanoTime();frames++;
        if(started==0)started=now;
        if(now-started<5_000_000_000L)return;
        var log=LoggerFactory.getLogger("mcstats.verify");
        log.info("Completed-frame window={} FPS; Minecraft F3 reference={} FPS",Math.round(frames*1e9/(now-started)),reference);
        log.info("HUD half-second FPS={}",mcstats.client.MCStatsClient.monitor.measuredFps);
        for(int i=0;i<4;i++)if(counts[i]>0)log.info("{}: {} ns/call, {} bytes/call ({} calls)",new String[]{"sampler","HUD extraction","dashboard extraction","geometry build"}[i],nanos[i]/counts[i],BEAN==null?"unavailable":Long.toString(bytes[i]/counts[i]),counts[i]);
        java.util.Arrays.fill(nanos,0);java.util.Arrays.fill(bytes,0);java.util.Arrays.fill(counts,0);started=now;frames=0;
    }
}
