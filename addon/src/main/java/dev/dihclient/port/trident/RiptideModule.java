package dev.dihclient.port.trident;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import net.minecraft.class_1268;
import net.minecraft.class_746;
import org.lwjgl.glfw.GLFW;

/**
 * Ported from an open-source client (GPL-3.0).
 * Uses a riptide trident while you hold right-click: starts the use, lets go once it is charged, and starts again.
 * It only acts in water or rain (the same place a server accepts a riptide), no Trident Util needed.
 */
public class RiptideModule extends Module {
    private int releasedTick = -100;
    private boolean broken;

    public RiptideModule() {
        super("Riptide", Category.DONUT, "Uses a riptide trident while you hold right-click.");
    }

    @Override
    protected void onEnable() {
        this.releasedTick = -100;
        this.broken = false;
    }

    @Override
    public void onWorldChange() {
        this.releasedTick = -100;
    }

    @Override
    public void onTick() {
        class_746 player = mc.field_1724;
        if (this.broken || player == null || mc.field_1761 == null || mc.field_1755 != null) {
            return;
        }
        try {
            if (GLFW.glfwGetMouseButton(mc.method_22683().method_4490(), 1) != 1 || !GrimRiptide.clientAccepts()) {
                return;
            }
            class_1268 hand = hand(player);
            if (hand == null) {
                return;
            }
            boolean using = player.method_6115();
            int release = TridentLogic.releaseTicks(TridentHooks.minChargeTicks(TridentLogic.VANILLA_CHARGE));
            switch (TridentLogic.step(using, using && GrimRiptide.isRiptide(player.method_6030()), player.method_6048(),
                    release, player.field_6012, this.releasedTick)) {
                case START_USE -> mc.field_1761.method_2919(player, hand);
                case RELEASE -> {
                    mc.field_1761.method_2897(player);
                    this.releasedTick = player.field_6012;
                }
                default -> { }
            }
        } catch (Throwable t) {
            this.broken = true;
            DIHClient.LOG.warn("[DIHClient] Riptide stopped after an error", t);
        }
    }

    private static class_1268 hand(class_746 player) {
        if (GrimRiptide.isRiptide(player.method_6047())) {
            return class_1268.field_5808;
        }
        return GrimRiptide.isRiptide(player.method_6079()) ? class_1268.field_5810 : null;
    }
}
