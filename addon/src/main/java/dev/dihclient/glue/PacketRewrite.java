package dev.dihclient.glue;

import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.movement.Flight;
import dev.dihclient.port.tools.Spinbot;
import net.minecraft.class_2596;
import net.minecraft.class_2828;

public final class PacketRewrite {
    private static Flight flight;

    private PacketRewrite() {
    }

    public static class_2596<?> apply(class_2596<?> packet) {
        if (!(packet instanceof class_2828 move)) {
            return packet;
        }
        Flight f = flight;
        if (f == null) {
            f = flight = ModuleManager.of(Flight.class);
        }
        class_2596<?> out = packet;
        if (f != null && f.spoofsGround() && !move.method_12273()) {
            out = withGround(move);
        }
        return Spinbot.rewrite(out);
    }

    private static class_2828 withGround(class_2828 m) {
        boolean collide = m.method_61225();
        if (m.method_36171() && m.method_36172()) {
            return new class_2828.class_2830(m.method_12269(0.0), m.method_12268(0.0), m.method_12274(0.0), m.method_12271(0.0F), m.method_12270(0.0F), true, collide);
        }
        if (m.method_36171()) {
            return new class_2828.class_2829(m.method_12269(0.0), m.method_12268(0.0), m.method_12274(0.0), true, collide);
        }
        if (m.method_36172()) {
            return new class_2828.class_2831(m.method_12271(0.0F), m.method_12270(0.0F), true, collide);
        }
        return new class_2828.class_5911(true, collide);
    }
}
