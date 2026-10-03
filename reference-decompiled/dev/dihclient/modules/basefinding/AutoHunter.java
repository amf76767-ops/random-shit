package dev.dihclient.modules.basefinding;

import dev.dihclient.autobuild.AhReader;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.world.Goto;
import dev.dihclient.render.Render3D;
import dev.dihclient.scan.ChunkMarkModule;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.CombatUtil;
import dev.dihclient.util.Money;
import dev.dihclient.util.Notifications;
import dev.dihclient.waypoint.WaypointManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_238;
import net.minecraft.class_638;

public class AutoHunter extends Module {
   private static final String[] KNOWN = new String[]{"eu central", "eu west", "na east", "na west", "asia", "oceania"};
   public final StringSetting regions = this.text(
      "Search Regions",
      "Regions to hunt in, comma separated (e.g. \"eu central, na east\"). Lands it somewhere else it /rtps again. Empty = any region.",
      "eu central",
      128
   );
   public final StringSetting rtpCommand = this.text("RTP Command", "Command for random teleport. The region argument is added: /rtp <region>.", "rtp", 32);
   public final StringSetting regionArgs = this.text(
      "Region Args", "Argument per region for /rtp <region>, e.g. \"eu central=eu-central, na east=na-east\". Missing = region name without spaces.", "", 256
   );
   public final BoolSetting useMenu = this.bool(
      "Use Menu", "Old servers: /rtp opens a menu and the region item is clicked. Off = /rtp <region> directly.", false
   );
   public final StringSetting dimensionWord = this.text("Dimension Word", "If the RTP menu asks for a dimension, this item is clicked.", "overworld", 32)
      .visibleWhen(this.useMenu::get);
   public final IntSetting areaSize = this.integer("Area Size", "Side length of the square that is searched around each landing spot (blocks).", 256, 64, 2048);
   public final IntSetting laneSpacing = this.integer(
      "Lane Spacing", "Distance between two walking lanes. Should be below 2x the server's reveal radius (2 chunks = 64).", 48, 16, 160
   );
   public final IntSetting legLength = this.integer("Leg Length", "Goto target every this many blocks along a lane.", 48, 16, 256);
   public final BoolSetting surface = this.bool("Surface", "Walks on the surface. Off = tunnels at Tunnel Y (Goto digs).", true);
   public final IntSetting tunnelY = this.integer("Tunnel Y", "Height of the tunnels when Surface is off.", -40, -60, 120)
      .visibleWhen(() -> !this.surface.get());
   public final BoolSetting useSus = this.bool("Use Sus ChunkFinder", "Counts Sus ChunkFinder marks as finds.", true);
   public final BoolSetting useTraces = this.bool("Use BaseTraces", "Counts BaseTraces marks as finds.", true);
   public final BoolSetting useNewChunks = this.bool("Use NewChunks (player blocks)", "Counts NewChunks chunks with player-made blocks as finds.", true);
   public final BoolSetting useSpawner = this.bool("Use SpawnerFinder", "Counts SpawnerFinder marks as finds.", false);
   public final BoolSetting useTuff = this.bool("Use TuffChunkFinder", "Counts TuffChunkFinder marks as finds.", false);
   public final BoolSetting enableDetectors = this.bool("Enable Detectors", "Turns the chosen finder modules on when the hunt starts.", true);
   public final BoolSetting pauseOnFind = this.bool("Pause On Find", "Stops walking when something is found (Resume continues).", true);
   public final BoolSetting stopOnPlayer = this.bool("Pause On Player", "Pauses when a non-friend player comes close.", true);
   public final IntSetting playerRange = this.integer("Player Range", "Distance for Pause On Player.", 48, 8, 128).visibleWhen(this.stopOnPlayer::get);
   public final IntSetting minHealth = this.integer("Min Health", "Pauses below this health (0 = off).", 10, 0, 19);
   public final IntSetting legTimeout = this.integer("Leg Timeout", "Seconds per Goto leg before it is skipped.", 120, 20, 600);
   public final IntSetting landDelay = this.integer("Land Delay", "Ticks to wait after landing (chunks load, scoreboard updates).", 60, 20, 400);
   public final IntSetting rtpRetry = this.integer("RTP Retry", "Seconds to wait when /rtp failed or is on cooldown.", 20, 5, 300);
   public final BoolSetting skipExplored = this.bool("Skip Explored", "/rtp again when it lands in an area that was already searched.", true);
   public final BoolSetting render = this.bool("Render", "Draws the current leg and the finds.", true);
   private AutoHunter.State state = AutoHunter.State.RTP_SEND;
   private AutoHunter.State afterCooldown = AutoHunter.State.RTP_SEND;
   private long cooldownUntil;
   private int ticks;
   private int guiStep;
   private boolean fallbackSent;
   private long sentAt;
   private class_638 startWorld;
   private double startX;
   private double startZ;
   private String chosen = "";
   private String region = "?";
   private int regionIndex;
   private final List<int[]> legs = new ArrayList<>();
   private int leg;
   private boolean legStarted;
   private int legTicks;
   private int failures;
   private int areaX;
   private int areaZ;
   private final Set<String> seen = new HashSet<>();
   private int finds;
   private String status = "Idle";
   private boolean loaded;
   private final List<String[]> landings = new ArrayList<>();
   private final List<String[]> explored = new ArrayList<>();
   private final List<String[]> found = new ArrayList<>();

