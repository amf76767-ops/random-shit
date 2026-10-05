package dev.dihclient.modules.misc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.Notifications;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_3419;

public class Radio extends Module {
   public final EnumSetting<Radio.Country> country = this.mode("Country", "Which stations to pick from.", Radio.Country.BOTH).onChange(this::refilter);
   public final StringSetting genre = this.text("Genre", "Optional genre / tag, e.g. rock, pop, techno, news, 80s. Empty = anything.", "", 32)
      .onChange(this::refilter);
   public final IntSetting volume = this.integer("Volume", "Radio volume in percent.", 50, 0, 100);
   public final BoolSetting gameVolume = this.bool("Use Master Volume", "Also follow Minecraft's master volume slider.", true);
   public final BoolSetting muteMusic = this.bool("Mute Game Music", "Stops the normal Minecraft music while the radio plays.", true);
   public final BoolSetting songToasts = this.bool("Song Toasts", "Toast when a new song starts (if the station sends titles).", true);
   private static final String[] API = new String[]{
      "de1.api.radio-browser.info",
      "de2.api.radio-browser.info",
      "fi1.api.radio-browser.info",
      "at1.api.radio-browser.info",
      "nl1.api.radio-browser.info",
      "all.api.radio-browser.info"
   };
   private static final String UA = "DIHClient/1.0 (Minecraft mod radio)";
   private static ClassLoader jlayer;
   private static Constructor<?> bitstreamCtor;
   private static Constructor<?> decoderCtor;
   private static Method readFrame;
   private static Method closeFrame;
   private static Method closeBitstream;
   private static Method decodeFrame;
   private static Method getBuffer;
   private static Method getBufferLength;
   private static Method outFrequency;
   private static Method outChannels;
   private final List<Radio.Station> stations = new ArrayList<>();
   private final Deque<String> recent = new ArrayDeque<>();
   private volatile int generation;
   private volatile boolean skip;
   private volatile boolean needList = true;
   private volatile Radio.Station current;
   private volatile String songTitle;
   private volatile String status = "Stopped";
   private volatile HttpURLConnection connection;
   private volatile float gain = 0.25F;

   public Radio() {
      super("Radio", Category.MISC, "Plays a random German / British internet radio station in-game, with song titles.");
      this.action("Next Station", "Switches to another random station.", () -> this.skip = true);
      this.action(
         "Show Song",
         "Shows the current station and song.",
         () -> Notifications.info("Radio", this.current == null ? this.status : this.current.name() + (this.songTitle != null ? " – " + this.songTitle : ""))
      );
   }

   @Override
   protected void onEnable() {
      int var1 = ++this.generation;
      this.skip = false;
      Thread var2 = new Thread(() -> this.run(var1), "DIHClient-Radio");
      var2.setDaemon(true);
      var2.start();
   }

   @Override
   protected void onDisable() {
      this.generation++;
      this.closeConnection();
      this.current = null;
      this.songTitle = null;
      this.status = "Stopped";
   }

   private void refilter() {
      this.needList = true;
      if (this.isEnabled()) {
         this.skip = true;
      }
   }

   @Override
   public void onTick() {
      float var1 = this.volume.get().intValue() / 100.0F;
      var1 *= var1;
      if (this.gameVolume.get()) {
         var1 *= mc.field_1690.method_71978(class_3419.field_15250);
      }

      this.gain = var1;
      if (this.muteMusic.get() && this.current != null && mc.field_1724.field_6012 % 20 == 0) {
         mc.method_1538().method_4859();
      }
   }

   private boolean alive(int var1) {
      return var1 == this.generation;
   }

   private void stopSelf(int var1) {
      mc.execute(() -> {
         if (this.alive(var1)) {
            this.setEnabled(false);
         }
      });
   }

   private void closeConnection() {
      HttpURLConnection var1 = this.connection;
      if (var1 != null) {
         var1.disconnect();
      }
   }

