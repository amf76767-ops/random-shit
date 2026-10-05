package dev.dihclient.port.donutc;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import net.minecraft.class_2596;
import net.minecraft.class_2828;
import net.minecraft.class_9836;

/** Ported from an open-source client (GPL-3.0). */
public class AirStuck extends Module {

    public static volatile AirStuck active;

    public AirStuck() {
        super("Air Stuck", Category.DONUT, "Freezes you in place, even mid-air.");
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    @Override
    public void onWorldChange() {

        if (this.isEnabled()) {
            this.setEnabled(false);
        }
    }

    @Override
    public boolean onPacketSend(class_2596<?> packet) {
        return isFrozenPacket(packet);
    }

    static boolean isFrozenPacket(class_2596<?> packet) {
        return packet instanceof class_9836 || packet instanceof class_2828;
    }
}