   public AutoHunter() {
      super(
         "AutoHunter",
         Category.BASEFINDING,
         "Base hunting autopilot: /rtp into your regions, maps the regions, walks lanes with Goto and lets the finders check every chunk."
      );
      this.action("Hunt Here", "Searches around your current position without /rtp.", this::huntHere);
      this.action("Resume", "Continues after a pause.", this::resume);
      this.action("Skip Area", "Leaves this area and does the next /rtp.", () -> {
         if (this.isEnabled()) {
            this.finishArea("skipped");
         }
      });
      this.action("Export Map", "Writes landings, searched areas and finds to dihclient/autohunter-map.csv.", this::export);
      this.action("Clear Map", "Forgets all landings, searched areas and finds.", () -> {
         this.landings.clear();
         this.explored.clear();
         this.found.clear();
         this.save();
         Notifications.info("AutoHunter", "Map cleared");
      });
   }

   private static Path file(String var0) {
      return FabricLoader.getInstance().getGameDir().resolve("dihclient").resolve(var0);
   }

   private void load() {
      this.landings.clear();
      this.explored.clear();
      this.found.clear();

      try {
         for (String var2 : Files.readAllLines(file("autohunter.txt"))) {
            String[] var3 = var2.split("\t");
            if (var3.length >= 2) {
               if (var3[0].equals("L")) {
                  this.landings.add(var3);
               } else if (var3[0].equals("E")) {
                  this.explored.add(var3);
               } else if (var3[0].equals("F")) {
                  this.found.add(var3);
               }
            }
         }
      } catch (Exception var4) {
      }

      this.loaded = true;
   }

   private void save() {
      try {
         Files.createDirectories(file("autohunter.txt").getParent());
         ArrayList var1 = new ArrayList();

         for (String[] var3 : this.landings) {
            var1.add(String.join("\t", var3));
         }

         for (String[] var7 : this.explored) {
            var1.add(String.join("\t", var7));
         }

         for (String[] var8 : this.found) {
            var1.add(String.join("\t", var8));
         }

         Files.write(file("autohunter.txt"), var1);
      } catch (Exception var4) {
      }
   }

   private void export() {
      if (!this.loaded) {
         this.load();
      }

      ArrayList var1 = new ArrayList();
      var1.add("kind,region,x,z,extra");

      for (String[] var3 : this.landings) {
         var1.add("landing," + var3[1] + "," + var3[2] + "," + var3[3] + ",");
      }

      for (String[] var7 : this.explored) {
         var1.add("searched," + var7[1] + "," + var7[2] + "," + var7[3] + ",size " + var7[4]);
      }

      for (String[] var8 : this.found) {
         var1.add("find," + var8[1] + "," + var8[3] + "," + var8[5] + "," + var8[2] + " y" + var8[4]);
      }

      try {
         Files.createDirectories(file("autohunter-map.csv").getParent());
         Files.write(file("autohunter-map.csv"), var1);
         Notifications.info("AutoHunter", "Exported " + (var1.size() - 1) + " entries to dihclient/autohunter-map.csv");
      } catch (Exception var4) {
         Notifications.error("AutoHunter", "Export failed: " + var4.getMessage());
      }
   }

