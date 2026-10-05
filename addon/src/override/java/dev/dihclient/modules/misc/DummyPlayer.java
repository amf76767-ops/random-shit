package dev.dihclient.modules.misc;

import com.mojang.authlib.GameProfile;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.ItemUtil;
import dev.dihclient.util.Notifications;
import dev.dihclient.util.RegistryUtil;
import java.util.UUID;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3966;
import net.minecraft.class_745;
import net.minecraft.class_1297.class_5529;

public class DummyPlayer extends Module {
    private static final int ENTITY_ID = -4200042;
    private static final float MAX_HEALTH = 20.0F;
    private class_745 dummy;
    private float health = MAX_HEALTH;

    public DummyPlayer() {
        super("DummyPlayer", Category.MISC, "Spawns a local client-side copy of you for combat/render testing. It has gravity and takes damage and knockback from your hits.");
    }

    @Override
    protected void onEnable() {
        if (!inGame()) {
            this.setEnabledSilently(false);
        } else {
            this.health = MAX_HEALTH;
            this.dummy = new class_745(mc.field_1687, new GameProfile(UUID.randomUUID(), mc.field_1724.method_7334().name()));
            this.dummy.method_5838(ENTITY_ID);
            this.dummy.method_5808(mc.field_1724.method_23317(), mc.field_1724.method_23318(), mc.field_1724.method_23321(),
                    mc.field_1724.method_36454(), mc.field_1724.method_36455());
            this.dummy.method_5847(mc.field_1724.method_5791());
            this.dummy.method_5636(mc.field_1724.method_73188());
            this.dummy.method_31548().method_7377(mc.field_1724.method_31548());
            mc.field_1687.method_53875(this.dummy);
        }
    }

    @Override
    protected void onDisable() {
        if (this.dummy != null && mc.field_1687 != null) {
            mc.field_1687.method_2945(this.dummy.method_5628(), class_5529.field_26999);
        }

        this.dummy = null;
    }

    @Override
    public void onWorldChange() {
        this.dummy = null;
        if (this.isEnabled()) {
            this.setEnabledSilently(false);
        }
    }

    private boolean solid(double x, double y, double z) {
        class_2338 pos = class_2338.method_49637(x, y, z);
        class_2680 state = mc.field_1687.method_8320(pos);
        return !state.method_26218(mc.field_1687, pos).method_1110();
    }

    @Override
    public void onTick() {
        if (this.dummy == null) {
            return;
        }
        double x = this.dummy.method_23317();
        double y = this.dummy.method_23318();
        double z = this.dummy.method_23321();
        class_243 v = this.dummy.method_18798();
        boolean ground = this.solid(x, y - 0.01, z);
        double vx = v.field_1352 * (ground ? 0.546 : 0.91);
        double vz = v.field_1350 * (ground ? 0.546 : 0.91);
        double vy = ground && v.field_1351 <= 0.0 ? 0.0 : (v.field_1351 - 0.08) * 0.98;
        if (Math.abs(vx) < 0.003) {
            vx = 0.0;
        }
        if (Math.abs(vz) < 0.003) {
            vz = 0.0;
        }

        double nx = x + vx;
        double nz = z + vz;
        if (vx != 0.0 && (this.solid(nx, y + 0.1, z) || this.solid(nx, y + 1.0, z))) {
            nx = x;
            vx = 0.0;
        }
        if (vz != 0.0 && (this.solid(nx, y + 0.1, nz) || this.solid(nx, y + 1.0, nz))) {
            nz = z;
            vz = 0.0;
        }
        double ny = y + vy;
        if (vy < 0.0 && this.solid(nx, ny, nz)) {
            ny = Math.floor(ny) + 1.0;
            vy = 0.0;
        } else if (vy > 0.0 && this.solid(nx, ny + 1.8, nz)) {
            vy = 0.0;
            ny = y;
        }
        if (ny < -128.0) {
            ny = mc.field_1724.method_23318() + 2.0;
            vy = 0.0;
        }

        this.dummy.method_5808(nx, ny, nz, this.dummy.method_36454(), this.dummy.method_36455());
        this.dummy.method_18800(vx, vy, vz);
        this.dummy.method_24830(ground);
    }

    public static void onSwing() {
        DummyPlayer d = ModuleManager.of(DummyPlayer.class);
        if (d != null && d.isEnabled() && d.dummy != null && inGame()) {
            d.swing();
        }
    }

    private static double weaponDamage(class_1799 stack) {
        String id = stack.method_7960() ? "" : ItemUtil.id(stack);
        if (id.endsWith("_sword")) {
            return id.contains("netherite") ? 8 : id.contains("diamond") ? 7 : id.contains("iron") ? 6 : id.contains("stone") ? 5 : 4;
        }
        if (id.endsWith("_axe")) {
            return id.contains("netherite") ? 10 : id.contains("wooden") || id.contains("golden") ? 7 : 9;
        }
        if (id.endsWith("mace")) {
            return 6;
        }
        if (id.endsWith("trident")) {
            return 9;
        }
        if (id.endsWith("_pickaxe") || id.endsWith("_shovel")) {
            return id.contains("netherite") ? 6 : id.contains("diamond") ? 5 : id.contains("iron") ? 4 : 3;
        }
        return 1;
    }

    private void swing() {
        if (!(mc.field_1765 instanceof class_3966 hit) || hit.method_17782() != this.dummy) {
            return;
        }
        class_1799 held = mc.field_1724.method_31548().method_5438(InvUtil.selectedSlot());
        double damage = weaponDamage(held);
        boolean crit = !mc.field_1724.method_24828() && mc.field_1724.field_6017 > 0.0;
        if (crit) {
            damage *= 1.5;
        }
        this.health -= (float) damage;
        this.dummy.field_6235 = 10;

        double dx = this.dummy.method_23317() - mc.field_1724.method_23317();
        double dz = this.dummy.method_23321() - mc.field_1724.method_23321();
        double len = Math.max(0.001, Math.hypot(dx, dz));
        double strength = mc.field_1690.field_1867.method_1434() ? 0.9 : 0.4;
        class_243 v = this.dummy.method_18798();
        this.dummy.method_18800(v.field_1352 * 0.5 + dx / len * strength, this.dummy.method_24828() ? 0.36 : Math.max(v.field_1351, 0.1),
                v.field_1350 * 0.5 + dz / len * strength);
        this.dummy.method_24830(false);

        if (this.health <= 0.0F) {
            Notifications.info("DummyPlayer", "Defeated. It is back with full health.");
            this.health = MAX_HEALTH;
        } else {
            Notifications.info("DummyPlayer", String.format("-%.1f%s  (%.1f / %.0f)", damage, crit ? " crit" : "", this.health, MAX_HEALTH));
        }
    }

    @Override
    public String getInfo() {
        return this.dummy == null ? null : String.format("%.0f", this.health);
    }
}
