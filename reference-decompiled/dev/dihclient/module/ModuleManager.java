package dev.dihclient.module;

import dev.dihclient.DIHClient;
import dev.dihclient.gui.ClickGuiScreen;
import dev.dihclient.modules.automation.AuctionHouse;
import dev.dihclient.modules.automation.AutoSell;
import dev.dihclient.modules.automation.FlipFinder;
import dev.dihclient.modules.automation.StashSorter;
import dev.dihclient.modules.automation.TaskQueue;
import dev.dihclient.modules.basefinding.AutoHunter;
import dev.dihclient.modules.basefinding.AutoTool;
import dev.dihclient.modules.basefinding.BaseTraces;
import dev.dihclient.modules.basefinding.BlockNotifier;
import dev.dihclient.modules.basefinding.NewChunks;
import dev.dihclient.modules.basefinding.PlayerActivity;
import dev.dihclient.modules.basefinding.RecentActivity;
import dev.dihclient.modules.basefinding.SpawnerFinder;
import dev.dihclient.modules.basefinding.SpawnerPie;
import dev.dihclient.modules.basefinding.SusChunkFinder;
import dev.dihclient.modules.basefinding.TuffChunkFinder;
import dev.dihclient.modules.client.BetterTablist;
import dev.dihclient.modules.client.ClickGui;
import dev.dihclient.modules.client.DihChat;
import dev.dihclient.modules.client.DiscordAlarm;
import dev.dihclient.modules.client.Enemies;
import dev.dihclient.modules.client.Friends;
import dev.dihclient.modules.client.Hud;
import dev.dihclient.modules.client.NotificationsModule;
import dev.dihclient.modules.client.Performance;
import dev.dihclient.modules.client.Profiles;
import dev.dihclient.modules.combat.AimAssist;
import dev.dihclient.modules.combat.AnchorMacro;
import dev.dihclient.modules.combat.AutoClicker;
import dev.dihclient.modules.combat.AutoInvTotem;
import dev.dihclient.modules.combat.AutoPot;
import dev.dihclient.modules.combat.AutoTotem;
import dev.dihclient.modules.combat.BowAimbot;
import dev.dihclient.modules.combat.Criticals;
import dev.dihclient.modules.combat.CrystalMacro;
import dev.dihclient.modules.combat.HitBoxes;
import dev.dihclient.modules.combat.KillAura;
import dev.dihclient.modules.combat.KnockbackControl;
import dev.dihclient.modules.combat.MaceCombo;
import dev.dihclient.modules.combat.Reach;
import dev.dihclient.modules.combat.ShieldBreaker;
import dev.dihclient.modules.combat.Surround;
import dev.dihclient.modules.combat.TriggerBot;
import dev.dihclient.modules.fun.BigHead;
import dev.dihclient.modules.fun.Cape;
import dev.dihclient.modules.fun.CustomSky;
import dev.dihclient.modules.fun.Dinnerbone;
import dev.dihclient.modules.fun.DiscoMode;
import dev.dihclient.modules.fun.DrunkMode;
import dev.dihclient.modules.fun.FakeWeather;
import dev.dihclient.modules.fun.Hats;
import dev.dihclient.modules.fun.ModelReplacer;
import dev.dihclient.modules.fun.Pet;
import dev.dihclient.modules.fun.TinyPlayers;
import dev.dihclient.modules.fun.UwuChat;
import dev.dihclient.modules.misc.AntiAfk;
import dev.dihclient.modules.misc.AutoLog;
import dev.dihclient.modules.misc.DummyPlayer;
import dev.dihclient.modules.misc.PacketBuffer;
import dev.dihclient.modules.misc.Radio;
import dev.dihclient.modules.misc.Watchlist;
import dev.dihclient.modules.movement.AirJump;
import dev.dihclient.modules.movement.AutoMLG;
import dev.dihclient.modules.movement.Blink;
import dev.dihclient.modules.movement.BoatFly;
import dev.dihclient.modules.movement.BoatNoClip;
import dev.dihclient.modules.movement.ElytraBoost;
import dev.dihclient.modules.movement.ElytraBot;
import dev.dihclient.modules.movement.ElytraFly;
import dev.dihclient.modules.movement.Flight;
import dev.dihclient.modules.movement.HighJump;
import dev.dihclient.modules.movement.Jesus;
import dev.dihclient.modules.movement.LongJump;
import dev.dihclient.modules.movement.Momentum;
import dev.dihclient.modules.movement.NoClip;
import dev.dihclient.modules.movement.NoFall;
import dev.dihclient.modules.movement.NoSlow;
import dev.dihclient.modules.movement.PacketFly;
import dev.dihclient.modules.movement.Parkour;
import dev.dihclient.modules.movement.ReverseStep;
import dev.dihclient.modules.movement.SafeWalk;
import dev.dihclient.modules.movement.Spider;
import dev.dihclient.modules.movement.Sprint;
import dev.dihclient.modules.movement.Step;
import dev.dihclient.modules.player.AutoArmor;
import dev.dihclient.modules.player.AutoCraft;
import dev.dihclient.modules.player.AutoEat;
import dev.dihclient.modules.player.AutoFish;
import dev.dihclient.modules.player.AutoMend;
import dev.dihclient.modules.player.AutoReconnect;
import dev.dihclient.modules.player.AutoRespawn;
import dev.dihclient.modules.player.AutoRestock;
import dev.dihclient.modules.player.AutoTrade;
import dev.dihclient.modules.player.AutoWalk;
import dev.dihclient.modules.player.ChestStealer;
import dev.dihclient.modules.player.ElytraSwap;
import dev.dihclient.modules.player.FastPlace;
import dev.dihclient.modules.player.HammerTool;
import dev.dihclient.modules.player.HotbarMacro;
import dev.dihclient.modules.player.InvManager;
import dev.dihclient.modules.player.InventoryMove;
import dev.dihclient.modules.player.PacketMine;
import dev.dihclient.modules.player.SurvivalAlerts;
import dev.dihclient.modules.render.BlockEsp;
import dev.dihclient.modules.render.Chams;
import dev.dihclient.modules.render.DamageNumbers;
import dev.dihclient.modules.render.Esp;
import dev.dihclient.modules.render.FreeLook;
import dev.dihclient.modules.render.Freecam;
import dev.dihclient.modules.render.Fullbright;
import dev.dihclient.modules.render.LogoutSpots;
import dev.dihclient.modules.render.MoneyHud;
import dev.dihclient.modules.render.Nametags;
import dev.dihclient.modules.render.NavigationHud;
import dev.dihclient.modules.render.NoRender;
import dev.dihclient.modules.render.StorageEsp;
import dev.dihclient.modules.render.TargetHud;
import dev.dihclient.modules.render.Trajectories;
import dev.dihclient.modules.render.Waypoints;
import dev.dihclient.modules.render.Xray;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.modules.world.AutoFarm;
import dev.dihclient.modules.world.AutoMine;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.modules.world.MapArt;
import dev.dihclient.modules.world.NetheriteFinder;
import dev.dihclient.modules.world.Nuker;
import dev.dihclient.modules.world.SafeRoute;
import dev.dihclient.modules.world.Scaffold;
import dev.dihclient.modules.world.SmartBridge;
import dev.dihclient.modules.world.Terraform;
import dev.dihclient.modules.world.Timer;
import dev.dihclient.modules.world.Tunnel;
import dev.dihclient.render.Render3D;
import dev.dihclient.util.BugReport;
import dev.dihclient.util.Money;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_1802;
import net.minecraft.class_2596;
import net.minecraft.class_310;
import net.minecraft.class_332;

