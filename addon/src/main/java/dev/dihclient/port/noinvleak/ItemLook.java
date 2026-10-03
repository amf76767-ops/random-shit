package dev.dihclient.port.noinvleak;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_10444;
import net.minecraft.class_10444.class_10445;
import net.minecraft.class_10444.class_10446;
import net.minecraft.class_1799;
import net.minecraft.class_310;
import net.minecraft.class_811;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * <p>
 * An item render state that also records what the model is made of ({@link #identity}): two stacks with the same identity look
 * the same in the GUI, so one rendered icon can serve both.
 */
final class ItemLook extends class_10444 {
    final List<Object> identity = new ArrayList<>();
    private final List<class_10446> layers = new ArrayList<>();

    @Override
    public void method_65605() {
        super.method_65605();
        this.identity.clear();
        this.layers.clear();
    }

    @Override
    public class_10446 method_65601() {
        class_10446 layer = super.method_65601();
        this.layers.add(layer);
        return layer;
    }

    @Override
    public void method_70946(Object element) {
        if (!(element instanceof class_10445)) {
            this.identity.add(element);
        }
    }

    boolean resolve(class_1799 stack) {
        class_310 mc = class_310.method_1551();
        try {
            mc.method_65386().method_65598(this, stack, class_811.field_4317, mc.field_1687, mc.field_1724, 0);
        } catch (RuntimeException e) {
            this.method_65605();
            return false;
        }
        for (class_10446 layer : this.layers) {
            layer.method_65615(class_10445.field_55341);
        }
        if (this.identity.isEmpty()) {
            this.identity.add(stack.method_7909());
        }
        return true;
    }
}
