package dev.fakedonut;

import java.util.Random;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.class_1937;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeDonut implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("FakeDonut");

    private static int tick;
    private static final java.util.concurrent.ConcurrentLinkedQueue<Object[]> PENDING = new java.util.concurrent.ConcurrentLinkedQueue<>();

    @Override
    public void onInitialize() {
        Config.load();
        Economy.load();
        Bases.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> Commands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(Auction::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Auction.stop();
            Economy.save();
            Bases.save();
        });
        ServerChunkEvents.CHUNK_GENERATE.register((world, chunk) -> {
            if (world.method_27983() != class_1937.field_25179) {
                return;
            }
            PENDING.add(new Object[] {world, chunk.method_12004().field_9181, chunk.method_12004().field_9180});
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
            if (tick % 10 != 0 || !Config.get().antiXray) {
                return;
            }
            int r = Config.get().proximityRadius;
            for (class_3218 world : server.method_3738()) {
                if (world.method_27983() != class_1937.field_25179) {
                    continue;
                }
                for (class_3222 p : world.method_18456()) {
                    if (p.method_23318() > Config.get().hideBelowY + r + 1) {
                        continue;
                    }
                    AntiXray.reveal(p, world, p.method_31477(), p.method_31478(), p.method_31479(), r);
                    AntiXray.prune(p);
                }
            }
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(world instanceof class_3218 sw) || !Config.get().antiXray || world.method_27983() != class_1937.field_25179) {
                return;
            }
            int r = Config.get().breakRevealRadius;
            for (class_3222 p : sw.method_18456()) {
                if (p.method_24515().method_19771(pos, 24.0)) {
                    AntiXray.reveal(p, sw, pos.method_10263(), pos.method_10264(), pos.method_10260(), r);
                }
            }
        });
        LOG.info("FakeDonut loaded");
    }
}
