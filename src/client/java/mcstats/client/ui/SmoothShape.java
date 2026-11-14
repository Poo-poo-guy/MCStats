package mcstats.client.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

/** Native batched color geometry with a one-framebuffer-pixel coverage fringe.
 * No low-resolution targets, textures, per-frame shader compilation or framebuffer copies.
 */
public record SmoothShape(Matrix3x2f pose, float x, float y, float width, float height,
                          float radius, float feather, double progress, int color,
                          ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
    @Override public RenderPipeline pipeline(){return RenderPipelines.GUI;}
    @Override public TextureSetup textureSetup(){return TextureSetup.noTexture();}
    private void vertex(VertexConsumer v,float x,float y,int c){v.addVertexWith2DPose(pose,x,y).setColor(c);}
    private void quad(VertexConsumer v,float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy,int a,int b,int c,int d){
        vertex(v,ax,ay,a);vertex(v,bx,by,b);vertex(v,cx,cy,c);vertex(v,dx,dy,d);
    }
    @Override public void buildVertices(VertexConsumer v){
        long start=mcstats.client.metrics.Diagnostics.begin(),bytes=mcstats.client.metrics.Diagnostics.allocated();
        try {
        if(progress>=0){arc(v);return;}
        float r=Math.min(radius,Math.min(width,height)/2),f=feather/2;
        int segments=segments(r/feather)/4;
        float centerX=x+width/2,centerY=y+height/2;
        // Four convex corner arcs, with straight edges joining the corners.
        float firstX=0,firstY=0,firstOx=0,firstOy=0,prevX=0,prevY=0,prevOx=0,prevOy=0;
        for(int corner=0;corner<4;corner++)for(int i=0;i<=segments;i++){
            double angle=Math.PI+(corner*Math.PI/2)+i*Math.PI/2/segments;
            float nx=(float)Math.cos(angle),ny=(float)Math.sin(angle);
            float cx=x+(corner==0||corner==3?r:width-r),cy=y+(corner<2?r:height-r);
            // Corner order: top-left, top-right, bottom-right, bottom-left.
            float ix=cx+nx*Math.max(0,r-f),iy=cy+ny*Math.max(0,r-f);
            float ox=cx+nx*(r+f),oy=cy+ny*(r+f);
            if(corner==0&&i==0){firstX=ix;firstY=iy;firstOx=ox;firstOy=oy;}
            else edge(v,centerX,centerY,prevX,prevY,ix,iy,prevOx,prevOy,ox,oy);
            prevX=ix;prevY=iy;prevOx=ox;prevOy=oy;
        }
        edge(v,centerX,centerY,prevX,prevY,firstX,firstY,prevOx,prevOy,firstOx,firstOy);
        }finally{mcstats.client.metrics.Diagnostics.end(3,start,bytes);}
    }
    private void edge(VertexConsumer v,float cx,float cy,float ax,float ay,float bx,float by,float ox,float oy,float px,float py){
        quad(v,cx,cy,bx,by,ax,ay,cx,cy,color,color,color,color);
        quad(v,ax,ay,bx,by,px,py,ox,oy,color,color,color&0xffffff,color&0xffffff);
    }
    private void arc(VertexConsumer v){
        double sweep=Math.clamp(progress,0,1)*Math.PI*2;
        if(sweep==0)return;
        float f=feather/2,outer=radius+width/2,inner=radius-width/2;
        int n=Math.max(1,(int)Math.ceil(segments(radius/feather)*progress));
        for(int i=0;i<n;i++){
            double a=-Math.PI/2+sweep*i/n,b=-Math.PI/2+sweep*(i+1)/n;
            band(v,a,b,inner+f,outer-f,color,color);
            band(v,a,b,inner-f,inner+f,color&0xffffff,color);
            band(v,a,b,outer-f,outer+f,color,color&0xffffff);
        }
        if(progress<1){cap(v,-Math.PI/2);cap(v,-Math.PI/2+sweep);}
    }
    private void band(VertexConsumer v,double a,double b,float in,float out,int ci,int co){
        quad(v,x+(float)Math.cos(a)*in,y+(float)Math.sin(a)*in,x+(float)Math.cos(b)*in,y+(float)Math.sin(b)*in,
             x+(float)Math.cos(b)*out,y+(float)Math.sin(b)*out,x+(float)Math.cos(a)*out,y+(float)Math.sin(a)*out,ci,ci,co,co);
    }
    private void cap(VertexConsumer v,double angle){
        float cx=x+(float)Math.cos(angle)*radius,cy=y+(float)Math.sin(angle)*radius;
        float r=width/2,f=feather/2;int n=segments(r/feather);
        // Only the outward half of each round cap, avoiding alpha overlap with the arc.
        double direction=angle==-Math.PI/2?angle-Math.PI/2:angle+Math.PI/2;
        for(int i=0;i<n/2;i++){
            double a=direction-Math.PI/2+Math.PI*i/(n/2),b=direction-Math.PI/2+Math.PI*(i+1)/(n/2);
            float ax=(float)Math.cos(a),ay=(float)Math.sin(a),bx=(float)Math.cos(b),by=(float)Math.sin(b);
            quad(v,cx,cy,cx+bx*(r-f),cy+by*(r-f),cx+ax*(r-f),cy+ay*(r-f),cx,cy,color,color,color,color);
            quad(v,cx+ax*(r-f),cy+ay*(r-f),cx+bx*(r-f),cy+by*(r-f),cx+bx*(r+f),cy+by*(r+f),cx+ax*(r+f),cy+ay*(r+f),color,color,color&0xffffff,color&0xffffff);
        }
    }
    public static int segments(double radiusPixels){return Math.clamp((int)Math.ceil(Math.PI/Math.acos(Math.max(-1,1-.15/Math.max(1,radiusPixels)))),16,256)/4*4;}
}
