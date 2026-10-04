package dev.dihclient.port.tools;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.render.Freecam;
import dev.dihclient.modules.render.FreeLook;
import dev.dihclient.port.tools.SpawnerProtectLogic.Deposit;
import dev.dihclient.port.tools.SpawnerProtectLogic.State;
import dev.dihclient.port.tools.SpawnerProtectLogic.Work;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.setting.StringSetting;
import dev.dihclient.util.HumanAim;
import dev.dihclient.util.InvUtil;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.Notifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_1707;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2338.class_2339;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2480;
import net.minecraft.class_2561;
import net.minecraft.class_2626;
import net.minecraft.class_2637;
import net.minecraft.class_2680;
import net.minecraft.class_304;
import net.minecraft.class_315;
import net.minecraft.class_3965;
import net.minecraft.class_437;
import net.minecraft.class_465;
import net.minecraft.class_634;
import net.minecraft.class_636;
import net.minecraft.class_638;
import net.minecraft.class_742;
import net.minecraft.class_746;

/**
 * Ported from an open-source client (GPL-3.0).
 * Mines your spawners into an ender chest when blocks start breaking around you: the break packets of the server are
 * counted, and enough of them far from you (and not from a whitelisted player) set it off. Then it walks to the spawners,
 * mines them, picks them up, puts them into an ender chest (placing or buying one, /shop is the shop of the server this
 * was made for) and finally logs out.
 *
 * While a run is going the module owns the movement keys, the head and the attack button, so you cannot disturb it.
 * The decisions live in {@link SpawnerProtectLogic}; the packet hooks are in SpawnerProtectPacketMixin, the attack and mouse
 * locks in SpawnerProtectAttackMixin and SpawnerProtectMouseMixin.
 */
public class SpawnerProtect extends Module {
    /** The running instance for the mixins, null while the module is off. */
    public static volatile SpawnerProtect active;

    private static final int SCAN_RADIUS = 16;
    private static final double RETARGET_DIST_SQR = 256.0;
    private static final int CHEST_SEARCH_RADIUS = 4;
    private static final double CHEST_ARRIVE_DISTANCE = 4.0;
    private static final int BREAK_COOLDOWN_TICKS = 6;
    private static final int RIGHT_CLICK_AFTER_TICKS = 60;
    private static final int CHEST_OPEN_RETRY_TICKS = 12;
    private static final double WHITELIST_RADIUS = 8.0;
    private static final double OWN_BREAK_RADIUS = 6.0;
    private static final String DISCONNECT_REASON = "[SpawnerProtect] Task finished, logged out safely.";

    public final IntSetting targetStackCount = this.integer("Stack To Deposit", "Stacks (64) of spawners to carry before they go into the ender chest.", 3, 1, 30);
    public final IntSetting scanRange = this.integer("Trigger Range", "Block breaks farther away than this (blocks, horizontal) are ignored.", 64, 16, 128);
    public final IntSetting rotationSpeed = this.integer("Rotation Speed", "Fastest head turn while it works, degrees per tick.", 15, 1, 30);
    public final BoolSetting detectBlockUpdates = this.bool("Detect Block Updates", "Watches the server's block updates for someone breaking blocks near you. Without it nothing starts the run.", true);
    public final StringSetting whitelist = this.text("Whitelist", "Players whose block breaking is ignored when they are close to it. Separate names with commas.", "", 512);
    public final DoubleSetting breakRange = this.dbl("Break Range", "How close the spawner has to be before it is mined, in blocks.", 5.5, 1.0, 8.0, 0.5);
    public final IntSetting blockUpdateThreshold = this.integer("Block Update Threshold", "How many far block breaks inside the window start the run.", 5, 1, 20);
    public final IntSetting blockUpdateWindow = this.integer("Block Update Window", "Seconds in which the breaks are counted.", 60, 1, 600);