   private void toast(String var1) {
      mc.execute(() -> Notifications.info("Radio", var1));
   }

   private void run(int var1) {
      try {
         this.status = "Loading decoder …";
         if (!this.loadDecoder()) {
            this.stopSelf(var1);
            return;
         }

         int var2 = 0;

         while (this.alive(var1)) {
            if (this.needList || this.stations.isEmpty()) {
               this.status = "Searching stations …";
               if (!this.fetchStations()) {
                  this.toast("No stations found – check the genre or your internet.");
                  this.stopSelf(var1);
                  return;
               }
            }

            Radio.Station var3 = this.pick();
            this.current = var3;
            this.songTitle = null;
            this.skip = false;
            this.status = "Connecting …";

            boolean var4;
            try {
               var4 = this.play(var3, var1);
            } catch (Exception var6) {
               DIHClient.LOG.warn("[DIHClient] radio station {} failed: {}", var3.name(), var6.toString());
               var4 = false;
            }

            if (!this.alive(var1)) {
               break;
            }

            if (!var4 && !this.skip) {
               if (++var2 >= 8) {
                  this.toast("Could not play any station – check your internet.");
                  this.stopSelf(var1);
                  return;
               }
            } else {
               var2 = 0;
            }
         }
      } catch (Throwable var7) {
         DIHClient.LOG.error("[DIHClient] radio crashed", var7);
         this.toast("Radio stopped: " + var7.getClass().getSimpleName());
      }
   }

   private Radio.Station pick() {
      synchronized (this.stations) {
         ArrayList var2 = new ArrayList();

         for (Radio.Station var4 : this.stations) {
            if (!this.recent.contains(var4.uuid())) {
               var2.add(var4);
            }
         }

         if (var2.isEmpty()) {
            this.recent.clear();
            var2.addAll(this.stations);
         }

         Radio.Station var7 = (Radio.Station)var2.get((int)(Math.random() * var2.size()));
         this.recent.addLast(var7.uuid());

         while (this.recent.size() > Math.min(15, this.stations.size() / 2)) {
            this.recent.removeFirst();
         }

         return var7;
      }
   }

   private boolean fetchStations() {
      List<String> var1 = switch ((Radio.Country)this.country.get()) {
         case GERMANY -> List.of("DE");
         case UK -> List.of("GB");
         case BOTH -> List.of("DE", "GB");
      };
      ArrayList var2 = new ArrayList();

      for (String var4 : var1) {
         var2.addAll(this.query(var4));
      }

      if (var2.isEmpty()) {
         return false;
      } else {
         Collections.shuffle(var2);
         synchronized (this.stations) {
            this.stations.clear();
            this.stations.addAll(var2);
            this.recent.clear();
         }

         this.needList = false;
         return true;
      }
   }

   private List<Radio.Station> query(String var1) {
      String var2 = this.genre.get().trim().toLowerCase(Locale.ROOT);
      String var3 = "/json/stations/search?countrycode="
         + var1
         + "&codec=MP3&hidebroken=true&order=clickcount&reverse=true&limit=250"
         + (var2.isEmpty() ? "" : "&tag=" + URLEncoder.encode(var2, StandardCharsets.UTF_8));

      for (String var7 : API) {
         try {
            HttpURLConnection var8 = (HttpURLConnection)URI.create("https://" + var7 + var3).toURL().openConnection();
            var8.setConnectTimeout(6000);
            var8.setReadTimeout(10000);
            var8.setRequestProperty("User-Agent", "DIHClient/1.0 (Minecraft mod radio)");
            if (var8.getResponseCode() == 200) {
               String var9;
               try (InputStream var10 = var8.getInputStream()) {
                  var9 = new String(var10.readAllBytes(), StandardCharsets.UTF_8);
               }

               ArrayList var19 = new ArrayList();

               for (JsonElement var13 : JsonParser.parseString(var9).getAsJsonArray()) {
                  JsonObject var14 = var13.getAsJsonObject();
                  String var15 = str(var14, "url_resolved");
                  if (var15.isEmpty()) {
                     var15 = str(var14, "url");
                  }

                  if (var15.startsWith("http")) {
                     var19.add(
                        new Radio.Station(
                           str(var14, "stationuuid"),
                           str(var14, "name").trim(),
                           var15,
                           var1,
                           str(var14, "tags"),
                           var14.has("bitrate") && !var14.get("bitrate").isJsonNull() ? var14.get("bitrate").getAsInt() : 0
                        )
                     );
                  }
               }

               return var19;
            }
         } catch (Exception var18) {
            DIHClient.LOG.warn("[DIHClient] radio directory {} failed: {}", var7, var18.toString());
         }
      }

      return List.of();
   }

