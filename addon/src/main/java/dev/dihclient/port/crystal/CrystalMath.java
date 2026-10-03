package dev.dihclient.port.crystal;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * The pure numbers of end crystal damage: falloff, difficulty, armor, resistance, enchants, shield angle and the
 * sight sampling of the exposure. No game classes in here, so it can be tested on its own ({@code CrystalAuraTests}).
 * Armor and enchant formulas are the ones of {@code DamageUtil} (1.21.11) for a source without weapon, which an
 * explosion always is.
 */
public final class CrystalMath {
    /** Blast radius 2 * 6 (power 6): damage reaches this far. */
    public static final double RADIUS = 12.0;
    /** Anti self-damage: the real hit may be a bit stronger than the estimate, plus a fixed margin of 2 (one heart). */
    public static final float SELF_SCALE = 1.15F;
    public static final float SELF_MARGIN = 2.0F;

    private CrystalMath() {
    }

    /** Damage before difficulty and armor, for an impact of (1 - distance / radius) * exposure. */
    public static float falloff(double impact) {
        return (float) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
    }

    /** Players only: peaceful 0, easy damage / 2 + 1 (never more than the damage), normal as is, hard x 1.5. Id is Difficulty#getId. */
    public static float difficulty(int id, float damage) {
        return switch (id) {
            case 0 -> 0.0F;
            case 1 -> Math.min(damage / 2.0F + 1.0F, damage);
            case 3 -> damage * 1.5F;
            default -> damage;
        };
    }

    /** DamageUtil.getDamageLeft for a source without weapon. */
    public static float armor(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float effective = clamp(armor - damage / f, armor * 0.2F, 20.0F);
        return damage * (1.0F - effective / 25.0F);
    }

    /** DamageUtil.getInflictedDamage: the enchantment protection points (capped at 20) take 4 % each. */
    public static float enchants(float damage, float protection) {
        return damage * (1.0F - clamp(protection, 0.0F, 20.0F) / 25.0F);
    }

    /** Share of damage a Resistance effect lets through; amplifier 0 is Resistance I. No effect: 1. */
    public static float resistance(int amplifier) {
        return Math.max(25 - (amplifier + 1) * 5, 0) / 25.0F;
    }

    /** Angle between the flat direction to the blast and where the shield holder looks. */
    public static double shieldAngle(double towardX, double towardZ, double facingX, double facingZ) {
        double length = Math.sqrt(towardX * towardX + towardZ * towardZ);
        // Vec3d#normalize turns a (nearly) zero vector into zero, so the dot product is 0 then
        double dot = length < 1.0E-5 ? 0.0 : (towardX / length) * facingX + (towardZ / length) * facingZ;
        return Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
    }

    /** The shield part: how much damage the shield takes away for a given damage and angle (0 when there is none). */
    public interface Shield {
        float blocked(float damage);
    }

    /**
     * Full damage of a blast.
     *
     * @param distance  distance from the blast to the victim
     * @param exposure  share of the body the blast can see, 0..1
     * @param player    only players are scaled by the difficulty
     */
    public static float damage(double distance, double exposure, boolean player, int difficulty, Shield shield,
                               float armor, float toughness, float resistance, float protection) {
        double d = distance / RADIUS;
        if (d > 1.0) {
            return 0.0F;
        }
        float damage = player ? difficulty(difficulty, falloff((1.0 - d) * exposure)) : falloff((1.0 - d) * exposure);
        float left = damage - (shield == null || damage <= 0.0F ? 0.0F : shield.blocked(damage));
        return reduced(left, armor, toughness, resistance, protection);
    }

    /** Most damage a blast at this distance can do (full exposure, no shield): used to skip hopeless spots quickly. */
    public static float upperBound(double distance, boolean player, int difficulty,
                                   float armor, float toughness, float resistance, float protection) {
        double d = distance / RADIUS;
        if (d > 1.0) {
            return 0.0F;
        }
        float damage = player ? difficulty(difficulty, falloff(1.0 - d)) : falloff(1.0 - d);
        return reduced(damage, armor, toughness, resistance, protection);
    }

    /** Armor, then Resistance, then enchantments: the order of the game. */
    public static float reduced(float damage, float armor, float toughness, float resistance, float protection) {
        float afterArmor = armor(damage, armor, toughness);
        return enchants(afterArmor * resistance, protection);
    }

    /** Asked for every sample point of the body: can a ray from here reach the blast? */
    public interface Sight {
        boolean clear(double x, double y, double z);
    }

    /**
     * The sampling grid of the game's exposure: a regular grid over the hitbox whose cell size is 1 / (2 * size + 1),
     * centred on the box in x and z. Returns seen / total.
     */
    public static float seen(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Sight sight) {
        double stepX = 1.0 / ((maxX - minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((maxY - minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((maxZ - minZ) * 2.0 + 1.0);
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
            return 0.0F;
        }
        int seen = 0;
        int total = 0;
        for (double fx = 0.0; fx <= 1.0; fx += stepX) {
            for (double fy = 0.0; fy <= 1.0; fy += stepY) {
                for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                    double x = lerp(fx, minX, maxX) + offsetX;
                    double y = lerp(fy, minY, maxY);
                    double z = lerp(fz, minZ, maxZ) + offsetZ;
                    if (sight.clear(x, y, z)) {
                        seen++;
                    }
                    total++;
                }
            }
        }
        return (float) seen / total;
    }

    /** True when the self damage would kill or nearly kill (the estimate may be off by a bit). */
    public static boolean endangers(float damage, float health) {
        return damage * SELF_SCALE + SELF_MARGIN >= health;
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
