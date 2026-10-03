package dev.dihclient.port.crystal;

import dev.dihclient.DIHClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.class_1297;
import net.minecraft.class_310;

/**
 * Tells the crystal modules when the local player starts an attack (Anubis: PlayerAttackEntityEvent).
 * Fed by {@code CrystalAttackMixin}; a listener that throws is logged and skipped.
 */
public final class AttackHooks {
    public interface Listener {
        void onAttack(class_1297 target);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private AttackHooks() {
    }

    public static void add(Listener l) {
        if (!LISTENERS.contains(l)) {
            LISTENERS.add(l);
        }
    }

    public static void remove(Listener l) {
        LISTENERS.remove(l);
    }

    /** Called from the mixin for every PlayerEntity.attack; only the local player on the game thread counts. */
    public static void fire(Object attacker, class_1297 target) {
        if (LISTENERS.isEmpty() || target == null) {
            return;
        }
        class_310 mc = class_310.method_1551();
        if (attacker != mc.field_1724 || !mc.method_18854()) {
            return;
        }
        for (Listener l : LISTENERS) {
            try {
                l.onAttack(target);
            } catch (Throwable t) {
                DIHClient.LOG.warn("[DIHClient] attack listener failed", t);
            }
        }
    }
}
