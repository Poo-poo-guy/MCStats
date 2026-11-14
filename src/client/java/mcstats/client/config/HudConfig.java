package mcstats.client.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Versioned, bounded, human-readable profiles. Coordinates are fractions of available screen space. */
public final class HudConfig {
    public enum Metric { HEALTH, FPS, FRAME_TIME, MEMORY, CPU, PING, RENDER_DISTANCE, GC, GPU }
    public enum Style { TEXT, PILL, CARD, GRAPH, GAUGE }
    public enum Theme { DARK, LIGHT, CUSTOM }
    public enum Monitoring {
        ECO(2000), BALANCED(1000), RESPONSIVE(500);
        public final long pollMillis;
        Monitoring(long ms) { pollMillis = ms; }
    }
    public static final class Widget {
        public Metric metric = Metric.HEALTH;
        public Style style = Style.PILL;
        public double x = .02, y = .02, scale = 1, opacity = .85;
        public int width = 160, height = 40, radius = 12, padding = 10, font = 0, accent = 0x82dbc5;
        public boolean border = true;
        public int group;
        public Widget(Metric metric, double y) { this.metric = metric; this.y = y; }
    }
    public final List<Widget> widgets = new ArrayList<>();
    public boolean enabled = true, reducedMotion, snap = true, reflections = true, blur;
    public int excellentColor=0x82dbc5,goodColor=0xa6d5fa,fairColor=0xf1c77e,poorColor=0xf08d9a;
    public int targetFps = 144, customBackground = 0x202633, textColor = 0xecf2fc;
    public Theme theme = Theme.DARK;
    public Monitoring monitoring = Monitoring.BALANCED;
    public HudConfig() { widgets.add(new Widget(Metric.HEALTH, .02)); }
    public void preset(String name) {
        widgets.clear();
        Metric[] metrics = switch (name) {
            case "FPS only" -> new Metric[]{Metric.FPS};
            case "Essential" -> new Metric[]{Metric.HEALTH, Metric.FPS, Metric.MEMORY};
            case "Streamer" -> new Metric[]{Metric.FPS, Metric.FRAME_TIME};
            case "Performance" -> new Metric[]{Metric.HEALTH, Metric.FPS, Metric.FRAME_TIME, Metric.CPU};
            case "Full Dashboard" -> Metric.values();
            default -> new Metric[]{Metric.HEALTH};
        };
        for (int i = 0; i < metrics.length; i++) {
            var w = new Widget(metrics[i], .02 + (i % 5) * .18);
            w.x = .02 + (i / 5) * .3;
            if (name.equals("Full Dashboard")) { w.style = Style.CARD; w.height = 60; }
            widgets.add(w);
        }
    }
    public String encode() throws IOException {
        var p = new Properties();
        p.setProperty("version", "1");
        p.setProperty("excellentColor",""+excellentColor);p.setProperty("goodColor",""+goodColor);
        p.setProperty("fairColor",""+fairColor);p.setProperty("poorColor",""+poorColor);
        p.setProperty("enabled", "" + enabled); p.setProperty("reducedMotion", "" + reducedMotion);
        p.setProperty("snap", "" + snap); p.setProperty("reflections", "" + reflections); p.setProperty("blur", "" + blur);
        p.setProperty("targetFps", "" + targetFps); p.setProperty("theme", theme.name());
        p.setProperty("background", "" + customBackground); p.setProperty("textColor", "" + textColor);
        p.setProperty("monitoring", monitoring.name()); p.setProperty("count", "" + widgets.size());
        for (int i = 0; i < widgets.size(); i++) {
            Widget w = widgets.get(i); String k = "widget." + i + ".";
            p.setProperty(k + "metric", w.metric.name()); p.setProperty(k + "style", w.style.name());
            p.setProperty(k + "x", "" + w.x); p.setProperty(k + "y", "" + w.y);
            p.setProperty(k + "scale", "" + w.scale); p.setProperty(k + "opacity", "" + w.opacity);
            p.setProperty(k + "width", "" + w.width); p.setProperty(k + "height", "" + w.height);
            p.setProperty(k + "radius", "" + w.radius); p.setProperty(k + "padding", "" + w.padding);
            p.setProperty(k + "font", "" + w.font); p.setProperty(k + "accent", "" + w.accent);
            p.setProperty(k + "border", "" + w.border); p.setProperty(k + "group", "" + w.group);
        }
        var out = new StringWriter(); p.store(out, "MCStats+ profile; version 1"); return out.toString();
    }
    public static HudConfig decode(String value) throws IOException {
        if (value.length() > 65536) throw new IOException("Profile exceeds 64 KiB");
        try {
            var p = new Properties(); p.load(new StringReader(value));
            if (!"1".equals(p.getProperty("version"))) throw new IOException("Unsupported profile version");
            var c = new HudConfig(); c.widgets.clear();
            c.excellentColor=integer(p,"excellentColor",0x82dbc5,0,0xffffff);c.goodColor=integer(p,"goodColor",0xa6d5fa,0,0xffffff);
            c.fairColor=integer(p,"fairColor",0xf1c77e,0,0xffffff);c.poorColor=integer(p,"poorColor",0xf08d9a,0,0xffffff);
            c.enabled = bool(p,"enabled",true); c.reducedMotion = bool(p,"reducedMotion",false);
            c.snap = bool(p,"snap",true); c.reflections = bool(p,"reflections",true); c.blur = bool(p,"blur",false);
            c.targetFps = integer(p,"targetFps",144,1,1000);
            c.theme = Theme.valueOf(p.getProperty("theme","DARK"));
            c.monitoring = Monitoring.valueOf(p.getProperty("monitoring","BALANCED"));
            c.customBackground = integer(p,"background",0x202633,0,0xffffff);
            c.textColor = integer(p,"textColor",0xecf2fc,0,0xffffff);
            int count = integer(p,"count",1,0,32);
            for (int i = 0; i < count; i++) {
                String k = "widget." + i + ".";
                var w = new Widget(Metric.valueOf(p.getProperty(k+"metric","HEALTH")),.02);
                w.style = Style.valueOf(p.getProperty(k+"style","PILL"));
                w.x = number(p,k+"x",.02,0,1); w.y = number(p,k+"y",.02,0,1);
                w.scale = number(p,k+"scale",1,.5,3); w.opacity = number(p,k+"opacity",.85,.1,1);
                w.width = integer(p,k+"width",160,70,600); w.height = integer(p,k+"height",40,24,300);
                w.radius = integer(p,k+"radius",12,0,32); w.padding = integer(p,k+"padding",10,2,24);
                w.font = integer(p,k+"font",0,0,3); w.accent = integer(p,k+"accent",0x82dbc5,0,0xffffff);
                w.border = bool(p,k+"border",true); w.group = integer(p,k+"group",0,0,32);
                c.widgets.add(w);
            }
            return c;
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid profile",ex); }
    }
    private static boolean bool(Properties p,String k,boolean fallback) throws IOException {
        String s=p.getProperty(k,""+fallback);
        if (!s.equals("true") && !s.equals("false")) throw new IOException("Invalid boolean: "+k);
        return Boolean.parseBoolean(s);
    }
    private static double number(Properties p,String k,double d,double min,double max) throws IOException {
        double n=Double.parseDouble(p.getProperty(k,""+d));
        if (!Double.isFinite(n)||n<min||n>max) throw new IOException("Out of range: "+k);
        return n;
    }
    private static int integer(Properties p,String k,int d,int min,int max) throws IOException {
        double n=number(p,k,d,min,max);
        if (n!=Math.rint(n)) throw new IOException("Expected integer: "+k);
        return (int)n;
    }
    public void save(Path file) throws IOException {
        String data = encode();
        decode(data); // Validate before touching the current profile.
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(),"mcstats-", ".tmp");
        try {
            Files.writeString(temp,data);
            try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public static HudConfig load(Path file) throws IOException {
        if (Files.size(file)>65536) throw new IOException("Profile exceeds 64 KiB");
        return decode(Files.readString(file));
    }
}