   private List<String> allowed() {
      ArrayList var1 = new ArrayList();

      for (String var5 : this.regions.get().split(",")) {
         String var6 = var5.trim().toLowerCase(Locale.ROOT);
         if (!var6.isEmpty()) {
            var1.add(var6);
         }
      }

      return var1;
   }

   private String argFor(String var1) {
      for (String var5 : this.regionArgs.get().split(",")) {
         int var6 = var5.indexOf(61);
         if (var6 > 0 && var5.substring(0, var6).trim().equalsIgnoreCase(var1)) {
            return var5.substring(var6 + 1).trim();
         }
      }

      return var1.replace(" ", "");
   }

   private String detectRegion() {
      ArrayList var1 = new ArrayList();

      for (String var3 : Money.sidebarLines()) {
         var1.add(var3.toLowerCase(Locale.ROOT));
      }

      ArrayList var8 = new ArrayList<>(this.allowed());

      for (String var6 : KNOWN) {
         if (!var8.contains(var6)) {
            var8.add(var6);
         }
      }

      for (String var12 : var1) {
         for (String var16 : var8) {
            if (var12.contains(var16)) {
               return var16;
            }
         }
      }

      for (String var13 : var1) {
         int var15 = var13.indexOf("region");
         int var17 = var13.indexOf(58, Math.max(0, var15));
         if (var15 >= 0 && var17 > 0 && var17 + 1 < var13.length()) {
            String var7 = var13.substring(var17 + 1).trim();
            if (!var7.isEmpty()) {
               return var7;
            }
         }
      }

      return this.chosen.isEmpty() ? "?" : this.chosen;
   }

   private boolean teleported() {
      return mc.field_1687 == this.startWorld
         ? Math.hypot(mc.field_1724.method_23317() - this.startX, mc.field_1724.method_23321() - this.startZ) > 300.0
         : mc.field_1687 != null && mc.field_1724 != null;
   }

   private boolean chatHas(String... var1) {
      for (String var3 : Money.chatSince(this.sentAt)) {
         for (String var7 : var1) {
            if (var3.contains(var7)) {
               return true;
            }
         }
      }

      return false;
   }

   private void cooldown(int var1, AutoHunter.State var2, String var3) {
      this.cooldownUntil = System.currentTimeMillis() + var1 * 1000L;
      this.afterCooldown = var2;
      this.state = AutoHunter.State.COOLDOWN;
      this.status = var3;
   }

   private boolean insideExplored(int var1, int var2) {
      for (String[] var4 : this.explored) {
         try {
            if (var4[1].equals(this.region)) {
               int var5 = Integer.parseInt(var4[2]);
               int var6 = Integer.parseInt(var4[3]);
               int var7 = Integer.parseInt(var4[4]) / 2;
               if (Math.abs(var1 - var5) < var7 && Math.abs(var2 - var6) < var7) {
                  return true;
               }
            }
         } catch (Exception var8) {
         }
      }

      return false;
   }

   private void plan(int var1, int var2) {
      this.legs.clear();
      this.areaX = var1;
      this.areaZ = var2;
      int var3 = this.areaSize.get() / 2;
      int var4 = this.laneSpacing.get();
      int var5 = this.legLength.get();
      boolean var6 = true;

      for (int var7 = var2 - var3; var7 <= var2 + var3; var7 += var4) {
         ArrayList var8 = new ArrayList();

         for (int var9 = var1 - var3; var9 <= var1 + var3; var9 += var5) {
            var8.add(new int[]{var9, var7});
         }

         int var10 = var8.isEmpty() ? var1 - var3 : ((int[])var8.get(var8.size() - 1))[0];
         if (var10 < var1 + var3) {
            var8.add(new int[]{var1 + var3, var7});
         }

         if (!var6) {
            Collections.reverse(var8);
         }

         this.legs.addAll(var8);
         var6 = !var6;
      }

      this.leg = 0;
      this.legStarted = false;
      this.failures = 0;
      this.state = AutoHunter.State.WALK;
   }

