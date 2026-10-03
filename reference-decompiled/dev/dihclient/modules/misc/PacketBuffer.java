package dev.dihclient.modules.misc;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2596;
import net.minecraft.class_2828;

public class PacketBuffer extends Module {
   public final IntSetting maxPackets = this.integer("Max Packets", "Flushes automatically when this many movement packets are buffered.", 200, 10, 2000)
      .legacy("packetBuffer.maxPackets");
   private final List<class_2596<?>> buffer = new ArrayList<>();
   private boolean flushing;

   public PacketBuffer() {
      super("PacketBuffer", Category.MISC, "Holds outgoing movement packets until disabled or flushed (blink).");
      this.action("Flush", "Sends all buffered packets now.", this::flush);
   }

   @Override
   public boolean onPacketSend(class_2596<?> var1) {
      if (!this.flushing && var1 instanceof class_2828) {
         this.buffer.add(var1);
         if (this.buffer.size() >= this.maxPackets.get()) {
            mc.execute(this::flush);
         }

         return true;
      } else {
         return false;
      }
   }

   public void flush() {
      if (mc.method_1562() == null) {
         this.buffer.clear();
      } else {
         this.flushing = true;

         try {
            for (class_2596 var2 : this.buffer) {
               mc.method_1562().method_52787(var2);
            }
         } finally {
            this.buffer.clear();
            this.flushing = false;
         }
      }
   }

   @Override
   protected void onDisable() {
      this.flush();
   }

   @Override
   public void onWorldChange() {
      this.buffer.clear();
   }

   @Override
   public String getInfo() {
      return Integer.toString(this.buffer.size());
   }
}
