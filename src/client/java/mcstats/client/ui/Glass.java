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
    private static java.util.Map<String,Component> textCache(){
        return new java.util.LinkedHashMap<>(128,.75f,true){
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,Component> e){return size()>128;}
        };
    }
    private static final java.util.List<java.util.Map<String,Component>> TEXT=java.util.List.of(textCache(),textCache(),textCache(),textCache());
    public static Component text(String text,int font) {
        int index=Math.floorMod(font,4);var cache=TEXT.get(index);Component c=cache.get(text);
        if(c==null){c=Component.literal(text).withStyle(Style.EMPTY.withFont(FONTS[index]));cache.put(text,c);}
        return c;
    }
    public static void text(GuiGraphicsExtractor g,String text,int x,int y,int color,int font) {
        label(g,text(text,font),x,y,color);
    }
    public static int alpha(int rgb,double a) { return ((int)(Math.clamp(a,0,1)*255)<<24)|(rgb&0xffffff); }
    private static final java.util.Deque<net.minecraft.client.gui.navigation.ScreenRectangle> clips=new java.util.ArrayDeque<>();
    public static void clip(GuiGraphicsExtractor g,int x,int y,int right,int bottom){
        var area=new net.minecraft.client.gui.navigation.ScreenRectangle(x,y,Math.max(0,right-x),Math.max(0,bottom-y)).transformMaxBounds(g.pose());
        if(!clips.isEmpty())area=area.intersection(clips.peek());
        if(area==null)area=net.minecraft.client.gui.navigation.ScreenRectangle.empty();
        clips.push(area);g.enableScissor(x,y,right,bottom);
    }
    public static void unclip(GuiGraphicsExtractor g){clips.pop();g.disableScissor();}
    private static void shape(GuiGraphicsExtractor g,float x,float y,float w,float h,float r,double progress,int color){
        var matrix=new org.joml.Matrix3x2f(g.pose());
        double pixels=(double)Minecraft.getInstance().getWindow().getWidth()/g.guiWidth();
        float feather=(float)(1/(pixels*Math.hypot(matrix.m00(),matrix.m01())));
        float left=progress<0?x:x-r-w/2,top=progress<0?y:y-r-w/2;
        float width=progress<0?w:2*r+w,height=progress<0?h:2*r+w;
        var bounds=new net.minecraft.client.gui.navigation.ScreenRectangle((int)Math.floor(left-feather),(int)Math.floor(top-feather),
                (int)Math.ceil(width+2*feather)+1,(int)Math.ceil(height+2*feather)+1).transformMaxBounds(matrix);
        ((mcstats.client.mixin.GuiStateAccessor)g).mcstats$state().addGuiElement(new SmoothShape(matrix,x,y,w,h,r,feather,progress,faded(color),clips.peek(),bounds));
    }
    public static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color){
        if(w>0&&h>0)shape(g,x,y,w,h,Math.min(radius,Math.min(w,h)/2),-1,color);
    }
    public static void panel(GuiGraphicsExtractor g,float x,float y,float w,float h,float r,int background,double opacity,boolean border,boolean reflection) {
        float pixel=(float)(g.guiWidth()/(double)Minecraft.getInstance().getWindow().getWidth()/Math.hypot(g.pose().m00(),g.pose().m01()));
        shape(g,x-2,y+2,w+4,h+4,r+2,-1,alpha(0,opacity*.04));
        shape(g,x-1,y+2,w+2,h+2,r+1,-1,alpha(0,opacity*.07));
        shape(g,x,y+2,w,h,r,-1,alpha(0,opacity*.1));
        shape(g,x,y,w,h,r,-1,alpha(border?0xb2c5db:background,opacity*.4));
        shape(g,x+pixel,y+pixel,w-2*pixel,h-2*pixel,Math.max(0,r-pixel),-1,alpha(background,opacity));
        if(reflection)shape(g,x+r,y+pixel,w-2*r,pixel,0,-1,alpha(0xffffff,.16));
    }
    public static void graph(GuiGraphicsExtractor g,double[] values,int head,int count,int x,int y,int w,int h,int color) {
        if (count<2) return;
        clip(g,x,y,x+w,y+h);
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
        unclip(g);
    }
    public static void gauge(GuiGraphicsExtractor g,int cx,int cy,int radius,double fraction,int color){
        float stroke=Math.max(2,radius*.13f);
        shape(g,cx,cy,stroke,stroke,radius,1,alpha(color,.16));
        shape(g,cx,cy,stroke,stroke,radius,Math.clamp(fraction,0,1),0xff000000|color);
    }
    /** Snap only the glyph origin in physical pixels; preserve fractional layout and native glyph metrics. */
    public static void label(GuiGraphicsExtractor g,Component value,float x,float y,int color){
        var p=g.pose();double scale=(double)Minecraft.getInstance().getWindow().getWidth()/g.guiWidth();
        double px=p.m00()*x+p.m10()*y+p.m20(),py=p.m01()*x+p.m11()*y+p.m21();
        float dx=(float)((Math.rint(px*scale)/scale-px)/p.m00());
        float dy=(float)((Math.rint(py*scale)/scale-py)/p.m11());
        p.pushMatrix();p.translate(x+dx,y+dy);
        g.text(Minecraft.getInstance().font,value,0,0,faded(color),false);p.popMatrix();
    }
    public static void centered(GuiGraphicsExtractor g,String value,int cx,int y,int color,int font){
        Component c=text(value,font);label(g,c,cx-Minecraft.getInstance().font.width(c)/2f,y,color);
    }
}