   private void finishArea(String var1) {
      if (Goto.running()) {
         Goto.stop();
      }

      if (!this.legs.isEmpty()) {
         this.explored.add(new String[]{"E", this.region, this.areaX + "", this.areaZ + "", this.areaSize.get() + "", System.currentTimeMillis() + ""});
         this.save();
      }

      this.legs.clear();
      Notifications.info("AutoHunter", "Area " + var1 + " (" + this.finds + " finds so far) – next /rtp");
      this.cooldown(3, AutoHunter.State.RTP_SEND, "Area " + var1);
   }

   private void pause(String var1) {
      if (Goto.running()) {
         Goto.stop();
      }

      this.state = AutoHunter.State.PAUSED;
      this.status = "Paused: " + var1;
      Notifications.alert("AutoHunter: " + var1, 80);
   }

   private void resume() {
      if (this.isEnabled() && this.state == AutoHunter.State.PAUSED) {
         this.legStarted = false;
         this.state = this.legs.isEmpty() ? AutoHunter.State.RTP_SEND : AutoHunter.State.WALK;
         this.status = "Resumed";
      }
   }

   private void huntHere() {
      if (inGame()) {
         if (!this.isEnabled()) {
            this.setEnabled(true);
         }

         this.region = this.detectRegion();
         this.plan((int)Math.floor(mc.field_1724.method_23317()), (int)Math.floor(mc.field_1724.method_23321()));
         this.status = "Hunting here (" + this.region + ")";
      }
   }

   private void enableDetectors() {
      if (this.useSus.get()) {
         this.turnOn(SusChunkFinder.class);
      }

      if (this.useTraces.get()) {
         this.turnOn(BaseTraces.class);
      }

      if (this.useNewChunks.get()) {
         this.turnOn(NewChunks.class);
      }

      if (this.useSpawner.get()) {
         this.turnOn(SpawnerFinder.class);
      }

      if (this.useTuff.get()) {
         this.turnOn(TuffChunkFinder.class);
      }
   }

   private void turnOn(Class<? extends Module> var1) {
      Module var2 = ModuleManager.of(var1);
      if (var2 != null && !var2.isEnabled()) {
         var2.setEnabled(true);
      }
   }

   private void checkFinds() {
      LinkedHashMap var1 = new LinkedHashMap();
      if (this.useSus.get()) {
         var1.put("Sus chunk", ModuleManager.of(SusChunkFinder.class));
      }

      if (this.useTraces.get()) {
         var1.put("Base traces", ModuleManager.of(BaseTraces.class));
      }

      if (this.useNewChunks.get()) {
         var1.put("Player blocks", ModuleManager.of(NewChunks.class));
      }

      if (this.useSpawner.get()) {
         var1.put("Spawner", ModuleManager.of(SpawnerFinder.class));
      }

      if (this.useTuff.get()) {
         var1.put("Tuff chunk", ModuleManager.of(TuffChunkFinder.class));
      }

      Iterator var2 = var1.entrySet().iterator();

      while (true) {
         Entry var3;
         ChunkMarkModule var4;
         ArrayList var5;
         while (true) {
            if (!var2.hasNext()) {
               return;
            }

            var3 = (Entry)var2.next();
            var4 = (ChunkMarkModule)var3.getValue();
            if (var4 != null && var4.isEnabled()) {
               try {
                  var5 = new ArrayList<>(var4.chunkMarks().entrySet());
                  break;
               } catch (Exception var13) {
               }
            }
         }

         for (Entry var7 : var5) {
            Integer var8 = (Integer)var7.getValue();
            if (!(var4 instanceof NewChunks var9 && (var8 == null || var8 != var9.playerColor.get()))
               && !(var4 instanceof SusChunkFinder var14 && var8 != null && var8 == var14.maybeColor.get())) {
               long var15 = (Long)var7.getKey();
               if (this.seen.add(this.region + "|" + (String)var3.getKey() + "|" + var15)) {
                  int var11 = ((int)var15 << 4) + 8;
                  int var12 = ((int)(var15 >>> 32) << 4) + 8;
                  this.onFind((String)var3.getKey(), var11, (int)Math.floor(mc.field_1724.method_23318()), var12);
               }
            }
         }
      }
   }

