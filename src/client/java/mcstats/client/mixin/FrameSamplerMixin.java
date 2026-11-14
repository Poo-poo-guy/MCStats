package mcstats.client.mixin;

import mcstats.client.MCStatsClient;
import mcstats.client.ui.StudioScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FrameSamplerMixin {
    @Inject(method="renderFrame",at=@At(value="FIELD",target="Lnet/minecraft/client/Minecraft;frames:I",opcode=Opcodes.PUTFIELD,ordinal=0,shift=At.Shift.AFTER))
    private void mcstats$sampleFrame(boolean renderLevel,CallbackInfo ci) {
        Minecraft mc=(Minecraft)(Object)this;
        long start=mcstats.client.metrics.Diagnostics.begin(),bytes=mcstats.client.metrics.Diagnostics.allocated();
        MCStatsClient.monitor.frame(mc,MCStatsClient.config,(mc.level!=null&&MCStatsClient.config.enabled&&!mc.gui.hud.isHidden()&&!MCStatsClient.config.widgets.isEmpty())||mc.gui.screen() instanceof StudioScreen);
        mcstats.client.metrics.Diagnostics.end(0,start,bytes);
        mcstats.client.metrics.Diagnostics.completedFrame(mc.getFps());
    }
}
