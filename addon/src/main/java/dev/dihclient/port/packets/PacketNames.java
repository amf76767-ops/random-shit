package dev.dihclient.port.packets;

import dev.dihclient.DIHClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_2596;
import net.minecraft.class_2960;

/**
 * Ported from a Meteor addon.
 * Readable packet names and field lists. The game runs with intermediary names at run time (the packet class is
 * {@code class_2626}), so the name comes from the packet type id the packet itself reports ("block_update"). The
 * accessor that returns it is found by its shape (a record that holds an {@link class_2960}), not by a mapped name.
 */
public final class PacketNames {
    private static final Map<Class<?>, String> NAMES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> FIELDS = new ConcurrentHashMap<>();
    private static final Method TYPE_GETTER = findTypeGetter();

    private PacketNames() {
    }

    private static Method findTypeGetter() {
        try {
            for (Method m : class_2596.class.getMethods()) {
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers()) || !m.getReturnType().isRecord()) {
                    continue;
                }
                for (RecordComponent c : m.getReturnType().getRecordComponents()) {
                    if (c.getType() == class_2960.class) {
                        return m;
                    }
                }
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Packet Log: packet type lookup failed, class names are used", t);
        }
        return null;
    }

    /** The packet id without the namespace ("block_update"), or the class name when the packet does not tell. Never null. */
    public static String name(class_2596<?> packet) {
        Class<?> type = packet.getClass();
        String cached = NAMES.get(type);
        if (cached != null) {
            return cached;
        }
        String name = null;
        if (TYPE_GETTER != null) {
            try {
                Object packetType = TYPE_GETTER.invoke(packet);
                for (RecordComponent c : packetType.getClass().getRecordComponents()) {
                    if (c.getType() == class_2960.class) {
                        String id = String.valueOf(c.getAccessor().invoke(packetType));
                        name = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                // falls back to the class name below
            }
        }
        if (name == null) {
            name = type.getSimpleName();
        }
        NAMES.put(type, name);
        return name;
    }

    /** " {field=value, ...}" with every value cut to {@code maxLength}; empty when the packet has no fields. */
    public static String describe(class_2596<?> packet, int maxLength) {
        Field[] fields = FIELDS.computeIfAbsent(packet.getClass(), PacketNames::instanceFields);
        StringBuilder sb = new StringBuilder();
        for (Field field : fields) {
            String value;
            try {
                value = String.valueOf(field.get(packet));
            } catch (Throwable t) {
                value = "<unreadable>";
            }
            sb.append(sb.length() == 0 ? " {" : ", ").append(field.getName()).append('=').append(PacketFilter.cut(value, maxLength));
        }
        return sb.length() == 0 ? "" : sb.append('}').toString();
    }

    private static Field[] instanceFields(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                .peek(f -> {
                    try {
                        f.setAccessible(true);
                    } catch (Throwable ignored) {
                        // read fails soft in describe()
                    }
                })
                .toArray(Field[]::new);
    }
}
