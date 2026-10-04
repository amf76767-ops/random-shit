package dev.dihclient.port.crystal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_10707;
import net.minecraft.class_1282;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1887;
import net.minecraft.class_1890;
import net.minecraft.class_1893;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_3726;
import net.minecraft.class_3959;
import net.minecraft.class_5134;
import net.minecraft.class_5321;
import net.minecraft.class_7924;
import net.minecraft.class_9334;
import net.minecraft.class_2338.class_2339;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

/**
 * Ported from an open-source client (GPL-3.0).
 * Explosion damage of an end crystal on a living entity as the game will deal it: blocks in the way (exposure),
 * armor, toughness, Resistance, Protection / Blast Protection, shield and difficulty. The numbers live in {@link CrystalMath}.
 */
public final class CrystalDamage {
    public static boolean ignoreTerrain;
    private static final class_1304[] ARMOR = new class_1304[]{class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166};

    private CrystalDamage() {
    }

    public static float estimate(class_243 center, class_1309 entity) {
        return new CrystalDamage.Victim(entity, class_243.field_1353).damage(center);
    }

    public static float health(class_1309 entity) {
        return entity.method_6032() + entity.method_6067();
    }

    public static boolean kills(class_1309 entity, float damage) {
        return entity.field_6008 <= 10 && damage >= health(entity);
    }

    public static boolean endangers(class_1657 self, float damage) {
        return !self.method_68878() && CrystalMath.endangers(damage, health(self));
    }

    private static float protection(class_1309 entity) {
        class_2378<class_1887> enchantments = entity.method_56673().method_30530(class_7924.field_41265);
        int total = 0;

        for (class_1304 slot : ARMOR) {
            class_1799 stack = entity.method_6118(slot);
            if (!stack.method_7960()) {
                total += level(enchantments, class_1893.field_9111, stack) + 2 * level(enchantments, class_1893.field_9107, stack);
            }
        }

        return total;
    }

    private static int level(class_2378<class_1887> enchantments, class_5321<class_1887> key, class_1799 stack) {
        return enchantments.method_46746(key).map(holder -> class_1890.method_8225(holder, stack)).orElse(0);
    }

        public static final class Victim {
        private final class_1309 entity;
        private final class_243 position;
        private final class_238 box;
        private final class_1282 source;
        private final float armor;
        private final float toughness;
        private final float protection;
        private final float resistance;
        private final class_10707 shield;
        private final boolean player;
        private final int difficulty;
        private final Map<class_243, Float> exposures = new HashMap<>();

        public Victim(class_1309 entity, class_243 motion) {
            class_243 allowed = motion.method_1027() == 0.0
                ? motion
                : class_1297.method_20736(entity, motion, entity.method_5829(), entity.method_73183(), List.of());
            this.entity = entity;
            this.position = entity.method_73189().method_1019(allowed);
            this.box = entity.method_5829().method_997(allowed);
            this.source = entity.method_48923().method_48819(null, null);
            this.armor = entity.method_6096();
            this.toughness = (float)entity.method_45325(class_5134.field_23725);
            this.protection = CrystalDamage.protection(entity);
            class_1293 effect = entity.method_6112(class_1294.field_5907);
            this.resistance = effect == null ? 1.0F : CrystalMath.resistance(effect.method_5578());
            this.player = entity instanceof class_1657;
            this.difficulty = entity.method_73183().method_8407().method_5461();
            class_1799 blocking = entity.method_62821();
            this.shield = blocking == null ? null : blocking.method_58694(class_9334.field_56396);
        }

        public class_1309 entity() {
            return this.entity;
        }

        public float damage(class_243 center) {
            return this.damage(center, false);
        }

        public float damage(class_243 center, boolean baseToCome) {
            double distance = Math.sqrt(this.position.method_1025(center));
            if (distance / CrystalMath.RADIUS > 1.0) {
                return 0.0F;
            }
            return CrystalMath.damage(distance, this.exposure(center, baseToCome), this.player, this.difficulty,
                    this.shield == null ? null : damage -> this.blocked(center, damage),
                    this.armor, this.toughness, this.resistance, this.protection);
        }

        private float blocked(class_243 center, float damage) {
            class_243 facing = this.entity.method_5631(0.0F, this.entity.method_5791());
            class_243 toward = center.method_1020(this.position);
            double angle = CrystalMath.shieldAngle(toward.field_1352, toward.field_1350, facing.field_1352, facing.field_1350);
            return this.shield.method_67202(this.source, damage, angle);
        }

        public float upperBound(class_243 center) {
            double distance = Math.sqrt(this.position.method_1025(center));
            return CrystalMath.upperBound(distance, this.player, this.difficulty, this.armor, this.toughness, this.resistance, this.protection);
        }

        public float exposure(class_243 center) {
            return this.exposure(center, false);
        }

        public float exposure(class_243 center, boolean baseToCome) {
            if (CrystalDamage.ignoreTerrain) {
                return 1.0F;
            } else {
                Float known = this.exposures.get(center);
                if (known != null) {
                    return known;
                } else {
                    class_238 base = baseToCome ? new class_238(class_2338.method_49637(center.field_1352, center.field_1351 - 0.5, center.field_1350)) : null;
                    float seen = !this.openAir(center) || base != null && this.hull(center).method_994(base) ? this.seenPercent(center, base) : 1.0F;
                    this.exposures.put(center, seen);
                    return seen;
                }
            }
        }

        private class_238 hull(class_243 center) {
            return this.box.method_991(new class_238(center, center));
        }

        private boolean openAir(class_243 center) {
            class_1937 level = this.entity.method_73183();
            class_3726 context = class_3726.method_16195(this.entity);
            int minX = class_3532.method_15357(Math.min(this.box.field_1323, center.field_1352));
            int minY = class_3532.method_15357(Math.min(this.box.field_1322, center.field_1351));
            int minZ = class_3532.method_15357(Math.min(this.box.field_1321, center.field_1350));
            int maxX = class_3532.method_15357(Math.max(this.box.field_1320, center.field_1352));
            int maxY = class_3532.method_15357(Math.max(this.box.field_1325, center.field_1351));
            int maxZ = class_3532.method_15357(Math.max(this.box.field_1324, center.field_1350));
            class_2339 pos = new class_2339();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        pos.method_10103(x, y, z);
                        if (!level.method_8320(pos).method_26194(level, pos, context).method_1110()) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }

        private float seenPercent(class_243 center, class_238 base) {
            class_1937 level = this.entity.method_73183();
            return CrystalMath.seen(this.box.field_1323, this.box.field_1322, this.box.field_1321, this.box.field_1320, this.box.field_1325, this.box.field_1324, (x, y, z) -> {
                class_243 from = new class_243(x, y, z);
                return level.method_17742(new class_3959(from, center, class_3960.field_17558, class_242.field_1348, this.entity)).method_17783()
                        == class_240.field_1333
                        && (base == null || !throughBlock(base, from, center));
            });
        }

        private static boolean throughBlock(class_238 block, class_243 from, class_243 to) {
            class_243 end = new class_243(
                class_3532.method_16436(1.0E-7, to.field_1352, from.field_1352),
                class_3532.method_16436(1.0E-7, to.field_1351, from.field_1351),
                class_3532.method_16436(1.0E-7, to.field_1350, from.field_1350)
            );
            return block.method_1006(from) || block.method_992(from, end).isPresent();
        }
    }
}
