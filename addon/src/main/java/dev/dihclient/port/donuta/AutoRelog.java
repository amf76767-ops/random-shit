package dev.dihclient.port.donuta;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import net.minecraft.class_1937;
import net.minecraft.class_310;
import net.minecraft.class_3928;
import net.minecraft.class_412;
import net.minecraft.class_437;
import net.minecraft.class_500;
import net.minecraft.class_638;
import net.minecraft.class_639;
import net.minecraft.class_642;
import net.minecraft.class_7134;
import net.minecraft.class_746;

/** Ported from an open-source client (GPL-3.0). */
public class AutoRelog extends Module implements ModuleManager.MenuTicking {
    public final IntSetting yLevel = this.integer("Y Level", "Relog when you go below this height (0 = deepslate level).", 0, -60, 16);
    public final DoubleSetting delay = this.dbl("Delay", "Seconds to wait on the server list before joining again.", 3.0, 1.0, 30.0, 0.5);

    private final AutoRelogLogic trigger = new AutoRelogLogic();
    private class_638 seenLevel;
    private class_746 seenPlayer;
    private class_642 rejoin;
    private boolean disconnected;
    private int rejoinTicks;

    public AutoRelog() {
        super("Auto Relog", Category.DONUT, "Relogs once when you go down to deepslate level.");
    }

    @Override
    protected void onEnable() {
        this.forget();
    }

    @Override
    protected void onDisable() {
        this.forget();
    }

    private void forget() {
        this.seenLevel = null;
        this.seenPlayer = null;
        this.trigger.reset();
        this.rejoin = null;
        this.disconnected = false;
    }

    @Override
    public void onTick() {
        class_310 game = mc;
        if (this.rejoin != null) {
            this.waitToRejoin(game);
            return;
        }
        class_746 player = game.field_1724;
        class_638 level = game.field_1687;
        if (player == null || level == null || game.field_1755 instanceof class_3928) {
            this.trigger.reset();
            return;
        }
        if (level != this.seenLevel || player != this.seenPlayer) {
            this.seenLevel = level;
            this.seenPlayer = player;
            this.trigger.reset();
        }
        if (!isOverworld(level)) {
            this.trigger.reset();
            return;
        }
        double y = player.method_23318();
        if (this.trigger.tick(y, this.yLevel.get())) {
            this.relog(game, y);
        }
    }

    private static boolean isOverworld(class_638 level) {
        return level.method_27983() == class_1937.field_25179 || level.method_40134().method_40225(class_7134.field_37666);
    }

    private void relog(class_310 game, double y) {
        class_642 server = game.method_1542() ? null : game.method_1558();
        if (server == null || server.method_52811()) {
            return;
        }
        DIHClient.LOG.info("[DIHClient] Auto Relog: reached Y {}, relogging", (int) Math.floor(y));
        this.rejoin = server;
        this.disconnected = false;
        this.rejoinTicks = Math.round((float) this.delay.get().doubleValue() * 20.0F);
        game.method_63588(() -> {
            try {
                if (this.rejoin == server && game.field_1687 != null) {
                    game.method_73360(class_638.field_61021);
                    this.disconnected = true;
                }
            } catch (Throwable t) {
                DIHClient.LOG.warn("[DIHClient] Auto Relog: leaving the server failed", t);
                this.rejoin = null;
            }
        });
    }

    private void waitToRejoin(class_310 game) {
        if (!this.disconnected) {
            return;
        }
        if (game.field_1687 != null || !(game.field_1755 instanceof class_500)) {
            this.rejoin = null;
            return;
        }
        if (--this.rejoinTicks > 0) {
            return;
        }
        class_642 server = this.rejoin;
        class_437 serverList = game.field_1755;
        this.rejoin = null;
        game.method_63588(() -> {
            try {
                if (game.field_1755 == serverList && game.field_1687 == null) {
                    class_412.method_36877(serverList, game, class_639.method_2950(server.field_3761), server, false, null);
                }
            } catch (Throwable t) {
                DIHClient.LOG.warn("[DIHClient] Auto Relog: joining again failed", t);
            }
        });
    }
}
