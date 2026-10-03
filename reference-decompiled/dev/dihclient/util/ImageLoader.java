package dev.dihclient.util;

import dev.dihclient.DIHClient;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.class_1011;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public final class ImageLoader {
   public static final String[] EXTENSIONS = new String[]{".png", ".gif", ".jpg", ".jpeg", ".bmp", ".webp"};

   private ImageLoader() {
   }

   public static boolean supported(Path var0) {
      String var1 = var0.getFileName().toString().toLowerCase(Locale.ROOT);

      for (String var5 : EXTENSIONS) {
         if (var1.endsWith(var5)) {
            return true;
         }
      }

      return false;
   }

   public static ImageLoader.Loaded load(Path var0, int var1) throws Exception {
      String var2 = var0.getFileName().toString().toLowerCase(Locale.ROOT);
      if (var2.endsWith(".gif")) {
         ArrayList var3 = new ArrayList();
         int var4 = readGif(var0, var3);
         if (var3.size() > 1) {
            return sheet(var3, var1, var4);
         }

         if (var3.size() == 1) {
            return new ImageLoader.Loaded(toNative(scale((BufferedImage)var3.get(0), var1)), 1, 0);
         }
      }

      BufferedImage var10 = null;

      try {
         var10 = ImageIO.read(var0.toFile());
      } catch (Exception var8) {
         DIHClient.LOG.warn("[DIHClient] ImageIO could not read {}: {}", var0, var8.toString());
      }

      if (var10 == null) {
         ImageLoader.Loaded var5;
         try (InputStream var11 = Files.newInputStream(var0)) {
            var5 = new ImageLoader.Loaded(class_1011.method_4309(var11), 1, 0);
         }

         return var5;
      } else {
         return new ImageLoader.Loaded(toNative(scaleKeepingSheet(var10, var1)), 1, 0);
      }
   }

   private static BufferedImage scaleKeepingSheet(BufferedImage var0, int var1) {
      int var2 = var0.getWidth();
      int var3 = var0.getHeight();
      double var4 = Math.min(1.0, Math.min((double)var1 / var2, 8192.0 / var3));
      if (var3 <= var2 * 1.01) {
         var4 = Math.min(var4, (double)var1 / Math.max(var2, var3));
      }

      return var4 >= 1.0 ? var0 : resize(var0, Math.max(1, (int)Math.round(var2 * var4)), Math.max(1, (int)Math.round(var3 * var4)));
   }

   private static BufferedImage scale(BufferedImage var0, int var1) {
      int var2 = var0.getWidth();
      int var3 = var0.getHeight();
      double var4 = Math.min(1.0, (double)var1 / Math.max(var2, var3));
      return var4 >= 1.0 ? var0 : resize(var0, Math.max(1, (int)Math.round(var2 * var4)), Math.max(1, (int)Math.round(var3 * var4)));
   }

   private static BufferedImage resize(BufferedImage var0, int var1, int var2) {
      BufferedImage var3 = new BufferedImage(var1, var2, 2);
      Graphics2D var4 = var3.createGraphics();
      var4.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      var4.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      var4.drawImage(var0, 0, 0, var1, var2, null);
      var4.dispose();
      return var3;
   }

   private static ImageLoader.Loaded sheet(List<BufferedImage> var0, int var1, int var2) {
      int var3 = ((BufferedImage)var0.get(0)).getWidth();
      int var4 = ((BufferedImage)var0.get(0)).getHeight();
      double var5 = Math.min(1.0, (double)var1 / Math.max(var3, var4));
      var5 = Math.min(var5, 8192.0 / ((double)var4 * var0.size()));
      int var7 = Math.max(1, (int)(var3 * var5));
      int var8 = Math.max(1, (int)(var4 * var5));
      class_1011 var9 = new class_1011(var7, var8 * var0.size(), true);

      for (int var10 = 0; var10 < var0.size(); var10++) {
         BufferedImage var11 = var5 < 1.0 ? resize((BufferedImage)var0.get(var10), var7, var8) : (BufferedImage)var0.get(var10);

         for (int var12 = 0; var12 < var8; var12++) {
            for (int var13 = 0; var13 < var7; var13++) {
               var9.method_61941(var13, var10 * var8 + var12, var11.getRGB(var13, var12));
            }
         }
      }

      return new ImageLoader.Loaded(var9, var0.size(), var2);
   }

   public static class_1011 toNative(BufferedImage var0) {
      int var1 = var0.getWidth();
      int var2 = var0.getHeight();
      class_1011 var3 = new class_1011(var1, var2, true);
      int[] var4 = new int[var1];

      for (int var5 = 0; var5 < var2; var5++) {
         var0.getRGB(0, var5, var1, 1, var4, 0, var1);

         for (int var6 = 0; var6 < var1; var6++) {
            var3.method_61941(var6, var5, var4[var6]);
         }
      }

      return var3;
   }

   private static int readGif(Path var0, List<BufferedImage> var1) throws Exception {
      Iterator var2 = ImageIO.getImageReadersByFormatName("gif");
      if (!var2.hasNext()) {
         return 0;
      } else {
         ImageReader var3 = (ImageReader)var2.next();

         int var27;
         try (ImageInputStream var4 = ImageIO.createImageInputStream(var0.toFile())) {
            var3.setInput(var4, false);
            int var5 = Math.min(var3.getNumImages(true), 256);
            BufferedImage var6 = null;
            long var7 = 0L;

            for (int var9 = 0; var9 < var5; var9++) {
               BufferedImage var10 = var3.read(var9);
               int var11 = 0;
               int var12 = 0;
               int var13 = 10;
               String var14 = "none";
               IIOMetadata var15 = var3.getImageMetadata(var9);
               Node var16 = var15.getAsTree("javax_imageio_gif_image_1.0");

               for (Node var17 = var16.getFirstChild(); var17 != null; var17 = var17.getNextSibling()) {
                  NamedNodeMap var18 = var17.getAttributes();
                  if (var17.getNodeName().equals("ImageDescriptor")) {
                     var11 = Integer.parseInt(var18.getNamedItem("imageLeftPosition").getNodeValue());
                     var12 = Integer.parseInt(var18.getNamedItem("imageTopPosition").getNodeValue());
                  } else if (var17.getNodeName().equals("GraphicControlExtension")) {
                     var13 = Integer.parseInt(var18.getNamedItem("delayTime").getNodeValue());
                     var14 = var18.getNamedItem("disposalMethod").getNodeValue();
                  }
               }

               if (var6 == null) {
                  int var28 = Math.max(var10.getWidth() + var11, var3.getWidth(0));
                  int var30 = Math.max(var10.getHeight() + var12, var3.getHeight(0));
                  var6 = new BufferedImage(var28, var30, 2);
               }

               BufferedImage var29 = var14.equals("restoreToPrevious") ? copy(var6) : null;
               Graphics2D var31 = var6.createGraphics();
               var31.drawImage(var10, var11, var12, null);
               var31.dispose();
               var1.add(copy(var6));
               var7 += (var13 <= 1 ? 10 : var13) * 10L;
               if (var14.equals("restoreToBackgroundColor")) {
                  Graphics2D var19 = var6.createGraphics();
                  var19.setComposite(AlphaComposite.Clear);
                  var19.fillRect(var11, var12, var10.getWidth(), var10.getHeight());
                  var19.dispose();
               } else if (var29 != null) {
                  var6 = var29;
               }
            }

            var27 = var1.isEmpty() ? 0 : (int)(var7 / var1.size());
         } finally {
            var3.dispose();
         }

         return var27;
      }
   }

   private static BufferedImage copy(BufferedImage var0) {
      BufferedImage var1 = new BufferedImage(var0.getWidth(), var0.getHeight(), 2);
      Graphics2D var2 = var1.createGraphics();
      var2.drawImage(var0, 0, 0, null);
      var2.dispose();
      return var1;
   }

   public static CompletableFuture<Path> pickFile(String var0) {
      CompletableFuture var1 = new CompletableFuture();
      Runnable var2 = () -> {
         try {
            Class var2x = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Method var3x = null;

            for (Method var7 : var2x.getMethods()) {
               if (var7.getName().equals("tinyfd_openFileDialog") && var7.getParameterCount() == 5 && var7.getParameterTypes()[0] == CharSequence.class) {
                  var3x = var7;
               }
            }

            if (var3x == null) {
               throw new NoSuchMethodException("tinyfd_openFileDialog");
            }

            String var9 = System.getProperty("user.home") + File.separator;
            String var10 = (String)var3x.invoke(null, var0, var9, null, "Images (png, gif, jpg)", false);
            var1.complete(var10 != null && !var10.isBlank() ? Path.of(var10) : null);
         } catch (Throwable var8) {
            DIHClient.LOG.warn("[DIHClient] file picker failed", var8);
            var1.completeExceptionally(var8);
         }
      };
      if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
         var2.run();
      } else {
         Thread var3 = new Thread(var2, "DIHClient-FilePicker");
         var3.setDaemon(true);
         var3.start();
      }

      return var1;
   }

   public record Loaded(class_1011 image, int frames, int frameMs) {
   }
}