    private final SpawnerProtectLogic.BreakCounter breaks = new SpawnerProtectLogic.BreakCounter();
    private State currentState = State.WAITING_FOR_STRANGER;
    private class_2338 targetBlock;
    private class_2338 targetChest;
    private int chestTick;
    private int breakCooldown;
    private int lagWaitTicks;
    private boolean strangerDetected;
    private int shopSequence;
    private int shopTick;
    private int miningTicks;
    private boolean forwardHeld;
    private boolean sneakHeld;
    private boolean keysOwned;
    private boolean serverMenuSeen;
    private boolean spawnerScanEmptyThisTick;
    private String whitelistRaw;
    private Set<String> whitelistNames = Set.of();
    private int failures;

    public SpawnerProtect() {
        super("Spawner Protect", Category.DONUT, "Mines your spawners into an ender chest when someone gets close.");
    }

    @Override
    protected void onEnable() {
        this.resetModule();
        this.serverMenuSeen = false;
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
        this.serverMenuSeen = false;
        this.forwardHeld = false;
        this.sneakHeld = false;
        this.currentState = State.WAITING_FOR_STRANGER;
        this.releaseKeys();
        if (mc.field_1724 != null) {
            mc.field_1724.method_5660(false);
        }
        class_636 gameMode = mc.field_1761;
        if (mc.field_1724 != null && gameMode != null && gameMode.method_2923()) {
            gameMode.method_2925();
        }
    }

    @Override
    public void onWorldChange() {
        // new world = new player, targets and the open menu of the old one mean nothing
        if (this.isEnabled()) {
            this.resetModule();
            this.releaseKeys();
        }
    }

    @Override
    public String getInfo() {
        return this.isDriving() ? this.currentState.name().toLowerCase(Locale.ROOT).replace('_', ' ') : null;
    }

    private void resetModule() {
        this.currentState = State.WAITING_FOR_STRANGER;
        this.strangerDetected = false;
        this.lagWaitTicks = 0;
        this.breaks.reset();
        this.resetState();
        this.forwardHeld = false;
        this.sneakHeld = false;
    }

    private void resetState() {
        this.targetChest = null;
        this.targetBlock = null;
        this.chestTick = 0;
        this.breakCooldown = 0;
        this.miningTicks = 0;
    }

    /** A run is going: the module owns keys, head and attack button. */
    public boolean isDriving() {
        return this.isEnabled() && this.currentState != State.WAITING_FOR_STRANGER;
    }

    /** Used by the mixin that stops mouse look. Free look in camera mode leaves the player's head alone anyway. */
    public boolean blocksMouseTurn() {
        if (!this.isDriving() || ModuleManager.on(Freecam.class)) {
            return false;
        }
        FreeLook look = FreeLook.active();
        return look == null || look.mode.get() == FreeLook.Mode.PLAYER;
    }

    public boolean blocksVanillaAttack() {
        return this.isDriving();
    }

    // ---------------------------------------------------------------- block updates

    /** Called by the mixin on the game thread just before the game applies a single block update. */
    public void onBlockUpdatePacket(class_2626 packet) {
        if (this.canDetect()) {
            this.checkBlockUpdate(packet.method_11309(), packet.method_11308());
        }
    }

    public void onSectionBlocksUpdatePacket(class_2637 packet) {
        if (this.canDetect()) {
            packet.method_30621(this::checkBlockUpdate);
        }
    }

    private boolean canDetect() {
        return !this.strangerDetected && mc.field_1724 != null && mc.field_1687 != null
            && !SpawnerProtectLogic.nearSpawn(mc.field_1724.method_23317(), mc.field_1724.method_23321())
            && this.detectBlockUpdates.get();
    }