   private static String str(JsonObject var0, String var1) {
      return var0.has(var1) && !var0.get(var1).isJsonNull() ? var0.get(var1).getAsString() : "";
   }

   private boolean play(Radio.Station var1, int var2) throws Exception {
      HttpURLConnection var3 = this.open(var1.url(), 0);
      this.connection = var3;
      String var4 = String.valueOf(var3.getContentType()).toLowerCase(Locale.ROOT);
      if (!var4.contains("aac") && !var4.contains("ogg") && !var4.contains("text/html")) {
         int var5 = 0;

         try {
            var5 = Integer.parseInt(String.valueOf(var3.getHeaderField("icy-metaint")).trim());
         } catch (NumberFormatException var32) {
         }

         BufferedInputStream var6 = new BufferedInputStream(var3.getInputStream(), 65536);
         Object var7 = var5 > 0 ? new Radio.IcyStream(var6, var5) : var6;
         Object var8 = bitstreamCtor.newInstance(var7);
         Object var9 = decoderCtor.newInstance();
         SourceDataLine var10 = null;
         long var11 = 0L;
         int var13 = 0;
         boolean var14 = false;

         try {
            while (this.alive(var2) && !this.skip) {
               Object var15;
               try {
                  var15 = readFrame.invoke(var8);
               } catch (InvocationTargetException var34) {
                  if (++var13 > 30) {
                     break;
                  }
                  continue;
               }

               if (var15 == null) {
                  break;
               }

               Object var16;
               try {
                  var16 = decodeFrame.invoke(var9, var15, var8);
               } catch (InvocationTargetException var33) {
                  closeFrame.invoke(var8);
                  if (++var13 > 30) {
                     break;
                  }
                  continue;
               }

               closeFrame.invoke(var8);
               var13 = 0;
               if (var10 == null) {
                  int var17 = (Integer)outFrequency.invoke(var9);
                  int var18 = (Integer)outChannels.invoke(var9);
                  AudioFormat var19 = new AudioFormat(var17, 16, var18, true, false);
                  var10 = AudioSystem.getSourceDataLine(var19);
                  var10.open(var19, var17 * var18 * 2 / 2);
                  var10.start();
               }

               short[] var36 = (short[])getBuffer.invoke(var16);
               int var37 = (Integer)getBufferLength.invoke(var16);
               byte[] var38 = new byte[var37 * 2];
               float var20 = this.gain;

               for (int var21 = 0; var21 < var37; var21++) {
                  int var22 = (int)(var36[var21] * var20);
                  var38[var21 * 2] = (byte)var22;
                  var38[var21 * 2 + 1] = (byte)(var22 >> 8);
               }

               var10.write(var38, 0, var38.length);
               if (++var11 == 20L && !var14) {
                  var14 = true;
                  this.status = "Playing";
                  this.toast(
                     "▶ "
                        + var1.name()
                        + (var1.bitrate() > 0 ? " · " + var1.bitrate() + " kbps" : "")
                        + " ("
                        + (var1.country().equals("GB") ? "UK" : "DE")
                        + ")"
                  );
               }
            }
         } finally {
            if (var10 != null) {
               if (!this.alive(var2) || this.skip) {
                  var10.flush();
               }

               var10.stop();
               var10.close();
            }

            try {
               closeBitstream.invoke(var8);
            } catch (Exception var31) {
            }

            var3.disconnect();
            if (this.connection == var3) {
               this.connection = null;
            }
         }

         return var11 > 200L;
      } else {
         var3.disconnect();
         return false;
      }
   }

