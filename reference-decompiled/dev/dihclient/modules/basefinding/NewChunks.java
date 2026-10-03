package dev.dihclient.modules.basefinding;

import dev.dihclient.module.Category;
import dev.dihclient.scan.ChunkMarkModule;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.RegistryUtil;
import dev.dihclient.waypoint.WaypointManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_2806;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3610;

public class NewChunks extends ChunkMarkModule {
   public final BoolSetting showNew = this.bool("Show New", "Marks freshly generated chunks.", true);
   public final BoolSetting showOld = this.bool("Show Old", "Marks chunks that were generated before (possible trails).", true);
   public final BoolSetting showPlayer = this.bool(
      "Show Player Chunks", "Marks old chunks that contain blocks only players make (see Player Blocks) in their own colour.", true
   );
   public final ColorSetting newColor = this.color("New Color", "Colour of new chunks.", -49088);
   public final ColorSetting oldColor = this.color("Old Color", "Colour of old chunks.", -12517568);
   public final ColorSetting playerColor = this.color("Player Color", "Colour of chunks with player-made blocks.", -20448).visibleWhen(this.showPlayer::get);
   public final IdListSetting playerBlocks = this.ids(
         "Player Blocks",
         "Blocks world generation never places – one of them in a chunk means a player was there. (Chests, torches, planks, beds … are left out on purpose: villages, mineshafts and ruins have them.)",
         IdListSetting.Kind.BLOCK,
         new String[]{
            "minecraft:shulker_box",
            "minecraft:white_shulker_box",
            "minecraft:orange_shulker_box",
            "minecraft:magenta_shulker_box",
            "minecraft:light_blue_shulker_box",
            "minecraft:yellow_shulker_box",
            "minecraft:lime_shulker_box",
            "minecraft:pink_shulker_box",
            "minecraft:gray_shulker_box",
            "minecraft:light_gray_shulker_box",
            "minecraft:cyan_shulker_box",
            "minecraft:purple_shulker_box",
            "minecraft:blue_shulker_box",
            "minecraft:brown_shulker_box",
            "minecraft:green_shulker_box",
            "minecraft:red_shulker_box",
            "minecraft:black_shulker_box",
            "minecraft:ender_chest",
            "minecraft:beacon",
            "minecraft:enchanting_table",
            "minecraft:anvil",
            "minecraft:chipped_anvil",
            "minecraft:damaged_anvil",
            "minecraft:comparator",
            "minecraft:observer",
            "minecraft:note_block",
            "minecraft:jukebox",
            "minecraft:respawn_anchor",
            "minecraft:netherite_block",
            "minecraft:diamond_block",
            "minecraft:emerald_block",
            "minecraft:nether_portal",
            "minecraft:conduit",
            "minecraft:crafter",
            "minecraft:honey_block",
            "minecraft:slime_block",
            "minecraft:daylight_detector",
            "minecraft:target",
            "minecraft:lodestone"
         }
      )
      .visibleWhen(this.showPlayer::get)
      .onChange(this::playerBlocksChanged);
   public final IntSetting window = this.integer("Window", "Seconds after a chunk arrives in which fluid flow still counts as new generation.", 15, 3, 60);
   public final IntSetting ignoreNear = this.integer(
      "Ignore Near", "Fluid updates / placed blocks this close to you are ignored (your own buckets and blocks).", 8, 0, 32
   );
   public final IntSetting remember = this.integer(
      "Remember",
      "How many judged chunks are kept in the background so they keep their colour when you come back (only chunks in render distance are shown).",
      20000,
      1000,
      100000
   );
   private static final Predicate<class_2680> FLOWING = var0 -> {
      class_3610 var1 = var0.method_26227();
      return !var1.method_15769() && !var1.method_15771();
   };
   public final BoolSetting strict = this.bool(
      "Strict", "Only counts fluid that flows out of a source/next to other fluid – filters out buckets, farms and redstone far away.", true
   );
   private static final byte NEW = 1;
   private static final byte OLD = 2;
   private static final byte PLAYER = 3;
   private final Map<Long, Long> loadedAt = new HashMap<>();
   private LinkedHashMap<Long, Byte> states = new LinkedHashMap<>();
   private final Map<Long, String> playerWhy = new HashMap<>();
   private String memoryKey = "";
   private final Map<Long, Integer> marks = new HashMap<>();
   private final Map<class_2680, Boolean> playerCache = new IdentityHashMap<>();
   private Set<String> playerIds = new HashSet<>();
   private int ticks;

