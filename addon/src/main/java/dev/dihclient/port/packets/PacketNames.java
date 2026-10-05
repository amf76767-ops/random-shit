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

            }
        }
        if (name == null) {
            name = type.getSimpleName();
        }
        NAMES.put(type, name);
        return name;
    }

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

                    }
                })
                .toArray(Field[]::new);
    }
}