   private HttpURLConnection open(String var1, int var2) throws IOException {
      if (var2 > 6) {
         throw new IOException("too many redirects");
      } else {
         HttpURLConnection var3 = (HttpURLConnection)URI.create(var1.trim()).toURL().openConnection();
         var3.setInstanceFollowRedirects(false);
         var3.setConnectTimeout(8000);
         var3.setReadTimeout(15000);
         var3.setRequestProperty("User-Agent", "DIHClient/1.0 (Minecraft mod radio)");
         var3.setRequestProperty("Icy-MetaData", "1");
         int var4 = var3.getResponseCode();
         if (var4 >= 300 && var4 < 400) {
            String var15 = var3.getHeaderField("Location");
            var3.disconnect();
            if (var15 == null) {
               throw new IOException("redirect without location");
            } else {
               return this.open(URI.create(var1).resolve(var15).toString(), var2 + 1);
            }
         } else if (var4 != 200) {
            var3.disconnect();
            throw new IOException("HTTP " + var4);
         } else {
            String var5 = String.valueOf(var3.getContentType()).toLowerCase(Locale.ROOT);
            String var6 = var1.toLowerCase(Locale.ROOT);
            if (!var5.contains("mpegurl") && !var5.contains("scpls") && !var6.endsWith(".m3u") && !var6.endsWith(".m3u8") && !var6.endsWith(".pls")) {
               return var3;
            } else {
               String var7;
               try (InputStream var8 = var3.getInputStream()) {
                  var7 = new String(var8.readNBytes(32768), StandardCharsets.UTF_8);
               }

               var3.disconnect();

               for (String var11 : var7.split("\\R")) {
                  var11 = var11.trim();
                  int var12 = var11.indexOf(61);
                  if (var11.toLowerCase(Locale.ROOT).startsWith("file") && var12 > 0) {
                     var11 = var11.substring(var12 + 1).trim();
                  }

                  if (var11.startsWith("http") && !var11.toLowerCase(Locale.ROOT).contains(".m3u8")) {
                     return this.open(var11, var2 + 1);
                  }
               }

               throw new IOException("empty playlist");
            }
         }
      }
   }

   private boolean loadDecoder() {
      if (jlayer != null) {
         return true;
      } else {
         try {
            // the MP3 decoder (JLayer, javazoom.jl) is part of this mod's jar: no download, no second jar, no extra class loader
            ClassLoader var9 = Radio.class.getClassLoader();
            Class var10 = var9.loadClass("javazoom.jl.decoder.Bitstream");
            Class var11 = var9.loadClass("javazoom.jl.decoder.Header");
            Class var12 = var9.loadClass("javazoom.jl.decoder.Decoder");
            Class var7 = var9.loadClass("javazoom.jl.decoder.SampleBuffer");
            bitstreamCtor = var10.getConstructor(InputStream.class);
            readFrame = var10.getMethod("readFrame");
            closeFrame = var10.getMethod("closeFrame");
            closeBitstream = var10.getMethod("close");
            decoderCtor = var12.getConstructor();
            decodeFrame = var12.getMethod("decodeFrame", var11, var10);
            outFrequency = var12.getMethod("getOutputFrequency");
            outChannels = var12.getMethod("getOutputChannels");
            getBuffer = var7.getMethod("getBuffer");
            getBufferLength = var7.getMethod("getBufferLength");
            jlayer = var9;
            return true;
         } catch (Exception var8) {
            DIHClient.LOG.error("[DIHClient] radio decoder failed", var8);
            this.toast("Could not load the MP3 decoder: " + var8.getClass().getSimpleName());
            return false;
         }
      }
   }

