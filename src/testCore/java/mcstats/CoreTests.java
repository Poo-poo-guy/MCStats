package mcstats;

import mcstats.client.metrics.*;
import mcstats.client.animation.Spring;
import mcstats.client.config.HudConfig;
import java.nio.file.*;
import java.util.Arrays;

/** Dependency-free executable regression tests; fails the Gradle check task on any failed case. */
public final class CoreTests {
    private static int cases;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double a,double b,double eps){check(Math.abs(a-b)<eps,a+" != "+b);}
    private static void test(String name,Checked action)throws Exception{action.run();cases++;System.out.println("PASS "+name);}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    private static FrameWindow.Snapshot frames(double millis){double[] f=new double[1800];Arrays.fill(f,millis);return new FrameWindow.Snapshot(f,30);}
    public static void main(String[] args)throws Exception {
        test("ring wraps and expires by elapsed time",()->{
            var w=new FrameWindow(3);for(int i=1;i<=5;i++)w.add(i*1_000_000_000L,i*10);
            check(Arrays.equals(w.snapshot(5_000_000_000L).frames(),new double[]{30,40,50}),"order");
            check(w.snapshot(35_000_000_000L).count()==0,"expiry");
        });
        test("invalid frame samples do not contaminate ring",()->{
            var w=new FrameWindow(4);w.add(1,Double.NaN);w.add(2,-1);w.add(3,10);
            check(w.snapshot(3).count()==1,"invalid sample");
            try{w.add(3,10);throw new AssertionError("duplicate time");}catch(IllegalArgumentException expected){}
            w.clear();w.add(1,20);near(w.snapshot(1).averageFps(),50,.001);
        });
        test("mean FPS and worst one-percent duration",()->{
            double[] f=new double[1000];Arrays.fill(f,10);Arrays.fill(f,990,1000,100);
            var s=new FrameWindow.Snapshot(f,10.9);near(s.averageFps(),1000/10.9,.001);near(s.onePercentLowFps(),10,.001);
            f[0]=10000;near(s.averageFps(),1000/10.9,.001);
            s.frames()[0]=10000;near(s.averageFps(),1000/10.9,.001);
        });
        test("empty metrics and warmup are unavailable",()->{
            check(Double.isNaN(new FrameWindow.Snapshot(new double[0],0).averageFps()),"empty FPS");
            check(!new FrameHealth().evaluate(new FrameWindow.Snapshot(new double[]{10,10},.5),144).available(),"warmup");
        });
        test("consistent target performance receives 100",()->{
            var h=new FrameHealth().evaluate(frames(1000./144),144);near(h.score(),100,.0001);check(h.category()==FrameHealth.Category.EXCELLENT,"category");
        });
        test("FPS target is only one score component",()->{
            var h=new FrameHealth().evaluate(frames(1000./60),144);near(h.score(),75+25*60./144,.001);
        });
        test("stutters and severe stalls use disjoint classes",()->{
            double[] f=frames(10).frames();f[0]=80;f[1]=400;
            var h=new FrameHealth().evaluate(new FrameWindow.Snapshot(f,30),100);
            check(h.stutterCount()==1&&h.stallCount()==1,"disjoint counts");near(h.consistency(),1,.001);check(h.score()<100,"penalty");
        });
        test("ordinary frame variance reduces consistency",()->{
            double[] f=frames(10).frames();for(int i=0;i<f.length;i+=2)f[i]=20;
            check(new FrameHealth().evaluate(new FrameWindow.Snapshot(f,30),50).consistency()<.5,"variance penalty");
        });
        test("score remains finite and bounded across severe workloads",()->{
            for(double ms:new double[]{1,7,16,60,300,1000}){
                double score=new FrameHealth().evaluate(frames(ms),144).score();check(Double.isFinite(score)&&score>=0&&score<=100,"range");
            }
        });
        test("category hysteresis resists threshold flutter",()->{
            var h=new FrameHealth();h.evaluate(frames(10),100);
            var near=h.evaluate(frames(1000./59),100);check(near.category()==FrameHealth.Category.EXCELLENT,"hold near boundary");
            check(h.evaluate(frames(1000./40),100).category()==FrameHealth.Category.GOOD,"drop outside band");
        });
        test("spring is independent of frame partition",()->{
            var a=new Spring(0);var b=new Spring(0);a.update(100,1,.3,false);
            for(int i=0;i<120;i++)b.update(100,1./120,.3,false);near(a.value(),b.value(),1e-9);
        });
        test("spring retarget, reduced motion and invalid inputs",()->{
            var s=new Spring(0);s.update(10,.05,.3,false);double before=s.value();s.update(-10,0,.3,false);near(before,s.value(),1e-9);
            near(s.update(4,.01,.3,true),4,1e-9);
            try{s.update(Double.NaN,1,.3,false);throw new AssertionError("NaN");}catch(IllegalArgumentException expected){}
        });
        test("profile roundtrip retains all widget settings",()->{
            var c=new HudConfig();c.preset("Full Dashboard");c.theme=HudConfig.Theme.CUSTOM;c.reducedMotion=true;
            var w=c.widgets.getFirst();w.font=3;w.group=2;w.scale=1.5;w.border=false;w.x=.9;
            var d=HudConfig.decode(c.encode());check(d.widgets.size()==HudConfig.Metric.values().length,"count");
            check(d.reducedMotion&&d.theme==HudConfig.Theme.CUSTOM,"globals");var v=d.widgets.getFirst();
            check(v.font==3&&v.group==2&&!v.border,"widget");near(v.x,.9,.001);near(v.scale,1.5,.001);
        });
        test("profile rejects malformed oversized and non-finite inputs",()->{
            for(String value:new String[]{"version=99","version=1\ncount=33","version=1\nwidget.0.x=NaN","version=1\nwidget.0.scale=-1","version=1\nenabled=maybe"," ".repeat(65537)}){
                try{HudConfig.decode(value);throw new AssertionError("invalid profile accepted");}catch(java.io.IOException expected){}
            }
        });
        test("atomic persistence and overwrite",()->{
            Path dir=Files.createTempDirectory("mcstats-test");Path file=dir.resolve("profile.properties");
            try{var c=new HudConfig();c.save(file);c.preset("FPS only");c.save(file);
                check(HudConfig.load(file).widgets.getFirst().metric==HudConfig.Metric.FPS,"overwrite");
                try(var paths=Files.list(dir)){check(paths.count()==1,"temporary file leaked");}
            }finally{Files.deleteIfExists(file);Files.delete(dir);}
        });
        System.out.println(cases+" tests passed");
    }
}
