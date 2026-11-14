package mcstats.client.metrics;

import mcstats.client.config.HudConfig;
import net.minecraft.client.Minecraft;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Frame data stays on the client thread. JVM polling is isolated on one daemon worker. */
public final class Monitor implements AutoCloseable {
    private final FrameWindow window = new FrameWindow(65536);
    private final FrameRate rate=new FrameRate();
    private long lastPresentation;
    public double measuredFps=Double.NaN,sessionFps=Double.NaN;
    private final FrameHealth scoring = new FrameHealth();
    private final JvmTelemetry telemetry = new JvmTelemetry();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"MCStats telemetry"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean polling = new AtomicBoolean();
    public volatile JvmTelemetry.Snapshot jvm;
    public FrameWindow.Snapshot frames = new FrameWindow.Snapshot(new double[0],0);
    public FrameHealth.Result health = scoring.evaluate(frames,144);
    public final double[][] history = new double[4][120];
    public long historyNanos;
    public int historySize, historyHead, currentFps = -1, ping = -1, renderDistance = -1;
    public double frameMillis = Double.NaN, smoothFps = Double.NaN;
    private long lastFrame, lastScore, lastPoll;
    private boolean active;
    public Monitor() { for (double[] h : history) java.util.Arrays.fill(h,Double.NaN); }
    public void frame(Minecraft mc, HudConfig config, boolean visible) {
        long now=System.nanoTime();
        currentFps=visible?mc.getFps():-1; // Reference counter only; the HUD uses our measured window.
        if(visible){
            rate.record(now);
            if(now-lastPresentation>=250_000_000L){measuredFps=rate.publish(now);sessionFps=rate.sessionFps();lastPresentation=now;}
        }else{rate.reset();measuredFps=sessionFps=Double.NaN;}
        renderDistance=mc.options.renderDistance().get();
        if (visible && now-lastPoll>=config.monitoring.pollMillis*1_000_000L && polling.compareAndSet(false,true)) {
            lastPoll=now;
            worker.execute(() -> { try { jvm=telemetry.sample(); } finally { polling.set(false); } });
        }

        boolean running=mc.level!=null && !mc.isPaused() && visible;
        if (!running) {
            if (active) { window.clear(); scoring.reset(); frames=window.snapshot(now); health=scoring.evaluate(frames,config.targetFps); }
            active=false; lastFrame=lastScore=0; smoothFps=frameMillis=Double.NaN; return;
        }
        active=true;
        if (lastFrame!=0) {
            frameMillis=(now-lastFrame)/1e6;
            window.add(now,frameMillis);
            double fps=1000/frameMillis;
            smoothFps=Double.isNaN(smoothFps)?fps:smoothFps+(fps-smoothFps)*(1-Math.exp(-frameMillis/250));
        }
        lastFrame=now;
        if (now-lastScore>=1_000_000_000L) {
            frames=window.snapshot(now); health=scoring.evaluate(frames,config.targetFps); lastScore=now;
            var connection=mc.getConnection();
            var info=connection==null||mc.player==null?null:connection.getPlayerInfo(mc.player.getUUID());
            ping=info==null?-1:info.getLatency(); renderDistance=mc.options.renderDistance().get();
            history[0][historyHead]=measuredFps; history[1][historyHead]=frames.count()==0?Double.NaN:1000/frames.averageFps();
            history[2][historyHead]=jvm==null?Double.NaN:jvm.heapUsed()/1048576.;
            history[3][historyHead]=health.available()?health.score():Double.NaN;
            historyNanos=now;historyHead=(historyHead+1)%120; historySize=Math.min(120,historySize+1);
        }
    }
    @Override public void close() { worker.shutdownNow(); }
}
