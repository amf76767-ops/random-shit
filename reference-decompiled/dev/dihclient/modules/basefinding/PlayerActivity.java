package dev.dihclient.modules.basefinding;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_1308;
import net.minecraft.class_1657;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2769;
import net.minecraft.class_3414;
import net.minecraft.class_3417;
import net.minecraft.class_3481;
import net.minecraft.class_6862;
import net.minecraft.class_7923;

public class PlayerActivity extends Module {
   public final BoolSetting placed = this.bool("Placed Blocks", "Blocks only players place: chests, beds, torches, signs, planks, rails, redstone …", true);
   public final BoolSetting mined = this.bool("Mined Blocks", "Stone, ores, logs and netherrack disappearing (explosions and endermen are ignored).", true);
   public final BoolSetting used = this.bool("Interactions", "Doors, trapdoors, gates, levers, buttons, repeaters, cake, jukebox, lit candles …", true);
   public final BoolSetting containers = this.bool("Containers", "Chests, ender chests, shulkers and barrels being opened.", true);
   public final BoolSetting sounds = this.bool("Player Sounds", "Sounds only players make: eating, armour, anvil, enchanting, pearls, rockets, buckets …", true);
   public final BoolSetting newPlayers = this.bool("New Players", "A player entity enters render distance (friends are skipped).", true);
   public final BoolSetting unseenOnly = this.bool(
      "Unseen Only", "Only alarm when no visible player is near the source – catches hidden/vanished players, ignores people you can already see.", false
   );
   public final IntSetting minDistance = this.integer("Min Distance", "Ignore everything closer than this (your own actions).", 7, 0, 32);
   public final IntSetting cooldown = this.integer("Cooldown", "Seconds before the same area (chunk) can alarm again.", 10, 0, 120);
   public final BoolSetting coords = this.bool("Show Coords", "Coordinates in the notification.", true);
   public final BoolSetting bigAlert = this.bool("Big Alert", "Large warning text in the middle of the screen.", true);
   public final BoolSetting sound = this.bool("Alarm Sound", "Plays an alarm sound.", true);
   public final IntSetting markerTime = this.integer("Marker Time", "Seconds a marker stays at the source (0 = off).", 20, 0, 300);
   public final BoolSetting tracer = this.bool("Tracer", "Line from your crosshair to fresh markers.", true).visibleWhen(() -> this.markerTime.get() > 0);
   public final ColorSetting unseenColor = this.color("Unseen Color", "Marker colour when nobody visible was there.", -61424);
   private final Deque<PlayerActivity.Event> events = new ArrayDeque<>();
   private final Map<Long, Long> areaCooldown = new HashMap<>();
   private final Set<Integer> knownPlayers = new HashSet<>();
   private final Map<class_2338, Integer> chestViewers = new HashMap<>();
   private class_243 lastExplosion;
   private long lastExplosionTick = -1000L;
   private long ticks;
   private static final List<class_6862<class_2248>> PLAYER_TAGS = List.of(
      class_3481.field_16443,
      class_3481.field_41282,
      class_3481.field_21490,
      class_3481.field_15486,
      class_3481.field_15463,
      class_3481.field_15481,
      class_3481.field_15479,
      class_3481.field_26983,
      class_3481.field_15501,
      class_3481.field_15493,
      class_3481.field_15471,
      class_3481.field_15459,
      class_3481.field_15469,
      class_3481.field_15504,
      class_3481.field_16584,
      class_3481.field_15495,
      class_3481.field_15487,
      class_3481.field_25147,
      class_3481.field_23799,
      class_3481.field_61206
   );
   private static final Set<class_2248> PLAYER_BLOCKS = Set.of(
      class_2246.field_9980,
      class_2246.field_10181,
      class_2246.field_16333,
      class_2246.field_16334,
      class_2246.field_10034,
      class_2246.field_10380,
      class_2246.field_16328,
      class_2246.field_10443,
      class_2246.field_10312,
      class_2246.field_10200,
      class_2246.field_10228,
      class_2246.field_10336,
      class_2246.field_10099,
      class_2246.field_22092,
      class_2246.field_22093,
      class_2246.field_10523,
      class_2246.field_10301,
      class_2246.field_16541,
      class_2246.field_22110,
      class_2246.field_10485,
      class_2246.field_10333,
      class_2246.field_16329,
      class_2246.field_16336,
      class_2246.field_16331,
      class_2246.field_10083,
      class_2246.field_16335,
      class_2246.field_16337,
      class_2246.field_16330,
      class_2246.field_10327,
      class_2246.field_16492,
      class_2246.field_10091,
      class_2246.field_10450,
      class_2246.field_10377,
      class_2246.field_10560,
      class_2246.field_10615,
      class_2246.field_10282,
      class_2246.field_10363,
      class_2246.field_10375,
      class_2246.field_10033,
      class_2246.field_10285,
      class_2246.field_23152,
      class_2246.field_10316,
      class_2246.field_10223,
      class_2246.field_10179,
      class_2246.field_10183,
      class_2246.field_10343
   );

