package mcstats.client.mixin;

import mcstats.client.MCStatsClient;
import mcstats.client.ui.StudioScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class FrameSamplerMixin {
    @Inject(method="runTick",at=@At("HEAD"))
    private void mcstats$sampleFrame(boolean renderLevel,CallbackInfo ci) {
        Minecraft mc=(Minecraft)(Object)this;
        MCStatsClient.monitor.frame(mc,MCStatsClient.config,(mc.level!=null&&MCStatsClient.config.enabled&&!mc.gui.hud.isHidden()&&!MCStatsClient.config.widgets.isEmpty())||mc.gui.screen() instanceof StudioScreen);
    }
}
