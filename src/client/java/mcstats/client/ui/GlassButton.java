package mcstats.client.ui;

import mcstats.client.MCStatsClient;
import mcstats.client.animation.Spring;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Keeps vanilla focus, keyboard activation, and narration while replacing the visual treatment. */
public final class GlassButton extends Button {
    private final Spring hover=new Spring(0);
    private long last=System.nanoTime();
    private final float scale;
    public GlassButton(int x,int y,int w,int h,String label,Runnable action,float scale) {
        super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION);
        this.scale=scale;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        long now=System.nanoTime();
        double v=hover.update(isHoveredOrFocused()?1:0,(now-last)/1e9,.13,MCStatsClient.config.reducedMotion); last=now;
        Glass.panel(g,getX(),getY(),getWidth(),getHeight(),(int)(8*scale),v>.1?0x33495a:0x242f40,.92,true,false);
        if (isFocused()) g.outline(getX(),getY(),getWidth(),getHeight(),Glass.faded(0xff82dbc5));
        g.pose().pushMatrix(); g.pose().translate(getX()+8*scale,getY()+(getHeight()-10*scale)/2); g.pose().scale(scale);
        Glass.text(g,getMessage().getString(),0,0,0xffeaf3fc,0);
        g.pose().popMatrix();
    }
}
