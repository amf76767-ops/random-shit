package dev.dihclient;

import dev.dihclient.port.tools.SpawnerProtectLogic;
import dev.dihclient.port.tools.SpawnerProtectLogic.Deposit;
import dev.dihclient.port.tools.SpawnerProtectLogic.State;
import dev.dihclient.port.tools.SpawnerProtectLogic.Work;
import dev.dihclient.port.tools.ToolsLogic;

public final class ToolsTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] args) {
        spear();
        hover();
        working();
        deposit();
        shop();
        breaks();
        misc();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void spear() {
        check(ToolsLogic.pickSpear(new int[]{0, 0, 0}, 0) == -1, "no spear -> -1");
        check(ToolsLogic.pickSpear(new int[]{1, 0, 3, 2}, 0) == 2, "highest level wins");
        check(ToolsLogic.pickSpear(new int[]{2, 0, 2, 0}, 2) == 2, "tie: the selected one stays");
        check(ToolsLogic.pickSpear(new int[]{2, 0, 2, 0}, 5) == 0, "tie without selected: first one");
        check(ToolsLogic.pickSpear(new int[]{0, 0, 1}, 0) == 2, "single spear in the main inventory");
        check(ToolsLogic.pickSpear(new int[]{-1, 0}, 0) == -1, "negative level is no spear");
        check(ToolsLogic.returnDelay(3, 1) == 4, "return delay adds jitter");
        check(ToolsLogic.returnDelay(1, 0) == 1 && ToolsLogic.returnDelay(0, 0) == 1, "return delay is at least 1");
    }

    static void hover() {
        check(ToolsLogic.hoverSwapButton(false, false, 1, false) == 40, "empty offhand -> offhand");
        check(ToolsLogic.hoverSwapButton(false, true, 5, false) == 40, "offhand first even with refill");
        check(ToolsLogic.hoverSwapButton(true, false, 1, false) == -1, "offhand full, no refill -> nothing");
        check(ToolsLogic.hoverSwapButton(true, true, 3, false) == 2, "refill goes to hotbar slot 3 (button 2)");
        check(ToolsLogic.hoverSwapButton(true, true, 3, true) == -1, "hotbar already has one");
        check(ToolsLogic.hoverSwapButton(true, true, 99, false) == 8, "slot is clamped");
    }

    static void working() {
        check(SpawnerProtectLogic.working(true, 500, 3) == Work.PICK_UP, "dropped spawner first");
        check(SpawnerProtectLogic.working(false, 192, 3) == Work.GO_TO_CHEST, "full stacks -> chest");
        check(SpawnerProtectLogic.working(false, 191, 3) == Work.SEARCH, "one short -> keep mining");
        check(SpawnerProtectLogic.noTarget(5, 0) == Work.WAIT, "no target: wait for lag");
        check(SpawnerProtectLogic.noTarget(5, 39) == Work.WAIT, "still waiting at 39");
        check(SpawnerProtectLogic.noTarget(5, 40) == Work.GO_TO_CHEST, "carrying something -> chest");
        check(SpawnerProtectLogic.noTarget(0, 40) == Work.FINISH, "nothing carried -> leave");
        check(SpawnerProtectLogic.chestPlan(true, 0) == State.GOING_TO_CHEST, "chest next to us");
        check(SpawnerProtectLogic.chestPlan(false, 2) == State.PLACING_ECHEST, "place the one we carry");
        check(SpawnerProtectLogic.chestPlan(false, 0) == State.BUYING_ECHEST, "buy one");
    }

    static void deposit() {
        check(SpawnerProtectLogic.deposit(0, true, true, true) == Deposit.WAIT, "wait for the menu to sync");
        check(SpawnerProtectLogic.deposit(14, true, true, true) == Deposit.WAIT, "still waiting at 14");
        check(SpawnerProtectLogic.deposit(15, true, true, false) == Deposit.CLICK, "spawner and space -> click");
        check(SpawnerProtectLogic.deposit(15, false, true, true) == Deposit.FULL, "chest full -> exit");
        check(SpawnerProtectLogic.deposit(15, true, false, true) == Deposit.RESUME, "inventory empty, more spawners -> work");
        check(SpawnerProtectLogic.deposit(15, true, false, false) == Deposit.DONE, "nothing left -> done");
    }

    static void shop() {
        check("SHOP".equals(SpawnerProtectLogic.shopTitle(1)) && SpawnerProtectLogic.shopSlot(1) == 11, "step 1: shop, End category");
        check("END".equals(SpawnerProtectLogic.shopTitle(2)) && SpawnerProtectLogic.shopSlot(2) == 9, "step 2: End, ender chest");
        check("ENDER CHEST".equals(SpawnerProtectLogic.shopTitle(3)) && SpawnerProtectLogic.shopSlot(3) == 25, "step 3: confirm");
        check(SpawnerProtectLogic.shopTitle(0) == null && SpawnerProtectLogic.shopSlot(4) == -1, "other steps click nothing");
        check(!SpawnerProtectLogic.shopMenuLost(0, false, 500), "step 0 never counts as lost");
        check(!SpawnerProtectLogic.shopMenuLost(2, true, 500), "open screen is not lost");
        check(!SpawnerProtectLogic.shopMenuLost(2, false, 60), "60 ticks is still ok");
        check(SpawnerProtectLogic.shopMenuLost(2, false, 61), "61 ticks without a screen restarts");
    }

    static void breaks() {
        long s = 1_000_000_000L;
        SpawnerProtectLogic.BreakCounter c = new SpawnerProtectLogic.BreakCounter();
        check(!c.record(0, 60 * s, 3), "1st break");
        check(!c.record(10 * s, 60 * s, 3), "2nd break");
        check(c.record(20 * s, 60 * s, 3), "3rd break inside the window triggers");
        c.reset();
        check(!c.record(0, 10 * s, 3) && !c.record(5 * s, 10 * s, 3), "after reset it counts again");
        check(!c.record(40 * s, 10 * s, 3), "gap longer than the window starts over");
        check(c.count() == 1, "count restarted at 1");
        SpawnerProtectLogic.BreakCounter one = new SpawnerProtectLogic.BreakCounter();
        check(one.record(5, s, 1), "threshold 1 triggers at once");
    }

    static void misc() {
        check(SpawnerProtectLogic.nearSpawn(0, 0) && SpawnerProtectLogic.nearSpawn(-99.9, 99.9), "inside the spawn square");
        check(!SpawnerProtectLogic.nearSpawn(100, 0) && !SpawnerProtectLogic.nearSpawn(0, -150), "outside the spawn square");
        var names = SpawnerProtectLogic.parseWhitelist(" Alice, bob ,,CHARLIE ");
        check(names.size() == 3 && names.contains("alice") && names.contains("bob") && names.contains("charlie"), "whitelist is trimmed and lower-cased");
        check(SpawnerProtectLogic.parseWhitelist("").isEmpty() && SpawnerProtectLogic.parseWhitelist(null).isEmpty(), "empty whitelist");
    }
}
