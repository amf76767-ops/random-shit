package dev.dihclient.modules.basefinding;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import dev.dihclient.scan.BlockScanner;
import dev.dihclient.scan.ChunkEvents;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.IdListSetting;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_3414;
import net.minecraft.class_3417;

public class BlockNotifier extends Module implements ChunkEvents.Listener {
   public final IdListSetting blocks = this.ids(
         "Blocks", "Blocks that trigger a notification.", IdListSetting.Kind.BLOCK, new String[]{"minecraft:ancient_debris"}
      )
      .legacy("notifier.blocks")
      .onChange(this::rebuild);
   public final IdListSetting entities = this.ids("Entities", "Entity types that trigger a notification.", IdListSetting.Kind.ENTITY, new String[0])
      .legacy("notifier.mobs")
      .onChange(this::rebuild);
   public final BoolSetting coords = this.bool("Show Coords", "Includes coordinates in the notification.", true);
   public final BoolSetting sound = this.bool("Sound", "Plays a sound on detection.", true);
   public final BoolSetting highlight = this.bool("Highlight", "Highlights notified targets.", true);
   private volatile Set<class_2248> blockSet = Set.of();
   private Set<class_1299<?>> entitySet = Set.of();
   private final BlockScanner scanner = new BlockScanner(var1 -> this.blockSet.contains(var1.method_26204()), 3);
   private final Set<class_2338> notifiedBlocks = new HashSet<>();
   private final Set<Integer> notifiedEntities = new HashSet<>();

   public BlockNotifier() {
      super("Block Notifier", Category.BASEFINDING, "Notifies once when selected blocks or entity types are detected and highlights them.");
   }

   private void rebuild() {
      this.blockSet = RegistryUtil.blocks(this.blocks.get());
      this.entitySet = RegistryUtil.entityTypes(this.entities.get());
      this.scanner.clear();
   }

   @Override
   protected void onEnable() {
      this.rebuild();
      this.notifiedBlocks.clear();
      this.notifiedEntities.clear();
   }

   @Override
   public void onWorldChange() {
      this.scanner.clear();
      this.notifiedBlocks.clear();
      this.notifiedEntities.clear();
   }

   @Override
   public void onChunkLoaded(int var1, int var2) {
      this.scanner.prioritize(var1, var2);
   }

   @Override
   public void onTick() {
      this.scanner.tick();

      for (List var2 : this.scanner.results().values()) {
         for (class_2338 var4 : var2) {
            if (this.notifiedBlocks.add(var4)) {
               this.notifyFound(RegistryUtil.pretty(RegistryUtil.blockId(mc.field_1687.method_8320(var4))), var4);
            }
         }
      }

      if (!this.entitySet.isEmpty()) {
         for (class_1297 var6 : mc.field_1687.method_18112()) {
            if (var6 != mc.field_1724 && this.entitySet.contains(var6.method_5864()) && this.notifiedEntities.add(var6.method_5628())) {
               this.notifyFound(var6.method_5477().getString(), var6.method_24515());
            }
         }
      }
   }

   private void notifyFound(String var1, class_2338 var2) {
      String var3 = this.coords.get() ? var1 + " at " + var2.method_10263() + ", " + var2.method_10264() + ", " + var2.method_10260() : var1 + " detected";
      Notifications.warn("Block Notifier", var3);
      if (this.sound.get() && mc.field_1724 != null) {
         mc.field_1724.method_5783((class_3414)class_3417.field_14622.comp_349(), 1.0F, 1.6F);
      }
   }

   @Override
   public void onRender3D(Render3D var1) {
      if (this.highlight.get()) {
         for (List var3 : this.scanner.results().values()) {
            for (class_2338 var5 : var3) {
               var1.box(new class_238(var5), -10166, 45, true);
            }
         }

         if (!this.entitySet.isEmpty()) {
            for (class_1297 var7 : mc.field_1687.method_18112()) {
               if (var7 != mc.field_1724 && this.entitySet.contains(var7.method_5864())) {
                  var1.boxOutline(var7.method_5829(), -10166, true);
               }
            }
         }
      }
   }
}
