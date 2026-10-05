package dev.fakedonut;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1792;

public final class Prices {
    private static final Map<class_1792, Long> TABLE = new HashMap<>();

    static {
        put(Ids.DIAMOND, 200);
        put(Ids.NETHERITE_INGOT, 3000);
        put(Ids.NETHERITE_SCRAP, 700);
        put(Ids.EMERALD, 60);
        put(Ids.IRON_INGOT, 8);
        put(Ids.GOLD_INGOT, 14);
        put(Ids.COAL, 3);
        put(Ids.REDSTONE, 3);
        put(Ids.LAPIS_LAZULI, 4);
        put(Ids.COOKED_BEEF, 3);
        put(Ids.COOKED_PORKCHOP, 3);
        put(Ids.AMETHYST_SHARD, 8);
        put(Ids.ENDER_PEARL, 40);
        put(Ids.ARROW, 1);
        put(Ids.BLAZE_ROD, 25);
        put(Ids.EXPERIENCE_BOTTLE, 20);
    }

    private Prices() {
    }

    private static void put(class_1792 item, long price) {
        TABLE.put(item, price);
    }

    public static long unit(class_1792 item) {
        return TABLE.getOrDefault(item, 1L);
    }
}
