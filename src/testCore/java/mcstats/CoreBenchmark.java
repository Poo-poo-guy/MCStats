package mcstats;

import mcstats.client.metrics.*;

/** Developer microbenchmark only; does not measure Minecraft renderer or total mod FPS overhead. */
public final class CoreBenchmark {
    public static void main(String[] args) {
        var w=new FrameWindow(65536);
        for(int i=1;i<=1_000_000;i++)w.add(i*100_000L, .1);
        long start=System.nanoTime();
        for(int i=1;i<=5_000_000;i++)w.add(100_000_000_000L+i*100_000L, .1);
        double ns=(System.nanoTime()-start)/5_000_000.;
        var health=new FrameHealth();var s=w.snapshot(600_000_000_000L);
        start=System.nanoTime();double result=0;
        for(int i=0;i<100;i++)result+=health.evaluate(s,144).score();
        System.out.printf(java.util.Locale.ROOT,"Ring write: %.1f ns/sample; score of %d frames: %.3f ms/evaluation; checksum %.1f%n",ns,s.count(),(System.nanoTime()-start)/1e8,result);
    }
}
