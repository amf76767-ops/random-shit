package dev.dihclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import dev.dihclient.DIHClient;
import dev.dihclient.mixin.accessor.RenderLayerInvoker;
import dev.dihclient.mixin.accessor.RenderPipelinesAccessor;
import dev.dihclient.util.Compat;
import net.minecraft.class_12247;
import net.minecraft.class_12249;
import net.minecraft.class_1921;
import net.minecraft.class_2960;

public final class DihRenderLayers {
   private static class_1921 linesNoDepth;
   private static class_1921 filledNoDepth;
   private static class_1921 filledDepth;
   private static boolean failed;

   private DihRenderLayers() {
   }

   private static void init() {
      if (linesNoDepth == null && !failed) {
         try {
            RenderPipeline var0 = RenderPipeline.builder(new Snippet[]{RenderPipelinesAccessor.dih$linesSnippet()})
               .withLocation(class_2960.method_60655("dihclient", "pipeline/lines_no_depth"))
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .build();
            RenderPipeline var1 = RenderPipeline.builder(new Snippet[]{RenderPipelinesAccessor.dih$positionColorSnippet()})
               .withLocation(class_2960.method_60655("dihclient", "pipeline/filled_no_depth"))
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withCull(false)
               .build();
            RenderPipeline var2 = RenderPipeline.builder(new Snippet[]{RenderPipelinesAccessor.dih$positionColorSnippet()})
               .withLocation(class_2960.method_60655("dihclient", "pipeline/filled_depth"))
               .withDepthWrite(false)
               .withCull(false)
               .build();
            linesNoDepth = RenderLayerInvoker.dih$of("dihclient_lines_no_depth", class_12247.method_75927(var0).method_75938());
            filledNoDepth = RenderLayerInvoker.dih$of("dihclient_filled_no_depth", class_12247.method_75927(var1).method_75937().method_75938());
            filledDepth = RenderLayerInvoker.dih$of("dihclient_filled_depth", class_12247.method_75927(var2).method_75937().method_75938());
         } catch (Throwable var3) {
            failed = true;
            DIHClient.LOG.error("[DIHClient] could not create ESP render layers, falling back to vanilla lines", var3);
         }
      }
   }

   public static boolean markFailed() {
      if (failed) {
         return false;
      } else {
         failed = true;
         filledDepth = null;
         filledNoDepth = null;
         linesNoDepth = null;
         return true;
      }
   }

   public static boolean failed() {
      return failed;
   }

   public static class_1921 lines(boolean var0) {
      if (Compat.safeRender()) {
         return class_12249.method_76015();
      } else {
         init();
         return var0 && linesNoDepth != null ? linesNoDepth : class_12249.method_76015();
      }
   }

   public static class_1921 filled(boolean var0) {
      if (Compat.safeRender()) {
         return class_12249.method_76023();
      } else {
         init();
         if (var0 && filledNoDepth != null) {
            return filledNoDepth;
         } else {
            return filledDepth != null ? filledDepth : class_12249.method_76023();
         }
      }
   }
}
