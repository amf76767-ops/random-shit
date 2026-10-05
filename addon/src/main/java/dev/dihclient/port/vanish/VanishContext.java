package dev.dihclient.port.vanish;

import java.util.Objects;
import java.util.function.Predicate;

/** Ported from an open-source client (GPL-3.0). */
public final class VanishContext {
    private Object level;
    private Object self;
    private String brand;

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
