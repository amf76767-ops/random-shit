package dev.dihclient.port.donutc;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_638;
import net.minecraft.class_746;

/** Ported from an open-source client (GPL-3.0). */
public class AntiTrap extends Module {
    private static final int SWEEP_INTERVAL_TICKS = 20;

    public final BoolSetting removeExisting = this.bool("Remove Existing", "Removes the armor stands and minecarts that are already there when you switch it on.", true);
    public final BoolSetting preventSpawn = this.bool("Prevent Spawn", "Removes new ones the moment they appear.", true);
    public final BoolSetting armorStands = this.bool("Armor Stands", "Treat armor stands as traps.", true);
    public final BoolSetting chestMinecarts = this.bool("Chest Minecarts", "Treat chest minecarts as traps.", true);

    private final Set<Integer> seen = new HashSet<>();
    private class_638 lastLevel;

    public AntiTrap() {
        super("Anti Trap", Category.DONUT, "Allows you to escape from armor stands and chest minecarts.");
    }

    @Override
    protected void onEnable() {
        this.seen.clear();
        this.lastLevel = mc.field_1687;
        if (mc.field_1687 != null) {
            List<class_1297> traps = this.collectTrapEntities(mc.field_1687, mc.field_1724);
            for (class_1297 entity : mc.field_1687.method_18112()) {
                if (this.isTrapEntity(entity)) {
                    this.seen.add(entity.method_5628());
                }
            }
            if (this.removeExisting.get()) {
                for (class_1297 entity : traps) {
                    entity.method_31472();
                }
                if (!traps.isEmpty()) {
                    Notifications.info(this.name(), "Removed " + traps.size() + (traps.size() == 1 ? " trap entity" : " trap entities"));
                }
            }
        }
    }

    @Override
    protected void onDisable() {
        this.seen.clear();
        this.lastLevel = null;
    }

    @Override
    public void onWorldChange() {
        this.seen.clear();
    }

    @Override
    public void onTick() {
        class_638 level = mc.field_1687;
        class_746 player = mc.field_1724;
        if (level == null || player == null) {
            this.seen.clear();
            return;
        }
        if (level != this.lastLevel) {
            this.lastLevel = level;
            this.seen.clear();
        }
        if (this.preventSpawn.get()) {
            for (class_1297 entity : new ArrayList<>(this.newTrapEntities(level))) {
                if (!entity.method_31481() && canRemove(player, entity)) {
                    entity.method_31472();
                }
            }
        }
        if (player.field_6012 % SWEEP_INTERVAL_TICKS == 0) {
            for (class_1297 entity : this.collectTrapEntities(level, player)) {
                entity.method_31472();
            }
            this.forgetGone(level);
        }
    }

    private List<class_1297> newTrapEntities(class_638 level) {
        List<class_1297> fresh = new ArrayList<>();
        for (class_1297 entity : level.method_18112()) {
            if (this.isTrapEntity(entity) && this.seen.add(entity.method_5628())) {
                fresh.add(entity);
            }
        }
        return fresh;
    }

    private void forgetGone(class_638 level) {
        this.seen.removeIf(id -> level.method_8469(id) == null);
    }

    private List<class_1297> collectTrapEntities(class_638 level, class_746 player) {
        List<class_1297> traps = new ArrayList<>();
        for (class_1297 entity : level.method_18112()) {
            if (this.isTrapEntity(entity) && canRemove(player, entity)) {
                traps.add(entity);
            }
        }
        return traps;
    }

    private boolean isTrapEntity(class_1297 entity) {
        if (entity == null) {
            return false;
        }
        class_1299<?> type = entity.method_5864();
        return this.armorStands.get() && type == class_1299.field_6131 || this.chestMinecarts.get() && type == class_1299.field_6126;
    }

    private static boolean canRemove(class_746 player, class_1297 entity) {
        if (entity == mc.method_1560()) {
            return false;
        }
        return player == null || entity != player && !entity.method_5821(player);
    }
}