   private void onFind(String var1, int var2, int var3, int var4) {
      this.finds++;
      this.found.add(new String[]{"F", this.region, var1.replace('\t', ' '), var2 + "", var3 + "", var4 + "", System.currentTimeMillis() + ""});
      this.save();

      try {
         WaypointManager.get().add("Hunt " + this.finds + " " + var1, var2, var3, var4, -49088);
      } catch (Exception var6) {
      }

      Notifications.push("AutoHunter", var1 + " at " + var2 + " " + var4 + " (" + this.region + ")", Notifications.Type.SUCCESS);
      if (this.pauseOnFind.get() && this.state == AutoHunter.State.WALK) {
         this.pause(var1 + " found at " + var2 + " " + var4);
      }
   }

   private void safety() {
      if (this.state == AutoHunter.State.WALK) {
         if (this.minHealth.get() > 0 && mc.field_1724.method_6032() < this.minHealth.get().intValue()) {
            this.pause("low health");
         } else if (this.stopOnPlayer.get() && CombatUtil.findTarget(this.playerRange.get().intValue()) != null) {
            this.pause("player nearby");
         }
      }
   }

   @Override
   protected void onEnable() {
      if (!inGame()) {
         this.setEnabledSilently(false);
      } else {
         if (!this.loaded) {
            this.load();
         }

         if (this.enableDetectors.get()) {
            this.enableDetectors();
         }

         this.state = AutoHunter.State.RTP_SEND;
         this.ticks = 0;
         this.legs.clear();
         this.status = "Starting";
      }
   }

   @Override
   protected void onDisable() {
      if (Goto.running()) {
         Goto.stop();
      }

      this.legs.clear();
      this.status = "Idle";
   }