   public PlayerActivity() {
      super(
         "Player Bypass",
         Category.BASEFINDING,
         "Alarms on things only players can do nearby: placed/mined blocks, opened chests and doors, player sounds. Warns extra when nobody visible is there."
      );
   }

   private static boolean isPlayerBlock(class_2680 var0) {
      if (PLAYER_BLOCKS.contains(var0.method_26204())) {
         return true;
      } else {
         for (class_6862 var2 : PLAYER_TAGS) {
            if (var0.method_26164(var2)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean isMineable(class_2680 var0) {
      return !var0.method_26164(class_3481.field_25806) && !var0.method_26164(class_3481.field_25807) && !var0.method_26164(class_3481.field_15475)
         ? class_7923.field_41175.method_10221(var0.method_26204()).method_12832().endsWith("_ore")
         : true;
   }

   private static boolean isEmpty(class_2680 var0) {
      return var0.method_26215() || var0.method_27852(class_2246.field_10382) || var0.method_27852(class_2246.field_10164);
   }

   private static boolean fireNear(class_2338 var0) {
      for (int var1 = -1; var1 <= 1; var1++) {
         for (int var2 = -1; var2 <= 1; var2++) {
            for (int var3 = -1; var3 <= 1; var3++) {
               if (mc.field_1687.method_8320(var0.method_10069(var1, var2, var3)).method_27852(class_2246.field_10036)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private boolean tooClose(class_243 var1) {
      return mc.field_1724 == null || mc.field_1724.method_73189().method_1025(var1) < this.minDistance.get() * this.minDistance.get();
   }

   private boolean mobNear(class_243 var1, double var2, class_1299<?>... var4) {
      class_238 var5 = new class_238(var1, var1).method_1014(var2);

      for (class_1297 var7 : mc.field_1687.method_8335(null, var5)) {
         if (var4.length == 0 && !(var7 instanceof class_1657) && var7.method_5805() && var7 instanceof class_1308) {
            return true;
         }

         for (class_1299 var11 : var4) {
            if (var7.method_5864() == var11) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean explosionNear(class_243 var1) {
      return this.lastExplosion != null && this.ticks - this.lastExplosionTick < 60L && this.lastExplosion.method_1025(var1) < 144.0;
   }

   private boolean unseen(class_243 var1) {
      for (class_1657 var3 : mc.field_1687.method_18456()) {
         if (var3 != mc.field_1724 && var3.method_73189().method_1025(var1) < 36.0) {
            return false;
         }
      }

      return true;
   }

   private static <T extends Comparable<T>> boolean changed(class_2680 var0, class_2680 var1, class_2769<T> var2) {
      return var0.method_28498(var2) && var1.method_28498(var2) && !var0.method_11654(var2).equals(var1.method_11654(var2));
   }

   private static String pretty(class_2248 var0) {
      return var0.method_9518().getString();
   }

   private String direction(class_243 var1) {
      double var2 = var1.field_1352 - mc.field_1724.method_23317();
      double var4 = var1.field_1350 - mc.field_1724.method_23321();
      String[] var6 = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
      double var7 = Math.toDegrees(Math.atan2(-var2, var4));
      int var9 = Math.floorMod(Math.round(var7 / 45.0), 8);
      return (int)Math.sqrt(var2 * var2 + var4 * var4) + "m " + var6[var9];
   }

   private void report(PlayerActivity.Kind var1, String var2, class_243 var3) {
      if (mc.field_1724 != null && mc.field_1687 != null && !this.tooClose(var3)) {
         boolean var4 = var1 != PlayerActivity.Kind.PLAYER && this.unseen(var3);
         if (!this.unseenOnly.get() || var1 == PlayerActivity.Kind.PLAYER || var4) {
            long var5 = class_1923.method_8331((int)Math.floor(var3.field_1352) >> 4, (int)Math.floor(var3.field_1350) >> 4) ^ (long)var1.ordinal() << 60;
            Long var7 = this.areaCooldown.get(var5);
            boolean var8 = var7 != null && this.ticks - var7 < this.cooldown.get().intValue() * 20L;
            this.events.addFirst(new PlayerActivity.Event(var1, var2, var3, var4, this.ticks));

            while (this.events.size() > 64) {
               this.events.removeLast();
            }

            if (!var8) {
               this.areaCooldown.put(var5, this.ticks);
               String var9 = this.coords.get() ? " @ " + (int)var3.field_1352 + " " + (int)var3.field_1351 + " " + (int)var3.field_1350 : "";
               String var10 = var2 + " (" + this.direction(var3) + ")" + var9;
               Notifications.warn(var4 ? "Unseen player!" : "Player activity", var10);
               if (this.bigAlert.get()) {
                  Notifications.alert((var4 ? "UNSEEN PLAYER: " : "ACTIVITY: ") + var2 + " – " + this.direction(var3), 60);
               }

               if (this.sound.get()) {
                  mc.field_1724.method_5783(var4 ? class_3417.field_17265 : (class_3414)class_3417.field_14622.comp_349(), 1.0F, var4 ? 0.7F : 1.3F);
               }
            }
         }
      }
   }

   public void onBlockChange(class_2338 var1, class_2680 var2) {
      if (mc.field_1687 != null) {
         class_2680 var3 = mc.field_1687.method_8320(var1);
         if (var3 != var2) {
            class_243 var4 = class_243.method_24953(var1);
            if (!this.tooClose(var4)) {
               if (var3.method_26204() == var2.method_26204()) {
                  if (var2.method_26204() == class_2246.field_16328) {
                     if (this.containers.get() && changed(var3, var2, class_2741.field_12537) && (Boolean)var2.method_11654(class_2741.field_12537)) {
                        this.report(PlayerActivity.Kind.CONTAINER, "Barrel opened", var4);
                     }

                     return;
                  }

                  if (this.used.get()) {
                     class_2248 var5 = var2.method_26204();
                     String var6 = null;
                     if (changed(var3, var2, class_2741.field_12537)) {
                        if (var5 == class_2246.field_16328) {
                           if (this.containers.get() && (Boolean)var2.method_11654(class_2741.field_12537)) {
                              this.report(PlayerActivity.Kind.CONTAINER, "Barrel opened", var4);
                           }

                           return;
                        }

                        if (var2.method_26164(class_3481.field_15494) && this.mobNear(var4, 3.0, class_1299.field_6077, class_1299.field_17713)) {
                           return;
                        }

                        if (changed(var3, var2, class_2741.field_12484)) {
                           return;
                        }

                        var6 = pretty(var5) + (var2.method_11654(class_2741.field_12537) ? " opened" : " closed");
                     } else if ((var5 == class_2246.field_10363 || var2.method_26164(class_3481.field_15493))
                        && changed(var3, var2, class_2741.field_12484)
                        && (Boolean)var2.method_11654(class_2741.field_12484)) {
                        var6 = pretty(var5) + " pressed";
                     } else if (changed(var3, var2, class_2741.field_12494)
                        || changed(var3, var2, class_2741.field_12534)
                        || changed(var3, var2, class_2741.field_12501)) {
                        var6 = pretty(var5) + " adjusted";
                     } else if (changed(var3, var2, class_2741.field_12505)) {
                        var6 = "Cake eaten";
                     } else if (changed(var3, var2, class_2741.field_12544)
                        || changed(var3, var2, class_2741.field_17393)
                        || changed(var3, var2, class_2741.field_23187)) {
                        var6 = pretty(var5) + " used";
                     } else if ((var2.method_26164(class_3481.field_26983) || var2.method_26164(class_3481.field_23799))
                        && changed(var3, var2, class_2741.field_12548)
                        && (Boolean)var2.method_11654(class_2741.field_12548)) {
                        var6 = pretty(var5) + " lit";
                     }

                     if (var6 != null) {
                        this.report(PlayerActivity.Kind.USED, var6, var4);
                     }
                  }
               } else if (this.placed.get() && isPlayerBlock(var2) && !isPlayerBlock(var3) && !this.mobNear(var4, 3.0, class_1299.field_6091)) {
                  this.report(PlayerActivity.Kind.PLACED, pretty(var2.method_26204()) + " placed", var4);
               } else if (this.mined.get()
                  && isEmpty(var2)
                  && (isMineable(var3) || isPlayerBlock(var3))
                  && !this.explosionNear(var4)
                  && !fireNear(var1)
                  && !this.mobNear(var4, 4.0, class_1299.field_6091, class_1299.field_6119, class_1299.field_6134)) {
                  this.report(PlayerActivity.Kind.MINED, pretty(var3.method_26204()) + " broken", var4);
               }
            }
         }
      }
   }

   public void onBlockEvent(class_2338 var1, class_2248 var2, int var3, int var4) {
      if (this.containers.get() && var3 == 1 && mc.field_1687 != null) {
         boolean var5 = var2 == class_2246.field_10034
            || var2 == class_2246.field_10380
            || var2 == class_2246.field_10443
            || var2.method_9564().method_26164(class_3481.field_21490)
            || var2.method_9564().method_26164(class_3481.field_61206);
         if (var5) {
            int var6 = this.chestViewers.getOrDefault(var1, 0);
            this.chestViewers.put(var1.method_10062(), var4);
            if (var4 > var6) {
               class_243 var7 = class_243.method_24953(var1);
               if (!this.mobNear(var7, 3.0, class_1299.field_61221)) {
                  this.report(PlayerActivity.Kind.CONTAINER, pretty(var2) + " opened" + (var4 > 1 ? " (" + var4 + " viewers)" : ""), var7);
               }
            }
         }
      }
   }

   public void onSound(class_3414 var1, double var2, double var4, double var6) {
      if (this.sounds.get() && mc.field_1687 != null) {
         String var8 = var1.comp_3319().method_12832();
         String var9 = playerSound(var8);
         if (var9 != null) {
            class_243 var10 = new class_243(var2, var4, var6);
            if (var8.startsWith("entity.player.") || !this.mobNear(var10, 2.5)) {
               this.report(PlayerActivity.Kind.SOUND, var9, var10);
            }
         }
      }
   }

   private static String playerSound(String var0) {
      if (var0.startsWith("entity.player.attack")) {
         return "Player attacking";
      } else if (var0.equals("entity.player.burp")) {
         return "Player burp";
      } else if (var0.equals("entity.player.levelup")) {
         return "Player level up";
      } else if (var0.startsWith("item.armor.equip")) {
         return "Armour equipped";
      } else if (var0.equals("block.anvil.use")) {
         return "Anvil used";
      } else if (var0.equals("block.enchantment_table.use")) {
         return "Enchanting";
      } else if (var0.equals("block.smithing_table.use")) {
         return "Smithing table used";
      } else if (var0.equals("block.respawn_anchor.charge") || var0.equals("block.respawn_anchor.set_spawn")) {
         return "Respawn anchor used";
      } else if (var0.equals("entity.ender_pearl.throw")) {
         return "Ender pearl thrown";
      } else if (var0.equals("item.firework_rocket.launch")) {
         return "Firework / elytra boost";
      } else if (var0.equals("item.flintandsteel.use")) {
         return "Flint and steel used";
      } else if (var0.startsWith("item.bucket.")) {
         return "Bucket used";
      } else if (var0.equals("entity.fishing_bobber.throw")) {
         return "Fishing";
      } else if (var0.equals("entity.experience_bottle.throw")) {
         return "XP bottle thrown";
      } else if (var0.equals("entity.generic.eat") || var0.equals("entity.generic.drink")) {
         return "Someone eating/drinking";
      } else if (var0.equals("item.shield.block")) {
         return "Shield block";
      } else {
         return var0.equals("block.beacon.activate") ? "Beacon activated" : null;
      }
   }

   public void onExplosion(class_243 var1) {
      this.lastExplosion = var1;
      this.lastExplosionTick = this.ticks;
   }

   @Override
   protected void onEnable() {
      this.reset();
      if (mc.field_1687 != null) {
         mc.field_1687.method_18456().forEach(var1 -> this.knownPlayers.add(var1.method_5628()));
      }
   }

   @Override
   public void onWorldChange() {
      this.reset();
   }

   private void reset() {
      this.events.clear();
      this.areaCooldown.clear();
      this.knownPlayers.clear();
      this.chestViewers.clear();
      this.lastExplosion = null;
   }

   @Override
   public void onTick() {
      this.ticks++;
      if (this.newPlayers.get()) {
         HashSet var1 = new HashSet();

         for (class_1657 var3 : mc.field_1687.method_18456()) {
            if (var3 != mc.field_1724) {
               var1.add(var3.method_5628());
               if (this.knownPlayers.add(var3.method_5628()) && !DIHClient.social().isFriend(var3)) {
                  this.report(PlayerActivity.Kind.PLAYER, var3.method_7334().name() + " in range", var3.method_73189());
               }
            }
         }

         this.knownPlayers.retainAll(var1);
      }

      if (this.ticks % 200L == 0L) {
         this.areaCooldown.values().removeIf(var1x -> this.ticks - var1x > 12000L);
         if (this.chestViewers.size() > 512) {
            this.chestViewers.clear();
         }
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      int var2 = this.markerTime.get() * 20;
      if (var2 > 0) {
         for (PlayerActivity.Event var4 : this.events) {
            long var5 = this.ticks - var4.tick();
            if (var5 <= var2) {
               int var7 = var4.unseen() ? this.unseenColor.get() | 0xFF000000 : var4.kind().color;
               class_238 var8 = new class_238(var4.pos(), var4.pos()).method_1014(0.5);
               int var9 = (int)(80.0 * (1.0 - (double)var5 / var2)) + 10;
               var1.box(var8, var7, var9, true);
               if (this.tracer.get() && var5 < 100L) {
                  var1.tracer(var4.pos(), var7);
               }
            }
         }
      }
   }

   @Override
   public String getInfo() {
      int var1 = 0;

      for (PlayerActivity.Event var3 : this.events) {
         if (this.ticks - var3.tick() < 1200L) {
            var1++;
         }
      }

      return Integer.toString(var1);
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      int var2 = 0;

      for (PlayerActivity.Event var4 : this.events) {
         if (var2++ >= 6) {
            break;
         }

         long var5 = (this.ticks - var4.tick()) / 20L;
         var1.add(
            (var4.unseen() ? "[UNSEEN] " : "")
               + var4.what()
               + " – "
               + (int)var4.pos().field_1352
               + " "
               + (int)var4.pos().field_1351
               + " "
               + (int)var4.pos().field_1350
               + " ("
               + var5
               + "s ago)"
         );
      }

      if (var1.isEmpty()) {
         var1.add("Nothing detected yet.");
      }

      return var1;
   }

   private record Event(PlayerActivity.Kind kind, String what, class_243 pos, boolean unseen, long tick) {
   }

   public static enum Kind {
      PLACED("placed", -11884289),
      MINED("mined", -24512),
      USED("used", -5213953),
      CONTAINER("opened", -10166),
      SOUND("sound", -12525360),
      PLAYER("player", -49088);

      final String verb;
      final int color;

      private Kind(String nullxx, int nullxxx) {
         this.verb = nullxx;
         this.color = nullxxx;
      }
   }
}
