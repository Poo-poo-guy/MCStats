package mcstats.client.ui;

import mcstats.client.MCStatsClient;
import mcstats.client.config.HudConfig;
import mcstats.client.config.HudConfig.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Path;

public final class StudioScreen extends Screen {
    private final Screen previous;
    private final mcstats.client.animation.Spring dashboardScore=new mcstats.client.animation.Spring(0);
    private mcstats.client.metrics.FrameHealth.Category dashboardCategory;
    private final mcstats.client.animation.Spring dashboardRed=new mcstats.client.animation.Spring(130),dashboardGreen=new mcstats.client.animation.Spring(219),dashboardBlue=new mcstats.client.animation.Spring(197);
    private long dashboardTime=System.nanoTime();
    private int page, selected=-1, presetIndex, slot=1;
    private float scale=1;
    private int ox,oy;
    private String status="";
    private static final String[] METRIC_LABELS={"FPS / 0.5 s","FRAME TIME","HEAP / MB","FRAMEHEALTH"};
    private String[] metricValues=new String[4];
    private String metricFooter="";
    private long metricsAt;
    private boolean dragging,resizing,closing;
    private final mcstats.client.animation.Spring entrance=new mcstats.client.animation.Spring(0);
    private long lastAnimation=System.nanoTime();
    private double lastX,lastY;
    private static final String[] PRESETS={"Minimal","Essential","Streamer","Performance","Full Dashboard","FPS only"};
    public StudioScreen(boolean studio) { super(Component.literal("MCStats+")); page=studio?1:0; previous=minecraft.gui.screen(); }
    private HudConfig c() { return MCStatsClient.config; }
    private Widget selected() { return selected>=0&&selected<c().widgets.size()?c().widgets.get(selected):null; }
    @Override protected void init() {
        scale=Math.min(1,Math.min(width/780f,height/460f)); ox=(int)((width-760*scale)/2); oy=(int)((height-440*scale)/2);
        for(int i=0;i<4;i++) { int p=i; button(24+i*176,65,164,new String[]{"Overview","HUD Studio","Appearance","Profiles"}[i],()->{page=p;rebuildWidgets();}); }
        button(680,20,56,"Done",this::onClose);
        if(page==1) studioControls();
        if(page==2) appearanceControls();
        if(page==3) profileControls();
    }
    private void button(int x,int y,int w,String label,Runnable action) {
        addRenderableWidget(new GlassButton(ox+(int)(x*scale),oy+(int)(y*scale),(int)(w*scale),(int)(26*scale),label,()->{
            if(!closing){action.run(); MCStatsClient.save();}
        },scale));
    }
    private void studioControls() {
        button(24,292,124,"Add widget",()->{if(c().widgets.size()<32){c().widgets.add(new Widget(Metric.FPS,.3));selected=c().widgets.size()-1;}rebuildWidgets();});
        button(158,292,124,"Remove",()->{if(selected()!=null){c().widgets.remove(selected);selected=-1;}rebuildWidgets();});
        button(292,292,134,"Snap: "+c().snap,()->{c().snap=!c().snap;rebuildWidgets();});
        button(436,292,134,"Border",()->{if(selected()!=null)selected().border=!selected().border;});
        button(580,292,154,"Widget accent",()->{
            try{if(selected()!=null)selected().accent=parseColor(minecraft.keyboardHandler.getClipboard());status="Widget accent applied";}
            catch(IllegalArgumentException ex){status="Copy a six-digit hex color first";}
        });
        Widget w=selected(); if(w==null)return;
        button(24,328,164,"Metric: "+w.metric,()->{w.metric=Metric.values()[(w.metric.ordinal()+1)%Metric.values().length];
            if(w.style==HudConfig.Style.GAUGE&&w.metric!=Metric.HEALTH||w.style==HudConfig.Style.GRAPH&&!hasGraph(w.metric))w.style=HudConfig.Style.PILL;
            rebuildWidgets();});
        button(198,328,164,"Style: "+w.style,()->{
            do { w.style=HudConfig.Style.values()[(w.style.ordinal()+1)%HudConfig.Style.values().length]; }
            while ((w.style==HudConfig.Style.GAUGE&&w.metric!=Metric.HEALTH)||(w.style==HudConfig.Style.GRAPH&&!hasGraph(w.metric)));
            if(w.style==HudConfig.Style.CARD||w.style==HudConfig.Style.GRAPH)w.height=Math.max(w.height,64);
            if(w.style==HudConfig.Style.GAUGE){w.height=Math.max(w.height,120);w.width=Math.max(w.width,140);}
            rebuildWidgets();
        });
        button(372,328,164,"Scale: "+Hud.number(w.scale,""),()->{w.scale=w.scale>=2? .5:Math.round((w.scale+.25)*4)/4.;rebuildWidgets();});
        button(546,328,188,"Group: "+w.group,()->{w.group=(w.group+1)%5;rebuildWidgets();});
        button(24,366,164,"Font: "+new String[]{"Sans","Medium","Mono","Compact"}[w.font],()->{w.font=(w.font+1)%4;rebuildWidgets();});
        button(198,366,164,"Padding: "+w.padding,()->{w.padding=w.padding>=24?2:w.padding+2;rebuildWidgets();});
        button(372,366,164,"Radius: "+w.radius,()->{w.radius=w.radius>=32?0:w.radius+4;rebuildWidgets();});
        button(546,366,188,"Opacity: "+Math.round(w.opacity*100)+"%",()->{w.opacity=w.opacity>=.99?.2:Math.min(1,w.opacity+.1);rebuildWidgets();});
    }
    private static boolean hasGraph(Metric m) { return m==Metric.HEALTH||m==Metric.FPS||m==Metric.FRAME_TIME||m==Metric.MEMORY; }
    private void appearanceControls() {
        button(24,144,220,"Theme: "+c().theme,()->{c().theme=Theme.values()[(c().theme.ordinal()+1)%3];rebuildWidgets();});
        button(268,144,220,"Motion: "+(c().reducedMotion?"Reduced":"Fluid"),()->{c().reducedMotion=!c().reducedMotion;rebuildWidgets();});
        button(512,144,220,"Reflections: "+c().reflections,()->{c().reflections=!c().reflections;rebuildWidgets();});
        button(24,188,220,"Monitoring: "+c().monitoring,()->{c().monitoring=Monitoring.values()[(c().monitoring.ordinal()+1)%3];rebuildWidgets();});
        button(268,188,220,"FPS target: "+c().targetFps,()->{int[] t={30,60,90,120,144,165,240,360};int n=0;while(n<t.length&&t[n]<=c().targetFps)n++;c().targetFps=t[n%t.length];rebuildWidgets();});
        button(512,188,220,"HUD: "+c().enabled,()->{c().enabled=!c().enabled;rebuildWidgets();});
        button(24,232,220,"Dashboard blur: "+c().blur,()->{c().blur=!c().blur;rebuildWidgets();});
        button(268,232,220,"Custom background",()->{c().theme=Theme.CUSTOM;c().customBackground=c().customBackground==0x202633?0x192d2c:0x202633;rebuildWidgets();});
        button(512,232,220,"Selected border",()->{if(selected()!=null)selected().border=!selected().border;});
        button(24,276,220,"Accent from clipboard",()->{
            try { int color=parseColor(minecraft.keyboardHandler.getClipboard()); for(Widget w:c().widgets)w.accent=color;status="Accent applied to all widgets"; }
            catch(IllegalArgumentException ex){status="Copy a six-digit hex color first, e.g. #82dbc5";}
        });
        button(268,276,220,"Background from clipboard",()->{
            try { c().customBackground=parseColor(minecraft.keyboardHandler.getClipboard());c().theme=Theme.CUSTOM;status="Custom background applied"; }
            catch(IllegalArgumentException ex){status="Copy a six-digit hex color first";}
        });
        button(512,276,220,"Text from clipboard",()->{
            try { c().textColor=parseColor(minecraft.keyboardHandler.getClipboard());status="Text color applied"; }
            catch(IllegalArgumentException ex){status="Copy a six-digit hex color first";}
        });
    }
    private static int parseColor(String s) {
        s=s.strip().replaceFirst("^#", ""); if(!s.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException();return Integer.parseInt(s,16);
    }
    private Path profile() { return MCStatsClient.configDirectory.resolve("profile-"+slot+".properties"); }
    private void profileControls() {
        button(24,148,330,"Preset: "+PRESETS[presetIndex],()->{presetIndex=(presetIndex+1)%PRESETS.length;rebuildWidgets();});
        button(380,148,350,"Apply preset",()->{c().preset(PRESETS[presetIndex]);selected=-1;status="Preset applied";});
        button(24,194,220,"Profile slot: "+slot,()->{slot=slot%5+1;rebuildWidgets();});
        button(268,194,220,"Save slot",()->{try{c().save(profile());status="Saved profile "+slot;}catch(IOException ex){status="Save failed: "+ex.getMessage();}});
        button(512,194,220,"Load slot",()->{try{MCStatsClient.config=HudConfig.load(profile());selected=-1;status="Loaded profile "+slot;}catch(IOException ex){status="Load failed: "+ex.getMessage();}});
        button(24,240,330,"Export profile to clipboard",()->{try{minecraft.keyboardHandler.setClipboard(c().encode());status="Profile copied";}catch(IOException ex){status="Export failed";}});
        button(380,240,350,"Import profile from clipboard",()->{try{MCStatsClient.config=HudConfig.decode(minecraft.keyboardHandler.getClipboard());selected=-1;status="Profile imported";}catch(IOException ex){status="Import rejected: "+ex.getMessage();}});
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        long diagStart=mcstats.client.metrics.Diagnostics.begin(),diagBytes=mcstats.client.metrics.Diagnostics.allocated();
        if(page==1) {
            g.fill(0,0,width,height,0x40202a38); MCStatsClient.hud.draw(g,true);
            for(int i=0;i<c().widgets.size();i++) {
                int[] b=Hud.bounds(c().widgets.get(i),width,height);
                g.outline(b[0],b[1],b[2],b[3],i==selected?0xff82dbc5:0x607c91aa);
                if(i==selected){
                    g.fill(b[0]+b[2]-7,b[1]+b[3]-7,b[0]+b[2],b[1]+b[3],0xff82dbc5);
                    if(dragging&&c().snap){g.horizontalLine(0,width,b[1],0x6082dbc5);g.verticalLine(b[0],0,height,0x6082dbc5);}
                }
            }
        }
        long now=System.nanoTime();
        double visibility=entrance.update(closing?0:1,(now-lastAnimation)/1e9,.25,c().reducedMotion);lastAnimation=now;
        Glass.opacity(visibility);
        try {
        g.pose().pushMatrix();g.pose().translate(ox,oy);g.pose().scale(scale);
        if(page!=1)Glass.panel(g,0,0,760,440,22,Hud.background(c()),.95,true,true);
        else {
            Glass.panel(g,0,55,760,51,18,0x151b27,.94,true,true);
            Glass.panel(g,0,270,760,170,18,0x151b27,.94,true,true);
        }
        if(page!=1)Glass.text(g,"MCStats+",24,23,0xff82dbc5,1);
        if(page!=1)Glass.text(g,page==1?"HUD Studio  /  your space, your signals":"Performance, with perspective.",164,25,0xffbccbde,0);
        if(page==0)overview(g);
        if(page==1) {
            Glass.text(g,"Drag to move · corner to resize · arrows to nudge · same group moves together",24,111,0xffe7efff,0);
            Glass.text(g,selected()==null?"Select a widget in the canvas to edit its appearance":"Editing "+selected().metric+" · positions anchor to the nearest screen edge",24,412,0xffa4b7ce,0);
        }
        if(page==2) {
            Glass.text(g,"Make it yours",24,115,Hud.foreground(c()),1);
            Glass.text(g,"Optional dashboard blur; gameplay uses a lightweight translucent glass fallback.",24,330,Hud.foreground(c()),0);
            Glass.text(g,"Exact numeric values and per-widget colors are editable in exported profiles.",24,351,Hud.foreground(c()),0);
        }
        if(page==3) {
            Glass.text(g,"A quiet HUD or a full command center.",24,115,Hud.foreground(c()),1);
            Glass.text(g,"Profiles include every widget, position, theme and monitoring setting.",24,305,Hud.foreground(c()),0);
            Glass.text(g,"Clipboard imports are validated before replacing your current layout.",24,326,Hud.foreground(c()),0);
        }
        if(!status.isEmpty())Glass.text(g,status.length()>95?status.substring(0,95):status,24,page==1?396:416,0xff82dbc5,0);
        g.pose().popMatrix();
        super.extractRenderState(g,mx,my,delta);
        } finally { Glass.opacity(1);mcstats.client.metrics.Diagnostics.end(2,diagStart,diagBytes); }
    }
    private void overview(GuiGraphicsExtractor g) {
        var m=MCStatsClient.monitor;var h=m.health;
        if(System.nanoTime()-metricsAt>250_000_000L){metricsAt=System.nanoTime();
        metricValues=new String[]{Hud.number(m.measuredFps,""),Hud.number(m.frameMillis," ms"),m.jvm==null?"Unavailable":Hud.number(m.jvm.heapUsed()/1048576.,""),h.available()?Math.round(h.score())+"  "+h.category():"Measuring…"};
        metricFooter="1% low: "+Hud.number(m.frames.onePercentLowFps()," FPS")+"   Smoothed: "+Hud.number(m.smoothFps," FPS")+"   Ping: "+(m.ping<0?"Unavailable":m.ping+" ms");
        }
        for(int i=0;i<4;i++) {
            int x=24+i*178;
            Glass.panel(g,x,112,166,132,12,Hud.background(c()),.8,true,true);
            Glass.text(g,METRIC_LABELS[i],x+12,124,Hud.foreground(c()),2);
            if(i==3){
                long now=System.nanoTime();
                double dt=(now-dashboardTime)/1e9;
                double score=Math.clamp(dashboardScore.update(h.available()?h.score():0,dt,.3,c().reducedMotion),0,100);dashboardTime=now;
                dashboardCategory=h.available()?mcstats.client.metrics.FrameHealth.classify(score,dashboardCategory):null;
                int color=dashboardCategory==null?c().excellentColor:switch(dashboardCategory){case EXCELLENT->c().excellentColor;case GOOD->c().goodColor;case FAIR->c().fairColor;case POOR->c().poorColor;};
                int animatedColor=((int)Math.clamp(dashboardRed.update((color>>16)&255,dt,.3,c().reducedMotion),0,255)<<16)
                    |((int)Math.clamp(dashboardGreen.update((color>>8)&255,dt,.3,c().reducedMotion),0,255)<<8)
                    |(int)Math.clamp(dashboardBlue.update(color&255,dt,.3,c().reducedMotion),0,255);
                Glass.gauge(g,x+46,177,25,score/100,animatedColor);
                Glass.centered(g,h.available()?""+Math.round(score):"—",x+46,171,Hud.foreground(c()),0);
                Glass.graph(g,m.history[i],m.historyHead,m.historySize,x+84,166,70,38,0x82dbc5);
                Glass.centered(g,h.available()?dashboardCategory.name():"Measuring…",x+83,217,Hud.foreground(c()),0);
            }else{
                Glass.text(g,metricValues[i],x+12,146,0xff82dbc5,1);
                Glass.graph(g,m.history[i],m.historyHead,m.historySize,x+12,173,140,54,0x82dbc5);
            }
        }
        Glass.text(g,"Why this score?",24,263,Hud.foreground(c()),1);
        if(h.available()) {
            Glass.text(g,"Consistency 35%    "+percent(h.consistency())+"      Stutters 30%    "+percent(h.stutters()),24,287,Hud.foreground(c()),0);
            Glass.text(g,"FPS target 25%      "+percent(h.target())+"      Severe stalls 10%    "+percent(h.stalls()),24,308,Hud.foreground(c()),0);
            Glass.text(g,h.stutterCount()+" stutters · "+h.stallCount()+" severe stalls · "+m.frames.count()+" samples · "+Hud.number(m.frames.seconds()," s observed"),24,330,Hud.foreground(c()),0);
        } else Glass.text(g,"Enter a world to collect at least one second of gameplay samples.",24,287,Hud.foreground(c()),0);
        Glass.text(g,metricFooter,24,355,Hud.foreground(c()),0);
        Glass.text(g,"Initial scoring model · rolling 30 s · latency excluded · GPU telemetry unavailable",24,387,Hud.foreground(c()),0);
    }
    private String percent(double n){return Math.round(n*100)+"%";}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick) {
        if(closing)return true;
        if(super.mouseClicked(e,doubleClick))return true;
        if(page!=1||e.button()!=InputConstants.MOUSE_BUTTON_LEFT)return false;
        for(int i=c().widgets.size()-1;i>=0;i--) {
            int[] b=Hud.bounds(c().widgets.get(i),width,height);
            if(e.x()>=b[0]&&e.x()<b[0]+b[2]&&e.y()>=b[1]&&e.y()<b[1]+b[3]) {
                selected=i;dragging=true;resizing=e.x()>b[0]+b[2]-10&&e.y()>b[1]+b[3]-10;lastX=e.x();lastY=e.y();rebuildWidgets();return true;
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        Widget w=selected();if(!dragging||w==null)return super.mouseDragged(e,dx,dy);
        dx=e.x()-lastX;dy=e.y()-lastY;lastX=e.x();lastY=e.y();
        if(resizing){w.width=(int)Math.clamp(w.width+dx/w.scale,w.style==HudConfig.Style.GAUGE?140:70,600);w.height=(int)Math.clamp(w.height+dy/w.scale,w.style==HudConfig.Style.GAUGE?120:24,300);}
        else move(w,dx,dy);
        return true;
    }
    private void move(Widget chosen,double dx,double dy) {
        for(Widget w:c().widgets) if(w==chosen||chosen.group!=0&&w.group==chosen.group) {
            int[] b=Hud.bounds(w,width,height);double x=b[0]+dx,y=b[1]+dy;
            if(c().snap) {
                if(Math.abs(x)<6)x=0;if(Math.abs(y)<6)y=0;
                if(Math.abs(x-(width-b[2]))<6)x=width-b[2];if(Math.abs(y-(height-b[3]))<6)y=height-b[3];
                if(Math.abs(x-(width-b[2])/2.)<5)x=(width-b[2])/2.;
                if(Math.abs(y-(height-b[3])/2.)<5)y=(height-b[3])/2.;
            }
            w.x=Math.clamp(x/Math.max(1,width-b[2]),0,1);w.y=Math.clamp(y/Math.max(1,height-b[3]),0,1);
        }
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { if(dragging){dragging=false;MCStatsClient.save();return true;}return super.mouseReleased(e); }
    @Override public boolean keyPressed(KeyEvent e) {
        if(page==1&&selected()!=null) {
            int dx=e.key()==InputConstants.KEY_LEFT?-1:e.key()==InputConstants.KEY_RIGHT?1:0,dy=e.key()==InputConstants.KEY_UP?-1:e.key()==InputConstants.KEY_DOWN?1:0;
            if(dx!=0||dy!=0){boolean snap=c().snap;c().snap=false;move(selected(),dx,dy);c().snap=snap;MCStatsClient.save();return true;}
        }
        return super.keyPressed(e);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        // Keep the native menu panorama; blur only the backdrop, never finished UI geometry or glyphs.
        if(minecraft.level==null)extractPanorama(g,delta);
        if(c().blur)g.blurBeforeThisStratum();
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){super.tick();if(closing&&entrance.value()<.01)minecraft.gui.setScreen(previous);}
    @Override public void onClose(){MCStatsClient.save();closing=true;if(c().reducedMotion)minecraft.gui.setScreen(previous);}
}