   @Override
   public void onTick() {
      if (inGame()) {
         this.ticks++;
         if (this.ticks % 20 == 0 && (this.state == AutoHunter.State.WALK || this.state == AutoHunter.State.PAUSED)) {
            this.checkFinds();
         }

         this.safety();
         switch (this.state) {
            case RTP_SEND:
               if (mc.field_1755 != null) {
                  return;
               }

               List var8 = this.allowed();
               this.chosen = var8.isEmpty() ? "" : (String)var8.get(this.regionIndex++ % var8.size());
               this.startWorld = mc.field_1687;
               this.startX = mc.field_1724.method_23317();
               this.startZ = mc.field_1724.method_23321();
               this.sentAt = System.currentTimeMillis();
               this.guiStep = 0;
               this.fallbackSent = false;
               String var10 = this.rtpCommand.get().trim().replaceFirst("^/", "");
               if (!this.useMenu.get()) {
                  String var11 = this.chosen.isEmpty() ? var10 : var10 + " " + this.argFor(this.chosen);
                  AhReader.command(var11);
                  this.fallbackSent = true;
                  this.state = AutoHunter.State.RTP_WAIT;
                  this.ticks = 0;
                  this.status = "/" + var11;
               } else {
                  AhReader.command(var10);
                  this.state = AutoHunter.State.RTP_GUI;
                  this.ticks = 0;
                  this.status = "/" + var10 + (this.chosen.isEmpty() ? "" : " → " + this.chosen);
               }
               break;
            case RTP_GUI:
               if (this.teleported()) {
                  this.state = AutoHunter.State.LANDED;
                  this.ticks = 0;
               } else {
                  if (AhReader.open() && this.ticks > 6) {
                     if (this.guiStep == 0) {
                        int var5 = this.chosen.isEmpty() ? -1 : AhReader.findByWords(this.chosen.split(" "));
                        if (var5 < 0 && this.chosen.isEmpty()) {
                           var5 = AhReader.findByWords("random");
                        }

                        if (var5 >= 0) {
                           AhReader.click(var5);
                           this.guiStep = 1;
                           this.ticks = 0;
                           this.status = "Clicked " + (this.chosen.isEmpty() ? "random" : this.chosen);
                        } else if (this.ticks > 30) {
                           AhReader.close();
                        }
                     } else if (this.guiStep == 1 && this.ticks > 10) {
                        int var6 = AhReader.findByWords(this.dimensionWord.get().trim().toLowerCase(Locale.ROOT));
                        if (var6 >= 0) {
                           AhReader.click(var6);
                        }

                        this.guiStep = 2;
                        this.ticks = 0;
                     }
                  }

                  if (this.ticks > 40 && this.guiStep == 0 && !AhReader.open() && !this.fallbackSent) {
                     String var7 = this.rtpCommand.get().trim().replaceFirst("^/", "");
                     if (!this.chosen.isEmpty()) {
                        AhReader.command(var7 + " " + this.argFor(this.chosen));
                     }

                     this.fallbackSent = true;
                     this.state = AutoHunter.State.RTP_WAIT;
                     this.ticks = 0;
                  } else if (this.guiStep == 2 || this.guiStep == 1 && this.ticks > 30) {
                     this.state = AutoHunter.State.RTP_WAIT;
                     this.ticks = 0;
                  }
               }
               break;
            case RTP_WAIT:
               if (this.teleported()) {
                  if (AhReader.open()) {
                     AhReader.close();
                  }

                  this.state = AutoHunter.State.LANDED;
                  this.ticks = 0;
               } else if (this.chatHas("cooldown", "abklingzeit", "you must wait", "musst warten", "please wait before", "on cooldown")) {
                  if (AhReader.open()) {
                     AhReader.close();
                  }

                  this.cooldown(this.rtpRetry.get(), AutoHunter.State.RTP_SEND, "RTP cooldown – retrying in " + this.rtpRetry.get() + "s");
               } else if (this.ticks > 400) {
                  if (AhReader.open()) {
                     AhReader.close();
                  }

                  this.cooldown(this.rtpRetry.get(), AutoHunter.State.RTP_SEND, "No teleport – retrying");
               } else {
                  this.status = "Waiting for teleport";
               }
               break;
            case LANDED:
               if (this.ticks >= this.landDelay.get() && (mc.field_1724.method_24828() || this.ticks >= 300)) {
                  this.region = this.detectRegion();
                  int var4 = (int)Math.floor(mc.field_1724.method_23317());
                  int var9 = (int)Math.floor(mc.field_1724.method_23321());
                  this.landings.add(new String[]{"L", this.region, var4 + "", var9 + "", System.currentTimeMillis() + ""});
                  this.save();
                  List var3 = this.allowed();
                  if (!var3.isEmpty() && !this.region.equals("?") && !var3.contains(this.region)) {
                     this.cooldown(this.rtpRetry.get(), AutoHunter.State.RTP_SEND, "Landed in " + this.region + " – not wanted, /rtp again");
                  } else if (this.skipExplored.get() && this.insideExplored(var4, var9)) {
                     this.cooldown(this.rtpRetry.get(), AutoHunter.State.RTP_SEND, "Already searched here – /rtp again");
                  } else {
                     this.plan(var4, var9);
                     this.status = "Hunting in " + this.region + " around " + var4 + " " + var9;
                     Notifications.info("AutoHunter", this.status);
                  }
               } else {
                  this.status = "Landed – loading";
               }
               break;
            case WALK:
               if (mc.field_1755 == null) {
                  if (this.leg >= this.legs.size()) {
                     this.finishArea("done");
                  } else {
                     int[] var1 = this.legs.get(this.leg);
                     if (!this.legStarted) {
                        Goto.start(
                           var1[0],
                           this.surface.get() ? Integer.MIN_VALUE : this.tunnelY.get(),
                           var1[1],
                           "AutoHunter " + (this.leg + 1) + "/" + this.legs.size()
                        );
                        this.legStarted = true;
                        this.legTicks = 0;
                     } else {
                        this.legTicks++;
                        if (Goto.running()) {
                           if (this.legTicks > this.legTimeout.get() * 20) {
                              Goto.stop();
                           }

                           this.status = "Leg " + (this.leg + 1) + "/" + this.legs.size() + " · " + this.finds + " finds · " + this.region;
                        } else if (this.legTicks >= 5) {
                           double var2 = Math.hypot(var1[0] + 0.5 - mc.field_1724.method_23317(), var1[1] + 0.5 - mc.field_1724.method_23321());
                           if (var2 > 8.0 && ++this.failures >= 6) {
                              this.finishArea("blocked");
                           } else {
                              this.leg++;
                              this.legStarted = false;
                           }
                        }
                     }
                  }
               }
            case PAUSED:
            default:
               break;
            case COOLDOWN:
               if (System.currentTimeMillis() >= this.cooldownUntil) {
                  this.state = this.afterCooldown;
                  this.ticks = 0;
               }
         }
      }
   }

