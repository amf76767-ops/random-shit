package dev.dihclient.port.chunks;

import dev.dihclient.DIHClient;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.class_2338;
import net.minecraft.class_2680;

/**
 * Block changes of the client world with the old and the new state (the original {@code BlockUpdateEvent}); fed by
 * {@code ClientWorldUpdateMixin} on the game thread. The position may be a mutable one: do not keep it.
 */
public final class BlockUpdates {
    @FunctionalInterface
    public interface Listener {
        void onUpdate(class_2338 pos, class_2680 oldState, class_2680 newState);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Set<Class<?>> FAILED = ConcurrentHashMap.newKeySet();

    private BlockUpdates() {
    }

    public static void add(Listener listener) {
        LISTENERS.add(listener);
    }

    public static void remove(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void dispatch(class_2338 pos, class_2680 oldState, class_2680 newState) {
        for (Listener l : LISTENERS) {
            try {
                l.onUpdate(pos, oldState, newState);
            } catch (Throwable t) {
                if (FAILED.add(l.getClass())) {
                    DIHClient.LOG.warn("[DIHClient] block update listener failed (reported once): " + l.getClass().getName(), t);
                }
            }
        }
    }
}
