package mcstats.client;

import mcstats.client.config.HudConfig;
import mcstats.client.metrics.Monitor;
import mcstats.client.ui.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.io.IOException;
import org.slf4j.LoggerFactory;

public final class MCStatsClient implements ClientModInitializer {
    public static final Path configDirectory=FabricLoader.getInstance().getConfigDir().resolve("mcstats");
    public static HudConfig config=new HudConfig();
    public static final Monitor monitor=new Monitor();
    public static final Hud hud=new Hud();
    private static KeyMapping toggle,dashboard,studio;
    @Override public void onInitializeClient() {
        Path file=configDirectory.resolve("current.properties");
        if(Files.exists(file))try{config=HudConfig.load(file);}catch(IOException ex){LoggerFactory.getLogger("mcstats").warn("Could not load MCStats+ profile; keeping file intact",ex);}
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mcstats","controls"));
        toggle=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcstats.toggle",InputConstants.KEY_F7,category));
        dashboard=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcstats.dashboard",InputConstants.KEY_F8,category));
        studio=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcstats.studio",InputConstants.KEY_F9,category));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            while(toggle.consumeClick()){config.enabled=!config.enabled;save();}
            while(dashboard.consumeClick())mc.gui.setScreen(new StudioScreen(false));
            while(studio.consumeClick())mc.gui.setScreen(new StudioScreen(true));
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mcstats","hud"),(g,t)->{
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(config.enabled&&!mc.gui.hud.isHidden()&&mc.level!=null&&!(mc.gui.screen() instanceof StudioScreen))hud.draw(g,false);
        });
        ScreenEvents.AFTER_INIT.register((mc,screen,width,height)->{
            if(screen instanceof TitleScreen)Screens.getWidgets(screen).add(new GlassButton(8,height-36,92,22,"MCStats+",()->mc.gui.setScreen(new StudioScreen(false)),1));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc->monitor.close());
    }
    public static void save(){try{config.save(configDirectory.resolve("current.properties"));}catch(IOException ex){LoggerFactory.getLogger("mcstats").error("Could not save MCStats+ profile",ex);}}
}
