package mcstats.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

/** Native GUI extraction only: no framebuffer replacement, shaders, or external UI framework. */
public final class Glass {
    private static final FontDescription[] FONTS = java.util.Arrays.stream(new String[]{"sans","medium","mono","serif"})
            .map(n -> new FontDescription.Resource(Identifier.fromNamespaceAndPath("mcstats",n))).toArray(FontDescription[]::new);
    private static double opacity=1;
    public static void opacity(double value) { opacity=Math.clamp(value,0,1); }
    public static int faded(int color) { return alpha(color,((color>>>24)/255.)*opacity); }
    private Glass() {}
    public static Component text(String text,int font) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(FONTS[Math.floorMod(font,4)]));
    }
    public static void text(GuiGraphicsExtractor g,String text,int x,int y,int color,int font) {
        g.text(Minecraft.getInstance().font,text(text,font),x,y,faded(color),false);
    }
    public static int alpha(int rgb,double a) { return ((int)(Math.clamp(a,0,1)*255)<<24)|(rgb&0xffffff); }
    public static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color) {
        color=faded(color);
        int r=Math.min(radius,Math.min(w,h)/2);
        if (w<=0||h<=0) return;
        if (r<=0) { g.fill(x,y,x+w,y+h,color); return; }
        g.fill(x,y+r,x+w,y+h-r,color);
        for (int row=0;row<r;row++) {
            double dy=r-row-.5;
            double inset=r-Math.sqrt(r*r-dy*dy);
            int edge=(int)Math.ceil(inset);
            g.fill(x+edge,y+row,x+w-edge,y+row+1,color);
            g.fill(x+edge,y+h-row-1,x+w-edge,y+h-row,color);
            double coverage=edge-inset;
            int aa=alpha(color,((color>>>24)/255.)*coverage);
            g.fill(x+edge-1,y+row,x+edge,y+row+1,aa);
            g.fill(x+w-edge,y+row,x+w-edge+1,y+row+1,aa);
            g.fill(x+edge-1,y+h-row-1,x+edge,y+h-row,aa);
            g.fill(x+w-edge,y+h-row-1,x+w-edge+1,y+h-row,aa);
        }
    }
    public static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int background,double opacity,boolean border,boolean reflection) {
        rounded(g,x,y+3,w,h,r,alpha(0,opacity*.18));
        rounded(g,x,y,w,h,r,alpha(border?0xb2c5db:background,opacity*.32));
        rounded(g,x+1,y+1,w-2,h-2,Math.max(0,r-1),alpha(background,opacity));
        if (reflection) g.horizontalLine(x+r,x+w-r,y+1,faded(alpha(0xffffff,.17)));
    }
    public static void graph(GuiGraphicsExtractor g,double[] values,int head,int count,int x,int y,int w,int h,int color) {
        if (count<2) return;
        g.enableScissor(x,y,x+w,y+h);
        double max=1;
        for (int i=0;i<count;i++) { double v=values[Math.floorMod(head-count+i,values.length)]; if(Double.isFinite(v))max=Math.max(max,v); }
        for (int i=0;i<count;i++) {
            double v=values[Math.floorMod(head-count+i,values.length)];
            if (!Double.isFinite(v)) continue;
            double since=Math.clamp((System.nanoTime()-mcstats.client.MCStatsClient.monitor.historyNanos)/1e9,0,1);
            if(mcstats.client.MCStatsClient.config.reducedMotion)since=0;
            int px=x+w-(int)((count-1-i+since)*w/120.), py=y+h-(int)(v/max*(h-2));
            g.fill(px,py,px+Math.max(1,w/120),y+h,faded(alpha(color,.16)));
            g.fill(px,py,px+Math.max(1,w/120),py+1,faded(0xff000000|color));
        }
        g.disableScissor();
    }
    public static void gauge(GuiGraphicsExtractor g,int cx,int cy,int radius,double fraction,int color) {
        for (int i=0;i<60;i++) {
            double a=Math.PI*2*i/60-Math.PI/2;
            int x=cx+(int)(Math.cos(a)*radius), y=cy+(int)(Math.sin(a)*radius);
            rounded(g,x-1,y-1,3,3,1,alpha(color,i<60*fraction?.95:.15));
        }
    }
}