   @Override
   public void onWorldChange() {
      if (this.isEnabled() && this.state == AutoHunter.State.WALK) {
         this.legs.clear();
         this.cooldown(this.rtpRetry.get(), AutoHunter.State.RTP_SEND, "World changed – new /rtp");
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.render.get() && mc.field_1724 != null) {
         if (this.state == AutoHunter.State.WALK && this.leg < this.legs.size()) {
            int[] var2 = this.legs.get(this.leg);
            var1.line(
               mc.field_1724.method_23317(),
               mc.field_1724.method_23318() + 0.1,
               mc.field_1724.method_23321(),
               var2[0] + 0.5,
               mc.field_1724.method_23318() + 0.1,
               var2[1] + 0.5,
               -16719648,
               true
            );
         }

         for (String[] var3 : this.found) {
            try {
               if (var3[1].equals(this.region)) {
                  int var4 = Integer.parseInt(var3[3]);
                  int var5 = Integer.parseInt(var3[4]);
                  int var6 = Integer.parseInt(var3[5]);
                  if (Math.abs(var4 - mc.field_1724.method_23317()) < 512.0 && Math.abs(var6 - mc.field_1724.method_23321()) < 512.0) {
                     var1.boxOutline(new class_238(var4 - 8, var5 - 2, var6 - 8, var4 + 8, var5 + 4, var6 + 8), -49088, true);
                  }
               }
            } catch (Exception var7) {
            }
         }
      }
   }

   @Override
   public String getInfo() {
      return this.state == AutoHunter.State.WALK ? this.leg + 1 + "/" + this.legs.size() : this.state.name().toLowerCase(Locale.ROOT);
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("Status: " + this.status);
      var1.add("Region: " + this.region + " · finds this session: " + this.finds + " · saved finds: " + this.found.size());
      LinkedHashMap var2 = new LinkedHashMap();

      for (String[] var4 : this.landings) {
         try {
            int var5 = Integer.parseInt(var4[2]);
            int var6 = Integer.parseInt(var4[3]);
            int[] var7 = var2.computeIfAbsent(var4[1], var2x -> new int[]{var5, var5, var6, var6, 0});
            var7[0] = Math.min(var7[0], var5);
            var7[1] = Math.max(var7[1], var5);
            var7[2] = Math.min(var7[2], var6);
            var7[3] = Math.max(var7[3], var6);
            var7[4]++;
         } catch (Exception var8) {
         }
      }

      for (Entry var10 : var2.entrySet()) {
         int[] var11 = (int[])var10.getValue();
         var1.add((String)var10.getKey() + ": " + var11[4] + " landings, x " + var11[0] + ".." + var11[1] + ", z " + var11[2] + ".." + var11[3]);
      }

      return var1;
   }

   private static enum State {
      RTP_SEND,
      RTP_GUI,
      RTP_WAIT,
      LANDED,
      WALK,
      PAUSED,
      COOLDOWN;
   }
}