public final class ModuleManager {
   private final List<Module> modules = new ArrayList<>();
   private final Map<Class<?>, Module> byClass = new HashMap<>();
   private final Map<String, Module> byName = new HashMap<>();
   private Object lastWorld;

   public void registerAll() {
      this.add(new KillAura());
      this.add(new TriggerBot());
      this.add(new AutoClicker());
      this.add(new Criticals());
      this.add(new KnockbackControl());
      this.add(new Reach());
      this.add(new AimAssist());
      this.add(new HitBoxes());
      this.add(new AutoTotem());
      this.add(new AutoInvTotem());
      this.add(new AutoPot());
      this.add(new ShieldBreaker());
      this.add(new CrystalMacro());
      this.add(new AnchorMacro());
      this.add(new BowAimbot());
      this.add(new Surround());
      this.add(new Flight());
      this.add(new BoatFly());
      this.add(new BoatNoClip());
      this.add(new NoClip());
      this.add(new Momentum());
      this.add(new Step());
      this.add(new Jesus());
      this.add(new NoFall());
      this.add(new Sprint());
      this.add(new NoSlow());
      this.add(new SafeWalk());
      this.add(new DihChat());
      this.add(new AuctionHouse());
      this.add(new AutoSell());
      this.add(new MoneyHud());
      this.add(new HammerTool());
      this.add(new LongJump());
      this.add(new HighJump());
      this.add(new AirJump());
      this.add(new Spider());
      this.add(new Parkour());
      this.add(new ElytraFly());
      this.add(new ElytraBot());
      this.add(new Blink());
      this.add(new PacketFly());
      this.add(new AutoMLG());
      this.add(new ElytraBoost());
      this.add(new ReverseStep());
      this.add(new InventoryMove());
      this.add(new AutoEat());
      this.add(new AutoArmor());
      this.add(new AutoFish());
      this.add(new AutoRespawn());
      this.add(new AutoReconnect());
      this.add(new PacketMine());
      this.add(new AutoWalk());
      this.add(new ChestStealer());
      this.add(new AutoRestock());
      this.add(new AutoCraft());
      this.add(new InvManager());
      this.add(new AutoMend());
      this.add(new AutoTrade());
      this.add(new FastPlace());
      this.add(new HotbarMacro("Key Pearl", "Throws an ender pearl from the hotbar and restores your previous slot.", class_1802.field_8634));
      this.add(new HotbarMacro("Key Wind Charge", "Uses a wind charge from the hotbar and restores your previous slot.", class_1802.field_49098));
      this.add(new HotbarMacro("Glowstone Macro", "Selects glowstone from the hotbar, uses it once, then restores your previous slot.", class_1802.field_8801));
      this.add(new ElytraSwap());
      this.add(new Fullbright());
      this.add(new Freecam());
      this.add(new FreeLook());
      this.add(new Esp());
      this.add(new BlockEsp());
      this.add(new Xray());
      this.add(new StorageEsp());
      this.add(new Nametags());
      this.add(new LogoutSpots());
      this.add(new Chams());
      this.add(new Trajectories());
      this.add(new DamageNumbers());
      this.add(new NoRender());
      this.add(new TargetHud());
      this.add(new NavigationHud());
      this.add(new Waypoints());
      this.add(new Scaffold());
      this.add(new SmartBridge());
      this.add(new Timer());
      this.add(new Nuker());
      this.add(new AutoBuild());
      this.add(new MapArt());
      this.add(new Terraform());
      this.add(new Tunnel());
      this.add(new AutoMine());
      this.add(new AutoFarm());
      this.add(new StashSorter());
      this.add(new TaskQueue());
      this.add(new Goto());
      this.add(new SafeRoute());
      this.add(new NetheriteFinder());
      this.add(new SusChunkFinder());
      this.add(new TuffChunkFinder());
      this.add(new PlayerActivity());
      this.add(new RecentActivity());
      this.add(new NewChunks());
      this.add(new BaseTraces());
      this.add(new SpawnerFinder());
      this.add(new AutoTool());
      this.add(new BlockNotifier());
      this.add(new AntiAfk());
      this.add(new AutoLog());
      this.add(new Watchlist());
      this.add(new Radio());
      this.add(new SurvivalAlerts());
      this.add(new PacketBuffer());
      this.add(new DummyPlayer());
      this.add(new UwuChat());
      this.add(new BigHead());
      this.add(new Pet());
      this.add(new Cape());
      this.add(new ModelReplacer());
      this.add(new CustomSky());
      this.add(new Dinnerbone());
      this.add(new TinyPlayers());
      this.add(new DiscoMode());
      this.add(new DrunkMode());
      this.add(new FakeWeather());
      this.add(new Hats());
      this.add(new FlipFinder());
      this.add(new MaceCombo());
      this.add(new AutoHunter());
      this.add(new SpawnerPie());
      this.add(new ClickGui());
      this.add(new Hud());
      this.add(new NotificationsModule());
      this.add(new DiscordAlarm());
      this.add(new Performance());
      this.add(new Friends());
      this.add(new Enemies());
      this.add(new BetterTablist());
      this.add(new Profiles());

      try {
         Field var1 = ClickGuiScreen.class.getDeclaredField("ICONS");
         var1.setAccessible(true);
         ((Map)var1.get(null)).put(Category.AUTOMATION, "⚒");
      } catch (Throwable var2) {
      }
   }

