package dev.fakedonut;

import java.util.List;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_9290;
import net.minecraft.class_9334;

public final class Items2 {
    private Items2() {
    }

    public static class_1799 stack(class_1792 item, int count) {
        return new class_1799(item, count);
    }

    public static class_2561 text(String s) {
        return class_2561.method_43470(s);
    }

    public static class_1799 named(class_1792 item, String name, String... lore) {
        class_1799 s = new class_1799(item, 1);
        s.method_57379(class_9334.field_49631, text(name));
        if (lore.length > 0) {
            s.method_57379(class_9334.field_49632, new class_9290(java.util.Arrays.stream(lore).map(Items2::text).toList()));
        }
        return s;
    }

    public static class_1799 withLore(class_1799 stack, List<String> extra) {
        class_1799 copy = stack.method_7972();
        class_9290 old = copy.method_58694(class_9334.field_49632);
        java.util.ArrayList<class_2561> lines = new java.util.ArrayList<>();
        if (old != null) {
            lines.addAll(old.comp_2400());
        }
        for (String e : extra) {
            lines.add(text(e));
        }
        copy.method_57379(class_9334.field_49632, new class_9290(lines));
        return copy;
    }
}
