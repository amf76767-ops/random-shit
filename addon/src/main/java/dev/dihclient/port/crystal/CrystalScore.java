package dev.dihclient.port.crystal;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * The judgement of Crystal Aura on one blast spot: is it safe for you and your friends, is it worth it, and how good is it.
 * Pure numbers; the module reads the damage with {@link CrystalDamage} and fills in the settings.
 */
public final class CrystalScore {
    /** Health at or below which a target is face-placed (when the setting is on). */
    public static final float FACE_PLACE_HEALTH = 10.0F;
    /** Rank bonus for a spot that kills (or forces a pop): it beats anything that does not. */
    public static final float LETHAL_BONUS = 20.0F;

    /** The settings that matter here. Mutable so the module can refresh one object per tick. */
    public static final class Config {
        public float minDamage = 4.0F;
        public float maxSelfDamage = 8.0F;
        public boolean facePlace = true;
        public boolean safeMode;
        public float minPlaceRatio = 1.4F;
        public float minExplode = 2.5F;
        public float maxExplode = 9.0F;
        public float minExplodeRatio = 1.1F;
        public float forcePop = 1.0F;
        public float antiSelfPop = 1.0F;
        public float antiFriendPop = 1.0F;
        public float maxFriendPlace = 8.0F;
        public float maxFriendExplode = 12.0F;
        public float minFriendRatio = 2.0F;
    }

    private CrystalScore() {
    }

    /** Least damage that is worth a crystal against a target with this health. */
    public static float minimumWorthwhile(Config c, float health) {
        return c.facePlace && health <= FACE_PLACE_HEALTH ? 0.0F : Math.min(c.minDamage, health);
    }

    /** Best the spot can do to this target (full exposure), or -infinity when even that is not worth it. */
    public static float bound(Config c, float upper, float targetHealth) {
        if (upper < minimumWorthwhile(c, targetHealth)) {
            return Float.NEGATIVE_INFINITY;
        }
        boolean mayKill = upper >= targetHealth;
        return upper + (mayKill ? LETHAL_BONUS : 0.0F);
    }

    /** The blast would (nearly) kill you: stop before looking at anybody else. */
    public static boolean selfBlocked(Config c, float selfDamage, float selfHealth, boolean selfCreative, boolean selfEndangered) {
        return (!selfCreative && c.antiSelfPop > 0.0F && selfDamage * c.antiSelfPop >= selfHealth) || selfEndangered;
    }

    /** The blast would kill the worst-hit friend ({@code friendHealth} is -1 when there is no friend in range). */
    public static boolean friendBlocked(Config c, float friendDamage, float friendHealth) {
        return friendHealth >= 0.0F && c.antiFriendPop > 0.0F && friendDamage * c.antiFriendPop >= friendHealth;
    }

    /**
     * Rank of a spot that passed {@link #selfBlocked} and {@link #friendBlocked}, or -infinity when it must not be used.
     *
     * @param breaking      true when the spot is an existing crystal that would be hit, false when one is to be placed
     * @param bestDamage    damage to the worst-hit enemy, -infinity when there is no enemy
     * @param kills         the hit takes the enemy's last health
     * @param toBeat        the rank to beat: lower ranks give -infinity
     */
    public static float rank(Config c, boolean breaking, float selfDamage, float friendDamage, float bestDamage, float bestHealth,
                             boolean kills, float toBeat) {
        if (bestDamage < 0.0F) {
            return Float.NEGATIVE_INFINITY;
        }
        float maxSelf = breaking ? c.maxExplode : c.maxSelfDamage;
        float maxFriend = breaking ? c.maxFriendExplode : c.maxFriendPlace;
        float minRatio = breaking ? c.minExplodeRatio : c.minPlaceRatio;
        boolean force = c.forcePop > 0.0F && bestDamage * c.forcePop >= bestHealth;
        if (!force && selfDamage > maxSelf && selfDamage >= bestDamage) {
            return Float.NEGATIVE_INFINITY;
        }
        if (!force && friendDamage > maxFriend && friendDamage >= bestDamage) {
            return Float.NEGATIVE_INFINITY;
        }
        float minimum = breaking ? c.minExplode : minimumWorthwhile(c, bestHealth);
        if (!force && bestDamage < minimum && !kills) {
            return Float.NEGATIVE_INFINITY;
        }
        float rank = c.safeMode ? bestDamage / Math.max(selfDamage, 0.01F) : bestDamage;
        if (!force && selfDamage > 0.0F && bestDamage / selfDamage < minRatio) {
            rank *= bestDamage / selfDamage / minRatio;
        }
        if (!force && friendDamage > 0.0F && bestDamage / friendDamage < c.minFriendRatio) {
            rank *= bestDamage / friendDamage / c.minFriendRatio;
        }
        if (kills || force) {
            rank += LETHAL_BONUS;
        }
        return rank > toBeat ? rank : Float.NEGATIVE_INFINITY;
    }

    /** All three stages in one call (what the module does, stage by stage, so it can skip the expensive damage reads). */
    public static float rank(Config c, boolean breaking, float selfDamage, float selfHealth, boolean selfCreative, boolean selfEndangered,
                             float friendDamage, float friendHealth, float bestDamage, float bestHealth, boolean kills, float toBeat) {
        if (selfBlocked(c, selfDamage, selfHealth, selfCreative, selfEndangered) || friendBlocked(c, friendDamage, friendHealth)) {
            return Float.NEGATIVE_INFINITY;
        }
        return rank(c, breaking, selfDamage, friendDamage, bestDamage, bestHealth, kills, toBeat);
    }
}
