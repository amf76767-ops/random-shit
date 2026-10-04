package dev.dihclient.port.vanish;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Ported from an open-source client (GPL-3.0) (ShardTracker).
 * Notices that "where you are" changed (other world object, new player object after a respawn-less transfer, other server
 * brand), so the module forgets everything it learned: tab list and entity knowledge of the old place is worthless.
 * The Donut-only part (which Goliath map region you stand in) is dropped.
 */
public final class VanishContext {
    private Object level;
    private Object self;
    private String brand;

    /**
     * @param nowLevel world object (null when not in a world)
     * @param nowSelf  own player object
     * @param isDead asked about the PREVIOUS own player object (a respawn creates a new object, that is no move)
     * @param nowBrand server brand string, may be null while unknown
     * @return true when the place changed since the previous call
     */
    public boolean moved(Object nowLevel, Object nowSelf, Predicate<Object> isDead, String nowBrand) {
        if (nowLevel == null || nowSelf == null) {
            clear();
            return false;
        }
        boolean moved = this.level != null
                && (nowLevel != this.level
                || nowSelf != this.self && !isDead.test(this.self)
                || nowBrand != null && this.brand != null && !Objects.equals(nowBrand, this.brand));
        this.level = nowLevel;
        this.self = nowSelf;
        this.brand = nowBrand;
        return moved;
    }

    public void clear() {
        this.level = null;
        this.self = null;
        this.brand = null;
    }
}
