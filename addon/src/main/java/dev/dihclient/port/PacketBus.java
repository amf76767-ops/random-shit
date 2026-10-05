package dev.dihclient.port;

import dev.dihclient.DIHClient;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.class_2596;

public final class PacketBus {

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