    private void checkBlockUpdate(class_2338 pos, class_2680 newState) {
        class_638 level = mc.field_1687;
        class_746 player = mc.field_1724;
        if (this.strangerDetected || level == null || player == null) {
            return;
        }
        try {
            class_243 center = class_243.method_24953(pos);
            for (class_742 other : level.method_18456()) {
                if (other.method_33571().method_1022(center) < WHITELIST_RADIUS && this.isWhitelisted(other.method_5477().getString())) {
                    return;
                }
            }
            if (pos.method_10264() <= SpawnerProtectLogic.MIN_DETECTION_Y || !newState.method_26215()) {
                return;
            }
            // the packet is not applied yet, so the world still has the block that is being broken
            class_2680 oldState = level.method_8320(pos);
            if (oldState.method_26215() || oldState.method_26204() instanceof class_2480) {
                return; // nothing was there, or a shulker box (people empty those)
            }
            if (player.method_33571().method_1022(center) < OWN_BREAK_RADIUS) {
                return; // our own digging
            }
            double dx = player.method_23317() - pos.method_10263();
            double dz = player.method_23321() - pos.method_10260();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > this.scanRange.get()) {
                return;
            }
            long window = this.blockUpdateWindow.get() * 1_000_000_000L;
            if (this.breaks.record(System.nanoTime(), window, this.blockUpdateThreshold.get())) {
                this.strangerDetected = true;
                this.currentState = State.WORKING;
                String text = "Remote detection: a block was broken nearby (" + (int) horizontal + "m horizontally, Y " + pos.method_10264() + ")";
                DIHClient.LOG.info("[DIHClient] Spawner Protect: {}", text);
                // Discord Alarm forwards warnings, so this also reaches the phone
                Notifications.warn(this.name(), text);
            }
        } catch (Throwable t) {
            this.fail(t);
        }
    }

    private boolean isWhitelisted(String name) {
        return this.whitelistNames().contains(name.toLowerCase(Locale.ROOT));
    }

    private Set<String> whitelistNames() {
        String raw = this.whitelist.get();
        if (!raw.equals(this.whitelistRaw)) {
            this.whitelistNames = SpawnerProtectLogic.parseWhitelist(raw);
            this.whitelistRaw = raw;
        }
        return this.whitelistNames;
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void onTick() {
        class_746 player = mc.field_1724;
        class_638 level = mc.field_1687;
        class_636 gameMode = mc.field_1761;
        if (player == null || level == null || gameMode == null) {
            return;
        }
        try {
            this.step(player, level, gameMode);
        } catch (Throwable t) {
            this.fail(t);
            this.resetModule();
        } finally {
            this.applyKeys(player);
        }
    }

    private void step(class_746 player, class_638 level, class_636 gameMode) {
        this.spawnerScanEmptyThisTick = false;
        // a server menu was open and is closed now: drop a half-finished block break from before
        if (mc.field_1755 instanceof class_465<?> container && container.method_17577().field_7763 != 0) {
            this.serverMenuSeen = true;
        } else if (this.serverMenuSeen && mc.field_1755 == null) {
            this.serverMenuSeen = false;
            if (this.isDriving() && !player.method_6115()) {
                gameMode.method_2925();
            }
        }

        if (SpawnerProtectLogic.nearSpawn(player.method_23317(), player.method_23321())) {
            if (this.strangerDetected) {
                this.resetModule();
            }
            return;
        }
        if (this.currentState == State.WAITING_FOR_STRANGER) {
            return;
        }
        if (this.currentState != State.OPENING_CHEST && this.currentState != State.DEPOSITING_ITEMS && this.currentState != State.BUYING_ECHEST) {
            this.updateSneak(player, true);
        }
        this.handleRotation(player, level);
        switch (this.currentState) {
            case WORKING -> this.handleWorking(player, level, gameMode);
            case GOING_TO_CHEST -> this.handleGoingToChest(player, level);
            case OPENING_CHEST -> this.handleOpeningChest(player, gameMode);
            case DEPOSITING_ITEMS -> this.handleDepositing(player, level, gameMode);
            case FINAL_EXIT -> this.handleFinalExit();
            case BUYING_ECHEST -> this.handleBuyingEChest(player, gameMode);
            case PLACING_ECHEST -> this.handlePlacingEChest(player, level, gameMode);
            default -> {
            }
        }
    }

    private void fail(Throwable t) {
        if (this.failures++ < 3) {
            DIHClient.LOG.warn("[DIHClient] Spawner Protect failed (the run is cancelled)", t);
        }
    }

    private void handleRotation(class_746 player, class_638 level) {
        class_243 target = null;
        if (this.currentState == State.WORKING) {
            class_1542 dropped = findDroppedSpawner(player, level);
            if (dropped != null) {
                target = dropped.method_73189();
            } else {
                if (this.needsNewTarget(player, level)) {
                    this.targetBlock = this.findRandomSpawner(player, level);
                    this.miningTicks = 0;
                }
                if (this.targetBlock != null) {
                    target = class_243.method_24953(this.targetBlock);
                }
            }
        } else if ((this.currentState == State.GOING_TO_CHEST || this.currentState == State.OPENING_CHEST) && this.targetChest != null) {
            target = class_243.method_24953(this.targetChest);
        }
        if (target != null) {
            HumanAim.stepTo(target, this.rotationSpeed.get(), 1.0F);
        }
    }

    private void handleWorking(class_746 player, class_638 level, class_636 gameMode) {
        if (player.field_7512 != player.field_7498) {
            player.method_7346();
        }
        class_1542 dropped = findDroppedSpawner(player, level);
        int carried = getSpawnerCount();
        Work work = SpawnerProtectLogic.working(dropped != null, carried, this.targetStackCount.get());
        if (work == Work.PICK_UP) {
            this.lagWaitTicks = 0;
            this.stopBreaking();
            this.forwardHeld = true;
            return;
        }
        if (work == Work.GO_TO_CHEST) {
            this.goToChest(player, level);
            return;
        }
        if (this.needsNewTarget(player, level)) {
            this.targetBlock = this.findRandomSpawner(player, level);
            this.miningTicks = 0;
        }
        if (this.targetBlock == null) {
            this.stopMovement();
            switch (SpawnerProtectLogic.noTarget(carried, this.lagWaitTicks)) {
                case WAIT -> this.lagWaitTicks++;
                case GO_TO_CHEST -> this.goToChest(player, level);
                default -> {
                    this.currentState = State.FINAL_EXIT;
                    this.lagWaitTicks = 0;
                }
            }
            return;
        }
        this.lagWaitTicks = 0;
        double dist = player.method_33571().method_1022(class_243.method_24953(this.targetBlock));
        if (dist > this.breakRange.get()) {
            this.stopBreaking();
            this.forwardHeld = true;
            return;
        }
        this.forwardHeld = false;
        this.miningTicks++;
        if (this.miningTicks > RIGHT_CLICK_AFTER_TICKS) {
            // a spawner that does not break (the server wants something else): click it once in a while
            gameMode.method_2896(player, class_1268.field_5808, new class_3965(class_243.method_24953(this.targetBlock), class_2350.field_11036, this.targetBlock, false));
            player.method_6104(class_1268.field_5808);
            this.miningTicks = 0;
        }
        if (this.breakCooldown <= 0) {
            gameMode.method_2902(this.targetBlock, class_2350.field_11036);
            player.method_6104(class_1268.field_5808);
            this.breakCooldown = BREAK_COOLDOWN_TICKS;
        } else {
            this.breakCooldown--;
        }
    }

    private boolean needsNewTarget(class_746 player, class_638 level) {
        return this.targetBlock == null
            || level.method_8320(this.targetBlock).method_26204() != class_2246.field_10260
            || player.method_24515().method_10262(this.targetBlock) > RETARGET_DIST_SQR;
    }

    private class_2338 findRandomSpawner(class_746 player, class_638 level) {
        if (this.spawnerScanEmptyThisTick) {
            return null;
        }
        class_2338 found = findRandomBlock(player, level, class_2246.field_10260, SCAN_RADIUS);
        this.spawnerScanEmptyThisTick = found == null;
        return found;
    }

    private void goToChest(class_746 player, class_638 level) {
        this.stopMovement();
        this.targetChest = findNearestBlock(player, level, class_2246.field_10443, CHEST_SEARCH_RADIUS);
        this.currentState = SpawnerProtectLogic.chestPlan(this.targetChest != null, getEnderChestCount());
        this.shopSequence = 0;
        this.shopTick = 0;
    }

    private void handleGoingToChest(class_746 player, class_638 level) {
        if (this.targetChest == null) {
            this.targetChest = findNearestBlock(player, level, class_2246.field_10443, CHEST_SEARCH_RADIUS);
        }
        if (this.targetChest == null) {
            this.currentState = State.WORKING;
            return;
        }
        this.forwardHeld = true;
        if (player.method_24515().method_19771(this.targetChest, CHEST_ARRIVE_DISTANCE)) {
            this.stopMovement();
            this.currentState = State.OPENING_CHEST;
        }
    }

    private void handleOpeningChest(class_746 player, class_636 gameMode) {
        this.updateSneak(player, false);
        if (this.chestTick % CHEST_OPEN_RETRY_TICKS == 0 && this.targetChest != null) {
            gameMode.method_2896(player, class_1268.field_5808, new class_3965(class_243.method_24953(this.targetChest), class_2350.field_11036, this.targetChest, false));
        }
        this.chestTick++;
        if (player.field_7512 instanceof class_1707) {
            this.chestTick = 0;
            this.currentState = State.DEPOSITING_ITEMS;
            this.lagWaitTicks = 0;
        }
    }

    private void handleDepositing(class_746 player, class_638 level, class_636 gameMode) {
        if (!(player.field_7512 instanceof class_1707 menu)) {
            if (this.lagWaitTicks < SpawnerProtectLogic.DEPOSIT_WAIT_TICKS) {
                this.lagWaitTicks++;
            }
            return;
        }
        int chestSlots = menu.field_7761.size() - 36;
        boolean hasSpace = false;
        for (int i = 0; i < chestSlots; i++) {
            class_1799 stack = menu.method_7611(i).method_7677();
            if (stack.method_7960() || stack.method_7909() == class_1802.field_8849 && stack.method_7947() < stack.method_7914()) {
                hasSpace = true;
                break;
            }
        }
        int spawnerSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (menu.method_7611(chestSlots + i).method_7677().method_7909() == class_1802.field_8849) {
                spawnerSlot = chestSlots + i;
                break;
            }
        }
        // the block search is only needed when the inventory is empty of spawners
        boolean spawnerBlockNearby = spawnerSlot < 0 && this.lagWaitTicks >= SpawnerProtectLogic.DEPOSIT_WAIT_TICKS
            && findNearestBlock(player, level, class_2246.field_10260, SCAN_RADIUS) != null;
        switch (SpawnerProtectLogic.deposit(this.lagWaitTicks, hasSpace, spawnerSlot >= 0, spawnerBlockNearby)) {
            case WAIT -> this.lagWaitTicks++;
            case CLICK -> gameMode.method_2906(menu.field_7763, spawnerSlot, 0, class_1713.field_7794, player);
            case FULL -> {
                this.currentState = State.FINAL_EXIT;
                this.lagWaitTicks = 0;
            }
            case RESUME -> {
                player.method_7346();
                this.currentState = State.WORKING;
            }
            case DONE -> {
                player.method_7346();
                this.currentState = State.FINAL_EXIT;
                this.lagWaitTicks = 0;
            }
        }
    }

    private void handleBuyingEChest(class_746 player, class_636 gameMode) {
        this.shopTick++;
        if (SpawnerProtectLogic.shopMenuLost(this.shopSequence, mc.field_1755 instanceof class_465, this.shopTick)) {
            DIHClient.LOG.info("[DIHClient] Spawner Protect: shop menu closed, retrying from the start");
            this.shopSequence = 0;
            this.shopTick = 0;
            return;
        }
        if (this.shopTick < SpawnerProtectLogic.SHOP_START_TICKS) {
            return;
        }
        switch (this.shopSequence) {
            case 0 -> {
                class_634 connection = mc.method_1562();
                if (connection == null) {
                    return;
                }
                DIHClient.LOG.info("[DIHClient] Spawner Protect: opening the shop");
                connection.method_45730("shop");
                this.shopSequence = 1;
                this.shopTick = 0;
            }
            case 1, 2, 3 -> {
                if (screenTitleContains(mc.field_1755, SpawnerProtectLogic.shopTitle(this.shopSequence))) {
                    gameMode.method_2906(player.field_7512.field_7763, SpawnerProtectLogic.shopSlot(this.shopSequence), 0, class_1713.field_7790, player);
                    this.shopSequence++;
                    this.shopTick = 0;
                }
            }
            case 4 -> {
                if (getEnderChestCount() > 0) {
                    DIHClient.LOG.info("[DIHClient] Spawner Protect: ender chest bought");
                    player.method_7346();
                    this.currentState = State.PLACING_ECHEST;
                    this.shopTick = 0;
                } else if (this.shopTick > SpawnerProtectLogic.SHOP_TIMEOUT_TICKS) {
                    Notifications.warn(this.name(), "Buying the ender chest failed (timed out), trying again");
                    player.method_7346();
                    this.shopSequence = 0;
                    this.shopTick = 0;
                }
            }
            default -> this.shopSequence = 0;
        }
    }

    private static boolean screenTitleContains(class_437 screen, String text) {
        return screen != null && text != null && screen.method_25440().getString().toUpperCase(Locale.ROOT).contains(text);
    }

    private void handlePlacingEChest(class_746 player, class_638 level, class_636 gameMode) {
        this.shopTick++;
        if (this.shopTick < SpawnerProtectLogic.PLACE_START_TICKS) {
            return;
        }
        int slot = InvUtil.findHotbar(class_1802.field_8466);
        if (slot == -1) {
            // in the main inventory: shift-click moves it into the hotbar
            int inMain = InvUtil.findInventory(stack -> stack.method_7909() == class_1802.field_8466);
            if (inMain >= 9) {
                gameMode.method_2906(player.field_7512.field_7763, inMain, 0, class_1713.field_7794, player);
                this.shopTick = 0;
                return;
            }
            this.currentState = State.BUYING_ECHEST;
            this.shopSequence = 0;
            this.shopTick = 0;
            return;
        }
        InvUtil.select(slot);
        class_2338 feet = player.method_24515();
        class_2338 place = null;
        for (class_2350 direction : class_2350.values()) {
            if (direction != class_2350.field_11036 && direction != class_2350.field_11033) {
                class_2338 candidate = feet.method_10093(direction);
                if (level.method_8320(candidate).method_45474()) {
                    place = candidate;
                    break;
                }
            }
        }
        if (place == null) {
            Notifications.warn(this.name(), "No space to place the ender chest");
            this.currentState = State.FINAL_EXIT;
            this.lagWaitTicks = 0;
            return;
        }
        gameMode.method_2896(player, class_1268.field_5808, new class_3965(class_243.method_24953(place), class_2350.field_11036, place, false));
        player.method_6104(class_1268.field_5808);
        this.targetChest = place;
        this.currentState = State.GOING_TO_CHEST;
    }

    private void handleFinalExit() {
        if (this.lagWaitTicks == 0) {
            Notifications.info(this.name(), "Done: waiting 2s for the server to save the items, then logging out");
        }
        this.lagWaitTicks++;
        if (this.lagWaitTicks > SpawnerProtectLogic.EXIT_WAIT_TICKS) {
            class_634 connection = mc.method_1562();
            if (connection != null) {
                connection.method_48296().method_10747(class_2561.method_43470(DISCONNECT_REASON));
            }
            this.resetModule();
        }
    }

    // ---------------------------------------------------------------- keys

    private void updateSneak(class_746 player, boolean sneak) {
        if (player != null) {
            player.method_5660(sneak);
        }
        this.sneakHeld = sneak;
    }

    private void stopBreaking() {
        this.breakCooldown = 0;
    }

    private void stopMovement() {
        this.stopBreaking();
        this.forwardHeld = false;
    }

    /** Presses the keys the run wants and holds the others up. Runs at the end of every tick. */
    private void applyKeys(class_746 player) {
        class_315 options = mc.field_1690;
        if (options == null) {
            return;
        }
        if (this.isDriving()) {
            options.field_1894.method_23481(this.forwardHeld);
            options.field_1832.method_23481(this.sneakHeld);
            options.field_1881.method_23481(false);
            options.field_1913.method_23481(false);
            options.field_1849.method_23481(false);
            options.field_1903.method_23481(false);
            this.keysOwned = true;
        } else if (this.keysOwned) {
            this.releaseKeys();
            player.method_5660(false);
        }
    }

    /** Gives the keys back to the real keyboard. */
    private void releaseKeys() {
        this.keysOwned = false;
        class_315 options = mc.field_1690;
        if (options == null || mc.method_22683() == null) {
            return;
        }
        for (class_304 key : new class_304[]{options.field_1894, options.field_1881, options.field_1913, options.field_1849, options.field_1903, options.field_1832}) {
            try {
                key.method_23481(mc.field_1755 == null && KeyUtil.isPhysicallyDown(key));
            } catch (Throwable t) {
                key.method_23481(false);
            }
        }
    }

    // ---------------------------------------------------------------- world scans

    private static class_1542 findDroppedSpawner(class_746 player, class_638 level) {
        class_1542 best = null;
        float bestDistance = 0.0F;
        for (class_1297 entity : level.method_18112()) {
            if (entity instanceof class_1542 item && item.method_6983().method_7909() == class_1802.field_8849) {
                float distance = player.method_5739(item);
                if (distance < 16.0F && (best == null || distance < bestDistance)) {
                    best = item;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static class_2338 findRandomBlock(class_746 player, class_638 level, class_2248 block, int radius) {
        class_2338 origin = player.method_24515();
        int maxDistSqr = radius * radius;
        List<class_2338> matches = new ArrayList<>();
        class_2339 pos = new class_2339();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= maxDistSqr) {
                        pos.method_10103(origin.method_10263() + x, origin.method_10264() + y, origin.method_10260() + z);
                        if (level.method_8320(pos).method_26204() == block) {
                            matches.add(pos.method_10062());
                        }
                    }
                }
            }
        }
        return matches.isEmpty() ? null : matches.get(ThreadLocalRandom.current().nextInt(matches.size()));
    }

    private static class_2338 findNearestBlock(class_746 player, class_638 level, class_2248 block, int radius) {
        class_2338 origin = player.method_24515();
        class_2338 nearest = null;
        double minDistSqr = Double.MAX_VALUE;
        class_2339 pos = new class_2339();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    pos.method_10103(origin.method_10263() + x, origin.method_10264() + y, origin.method_10260() + z);
                    if (level.method_8320(pos).method_26204() == block) {
                        double distSqr = x * x + y * y + z * z;
                        if (distSqr < minDistSqr) {
                            minDistSqr = distSqr;
                            nearest = pos.method_10062();
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static int getSpawnerCount() {
        return InvUtil.count(stack -> stack.method_7909() == class_1802.field_8849);
    }

    private static int getEnderChestCount() {
        return InvUtil.count(stack -> stack.method_7909() == class_1802.field_8466);
    }
}
