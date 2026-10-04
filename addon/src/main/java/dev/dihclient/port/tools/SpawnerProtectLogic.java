package dev.dihclient.port.tools;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Ported from an open-source client (GPL-3.0).
 * The decisions of Spawner Protect without any game class: which step comes next, when a pile of broken blocks counts as
 * "someone is digging towards us", the whitelist. The module only reads the game and carries the decisions out.
 */
public final class SpawnerProtectLogic {
    public enum State {
        WAITING_FOR_STRANGER, WORKING, GOING_TO_CHEST, OPENING_CHEST, DEPOSITING_ITEMS, FINAL_EXIT, BUYING_ECHEST, PLACING_ECHEST
    }

    /** What the WORKING state does this tick. */
    public enum Work { PICK_UP, GO_TO_CHEST, SEARCH, MINE, WAIT, FINISH }

    /** What the DEPOSITING_ITEMS state does this tick. */
    public enum Deposit { WAIT, CLICK, FULL, RESUME, DONE }

    public static final int NO_TARGET_WAIT_TICKS = 40;
    public static final int DEPOSIT_WAIT_TICKS = 15;
    public static final int EXIT_WAIT_TICKS = 40;
    public static final int SHOP_START_TICKS = 30;
    public static final int SHOP_RETRY_TICKS = 60;
    public static final int SHOP_TIMEOUT_TICKS = 100;
    public static final int PLACE_START_TICKS = 10;
    public static final int MIN_DETECTION_Y = -64;
    /** The server spawn is a square around 0/0; block breaking there is normal and ignored. */
    public static final double SPAWN_HALF_SIZE = 100.0;

    private SpawnerProtectLogic() {
    }

    /** First look at the WORKING state, before a mining target is searched. */
    public static Work working(boolean droppedSpawnerNearby, int spawnersCarried, int stackLimit) {
        if (droppedSpawnerNearby) {
            return Work.PICK_UP;
        }
        return spawnersCarried >= stackLimit * 64 ? Work.GO_TO_CHEST : Work.SEARCH;
    }

    /** No spawner left to mine: wait a little (the server may lag), then bank what we carry, or leave when there is nothing. */
    public static Work noTarget(int spawnersCarried, int waitedTicks) {
        if (waitedTicks < NO_TARGET_WAIT_TICKS) {
            return Work.WAIT;
        }
        return spawnersCarried > 0 ? Work.GO_TO_CHEST : Work.FINISH;
    }

    /** Where to deposit: a chest next to us, else place one we carry, else buy one. */
    public static State chestPlan(boolean enderChestNearby, int enderChestsCarried) {
        if (enderChestNearby) {
            return State.GOING_TO_CHEST;
        }
        return enderChestsCarried > 0 ? State.PLACING_ECHEST : State.BUYING_ECHEST;
    }

    public static Deposit deposit(int waitedTicks, boolean chestHasSpace, boolean spawnerInInventory, boolean spawnerBlockNearby) {
        if (waitedTicks < DEPOSIT_WAIT_TICKS) {
            return Deposit.WAIT;
        }
        if (spawnerInInventory) {
            return chestHasSpace ? Deposit.CLICK : Deposit.FULL;
        }
        return spawnerBlockNearby ? Deposit.RESUME : Deposit.DONE;
    }

    /** Title the shop menu must contain before step {@code sequence} (1-3) clicks; null for the other steps. */
    public static String shopTitle(int sequence) {
        return switch (sequence) {
            case 1 -> "SHOP";
            case 2 -> "END";
            case 3 -> "ENDER CHEST";
            default -> null;
        };
    }

    /** The slot clicked in shop step 1-3 (End category, Ender Chest, confirm); -1 for the other steps. */
    public static int shopSlot(int sequence) {
        return switch (sequence) {
            case 1 -> 11;
            case 2 -> 9;
            case 3 -> 25;
            default -> -1;
        };
    }

    /** The shop closed in the middle of the purchase: start again from the command. */
    public static boolean shopMenuLost(int sequence, boolean screenOpen, int shopTick) {
        return sequence > 0 && !screenOpen && shopTick > SHOP_RETRY_TICKS;
    }

    public static boolean nearSpawn(double x, double z) {
        return Math.abs(x) < SPAWN_HALF_SIZE && Math.abs(z) < SPAWN_HALF_SIZE;
    }

    /** Lower-cased names from a comma separated list. */
    public static Set<String> parseWhitelist(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (String entry : raw.split(",")) {
            String name = entry.trim();
            if (!name.isEmpty()) {
                out.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** Counts broken blocks inside a time window; reaching the threshold means a stranger is at work. */
    public static final class BreakCounter {
        private int count;
        private long last;
        private boolean any;

        /** @return true when this break reached the threshold */
        public boolean record(long nowNanos, long windowNanos, int threshold) {
            if (!this.any || nowNanos - this.last > windowNanos) {
                this.count = 0;
            }
            this.count++;
            this.last = nowNanos;
            this.any = true;
            return this.count >= threshold;
        }

        public void reset() {
            this.count = 0;
            this.any = false;
        }

        public int count() {
            return this.count;
        }
    }
}
