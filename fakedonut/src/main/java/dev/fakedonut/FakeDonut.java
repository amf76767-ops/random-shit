package dev.fakedonut;

import java.util.Random;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.class_1937;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeDonut implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("FakeDonut");

    private static int tick;
    private static final java.util.Set<Long> SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ConcurrentLinkedQueue<Object[]> PENDING = new java.util.concurrent.ConcurrentLinkedQueue<>();

    @Override
    public void onInitialize() {
        Config.load();
        Economy.load();
        Bases.load();
        Stashes.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> Commands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(Auction::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Auction.stop();
            Economy.save();
            Bases.save();
        });
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world.method_27983() != class_1937.field_25179) {
                return;
            }
            int cx = chunk.method_12004().field_9181;
            int cz = chunk.method_12004().field_9180;
            if (SEEN.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL))) {
                PENDING.add(new Object[] {world, cx, cz});
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SEEN.clear();
            PENDING.clear();
            AntiXray.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            for (int n = 0; n < 2; n++) {
                Object[] job = PENDING.poll();
                if (job == null) {
                    break;
                }
                class_3218 w = (class_3218) job[0];
                int jx = (Integer) job[1];
                int jz = (Integer) job[2];
                if (w.method_8393(jx, jz)) {
                    long seed = (((long) jx << 32) ^ (jz & 0xFFFFFFFFL)) * 341873128712L ^ w.method_8412();
                    try {
                        BaseGen.tryChunk(w, w.method_8497(jx, jz), new Random(seed), false);
                    } catch (RuntimeException e) {
                        LOG.warn("base generation failed at chunk {} {}", jx, jz, e);
                    }
                } else if (PENDING.size() < 4096) {
                    PENDING.add(job);
                }
            }
            if (tick % 6000 == 0) {
                Auction.expire();
                Auction.restock();
            }
            if (tick % 10 != 0) {
                return;
            }
            for (class_3218 world : server.method_3738()) {
                if (world.method_27983() != class_1937.field_25179) {
                    continue;
                }
                for (class_3222 p : world.method_18456()) {
                    try {
                        AntiXray.tick(world, p);
                    } catch (RuntimeException e) {
                        LOG.warn("anti-xray tick failed", e);
                    }
                }
            }
        });
        LOG.info("FakeDonut loaded");
    }
}
