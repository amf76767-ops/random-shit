package dev.dihclient.port.crystal;

/** Ported from an open-source client (GPL-3.0). */
public final class CrystalMath {

    public static final double RADIUS = 12.0;

    public static final float SELF_SCALE = 1.15F;
    public static final float SELF_MARGIN = 2.0F;

    private CrystalMath() {
    }

    public static float falloff(double impact) {
        return (float) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
    }

    public static float difficulty(int id, float damage) {
        return switch (id) {
            case 0 -> 0.0F;
            case 1 -> Math.min(damage / 2.0F + 1.0F, damage);
            case 3 -> damage * 1.5F;
            default -> damage;
        };
    }

    public static float armor(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float effective = clamp(armor - damage / f, armor * 0.2F, 20.0F);
        return damage * (1.0F - effective / 25.0F);
    }

    public static float enchants(float damage, float protection) {
        return damage * (1.0F - clamp(protection, 0.0F, 20.0F) / 25.0F);
    }

    public static float resistance(int amplifier) {
        return Math.max(25 - (amplifier + 1) * 5, 0) / 25.0F;
    }

    public static double shieldAngle(double towardX, double towardZ, double facingX, double facingZ) {
        double length = Math.sqrt(towardX * towardX + towardZ * towardZ);

        double dot = length < 1.0E-5 ? 0.0 : (towardX / length) * facingX + (towardZ / length) * facingZ;
        return Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
    }

    public interface Shield {
        float blocked(float damage);
    }

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

    public static float upperBound(double distance, boolean player, int difficulty,
                                   float armor, float toughness, float resistance, float protection) {
        double d = distance / RADIUS;
        if (d > 1.0) {
            return 0.0F;
        }
        float damage = player ? difficulty(difficulty, falloff(1.0 - d)) : falloff(1.0 - d);
        return reduced(damage, armor, toughness, resistance, protection);
    }

    public static float reduced(float damage, float armor, float toughness, float resistance, float protection) {
        float afterArmor = armor(damage, armor, toughness);
        return enchants(afterArmor * resistance, protection);
    }

    public interface Sight {
        boolean clear(double x, double y, double z);
    }

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