   private void add(Module var1) {
      this.modules.add(var1);
      this.byClass.putIfAbsent(var1.getClass(), var1);
      this.byName.put(var1.name().toLowerCase(Locale.ROOT), var1);
      this.byName.put(var1.id(), var1);
   }

   public <T extends Module> T get(Class<T> var1) {
      return (T)this.byClass.get(var1);
   }

   public Module get(String var1) {
      return var1 == null ? null : this.byName.get(var1.toLowerCase(Locale.ROOT));
   }

   public static <T extends Module> T of(Class<T> var0) {
      return DIHClient.modules().get(var0);
   }

   public static boolean on(Class<? extends Module> var0) {
      ModuleManager var1 = DIHClient.modules();
      if (var1 == null) {
         return false;
      } else {
         Module var2 = var1.get(var0);
         return var2 != null && var2.isEnabled();
      }
   }

   public List<Module> all() {
      return Collections.unmodifiableList(this.modules);
   }

   public List<Module> byCategory(Category var1) {
      ArrayList var2 = new ArrayList();

      for (Module var4 : this.modules) {
         if (var4.category() == var1 && !var4.isHidden()) {
            var2.add(var4);
         }
      }

      return var2;
   }

   public void tick() {
      class_310 var1 = class_310.method_1551();
      if (var1.field_1687 != this.lastWorld) {
         boolean var2 = this.lastWorld != null && var1.field_1687 != null;
         this.lastWorld = var1.field_1687;
         if (var2) {
            this.onWorldChange();
         }
      }

      if (var1.field_1724 != null && var1.field_1687 != null) {
         try {
            Money.tick();
         } catch (Throwable var4) {
         }

         for (Module var7 : this.modules) {
            if (var7.isEnabled()) {
               var7.runPendingEnable();
               safe(var7, "tick", var7::onTick);
            }
         }
      } else {
         for (Module var3 : this.modules) {
            if (var3.isEnabled() && var3 instanceof ModuleManager.MenuTicking) {
               safe(var3, "tick", var3::onTick);
            }
         }
      }
   }

