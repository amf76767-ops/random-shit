package dev.dihclient;

import dev.dihclient.config.ConfigManager;
import dev.dihclient.gui.MeteorGuiScreen;
import dev.dihclient.hud.HudManager;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.render.Render3D;
import dev.dihclient.scan.ScanBudget;
import dev.dihclient.social.SocialManager;
import dev.dihclient.util.Notifications;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.EndMain;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2960;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_304.class_11900;
import net.minecraft.class_3675.class_307;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DIHClient implements ClientModInitializer {
   public static final String NAME = "DIHClient";
   public static final String VERSION = "3";
   public static final String MOD_ID = "dihclient";
   public static final Logger LOG = LoggerFactory.getLogger("DIHClient");
   private static ModuleManager modules;
   private static ConfigManager config;
   private static SocialManager social;
   private static HudManager hud;
   private static class_304 clickGuiKey;
   private static int ticks;

   public void onInitializeClient() {
      Path var1 = FabricLoader.getInstance().getConfigDir().resolve("dihclient");
      social = new SocialManager(var1);
      modules = new ModuleManager();
      hud = new HudManager();
      config = new ConfigManager(var1);
      modules.registerAll();
      hud.registerDefaults();
      config.load();
      social.load();
      class_11900 var2 = class_11900.method_74698(class_2960.method_60655("dihclient", "main"));
      clickGuiKey = KeyBindingHelper.registerKeyBinding(new class_304("key.dihclient.clickgui", class_307.field_1668, 344, var2));
      ClientTickEvents.END_CLIENT_TICK.register(DIHClient::onEndTick);
      HudElementRegistry.addLast(class_2960.method_60655("dihclient", "hud"), (var0, var1x) -> hud.render(var0, var1x.method_60637(true)));
      WorldRenderEvents.END_MAIN.register((EndMain)var0 -> Render3D.render(var0, modules));
      ClientPlayConnectionEvents.JOIN.register((Join)(var0, var1x, var2x) -> modules.onWorldChange());
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(var0, var1x) -> modules.onWorldChange());
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)var0 -> config.save());
      LOG.info("[{}] {} loaded with {} modules", new Object[]{"DIHClient", "3", modules.all().size()});
   }

   private static void onEndTick(class_310 var0) {
      ticks++;

      while (clickGuiKey.method_1436()) {
         if (var0.field_1755 == null) {
            var0.method_1507(MeteorGuiScreen.create());
         }
      }

      ScanBudget.begin();
      modules.tick();
      Notifications.tick();
      config.tickAutosave();
   }

   public static ModuleManager modules() {
      return modules;
   }

   public static ConfigManager config() {
      return config;
   }

   public static SocialManager social() {
      return social;
   }

   public static HudManager hud() {
      return hud;
   }

   public static int ticks() {
      return ticks;
   }

   public static class_304 clickGuiKey() {
      return clickGuiKey;
   }
}