   public NewChunks() {
      super(
         "New Chunks",
         Category.BASEFINDING,
         "Red = freshly generated chunk, green = chunk that existed before, orange = chunk with blocks only players make. Only chunks in your render distance are shown."
      );
      this.style.set(ChunkMarkModule.Style.PLATE);
      this.heightMode.set(ChunkMarkModule.Height.PLAYER);
      this.opacity.set(55);
      this.pulse.set(false);
      this.labels.set(false);
      this.notify.set(false);
      this.newColor.onChange(this::rebuild);
      this.oldColor.onChange(this::rebuild);
      this.playerColor.onChange(this::rebuild);
      this.showNew.onChange(this::rebuild);
      this.showOld.onChange(this::rebuild);
      this.showPlayer.onChange(this::rebuild);
      this.action("Clear", "Forgets all chunks.", this::reset);
      this.playerBlocksChanged();
   }

   private void playerBlocksChanged() {
      this.playerIds = new HashSet<>(this.playerBlocks.get());
      synchronized (this.playerCache) {
         this.playerCache.clear();
      }
   }

   private boolean playerBlock(class_2680 var1) {
      if (var1.method_26215()) {
         return false;
      } else {
         synchronized (this.playerCache) {
            Boolean var3 = this.playerCache.get(var1);
            if (var3 == null) {
               var3 = this.playerIds.contains(RegistryUtil.blockId(var1));
               this.playerCache.put(var1, var3);
            }

            return var3;
         }
      }
   }

   private void reset() {
      this.loadedAt.clear();
      this.states = new LinkedHashMap<>();
      this.playerWhy.clear();
      this.memoryKey = "";
      this.marks.clear();
   }

   private void syncDimension() {
      String var1 = WaypointManager.worldKey() + "|" + WaypointManager.dimKey();
      if (!var1.equals(this.memoryKey)) {
         this.memoryKey = var1;
         this.states = new LinkedHashMap<>();
         this.playerWhy.clear();
         this.loadedAt.clear();
         this.marks.clear();
      }
   }

   private void rebuild() {
      this.marks.clear();

      for (Long var2 : this.loadedAt.keySet()) {
         this.refreshMark(var2);
      }
   }

   private void refreshMark(long var1) {
      Byte var3 = this.states.get(var1);
      if (var3 == null || !this.loadedAt.containsKey(var1)) {
         this.marks.remove(var1);
      } else if (var3 == 3 && this.showPlayer.get()) {
         this.marks.put(var1, this.playerColor.get());
      } else if ((var3 == 2 || var3 == 3 && !this.showPlayer.get()) && this.showOld.get()) {
         this.marks.put(var1, this.oldColor.get());
      } else if (var3 == 1 && this.showNew.get()) {
         this.marks.put(var1, this.newColor.get());
      } else {
         this.marks.remove(var1);
      }
   }

   private void set(long var1, byte var3) {
      Byte var4 = this.states.remove(var1);
      if (var4 != null && var4 == 3 && var3 != 3) {
         var3 = 3;
      }

      this.states.put(var1, var3);
      if (this.states.size() > this.remember.get()) {
         Iterator var5 = this.states.entrySet().iterator();

         while (this.states.size() > this.remember.get() && var5.hasNext()) {
            Entry var6 = (Entry)var5.next();
            if (!this.loadedAt.containsKey(var6.getKey())) {
               this.playerWhy.remove(var6.getKey());
               var5.remove();
            }
         }
      }

      this.refreshMark(var1);
   }

   @Override
   public Map<Long, Integer> chunkMarks() {
      return this.marks;
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   public void onWorldChange() {
      this.loadedAt.clear();
      this.marks.clear();
      this.memoryKey = "";
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null) {
         this.syncDimension();
         if (++this.ticks % 40 == 0) {
            Iterator var1 = this.loadedAt.keySet().iterator();

            while (var1.hasNext()) {
               long var2 = (Long)var1.next();
               if (!mc.field_1687.method_2935().method_12123(class_1923.method_8325(var2), class_1923.method_8332(var2))) {
                  var1.remove();
                  this.marks.remove(var2);
               }
            }
         }
      }
   }

   @Override
   public void onChunkLoaded(int var1, int var2) {
      if (mc.field_1687 != null) {
         this.syncDimension();
         long var3 = class_1923.method_8331(var1, var2);
         this.loadedAt.put(var3, System.currentTimeMillis());
         class_2818 var5 = mc.field_1687.method_2935().method_2857(var1, var2, class_2806.field_12803, false);
         if (var5 != null) {
            Byte var6 = this.states.get(var3);
            String var7 = this.showPlayer.get() ? this.findPlayerBlock(var5) : null;
            if (var7 != null) {
               this.playerWhy.put(var3, var7);
               this.set(var3, (byte)3);
               return;
            }

            if (var6 == null) {
               for (class_2826 var11 : var5.method_12006()) {
                  if (var11 != null && !var11.method_38292() && var11.method_19523(FLOWING) && has(var11, FLOWING)) {
                     this.set(var3, (byte)2);
                     return;
                  }
               }
            }
         }

         this.refreshMark(var3);
      }
   }

