package dev.dihclient.account;

import dev.dihclient.DIHClient;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;

/**
 * Puts another login into the running game. Minecraft 1.21 keeps more than the session: the chat signing keys, the
 * report context and the social interactions are made from the session once at start. A server checks the chat key
 * against the account's UUID and kicks on a mismatch, so they are made again for the new account. The first
 * swap remembers what the game started with; {@link #restore()} puts it back.
 *
 * Everything is found by name through reflection and every step has its own try, so a change in a later game version
 * costs one feature (the message says which), not the game.
 */
public final class SessionApplier {
    // session, user api service, chat signing keys, report context, social interactions
    private static final String[] FIELDS = {"field_1726", "field_26902", "field_39068", "field_39492", "field_41331"};
    private static Object[] original;

    private SessionApplier() {
    }

    /** @return null when everything worked, else a short text for the player */
    public static synchronized String apply(String name, UUID uuid, String token, String xuid, boolean offline) {
        class_310 mc = class_310.method_1551();
        List<String> problems = new ArrayList<>();
        try {
            remember(mc);
            Object user = newUser(name, uuid, token, xuid);
            set(mc, FIELDS[0], user);
            Object api;
            try {
                api = offline ? offlineApi() : onlineApi(mc, token);
                set(mc, FIELDS[1], api);
            } catch (Throwable t) {
                problems.add("user service");
                DIHClient.LOG.warn("[DIHClient] accounts: user service not replaced", t);
                return summary(problems);
            }
            try {
                Class<?> keys = Class.forName("net.minecraft.class_7853");
                Object value = offline ? keys.getField("field_40800").get(null)
                        : call(keys, "method_46532", null, api, user, gameDir());
                set(mc, FIELDS[2], value);
            } catch (Throwable t) {
                problems.add("chat keys");
                DIHClient.LOG.warn("[DIHClient] accounts: chat keys not replaced", t);
            }
            try {
                Object environment = call(Class.forName("net.minecraft.class_7569"), "method_44586", null);
                set(mc, FIELDS[3], call(Class.forName("net.minecraft.class_7574"), "method_44599", null, environment, api));
            } catch (Throwable t) {
                problems.add("report context");
                DIHClient.LOG.warn("[DIHClient] accounts: report context not replaced", t);
            }
            try {
                Class<?> social = Class.forName("net.minecraft.class_6628");
                Object fresh = null;
                for (Constructor<?> c : social.getDeclaredConstructors()) {
                    if (c.getParameterCount() == 3) {
                        c.setAccessible(true);
                        fresh = c.newInstance(mc, api, user);
                    }
                }
                if (fresh == null) {
                    throw new NoSuchMethodException("social interactions constructor");
                }
                Object old = get(mc, FIELDS[4]);
                set(mc, FIELDS[4], fresh);
                if (old instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } catch (Throwable t) {
                problems.add("social interactions");
                DIHClient.LOG.warn("[DIHClient] accounts: social interactions not replaced", t);
            }
            return summary(problems);
        } catch (Throwable t) {
            DIHClient.LOG.error("[DIHClient] could not set session", t);
            return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }
    }

    /** Back to the account the game was started with. @return null on success, else a text; "nothing to restore" when never swapped */
    public static synchronized String restore() {
        if (original == null) {
            return "Already on the original account";
        }
        class_310 mc = class_310.method_1551();
        try {
            for (int i = FIELDS.length - 1; i >= 0; i--) {
                if (original[i] != null) {
                    Object current = get(mc, FIELDS[i]);
                    set(mc, FIELDS[i], original[i]);
                    if (i == 4 && current != original[i] && current instanceof AutoCloseable closeable) {
                        closeable.close();
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            DIHClient.LOG.error("[DIHClient] could not restore session", t);
            return t.getClass().getSimpleName();
        }
    }

    private static String summary(List<String> problems) {
        return problems.isEmpty() ? null : "Logged in, but not renewed: " + String.join(", ", problems) + " (some servers may kick you)";
    }

    private static void remember(class_310 mc) throws ReflectiveOperationException {
        if (original == null) {
            Object[] values = new Object[FIELDS.length];
            for (int i = 0; i < FIELDS.length; i++) {
                values[i] = get(mc, FIELDS[i]);
            }
            original = values;
        }
    }

    private static Object newUser(String name, UUID uuid, String token, String xuid) throws ReflectiveOperationException {
        Class<?> user = Class.forName("net.minecraft.class_320");
        for (Constructor<?> c : user.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length >= 5 && p[0] == String.class && p[1] == UUID.class && p[2] == String.class
                    && p[3] == Optional.class && p[4] == Optional.class) {
                c.setAccessible(true);
                if (p.length == 5) {
                    return c.newInstance(name, uuid, token, Optional.ofNullable(xuid), Optional.empty());
                }
                if (p.length == 6 && p[5].isEnum()) { // older versions carry an account type as well
                    Object[] types = p[5].getEnumConstants();
                    Object type = types[types.length - 1];
                    for (Object t : types) {
                        if ("MSA".equals(((Enum<?>) t).name())) {
                            type = t;
                        }
                    }
                    return c.newInstance(name, uuid, token, Optional.ofNullable(xuid), Optional.empty(), type);
                }
            }
        }
        throw new NoSuchMethodException("session constructor not found in this version");
    }

    private static Object offlineApi() throws ReflectiveOperationException {
        return Class.forName("com.mojang.authlib.minecraft.UserApiService").getField("OFFLINE").get(null);
    }

    private static Object onlineApi(class_310 mc, String token) throws ReflectiveOperationException {
        Proxy proxy = (Proxy) get(mc, "field_1739");
        Object service = Class.forName("com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService")
                .getConstructor(Proxy.class).newInstance(proxy);
        return service.getClass().getMethod("createUserApiService", String.class).invoke(service, token);
    }

    private static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    private static Field field(Object owner, String name) throws NoSuchFieldException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // look in the super class
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object get(Object owner, String name) throws ReflectiveOperationException {
        return field(owner, name).get(owner);
    }

    /** Final instance fields can be written once they are made accessible. */
    private static void set(Object owner, String name, Object value) throws ReflectiveOperationException {
        field(owner, name).set(owner, value);
    }

    private static Object call(Class<?> type, String name, Object receiver, Object... args) throws ReflectiveOperationException {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(receiver, args);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
