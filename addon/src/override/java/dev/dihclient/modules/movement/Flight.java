package dev.dihclient.modules.movement;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.MoveUtil;
import net.minecraft.class_243;

/**
 * Client flight. Vanilla = free flight, Glide = slow descent, Packet = the old PacketFly (phase and fly through blocks with
 * position packets). PacketFly is no module of its own any more; its settings are shown here while Packet is selected.
 */
public class Flight extends Module {
    public final EnumSetting<Flight.Mode> mode = this.mode("Mode",
            "Vanilla: free flight. Glide: slow descent. Packet: fly and phase through blocks with position packets (server-dependent, raise Factor slowly).",
            Flight.Mode.VANILLA).legacy("flight.mode");
    public final DoubleSetting horizontal = this.dbl("Horizontal Speed", "Horizontal speed.", 0.35, 0.05, 5.0, 0.01)
            .legacy("flight.horizontalSpeed").visibleWhen(() -> this.mode.get() != Flight.Mode.PACKET);
    public final DoubleSetting vertical = this.dbl("Vertical Speed", "Up/down speed (jump/sneak).", 0.3, 0.05, 5.0, 0.01)
            .legacy("flight.verticalSpeed").visibleWhen(() -> this.mode.get() != Flight.Mode.PACKET);
    public final DoubleSetting glide = this.dbl("Glide Speed", "Descent speed in Glide mode.", 0.08, 0.0, 1.0, 0.01)
            .legacy("flight.glideSpeed").visibleWhen(() -> this.mode.get() == Flight.Mode.GLIDE);
    public final BoolSetting antiKick = this.bool("Anti Kick", "Small downward pulse every second to avoid fly kicks.", true)
            .legacy("flight.antiKick").visibleWhen(() -> this.mode.get() != Flight.Mode.PACKET);
    private boolean captured;
    private boolean oldNoGravity;
    private int antiKickTicks;

    public Flight() {
        super("Flight", Category.MOVEMENT, "Client flight with Vanilla, Glide and Packet modes (Packet = the former PacketFly).");
    }

    private static PacketFly packetFly() {
        return ModuleManager.of(PacketFly.class);
    }

    /** Leaves Packet mode: PacketFly is a hidden helper that runs only while Flight is in Packet mode. */
    private void stopPacket() {
        PacketFly pf = packetFly();
        if (pf != null && pf.isEnabled()) {
            pf.setEnabledSilently(false);
        }
    }

    @Override
    public void onTick() {
        if (this.mode.get() == Flight.Mode.PACKET) {
            this.releaseVanilla();
            PacketFly pf = packetFly();
            if (pf != null) {
                if (!pf.isEnabled()) {
                    pf.setEnabledSilently(true);
                }
                pf.onTick();
            }
            return;
        }
        this.stopPacket();
        if (!this.captured) {
            this.oldNoGravity = mc.field_1724.method_5740();
            this.captured = true;
        }

        boolean glideMode = this.mode.get() == Flight.Mode.GLIDE;
        mc.field_1724.method_5875(!glideMode);
        double[] dir = MoveUtil.direction(this.horizontal.get());
        double vy;
        if (mc.field_1690.field_1903.method_1434()) {
            vy = this.vertical.get();
        } else if (mc.field_1690.field_1832.method_1434()) {
            vy = -this.vertical.get();
        } else if (glideMode) {
            vy = Math.max(mc.field_1724.method_18798().field_1351, -Math.abs(this.glide.get()));
        } else {
            vy = 0.0;
        }

        if (!glideMode && vy == 0.0 && this.antiKick.get() && ++this.antiKickTicks >= 20) {
            vy = -0.04;
            this.antiKickTicks = 0;
        }

        mc.field_1724.method_18800(dir[0], vy, dir[1]);
    }

    private void releaseVanilla() {
        if (this.captured && mc.field_1724 != null) {
            mc.field_1724.method_5875(this.oldNoGravity);
            this.captured = false;
        }
    }

    @Override
    protected void onDisable() {
        this.stopPacket();
        if (mc.field_1724 != null) {
            if (this.captured) {
                mc.field_1724.method_5875(this.oldNoGravity);
            }

            class_243 v = mc.field_1724.method_18798();
            mc.field_1724.method_18800(v.field_1352, 0.0, v.field_1350);
        }

        this.captured = false;
        this.antiKickTicks = 0;
    }

    @Override
    public String getInfo() {
        return this.mode.displayValue();
    }

    public static enum Mode {
        VANILLA,
        GLIDE,
        PACKET;
    }
}
