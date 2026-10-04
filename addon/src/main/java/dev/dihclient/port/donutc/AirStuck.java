package dev.dihclient.port.donutc;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import net.minecraft.class_2596;
import net.minecraft.class_2828;
import net.minecraft.class_9836;

/**
 * Ported from an open-source client (GPL-3.0).
 * Freezes you in place, even mid-air: your movement step does not run (DonutCAirStuckMixin) and the position and
 * tick-end packets are not sent, so the server keeps you where you were while you can still look around and use the
 * game. Everything else (keep-alive, teleport confirmations, chat, interaction) is sent as usual.
 * <p>
 * The original did not remember this module across restarts. DIH saves the state of every module, so instead it switches
 * itself off when you join or leave a server or change dimension.
 */
public class AirStuck extends Module {
    /** The running instance for the mixin, null while the module is off. */
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
        // a stuck player in a fresh world, or a saved "on" from the last session, would just be a frozen game
        if (this.isEnabled()) {
            this.setEnabled(false);
        }
    }

    @Override
    public boolean onPacketSend(class_2596<?> packet) {
        return isFrozenPacket(packet);
    }

    /** The packets that tell the server where you are and that a client tick ended. */
    static boolean isFrozenPacket(class_2596<?> packet) {
        return packet instanceof class_9836 || packet instanceof class_2828;
    }
}
