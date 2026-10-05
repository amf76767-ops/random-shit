package dev.dihclient.port.donuta;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import net.minecraft.class_2596;
import net.minecraft.class_2828;
import net.minecraft.class_2828.class_2830;
import net.minecraft.class_634;
import net.minecraft.class_746;

/** Ported from an open-source client (GPL-3.0). */
public class DonutNoFall extends Module {
    private final DonutNoFallLogic logic = new DonutNoFallLogic();

    private boolean sendingReplacement;

    public DonutNoFall() {
        super("Donut NoFall", Category.DONUT, "Take no fall damage on DonutSMP.");
    }

    @Override
    protected void onEnable() {
        this.logic.reset();
        this.sendingReplacement = false;
    }

    @Override
    public void onWorldChange() {

        this.logic.reset();
    }

    @Override
    public void onTick() {
        class_746 player = mc.field_1724;
        if (player == null) {
            return;
        }
        if (this.logic.tick(player.field_6017)) {
            player.method_24830(false);
        }
    }

    @Override
    public boolean onPacketSend(class_2596<?> packet) {
        if (this.sendingReplacement || !(packet instanceof class_2828 move)) {
            return false;
        }
        class_746 player = mc.field_1724;
        class_634 connection = mc.method_1562();
        if (player == null || connection == null) {
            return false;
        }
        DonutNoFallLogic.Send send = this.logic.packet(move.method_12273(), player.method_24828());
        if (send.nudge) {
            player.method_23327(player.method_23317(), player.method_23318() + 1.0E-8, player.method_23321());
        }
        this.sendingReplacement = true;
        try {
            connection.method_52787(new class_2830(player.method_23317(), player.method_23318(), player.method_23321(),
                    player.method_36454(), player.method_36455(), send.onGround, false));
        } finally {
            this.sendingReplacement = false;
        }
        return true;
    }
}