   public void render2D(class_332 var1, float var2) {
      int var3 = 0;

      for (int var4 = this.modules.size(); var3 < var4; var3++) {
         Module var5 = this.modules.get(var3);
         if (var5.isEnabled()) {
            try {
               var5.onRender2D(var1, var2);
            } catch (Throwable var7) {
               fail(var5, "render2D", var7);
            }
         }
      }
   }

   public void render3D(Render3D var1) {
      int var2 = 0;

      for (int var3 = this.modules.size(); var2 < var3; var2++) {
         Module var4 = this.modules.get(var2);
         if (var4.isEnabled()) {
            try {
               var4.onRender3D(var1);
            } catch (Throwable var6) {
               fail(var4, "render3D", var6);
            }
         }
      }
   }

   public boolean packetSend(class_2596<?> var1) {
      boolean var2 = false;

      for (Module var4 : this.modules) {
         if (var4.isEnabled()) {
            try {
               if (var4.onPacketSend(var1)) {
                  var2 = true;
               }
            } catch (Throwable var6) {
               DIHClient.LOG.error("[DIHClient] {} packet hook failed", var4.name(), var6);
            }
         }
      }

      return var2;
   }

   public void onWorldChange() {
      for (Module var2 : this.modules) {
         safe(var2, "worldChange", var2::onWorldChange);
      }
   }

   public void onKey(int var1) {
      if (var1 >= 0) {
         for (Module var3 : this.modules) {
            if (var3.keybind() == var1) {
               if (var3.isActionModule()) {
                  if (var3.isEnabled() || !var3.isToggleable()) {
                     safe(var3, "action", var3::onAction);
                  }
               } else {
                  var3.toggle();
               }
            }
         }
      }
   }

   private static void safe(Module var0, String var1, Runnable var2) {
      try {
         var2.run();
      } catch (Throwable var4) {
         fail(var0, var1, var4);
      }
   }

   private static void fail(Module var0, String var1, Throwable var2) {
      DIHClient.LOG.error("[DIHClient] {} {} failed", new Object[]{var0.name(), var1, var2});
      BugReport.error(var0.name() + " " + var1, var2);
   }

   public interface MenuTicking {
   }
}