   private static byte[] download(String var0) throws IOException {
      HttpURLConnection var1 = (HttpURLConnection)URI.create(var0).toURL().openConnection();
      var1.setConnectTimeout(8000);
      var1.setReadTimeout(15000);
      var1.setRequestProperty("User-Agent", "DIHClient/1.0 (Minecraft mod radio)");
      if (var1.getResponseCode() != 200) {
         throw new IOException("HTTP " + var1.getResponseCode());
      } else {
         byte[] var4;
         try (
            InputStream var2 = var1.getInputStream();
            ByteArrayOutputStream var3 = new ByteArrayOutputStream();
         ) {
            var2.transferTo(var3);
            var4 = var3.toByteArray();
         } finally {
            var1.disconnect();
         }

         return var4;
      }
   }

   @Override
   public String getInfo() {
      Radio.Station var1 = this.current;
      return var1 == null ? null : (var1.name().length() > 22 ? var1.name().substring(0, 21) + "…" : var1.name());
   }

   @Override
   public List<String> details() {
      Radio.Station var1 = this.current;
      ArrayList var2 = new ArrayList();
      var2.add(
         var1 == null
            ? this.status
            : var1.name() + " (" + (var1.country().equals("GB") ? "UK" : "DE") + (var1.bitrate() > 0 ? ", " + var1.bitrate() + " kbps" : "") + ")"
      );
      if (this.songTitle != null) {
         var2.add("♪ " + this.songTitle);
      }

      if (var1 != null && !var1.tags().isBlank()) {
         var2.add(var1.tags().length() > 60 ? var1.tags().substring(0, 60) + "…" : var1.tags());
      }

      var2.add(this.status);
      return var2;
   }

   public static enum Country {
      GERMANY,
      UK,
      BOTH;
   }

   private final class IcyStream extends FilterInputStream {
      private final int metaInt;
      private int left;

      IcyStream(InputStream nullx, int nullxx) {
         super(nullx);
         this.metaInt = nullxx;
         this.left = nullxx;
      }

      @Override
      public int read() throws IOException {
         byte[] var1 = new byte[1];
         int var2 = this.read(var1, 0, 1);
         return var2 <= 0 ? -1 : var1[0] & 0xFF;
      }

      @Override
      public int read(byte[] var1, int var2, int var3) throws IOException {
         if (this.left == 0) {
            this.readMeta();
            this.left = this.metaInt;
         }

         int var4 = this.in.read(var1, var2, Math.min(var3, this.left));
         if (var4 > 0) {
            this.left -= var4;
         }

         return var4;
      }

      private void readMeta() throws IOException {
         int var1 = this.in.read();
         if (var1 > 0) {
            byte[] var2 = this.in.readNBytes(var1 * 16);
            String var3 = new String(var2, StandardCharsets.UTF_8);
            if (var3.contains("�")) {
               var3 = new String(var2, StandardCharsets.ISO_8859_1);
            }

            int var4 = var3.indexOf("StreamTitle='");
            if (var4 >= 0) {
               int var5 = var3.indexOf("';", var4 + 13);
               String var6 = (var5 > 0 ? var3.substring(var4 + 13, var5) : var3.substring(var4 + 13)).trim();
               if (!var6.isEmpty() && !var6.equals(Radio.this.songTitle)) {
                  Radio.this.songTitle = var6;
                  if (Radio.this.songToasts.get()) {
                     Radio.this.toast("♪ " + var6);
                  }
               }
            }
         }
      }

      @Override
      public long skip(long var1) throws IOException {
         byte[] var3 = new byte[(int)Math.min(var1, 8192L)];
         int var4 = this.read(var3, 0, var3.length);
         return Math.max(0, var4);
      }
   }

   private record Station(String uuid, String name, String url, String country, String tags, int bitrate) {
   }
}
