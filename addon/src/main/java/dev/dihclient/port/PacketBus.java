package dev.dihclient.port;

import dev.dihclient.DIHClient;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.class_2596;

/**
 * Packet events for the ported modules. DIH only knew "packet send"; the received packets come from {@code NetHandlerMixin}.
 * Three points in time, like in the Anubis client the modules come from:
 * <ul>
 *   <li>{@link #netty}: on the network thread, before the game sees the packet. May cancel it.</li>
 *   <li>{@link #received}: on a worker thread right after, for heavy work (the packet is never touched by the game thread).</li>
 *   <li>{@link #applied}: on the game thread after the game has handled the packet (login, chunk, block, section, light).</li>
 * </ul>
 * A listener that throws is logged once and skipped; it can never break the connection.
 */
public final class PacketBus {
    /** @return true to swallow the packet */
    public interface Netty {
        boolean onPacket(class_2596<?> packet);
    }

    public interface Listener {
        void onPacket(class_2596<?> packet);
    }

    private static final List<Netty> NETTY = new CopyOnWriteArrayList<>();
    private static final List<Listener> RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<Listener> APPLIED = new CopyOnWriteArrayList<>();
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8192), r -> {
                Thread t = new Thread(r, "DIHClient-PacketBus");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardPolicy());

    private PacketBus() {
    }

    public static void netty(Netty l) {
        NETTY.add(l);
    }

    public static void received(Listener l) {
        RECEIVED.add(l);
    }

    public static void applied(Listener l) {
        APPLIED.add(l);
    }

    public static void remove(Object l) {
        NETTY.remove(l);
        RECEIVED.remove(l);
        APPLIED.remove(l);
    }

    public static boolean wantsNetty() {
        return !NETTY.isEmpty();
    }

    public static boolean wantsReceived() {
        return !RECEIVED.isEmpty();
    }

    public static boolean wantsApplied() {
        return !APPLIED.isEmpty();
    }

    /** Called by the mixin on the network thread. @return true when a listener swallowed the packet */
    public static boolean dispatchNetty(class_2596<?> packet) {
        boolean cancel = false;
        for (Netty l : NETTY) {
            try {
                cancel |= l.onPacket(packet);
            } catch (Throwable t) {
                fail(l, t);
            }
        }
        return cancel;
    }

    /** Called by the mixin when the packet was not swallowed. Runs the listeners on the worker thread, only while the connection lives. */
    public static void dispatchReceived(class_2596<?> packet, BooleanSupplier connectionActive) {
        if (RECEIVED.isEmpty()) {
            return;
        }
        WORKER.execute(() -> {
            if (connectionActive != null && !connectionActive.getAsBoolean()) {
                return;
            }
            for (Listener l : RECEIVED) {
                try {
                    l.onPacket(packet);
                } catch (Throwable t) {
                    fail(l, t);
                }
            }
        });
    }

    /** Called by the mixin on the game thread after the game handled the packet. */
    public static void dispatchApplied(class_2596<?> packet) {
        for (Listener l : APPLIED) {
            try {
                l.onPacket(packet);
            } catch (Throwable t) {
                fail(l, t);
            }
        }
    }

    private static final java.util.Set<Object> FAILED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void fail(Object listener, Throwable t) {
        if (FAILED.add(listener.getClass())) {
            DIHClient.LOG.warn("[DIHClient] packet listener failed (reported once): " + listener.getClass().getName(), t);
        }
    }
}
