package mcstats.client.ui;

import mcstats.client.MCStatsClient;
import mcstats.client.animation.Spring;
import mcstats.client.config.HudConfig;
import mcstats.client.config.HudConfig.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

public final class Hud {
    private static final class Presentation {
        final Spring score=new Spring(0);
        Spring x,y,width,height,red,green,blue;
        final Spring opacity=new Spring(0);
        int displayedScore=-1,labelFont=-1;
        mcstats.client.metrics.FrameHealth.Category displayCategory;
        net.minecraft.network.chat.Component label,subtitle;
        long last;
        String value="Warming up",detail="";
    }
    private final Map<Widget,Presentation> states=new IdentityHashMap<>();
    private long lastLabels;
    public static int background(HudConfig c) { return c.theme==Theme.LIGHT?0xf4f7fc:c.theme==Theme.CUSTOM?c.customBackground:0x151b27; }
    public static int foreground(HudConfig c) { return c.theme==Theme.LIGHT?0xff172333:0xff000000|c.textColor; }
    public static int[] bounds(Widget w,int screenW,int screenH) {
        int width=Math.min(screenW,(int)(w.width*w.scale)),height=Math.min(screenH,(int)(w.height*w.scale));
        return new int[]{(int)(w.x*Math.max(0,screenW-width)),(int)(w.y*Math.max(0,screenH-height)),width,height};
    }
    public void draw(GuiGraphicsExtractor g,boolean editing) {
        long start=mcstats.client.metrics.Diagnostics.begin(),bytes=mcstats.client.metrics.Diagnostics.allocated();
        HudConfig c=MCStatsClient.config;
        long now=System.nanoTime(); boolean refresh=now-lastLabels>250_000_000;
        if (refresh) { lastLabels=now; states.keySet().retainAll(c.widgets); }
        for (Widget w:c.widgets) {
            Presentation p=states.computeIfAbsent(w,k->new Presentation());
            if (refresh||p.label==null) labels(w,p);
            float bw=Math.min(g.guiWidth(),(float)(w.width*w.scale)),bh=Math.min(g.guiHeight(),(float)(w.height*w.scale));
            float bx=(float)(w.x*Math.max(0,g.guiWidth()-bw)),by=(float)(w.y*Math.max(0,g.guiHeight()-bh));
            if(p.x==null){p.x=new Spring(bx);p.y=new Spring(by);p.width=new Spring(bw);p.height=new Spring(bh);}
            double dt=p.last==0?0:(now-p.last)/1e9;
            bx=(float)(p.x.update(bx,dt,.2,c.reducedMotion));by=(float)(p.y.update(by,dt,.2,c.reducedMotion));
            bw=(float)(p.width.update(bw,dt,.2,c.reducedMotion));bh=(float)(p.height.update(bh,dt,.2,c.reducedMotion));
            drawWidget(g,w,p,bx,by,bw,bh,now,c);
        }
        mcstats.client.metrics.Diagnostics.end(1,start,bytes);
    }
    private void labels(Widget w,Presentation p) {
        var m=MCStatsClient.monitor; var j=m.jvm; var h=m.health;
        p.value=switch(w.metric) {
            case HEALTH -> h.available()?Math.round(h.score())+"  "+h.category().name():"Measuring…";
            case FPS -> Double.isFinite(m.measuredFps)?Math.round(m.measuredFps)+" FPS":"Measuring…";
            case FRAME_TIME -> number(m.frameMillis," ms");
            case MEMORY -> j==null?"Unavailable":Math.round(j.heapUsed()/1048576.)+" MB";
            case CPU -> j==null?"Unavailable":number(j.processCpu()*100,"% CPU");
            case PING -> m.ping<0?"Unavailable":m.ping+" ms ping";
            case RENDER_DISTANCE -> m.renderDistance+" chunks";
            case GC -> j==null||j.gcCount()<0?"Unavailable":j.gcCount()+" collections";
            case GPU -> "Unavailable";
        };
        p.detail=switch(w.metric) {
            case FPS -> "Avg "+number(m.frames.averageFps(),"")+"  ·  1% "+number(m.frames.onePercentLowFps(),"");
            case HEALTH -> "Rendering smoothness · 30 s window";
            case MEMORY -> j==null||j.heapMax()<0?"Heap limit unavailable":"Heap limit "+Math.round(j.heapMax()/1048576.)+" MB";
            case CPU -> j==null?"System unavailable":"System "+number(j.systemCpu()*100,"%");
            case GC -> j==null||j.gcMillis()<0?"GC time unavailable":j.gcMillis()+" ms cumulative GC";
            case GPU -> "VRAM / temperature unavailable";
            default -> w.metric.name().replace('_',' ');
        };
        if(w.metric!=Metric.HEALTH||!h.available())p.label=Glass.text(p.value,w.font);p.subtitle=Glass.text(p.detail,w.font);
    }
    private void drawWidget(GuiGraphicsExtractor g,Widget w,Presentation p,float x,float y,float width,float height,long now,HudConfig c) {
        double dt=p.last==0?0:(now-p.last)/1e9; p.last=now;
        var h=MCStatsClient.monitor.health;
        double score=Math.clamp(p.score.update(h.available()?h.score():0,dt,.3,c.reducedMotion),0,100);
        p.displayCategory=h.available()?mcstats.client.metrics.FrameHealth.classify(score,p.displayCategory):null;
        int targetColor=w.metric==Metric.HEALTH&&h.available()?switch(p.displayCategory){
            case EXCELLENT->c.excellentColor;case GOOD->c.goodColor;case FAIR->c.fairColor;case POOR->c.poorColor;
        }:w.accent;
        if(p.red==null){p.red=new Spring((targetColor>>16)&255);p.green=new Spring((targetColor>>8)&255);p.blue=new Spring(targetColor&255);}
        int accent=(channel(p.red.update((targetColor>>16)&255,dt,.3,c.reducedMotion))<<16)
                |(channel(p.green.update((targetColor>>8)&255,dt,.3,c.reducedMotion))<<8)
                |channel(p.blue.update(targetColor&255,dt,.3,c.reducedMotion));
        if (w.style!=HudConfig.Style.TEXT) Glass.panel(g,x,y,width,height,(int)(w.radius*w.scale),background(c),p.opacity.update(w.opacity,dt,.25,c.reducedMotion),w.border,c.reflections);
        Glass.clip(g,(int)Math.floor(x),(int)Math.floor(y),(int)Math.ceil(x+width),(int)Math.ceil(y+height));
        g.pose().pushMatrix(); g.pose().translate(x,y); g.pose().scale((float)w.scale);
        int pad=w.padding,fg=foreground(c);
        
        if (w.metric==Metric.HEALTH&&h.available()) {
            int displayed=(int)Math.round(score);
            if(displayed!=p.displayedScore||p.labelFont!=w.font||p.label==null||!p.label.getString().endsWith(p.displayCategory.name())){p.displayedScore=displayed;p.labelFont=w.font;p.label=Glass.text(displayed+"  "+p.displayCategory.name(),w.font);}
        }
        if(w.metric==Metric.HEALTH&&(w.style==HudConfig.Style.GAUGE||w.style==HudConfig.Style.CARD)){
            boolean large=w.style==HudConfig.Style.GAUGE;
            int radius=large?Math.max(12,Math.min(w.width/2-pad,w.height/2-pad-8)):Math.min(20,w.height/2-5);
            int cx=large?w.width/2:pad+radius,cy=large?w.height/2-4:w.height/2;
            Glass.gauge(g,cx,cy,radius,h.available()?score/100:0,accent);
            String numeric=h.available()?""+Math.round(score):"—";
            Glass.centered(g,numeric,cx,cy-6,fg,w.font);
            String status=h.available()?p.displayCategory.name():"Measuring…";
            if(large)Glass.centered(g,status,cx,cy+radius+5,fg,w.font);
            else Glass.text(g,status,cx+radius+12,cy-6,fg,w.font);
        }else{
            int textX=pad;
            if(w.metric==Metric.HEALTH){Glass.rounded(g,pad,Math.max(5,w.height/2-3),6,6,3,0xff000000|accent);textX+=12;}
            if(p.label!=null)Glass.label(g,p.label,textX,w.style==HudConfig.Style.CARD?pad:Math.max(4,(w.height-12)/2),fg);
            if(w.style==HudConfig.Style.CARD&&w.height>=48)Glass.label(g,p.subtitle,pad,pad+19,Glass.alpha(fg,.62));
        }
        if (w.style==HudConfig.Style.GRAPH&&w.height>=48) {
            int idx=switch(w.metric){case FPS->0;case FRAME_TIME->1;case MEMORY->2;default->3;};
            var m=MCStatsClient.monitor;
            Glass.graph(g,m.history[idx],m.historyHead,m.historySize,pad,24,w.width-pad*2,w.height-28,accent);
        }
        g.pose().popMatrix(); Glass.unclip(g);
    }
    private static int channel(double x){return (int)Math.clamp(x,0,255);}
    public static int healthColor(double score) { var c=MCStatsClient.config;return score>=90?c.excellentColor:score>=75?c.goodColor:score>=50?c.fairColor:c.poorColor; }
    public static String number(double n,String suffix) { return Double.isFinite(n)?String.format(Locale.ROOT,"%.1f%s",n,suffix):"Unavailable"; }
}
