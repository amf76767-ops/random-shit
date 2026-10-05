package dev.dihclient.port.staff;

import net.minecraft.class_1937;
import net.minecraft.class_310;
import net.minecraft.class_634;
import net.minecraft.class_638;
import net.minecraft.class_746;

/** Ported from an open-source client (GPL-3.0). */
public final class ShardTracker {
    private static final int OFF_MAP = Integer.MIN_VALUE;

    private class_638 level;
    private class_746 self;
    private String brand;
    private int goliath = OFF_MAP;

    public boolean moved(class_310 mc) {
        class_638 nowLevel = mc.field_1687;
        class_746 nowSelf = mc.field_1724;
        if (nowLevel == null || nowSelf == null) {
            this.clear();
            return false;
        }
        class_634 connection = mc.method_1562();
        String nowBrand = connection == null ? null : connection.method_52790();
        int nowGoliath = goliath(nowLevel, nowSelf);
        boolean moved = this.level != null
                && (nowLevel != this.level
                || nowSelf != this.self && !this.self.method_29504()
                || nowGoliath != this.goliath
                || nowBrand != null && this.brand != null && !nowBrand.equals(this.brand));
        this.level = nowLevel;
        this.self = nowSelf;
        this.brand = nowBrand;
        this.goliath = nowGoliath;
        return moved;
    }

    public void clear() {
        this.level = null;
        this.self = null;
        this.brand = null;
        this.goliath = OFF_MAP;
    }

    private static int goliath(class_638 level, class_746 self) {
        return level.method_27983() != class_1937.field_25179 ? OFF_MAP : GoliathMap.goliathAt(self.method_23317(), self.method_23321());
    }
}
