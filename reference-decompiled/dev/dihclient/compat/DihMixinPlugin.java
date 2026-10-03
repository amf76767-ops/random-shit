package dev.dihclient.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class DihMixinPlugin implements IMixinConfigPlugin {
   private static final Set<String> CHUNK_RENDER_MIXINS = Set.of(
      "dev.dihclient.mixin.BlockRenderManagerMixin", "dev.dihclient.mixin.BlockMixin", "dev.dihclient.mixin.ChunkOcclusionDataBuilderMixin"
   );
   private boolean vulkan;

   public void onLoad(String var1) {
      this.vulkan = FabricLoader.getInstance().isModLoaded("vulkanmod");
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String var1, String var2) {
      return !this.vulkan || !CHUNK_RENDER_MIXINS.contains(var2);
   }

   public void acceptTargets(Set<String> var1, Set<String> var2) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String var1, ClassNode var2, String var3, IMixinInfo var4) {
   }

   public void postApply(String var1, ClassNode var2, String var3, IMixinInfo var4) {
   }
}