   private String findPlayerBlock(class_2818 var1) {
      Predicate var2 = this::playerBlock;

      for (class_2826 var6 : var1.method_12006()) {
         if (var6 != null && !var6.method_38292() && var6.method_19523(var2)) {
            for (int var7 = 0; var7 < 16; var7++) {
               for (int var8 = 0; var8 < 16; var8++) {
                  for (int var9 = 0; var9 < 16; var9++) {
                     class_2680 var10 = var6.method_12254(var9, var7, var8);
                     if (var2.test(var10)) {
                        return var10.method_26204().method_9518().getString();
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private static boolean has(class_2826 var0, Predicate<class_2680> var1) {
      class_2680 var2 = null;

      for (int var3 = 0; var3 < 16; var3++) {
         for (int var4 = 0; var4 < 16; var4++) {
            for (int var5 = 0; var5 < 16; var5++) {
               class_2680 var6 = var0.method_12254(var4, var3, var5);
               if (var6 != var2) {
                  var2 = var6;
                  if (var1.test(var6)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private static boolean nextToFluid(class_2338 var0) {
      for (class_2350 var4 : class_2350.values()) {
         if (!mc.field_1687.method_8320(var0.method_10093(var4)).method_26227().method_15769()) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void onChunkUnloaded(int var1, int var2) {
      long var3 = class_1923.method_8331(var1, var2);
      this.loadedAt.remove(var3);
      this.marks.remove(var3);
   }

   private boolean nearMe(class_2338 var1) {
      int var2 = this.ignoreNear.get();
      double var3 = var1.method_10263() + 0.5 - mc.field_1724.method_23317();
      double var5 = var1.method_10264() + 0.5 - mc.field_1724.method_23318();
      double var7 = var1.method_10260() + 0.5 - mc.field_1724.method_23321();
      return var3 * var3 + var5 * var5 + var7 * var7 < var2 * var2;
   }

   @Override
   public void onBlockUpdate(class_2338 var1, class_2680 var2) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         long var3 = class_1923.method_8331(var1.method_10263() >> 4, var1.method_10260() >> 4);
         if (this.showPlayer.get() && this.playerBlock(var2)) {
            if (!this.nearMe(var1)) {
               this.syncDimension();
               this.playerWhy.put(var3, var2.method_26204().method_9518().getString());
               this.set(var3, (byte)3);
            }
         } else if (FLOWING.test(var2)) {
            this.syncDimension();
            if (!this.states.containsKey(var3)) {
               Long var5 = this.loadedAt.get(var3);
               if (var5 != null
                  && System.currentTimeMillis() - var5 <= this.window.get().intValue() * 1000L
                  && !this.nearMe(var1)
                  && (!this.strict.get() || nextToFluid(var1))) {
                  this.set(var3, (byte)1);
               }
            }
         }
      }
   }

   @Override
   protected String chunkLabel(long var1) {
      Byte var3 = this.states.get(var1);
      if (var3 == null) {
         return null;
      } else if (var3 == 3) {
         return "player";
      } else {
         return var3 == 1 ? "new" : "old";
      }
   }

   @Override
   protected String chunkSubLabel(long var1) {
      return this.playerWhy.get(var1);
   }

   private int count(byte var1) {
      int var2 = 0;

      for (Long var4 : this.loadedAt.keySet()) {
         Byte var5 = this.states.get(var4);
         if (var5 != null && var5 == var1) {
            var2++;
         }
      }

      return var2;
   }

   @Override
   public String getInfo() {
      int var1 = this.count((byte)3);
      return this.count((byte)1) + "/" + this.count((byte)2) + (var1 > 0 ? "/" + var1 : "");
   }

   @Override
   public List<String> details() {
      ArrayList var1 = new ArrayList();
      var1.add("In render distance: " + this.count((byte)1) + " new · " + this.count((byte)2) + " old · " + this.count((byte)3) + " with player blocks");
      var1.add("Remembered in the background: " + this.states.size() + " chunks");
      var1.add("Chunks without any fluid or player blocks can't be told apart and stay unmarked.");
      return var1;
   }
}
