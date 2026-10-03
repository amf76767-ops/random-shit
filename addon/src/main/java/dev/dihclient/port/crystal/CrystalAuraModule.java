package dev.dihclient.port.crystal;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.module.ModuleManager;
import dev.dihclient.modules.combat.CrystalMacro;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import dev.dihclient.util.KeyUtil;
import dev.dihclient.util.RotationUtil;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.class_1293;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338.class_2339;
import net.minecraft.class_2338;
import net.minecraft.class_2350.class_2351;
import net.minecraft.class_2350.class_2353;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_638;
import net.minecraft.class_742;
import net.minecraft.class_746;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Places and breaks end crystals where they hurt the enemy most and you and your friends least. With the Grim-Aldenz
 * profile it only acts when the rotation the server saw already points at the target (silent rotation, see {@link ServerRotation}).
 */
public class CrystalAuraModule extends Module {
    public enum DamageMode { NORMAL, SAFE }

    private static final int COUNTER_CAP = 1000;
    private static final int IDLE_TICKS = 10;
    private static final double AIM_INSET = 0.05;
    private static final int MAX_TARGETS = 3;
    private static final int MAX_BREAK_ATTEMPTS = 3;
    private static final float LOOK_BONUS = 2.0F;
    private static final int MAX_LEAD_TICKS = 5;
    private static final double MAX_STEP_SQR = 1.0;
    private static final float LOOK_MATCH_DEGREES = 2.0F;
    private static final double PLACE_BREAK_MARGIN = 0.1;
    private static final float ONE_LOOK_BONUS = 2.0F;
    private static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.98;
    private static final float OBSIDIAN_UPGRADE_BAR = 8.0F;
    private static final float OBSIDIAN_MIN_GAIN = 5.0F;
    private static final float OBSIDIAN_MIN_RATIO = 2.0F;
    public final BoolSetting requireHold = this.bool("Require Hold", "Only works while the use key (right mouse) is held.", false);
    public final IntSetting breakDelay = this.integer("Break Delay", "Ticks between two crystal hits.", 0, 0, 20);
    public final IntSetting placeDelay = this.integer("Place Delay", "Ticks between two crystal placements.", 0, 0, 20);
    public final DoubleSetting breakRange = this.dbl("Break Range", "How far you hit a crystal. Grim-Aldenz caps it at 3.02.", 3.0, 1.0, 6.0, 0.1);
    public final DoubleSetting placeRange = this.dbl("Place Range", "How far you place a crystal. Grim-Aldenz caps it at 4.5.", 4.5, 1.0, 6.0, 0.1);
    public final DoubleSetting targetRange = this.dbl("Target Range", "Players farther away than this are ignored.", 8.0, 1.0, 12.0, 0.1);
    public final DoubleSetting minDamage = this.dbl("Min Damage", "Least damage a placed crystal has to do to the target.", 4.0, 0.0, 36.0, 0.1);
    public final DoubleSetting maxSelfDamage = this.dbl("Max Self Damage", "Most damage a placed crystal may do to you (unless it kills or pops the target).", 8.0, 0.0, 36.0, 0.1);
    public final BoolSetting facePlace = this.bool("Face Place", "Targets at 5 hearts or less are hit with any crystal damage.", true);
    public final BoolSetting skipFriends = this.bool("Skip Friends", "Never targets DIH friends, and keeps crystals away from them.", true);
    public final BoolSetting ignoreTerrain = this.bool("Ignore Terrain", "Counts the target as fully exposed: faster, but ignores blocks that shield it.", true);
    public final EnumSetting<DamageMode> damageMode = this.mode("Damage Mode", "Normal: most damage to the target - Safe: best damage-to-self ratio.", DamageMode.NORMAL);
    public final DoubleSetting minPlaceRatio = this.dbl("Min Place Ratio", "Target damage / own damage a placed crystal should reach.", 1.4, 0.0, 5.0, 0.1);
    public final DoubleSetting minExplode = this.dbl("Min Explode", "Least damage to the target to hit an existing crystal.", 2.5, 0.0, 20.0, 0.1);
    public final DoubleSetting maxExplode = this.dbl("Max Explode", "Most damage to you an existing crystal may do when you hit it.", 9.0, 0.0, 36.0, 0.1);
    public final DoubleSetting minExplodeRatio = this.dbl("Min Explode Ratio", "Target damage / own damage needed to hit an existing crystal.", 1.1, 0.0, 5.0, 0.1);
    public final DoubleSetting forcePop = this.dbl("Force Pop", "Ignores the safety limits when this many crystals would take the target's last health. 0 = off.", 1.0, 0.0, 10.0, 0.1);
    public final DoubleSetting antiSelfPop = this.dbl("Anti Self Pop", "Skips crystals when this many of them would kill you. 0 = off.", 1.0, 0.0, 10.0, 0.1);
    public final DoubleSetting antiFriendPop = this.dbl("Anti Friend Pop", "Skips crystals when this many of them would kill a friend. 0 = off.", 1.0, 0.0, 10.0, 0.1);
    public final DoubleSetting maxFriendPlace = this.dbl("Max Friend Place", "Most damage a placed crystal may do to a friend.", 8.0, 0.0, 36.0, 0.1);
    public final DoubleSetting maxFriendExplode = this.dbl("Max Friend Explode", "Most damage a hit crystal may do to a friend.", 12.0, 0.0, 36.0, 0.1);
    public final DoubleSetting minFriendRatio = this.dbl("Min Friend Ratio", "Target damage / friend damage a crystal should reach.", 2.0, 0.0, 5.0, 0.1);
    public final DoubleSetting slowDamage = this.dbl("Slow Damage", "Crystals that do no more than this to the target are placed slowly.", 3.0, 0.0, 20.0, 0.1);
    public final IntSetting slowPlaceDelay = this.integer("Slow Place Delay", "Place delay for those weak crystals.", 10, 0, 20);
    public final BoolSetting autoObsidian = this.bool("Auto Obsidian", "Places obsidian for a crystal when that does clearly more damage.", true);
    public final IntSetting obsidianDelay = this.integer("Obsidian Delay", "Ticks between two obsidian placements.", 1, 0, 20).visibleWhen(this.autoObsidian::get);
    public final IntSetting jitter = this.integer("Jitter", "Random extra ticks (0 to this) added to the delays and the slot switch.", 0, 0, 4);
    public final BoolSetting switchBack = this.bool("Switch Back", "Goes back to the slot you had before when it stops.", true);
    public final EnumSetting<Bypass.Profile> acProfile = this.mode("AC Profile", "Grim aldenz: keeps to what Grim accepts (silent rotation, 3.02 / 4.5 range, 80 actions per second).", Bypass.Profile.GRIM_ALDENZ);
    private Bypass.Profile profile = Bypass.Profile.OFF;
    private boolean placeSlow;
    private boolean engaged;
    private int idleTicks;
    private int savedSlot = -1;
    private int ownSlot = -1;
    private int ticksSinceBreak = 1000;
    private int ticksSincePlace = 1000;
    private int ticksSinceSwitch = 1000;
    private int breakJitter;
    private int placeJitter;
    private int switchSettle = 1;
    private int pendingBreakId = -1;
    private int pendingBreakAge = 1000;
    private class_2338 pendingCrystalBase;
    private int pendingCrystalAge = 1000;
    private class_2338 pendingObsidian;
    private int pendingObsidianAge = 1000;
    private final Int2IntOpenHashMap breakAttempts = new Int2IntOpenHashMap();
    private final CrystalScore.Config score = new CrystalScore.Config();
    private final AttackHooks.Listener attackListener = this::onAttack;
    private final Bypass.Budget budget = new Bypass.Budget();

    public CrystalAuraModule() {
        super("Crystal Aura", Category.COMBAT, "Places and breaks end crystals for the most damage.");
    }

    @Override
    protected void onEnable() {
        this.resetState();
        AttackHooks.add(this.attackListener);
    }

    @Override
    protected void onDisable() {
        AttackHooks.remove(this.attackListener);
        class_310 mc = class_310.method_1551();
        try {
            this.disengage(mc, mc.field_1724);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Crystal Aura could not restore the slot", t);
        }
        this.resetState();
    }

    @Override
    public void onWorldChange() {
        this.resetState();
    }

    @Override
    public String getInfo() {
        return this.profile.grim() ? "Grim" : null;
    }

    @Override
    public void onTick() {
        class_310 mc = class_310.method_1551();
        this.ageCounters();
        this.profile = this.acProfile.get();
        CrystalDamage.ignoreTerrain = this.ignoreTerrain.get();
        this.syncScore();
        class_746 player = mc.field_1724;
        if (player != null && mc.field_1687 != null && mc.field_1761 != null && !player.method_29504()) {
            this.forgetGoneCrystals(mc.field_1687);
            if (!this.canRun(mc, player)) {
                this.disengage(mc, player);
            } else if (!ownedByMacro()) {
                if (this.ownSlot != -1 && !this.ownsSelection(player)) {
                    this.forgetSlots();
                }

                if (!player.method_3144() && (this.ownsSelection(player) || mayTakeHand(player))) {
                    if (player.method_6115() && !Bypass.allowAttackWhileUsing(this.profile)) {
                        this.idle(mc, player);
                    } else {
                        List<class_1309> enemies = this.findTargets(mc, player);
                        if (enemies.isEmpty()) {
                            this.idle(mc, player);
                        } else {
                            CrystalAuraModule.Look look = !Bypass.requiresLook(this.profile)
                                ? CrystalAuraModule.Look.INSTANT
                                : (ServerRotation.correctable(player) ? CrystalAuraModule.Look.SILENT : CrystalAuraModule.Look.CROSSHAIR);
                            Rotation fixedLook = null;
                            if (look == CrystalAuraModule.Look.CROSSHAIR) {
                                fixedLook = new Rotation(player.method_36454(), player.method_36455());
                                if (!sameLook(ServerRotation.sent(), fixedLook)) {
                                    this.idle(mc, player);
                                    return;
                                }
                            }

                            int window = Bypass.confirmTicks(this.profile, Crystals.confirmWindow(mc, player));
                            int trust = Crystals.confirmWindow(mc, player);
                            class_1511 lastHit = this.lastHit(mc.field_1687, trust);
                            if (this.pendingCrystalBase != null && Crystals.hasCrystal(mc.field_1687, this.pendingCrystalBase, lastHit)) {
                                this.pendingCrystalBase = null;
                            }

                            CrystalAuraModule.Fight fight = new CrystalAuraModule.Fight(
                                player,
                                mc.field_1687,
                                enemies,
                                this.findFriends(mc, player),
                                window,
                                Crystals.latencyMs(mc, player),
                                fixedLook,
                                look == CrystalAuraModule.Look.SILENT ? 2.0F : 0.0F
                            );
                            CrystalAuraModule.Plan breakPlan = this.planBreak(fight, lastHit);
                            if (breakPlan != null && this.due(mc, breakPlan)) {
                                this.engage();
                                if (this.perform(mc, player, breakPlan, look)) {
                                    if (Bypass.allowSameTickPlaceAndBreak(this.profile)) {
                                        CrystalAuraModule.Plan refill = look != CrystalAuraModule.Look.INSTANT
                                            ? this.planRefill(fight, breakPlan.crystal())
                                            : this.planPlacement(fight, breakPlan.crystal(), true);
                                        if (refill != null) {
                                            this.perform(mc, player, refill, look);
                                        }

                                        if (Bypass.allowMultiBreak(this.profile) && this.budget.allows(this.profile)) {
                                            CrystalAuraModule.Plan another = this.planBreak(fight, lastHit);
                                            if (another != null && another.crystal() != breakPlan.crystal()) {
                                                this.perform(mc, player, another, look);
                                            }
                                        }

                                        if (Bypass.allowMultiPlace(this.profile) && this.budget.allows(this.profile)) {
                                            CrystalAuraModule.Plan extra = this.planPlacement(fight, breakPlan.crystal(), true);
                                            if (extra != null && extra.target() != null && (refill == null || !extra.target().equals(refill.target()))) {
                                                this.perform(mc, player, extra, look);
                                            }
                                        }
                                    }
                                }
                            } else {
                                boolean baseComing = breakPlan != null || lastHit != null || this.pendingCrystalBase != null && this.pendingCrystalAge < trust;
                                CrystalAuraModule.Plan placePlan = this.planPlacement(fight, lastHit, !baseComing);
                                this.placeSlow = placePlan != null
                                    && placePlan.step() == CrystalAuraModule.Step.CRYSTAL
                                    && this.enemyDamage(fight, placePlan) <= this.slowDamage.getFloat();
                                if (placePlan == null && lastHit != null) {
                                    CrystalAuraModule.Plan retry = this.planBreak(fight, null);
                                    if (retry != null && this.due(mc, retry)) {
                                        this.engage();
                                        this.perform(mc, player, retry, look);
                                        return;
                                    }
                                }

                                CrystalAuraModule.Plan next = placePlan != null ? placePlan : breakPlan;
                                if (next == null) {
                                    this.idle(mc, player);
                                } else {
                                    this.engage();
                                    this.perform(mc, player, next, look);
                                }
                            }
                        }
                    }
                } else {
                    this.idle(mc, player);
                }
            } else {
                this.disengage(mc, player);
            }
        } else {
            this.disengage(mc, null);
        }
    }

    /** Your own hit on a crystal (by hand or by this module): it is gone soon, do not hit it again right away. */
    private void onAttack(class_1297 target) {
        if (target instanceof class_1511 crystal && crystal.method_73183().method_8608()) {
            this.markBreak(crystal);
        }
    }

    /** DIH's Crystal Macro also switches slots and clicks; while it is on, this module leaves the hand to it (Anubis: CombatMacroLock). */
    private static boolean ownedByMacro() {
        return ModuleManager.on(CrystalMacro.class);
    }

    private boolean canRun(class_310 mc, class_746 player) {
        return Melee.canAct(mc) && !player.method_7325() ? !this.requireHold.get() || KeyUtil.isPhysicallyDown(mc.field_1690.field_1904) : false;
    }

    /** Copies the settings into the pure scoring config once per tick. */
    private void syncScore() {
        CrystalScore.Config c = this.score;
        c.minDamage = this.minDamage.getFloat();
        c.maxSelfDamage = this.maxSelfDamage.getFloat();
        c.facePlace = this.facePlace.get();
        c.safeMode = this.damageMode.get() == DamageMode.SAFE;
        c.minPlaceRatio = this.minPlaceRatio.getFloat();
        c.minExplode = this.minExplode.getFloat();
        c.maxExplode = this.maxExplode.getFloat();
        c.minExplodeRatio = this.minExplodeRatio.getFloat();
        c.forcePop = this.forcePop.getFloat();
        c.antiSelfPop = this.antiSelfPop.getFloat();
        c.antiFriendPop = this.antiFriendPop.getFloat();
        c.maxFriendPlace = this.maxFriendPlace.getFloat();
        c.maxFriendExplode = this.maxFriendExplode.getFloat();
        c.minFriendRatio = this.minFriendRatio.getFloat();
    }

    private static boolean mayTakeHand(class_746 player) {
        class_1799 main = player.method_6047();
        return main.method_31574(class_1802.field_8301) || main.method_31574(class_1802.field_8281) || Crystals.isCombatItem(main);
    }

    private boolean ownsSelection(class_746 player) {
        return this.ownSlot != -1 && player.method_31548().method_67532() == this.ownSlot;
    }

    private void engage() {
        this.engaged = true;
        this.idleTicks = 0;
    }

    private void idle(class_310 mc, class_746 player) {
        if (this.engaged && ++this.idleTicks >= 10) {
            this.disengage(mc, player);
        }
    }

    private void disengage(class_310 mc, class_746 player) {
        this.engaged = false;
        this.idleTicks = 0;
        if (player == null) {
            this.forgetSlots();
        } else if (mc.field_1755 == null && !Melee.eating()) {
            this.restoreSlot(player);
        }
    }

    private void restoreSlot(class_746 player) {
        if (this.ownSlot != -1) {
            class_1661 inventory = player.method_31548();
            if (this.switchBack.get() && class_1661.method_7380(this.savedSlot) && this.savedSlot != this.ownSlot && inventory.method_67532() == this.ownSlot) {
                inventory.method_61496(this.savedSlot);
                this.ticksSinceSwitch = 0;
            }

            this.forgetSlots();
        }
    }

    private void forgetSlots() {
        this.savedSlot = -1;
        this.ownSlot = -1;
    }

    private void resetState() {
        this.engaged = false;
        this.idleTicks = 0;
        this.forgetSlots();
        this.ticksSinceBreak = 1000;
        this.ticksSincePlace = 1000;
        this.ticksSinceSwitch = 1000;
        this.breakJitter = 0;
        this.placeJitter = 0;
        this.switchSettle = 1;
        this.pendingBreakId = -1;
        this.pendingBreakAge = 1000;
        this.pendingCrystalBase = null;
        this.pendingCrystalAge = 1000;
        this.pendingObsidian = null;
        this.pendingObsidianAge = 1000;
        this.breakAttempts.clear();
        this.placeSlow = false;
        this.budget.reset();
    }

    private void forgetGoneCrystals(class_638 level) {
        IntIterator ids = this.breakAttempts.keySet().iterator();

        while (ids.hasNext()) {
            if (level.method_8469(ids.nextInt()) == null) {
                ids.remove();
            }
        }
    }

    private float breakReach() {
        return Bypass.clampBreakRange(this.profile, this.breakRange.getFloat());
    }

    private float placeReach() {
        return Bypass.clampPlaceRange(this.profile, this.placeRange.getFloat());
    }

    private static boolean oneLook(class_243 eye, class_238 crystal, class_243 placeAim, double reach) {
        class_243 direction = placeAim.method_1020(eye).method_1029();
        return crystal.method_992(eye, eye.method_1019(direction.method_1021(reach))).isPresent();
    }

    private static class_243 lead(class_1309 entity, int ticks) {
        double dx = entity.method_23317() - entity.field_6014;
        double dz = entity.method_23321() - entity.field_5969;
        if (ticks > 0 && !(dx * dx + dz * dz > 1.0)) {
            double dy = 0.0;
            if (!entity.method_24828()) {
                double speed = entity.method_23318() - entity.field_6036;

                for (int tick = 0; tick < ticks; tick++) {
                    speed = (speed - 0.08) * 0.98;
                    dy += speed;
                }
            }

            return new class_243(dx * ticks, dy, dz * ticks);
        } else {
            return class_243.field_1353;
        }
    }

    private boolean canHostSoon(CrystalAuraModule.Fight fight, class_2338 base, class_1297 breaking) {
        class_2338 above = base.method_10084();
        if (!fight.level.method_22347(above)) {
            return false;
        } else {
            class_238 space = new class_238(
                above.method_10263(),
                above.method_10264(),
                above.method_10260(),
                above.method_10263() + 1.0,
                above.method_10264() + 2.0,
                above.method_10260() + 1.0
            );

            for (class_1297 entity : fight.level.method_8335(breaking, space)) {
                if (entity.method_5805()) {
                    if (!(entity instanceof class_1309 living) || !fight.enemies.contains(living)) {
                        return false;
                    }

                    class_238 then = living.method_5829().method_997(lead(living, fight.arriveLead));
                    boolean over = then.field_1322 >= space.field_1325;
                    boolean aside = then.field_1320 <= space.field_1323
                        || then.field_1323 >= space.field_1320
                        || then.field_1324 <= space.field_1321
                        || then.field_1321 >= space.field_1324;
                    if (!over && !aside) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    private List<class_1309> findTargets(class_310 mc, class_746 player) {
        double range = this.targetRange.getFloat() * this.targetRange.getFloat();
        List<class_1309> enemies = new ArrayList<>();

        for (class_742 other : mc.field_1687.method_18456()) {
            if (Melee.isHittable(player, other) && (!this.skipFriends.get() || !Melee.isFriend(other)) && player.method_5858(other) < range) {
                enemies.add(other);
            }
        }

        enemies.sort(Comparator.comparingDouble(enemy -> player.method_5858(enemy)));
        return enemies.size() > 3 ? enemies.subList(0, 3) : enemies;
    }

    private List<class_1309> findFriends(class_310 mc, class_746 player) {
        if (!this.skipFriends.get()) {
            return List.of();
        } else {
            double range = this.targetRange.getFloat() * this.targetRange.getFloat();
            List<class_1309> friends = new ArrayList<>();

            for (class_742 other : mc.field_1687.method_18456()) {
                if (other != player && other.method_5805() && Melee.isFriend(other) && player.method_5858(other) < range) {
                    friends.add(other);
                }
            }

            return friends;
        }
    }

    private class_1511 lastHit(class_638 level, int trust) {
        if (this.pendingBreakId >= 0 && this.pendingBreakAge < trust) {
            return level.method_8469(this.pendingBreakId) instanceof class_1511 crystal && crystal.method_5805() ? crystal : null;
        } else {
            return null;
        }
    }

    private CrystalAuraModule.Plan planBreak(CrystalAuraModule.Fight fight, class_1511 skip) {
        float reach = this.breakReach();
        CrystalAuraModule.Side side = fight.side(CrystalAuraModule.Step.BREAK);
        CrystalAuraModule.Plan best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        class_238 around = fight.player.method_5829().method_1014(reach + 2.0);

        for (class_1511 crystal : fight.level.method_18467(class_1511.class, around)) {
            if (crystal != skip
                && crystal.method_5805()
                && this.breakAttempts.get(crystal.method_5628()) < 3
                && Bypass.mayRetryCrystal(this.profile, crystal.method_5628(), this.pendingBreakId, this.pendingBreakAge, fight.window)) {
                class_243 eye = fight.eye;
                if (!(eye.method_1025(Crystals.closestPoint(eye, crystal.method_5829())) > reach * reach)) {
                    class_243 center = crystal.method_73189();
                    if (!this.normalRank() || !(this.bound(side, center) + fight.lookBonus <= bestScore)) {
                        CrystalAuraModule.Plan plan = this.breakLook(fight, crystal);
                        if (plan != null) {
                            float score = this.score(side, center, bestScore - fight.lookBonus, false, true);
                            if (score != Float.NEGATIVE_INFINITY) {
                                score += fight.lookCredit(plan);
                                if (!(score <= bestScore)) {
                                    best = plan;
                                    bestScore = score;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (best == null) {
            return null;
        } else {
            int weapon = weaponRoute(fight.player);
            return weapon == -1 ? null : best.withRoute(weapon);
        }
    }

    private CrystalAuraModule.Plan breakLook(CrystalAuraModule.Fight fight, class_1511 crystal) {
        if (fight.fixedLook != null) {
            CrystalAuraModule.Plan plan = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, null, null, crystal.method_5829().method_1005(), -2);
            return this.hitAlong(fight.player, plan, fight.eye, fight.fixedLook) != null ? plan : null;
        } else {
            class_2338 base = crystal.method_24515().method_10074();
            if (Crystals.isBase(fight.level.method_8320(base))) {
                CrystalAuraModule.Plan throughBase = new CrystalAuraModule.Plan(
                    CrystalAuraModule.Step.BREAK, crystal, base, base, aimOnTop(fight.level, base), -2
                );
                if (this.hitAlong(fight.player, throughBase, fight.eye, fight.lookAt(throughBase.aim())) != null) {
                    return throughBase;
                }
            }

            class_243 nearest = Crystals.closestPoint(fight.eye, crystal.method_5829().method_1011(0.05));
            CrystalAuraModule.Plan plan = new CrystalAuraModule.Plan(CrystalAuraModule.Step.BREAK, crystal, null, null, nearest, -2);
            return this.hitAlong(fight.player, plan, fight.eye, fight.lookAt(nearest)) != null ? plan : null;
        }
    }

    private static int weaponRoute(class_746 player) {
        if (meleeDamage(player, player.method_6047()) > 0.0) {
            return -2;
        } else {
            class_1661 inventory = player.method_31548();
            int best = -1;
            double bestDamage = 0.0;

            for (int slot = 0; slot < class_1661.method_7368(); slot++) {
                double damage = meleeDamage(player, inventory.method_5438(slot));
                if (damage > bestDamage && slot != inventory.method_67532()) {
                    bestDamage = damage;
                    best = slot;
                }
            }

            return best;
        }
    }

    private static double meleeDamage(class_746 player, class_1799 stack) {
        double damage = 1.0 + Melee.attackDamage(stack);
        class_1293 weakness = player.method_6112(class_1294.field_5911);
        if (weakness != null) {
            damage -= 4.0 * (weakness.method_5578() + 1);
        }

        class_1293 strength = player.method_6112(class_1294.field_5910);
        if (strength != null) {
            damage += 3.0 * (strength.method_5578() + 1);
        }

        return damage;
    }

    private CrystalAuraModule.Plan planRefill(CrystalAuraModule.Fight fight, class_1511 broken) {
        class_638 level = fight.level;
        class_2338 base = broken.method_24515().method_10074();
        if (!Crystals.isBase(level.method_8320(base))) {
            return null;
        } else if (base.equals(this.pendingCrystalBase) && this.pendingCrystalAge < fight.window) {
            return null;
        } else if (Crystals.route(fight.player, class_1802.field_8301, level.method_8320(base), this.savedSlot) != -2) {
            return null;
        } else if (!this.canHostSoon(fight, base, broken)) {
            return null;
        } else {
            return this.score(fight.side(CrystalAuraModule.Step.CRYSTAL), Crystals.explosionAt(base), Float.NEGATIVE_INFINITY, false, false)
                    == Float.NEGATIVE_INFINITY
                ? null
                : new CrystalAuraModule.Plan(CrystalAuraModule.Step.CRYSTAL, null, base, base, aimOnTop(level, base), -2);
        }
    }

    private CrystalAuraModule.Plan planPlacement(CrystalAuraModule.Fight fight, class_1511 breaking, boolean mayLayBase) {
        CrystalAuraModule.Plan crystal = this.planBase(fight, CrystalAuraModule.Step.CRYSTAL, breaking);
        if (this.autoObsidian.get() && mayLayBase) {
            float crystalDamage = crystal == null ? 0.0F : this.enemyDamage(fight, crystal);
            if (crystal != null && crystalDamage >= 8.0F) {
                return crystal;
            } else {
                CrystalAuraModule.Plan obsidian = this.planBase(fight, CrystalAuraModule.Step.OBSIDIAN, null);
                if (obsidian != null && crystal != null) {
                    float obsidianDamage = this.enemyDamage(fight, obsidian);
                    boolean upgrade = obsidianDamage >= crystalDamage + 5.0F && obsidianDamage >= crystalDamage * 2.0F;
                    return upgrade ? obsidian : crystal;
                } else {
                    return obsidian == null ? crystal : obsidian;
                }
            }
        } else {
            return crystal;
        }
    }

    private float enemyDamage(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
        class_243 center = Crystals.explosionAt(plan.target());
        float most = 0.0F;

        for (CrystalDamage.Victim enemy : fight.side(plan.step()).enemies) {
            most = Math.max(most, enemy.damage(center, plan.step() == CrystalAuraModule.Step.OBSIDIAN));
        }

        return most;
    }

    private CrystalAuraModule.Plan planBase(CrystalAuraModule.Fight fight, CrystalAuraModule.Step step, class_1511 breaking) {
        class_746 player = fight.player;
        class_638 level = fight.level;
        class_1792 item = step == CrystalAuraModule.Step.CRYSTAL ? class_1802.field_8301 : class_1802.field_8281;
        class_2680 anyBase = class_2246.field_10540.method_9564();
        if (Crystals.route(player, item, anyBase, this.savedSlot) == -1) {
            return null;
        } else if (step == CrystalAuraModule.Step.OBSIDIAN && Crystals.route(player, class_1802.field_8301, anyBase, this.savedSlot) == -1) {
            return null;
        } else {
            CrystalAuraModule.Side side = fight.side(step);
            float placeReach = this.placeReach();
            float breakReach = this.breakReach();
            class_243 eye = fight.eye;
            class_2338 origin = class_2338.method_49638(eye);
            int radius = class_3532.method_15386(placeReach);
            class_2338 pending = step == CrystalAuraModule.Step.CRYSTAL ? this.pendingCrystalBase : this.pendingObsidian;
            int pendingAge = step == CrystalAuraModule.Step.CRYSTAL ? this.pendingCrystalAge : this.pendingObsidianAge;
            List<CrystalAuraModule.Candidate> candidates = new ArrayList<>();
            class_2339 cell = new class_2339();

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        cell.method_25504(origin, dx, dy, dz);
                        class_2680 state = level.method_8320(cell);
                        if (step == CrystalAuraModule.Step.CRYSTAL ? Crystals.isBase(state) : state.method_45474()) {
                            class_2338 base = cell.method_10062();
                            class_2338 clicked = step == CrystalAuraModule.Step.CRYSTAL ? base : base.method_10074();
                            if ((step != CrystalAuraModule.Step.OBSIDIAN || level.method_8320(clicked).method_26206(level, clicked, class_2350.field_11036))
                                && (!base.equals(pending) || pendingAge >= fight.window)) {
                                class_243 aim = aimOnTop(level, clicked);
                                if (!(eye.method_1025(aim) > placeReach * placeReach)) {
                                    class_238 crystal = Crystals.boxOn(base);
                                    double breakable = breakReach - 0.1;
                                    if (!(eye.method_1025(Crystals.closestPoint(eye, crystal)) > breakable * breakable)) {
                                        float bound = this.bound(side, Crystals.explosionAt(base));
                                        if (bound != Float.NEGATIVE_INFINITY) {
                                            float cycle = step == CrystalAuraModule.Step.CRYSTAL
                                                    && fight.lookBonus > 0.0F
                                                    && oneLook(eye, crystal, aim, breakable)
                                                ? 2.0F
                                                : 0.0F;
                                            candidates.add(
                                                new CrystalAuraModule.Candidate(
                                                    new CrystalAuraModule.Plan(step, null, clicked, base, aim, -2), bound + cycle, cycle
                                                )
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            candidates.sort(Comparator.comparingDouble(CrystalAuraModule.Candidate::bound).reversed());
            CrystalAuraModule.Plan best = null;
            float bestScore = Float.NEGATIVE_INFINITY;

            for (CrystalAuraModule.Candidate candidate : candidates) {
                if (this.normalRank() && candidate.bound() + fight.lookBonus <= bestScore) {
                    break;
                }

                CrystalAuraModule.Plan found = candidate.plan();
                float score = this.score(
                    side, Crystals.explosionAt(found.target()), bestScore - fight.lookBonus - candidate.cycle(), step == CrystalAuraModule.Step.OBSIDIAN, false
                );
                if (score != Float.NEGATIVE_INFINITY
                    && level.method_8621().method_11952(found.clicked())
                    && this.canHostSoon(fight, found.target(), step == CrystalAuraModule.Step.CRYSTAL ? breaking : null)) {
                    CrystalAuraModule.Plan plan = this.reachable(fight, found);
                    if (plan != null) {
                        score += fight.lookCredit(plan) + candidate.cycle();
                        if (!(score <= bestScore)) {
                            best = plan;
                            bestScore = score;
                        }
                    }
                }
            }

            if (best == null) {
                return null;
            } else {
                int route = Crystals.route(player, item, level.method_8320(best.clicked()), this.savedSlot);
                return route == -1 ? null : best.withRoute(route);
            }
        }
    }

    private CrystalAuraModule.Plan reachable(CrystalAuraModule.Fight fight, CrystalAuraModule.Plan plan) {
        if (this.hitAlong(fight.player, plan, fight.eye, fight.lookAt(plan.aim())) != null) {
            return plan;
        } else if (plan.step() == CrystalAuraModule.Step.CRYSTAL && fight.fixedLook == null) {
            class_243 middle = class_243.method_24953(plan.clicked());

            for (class_2350 side : class_2353.field_11062) {
                class_243 face = middle.method_1031(side.method_10148() * 0.45, 0.0, side.method_10165() * 0.45);
                if (!((fight.eye.field_1352 - face.field_1352) * side.method_10148() + (fight.eye.field_1350 - face.field_1350) * side.method_10165() <= 0.0)) {
                    CrystalAuraModule.Plan aimed = new CrystalAuraModule.Plan(plan.step(), null, plan.clicked(), plan.target(), face, plan.route());
                    if (this.hitAlong(fight.player, aimed, fight.eye, fight.lookAt(face)) != null) {
                        return aimed;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static class_243 aimOnTop(class_638 level, class_2338 pos) {
        double top = level.method_8320(pos).method_26218(level, pos).method_1105(class_2351.field_11052);
        return new class_243(pos.method_10263() + 0.5, pos.method_10264() + top - 0.05, pos.method_10260() + 0.5);
    }

    private float bound(CrystalAuraModule.Side side, class_243 center) {
        float best = Float.NEGATIVE_INFINITY;

        for (CrystalDamage.Victim enemy : side.enemies) {
            best = Math.max(best, CrystalScore.bound(this.score, enemy.upperBound(center), CrystalDamage.health(enemy.entity())));
        }

        return best;
    }

    private float score(CrystalAuraModule.Side side, class_243 center, float toBeat, boolean baseToCome, boolean breaking) {
        float selfDamage = side.self.damage(center, baseToCome);
        class_1309 self = side.self.entity();
        float selfHealth = CrystalDamage.health(self);
        boolean creative = self instanceof class_1657 player && player.method_68878();
        if (CrystalScore.selfBlocked(this.score, selfDamage, selfHealth, creative, CrystalDamage.endangers((class_746) self, selfDamage))) {
            return Float.NEGATIVE_INFINITY;
        }

        float friendDamage = 0.0F;
        float friendHealth = -1.0F;

        for (CrystalDamage.Victim friend : side.friends) {
            float damage = friend.damage(center, baseToCome);
            if (damage > friendDamage) {
                friendDamage = damage;
                friendHealth = CrystalDamage.health(friend.entity());
            }
        }

        if (CrystalScore.friendBlocked(this.score, friendDamage, friendHealth)) {
            return Float.NEGATIVE_INFINITY;
        }

        float bestDamage = Float.NEGATIVE_INFINITY;
        float bestHealth = 0.0F;
        boolean kills = false;

        for (CrystalDamage.Victim enemy : side.enemies) {
            float damage = enemy.damage(center, baseToCome);
            if (!(damage <= bestDamage)) {
                bestDamage = damage;
                bestHealth = CrystalDamage.health(enemy.entity());
                kills = CrystalDamage.kills(enemy.entity(), damage);
            }
        }

        return CrystalScore.rank(this.score, breaking, selfDamage, friendDamage, bestDamage, bestHealth, kills, toBeat);
    }

    private class_239 hitAlong(class_746 player, CrystalAuraModule.Plan plan, class_243 eye, Rotation look) {
        if (look == null) {
            return null;
        } else {
            class_243 direction = class_243.method_1030(look.pitch(), look.yaw());
            if (plan.step() == CrystalAuraModule.Step.BREAK) {
                class_238 box = plan.crystal().method_5829();
                if (box.method_1006(eye)) {
                    return new class_3966(plan.crystal(), eye);
                } else {
                    class_243 at = box.method_992(eye, eye.method_1019(direction.method_1021(this.breakReach()))).orElse(null);
                    return at != null && Melee.lineIsClear(player, eye, at) ? new class_3966(plan.crystal(), at) : null;
                }
            } else {
                class_638 level = (class_638)player.method_73183();
                class_2680 clicked = level.method_8320(plan.clicked());
                class_3965 hit = clicked.method_26218(level, plan.clicked())
                    .method_1092(eye, eye.method_1019(direction.method_1021(this.placeReach())), plan.clicked());
                if (hit != null && Melee.lineIsClear(player, eye, hit.method_17784())) {
                    return plan.step() == CrystalAuraModule.Step.OBSIDIAN && !plan.target().equals(Crystals.placedObsidian(level, player, hit, clicked))
                        ? null
                        : hit;
                } else {
                    return null;
                }
            }
        }
    }

    private boolean perform(class_310 mc, class_746 player, CrystalAuraModule.Plan plan, CrystalAuraModule.Look look) {
        class_243 eye = player.method_33571();
        Rotation aim = Rotation.toward(plan.aim());
        Rotation sent = ServerRotation.sent();
        Rotation before = look == CrystalAuraModule.Look.INSTANT ? aim : sent;

        Rotation next = switch (look) {
            case INSTANT -> aim;
            case SILENT -> sent;
            case CROSSHAIR -> new Rotation(player.method_36454(), player.method_36455());
        };
        class_239 hit = before != null && next != null ? this.hitAlong(player, plan, eye, before) : null;
        if (hit != null && next != before && this.hitAlong(player, plan, eye, next) == null) {
            hit = null;
        }

        boolean ready = plan.route() == -2 && this.due(mc, plan) && this.budget.allows(this.profile) && hit != null;
        if (look == CrystalAuraModule.Look.SILENT) {
            ServerRotation.request(ready ? sent : aim);
        }

        if (plan.route() != -2) {
            this.select(player, plan.route());
            return false;
        } else if (!ready) {
            return false;
        } else {
            if (plan.step() == CrystalAuraModule.Step.BREAK) {
                if (!Crystals.strike(mc, player, plan.crystal(), this.profile)) {
                    return false;
                }

                this.markBreak(plan.crystal());
                this.breakAttempts.addTo(plan.crystal().method_5628(), 1);
                this.ticksSinceBreak = 0;
                this.breakJitter = this.rollJitter();
            } else {
                Crystals.rightClick(mc, player, (class_3965)hit);
                if (plan.step() == CrystalAuraModule.Step.CRYSTAL) {
                    this.pendingCrystalBase = plan.target();
                    this.pendingCrystalAge = 0;
                } else {
                    this.pendingObsidian = plan.target();
                    this.pendingObsidianAge = 0;
                }

                this.ticksSincePlace = 0;
                this.placeJitter = this.rollJitter();
            }

            this.budget.note();
            return true;
        }
    }

    private static boolean sameLook(Rotation a, Rotation b) {
        return a != null && Math.abs(class_3532.method_15393(b.yaw() - a.yaw())) <= 2.0F && Math.abs(a.pitch() - b.pitch()) <= 2.0F;
    }

    private boolean normalRank() {
        return this.damageMode.get() != DamageMode.SAFE;
    }

    private int placeWait() {
        int delay = this.placeDelay.get();
        if (this.placeSlow) {
            delay = Math.max(delay, this.slowPlaceDelay.get());
        }

        return delay;
    }

    private boolean due(class_310 mc, CrystalAuraModule.Plan plan) {
        return switch (plan.step()) {
            case BREAK -> this.ticksSinceBreak >= Bypass.minDelay(this.profile, this.breakDelay.get()) + this.breakJitter;
            case CRYSTAL -> this.placeSettled(mc) && this.ticksSincePlace >= Bypass.minDelay(this.profile, this.placeWait()) + this.placeJitter;
            case OBSIDIAN -> this.placeSettled(mc) && this.ticksSincePlace >= Bypass.minDelay(this.profile, this.obsidianDelay.get()) + this.placeJitter;
        };
    }

    private boolean placeSettled(class_310 mc) {
        boolean digging = mc.field_1761.method_2923() && !Bypass.allowPlaceWhileDigging(this.profile);
        return this.ticksSinceSwitch >= this.switchSettle && !digging;
    }

    private void select(class_746 player, int slot) {
        if (this.ticksSinceSwitch >= Bypass.minSwitchInterval(this.profile) && class_1661.method_7380(slot)) {
            class_1661 inventory = player.method_31548();
            if (this.savedSlot == -1) {
                this.savedSlot = inventory.method_67532();
            }

            inventory.method_61496(slot);
            this.ownSlot = slot;
            this.ticksSinceSwitch = 0;
            this.switchSettle = 1 + this.rollJitter();
        }
    }

    private int rollJitter() {
        int spread = this.jitter.get();
        return spread > 0 ? ThreadLocalRandom.current().nextInt(spread + 1) : 0;
    }

    private void markBreak(class_1511 crystal) {
        this.pendingBreakId = crystal.method_5628();
        this.pendingBreakAge = 0;
    }

    private void ageCounters() {
        this.ticksSinceBreak = Math.min(this.ticksSinceBreak + 1, 1000);
        this.ticksSincePlace = Math.min(this.ticksSincePlace + 1, 1000);
        this.ticksSinceSwitch = Math.min(this.ticksSinceSwitch + 1, 1000);
        this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
        this.pendingCrystalAge = Math.min(this.pendingCrystalAge + 1, 1000);
        this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
    }

        private record Candidate(CrystalAuraModule.Plan plan, float bound, float cycle) {
    }

        private final class Fight {
        final class_746 player;
        final class_638 level;
        final class_243 eye;
        final List<class_1309> enemies;
        final List<class_1309> friends;
        final int window;
        final Rotation fixedLook;
        final Rotation serverLook;
        final float lookBonus;
        private final int breakLead;
        private final int placeLead;
        final int arriveLead;
        private CrystalAuraModule.Side breakSide;
        private CrystalAuraModule.Side placeSide;

        Fight(
            class_746 player,
            class_638 level,
            List<class_1309> enemies,
            List<class_1309> friends,
            int window,
            int latencyMs,
            Rotation fixedLook,
            float lookBonus
        ) {
            this.player = player;
            this.level = level;
            this.eye = player.method_33571();
            this.enemies = enemies;
            this.friends = friends;
            this.window = window;
            this.fixedLook = fixedLook;
            this.serverLook = ServerRotation.sent();
            this.lookBonus = lookBonus;
            int roundTrip = latencyMs / 50;
            this.breakLead = Math.min(roundTrip + 1, 5);
            this.placeLead = Math.min(2 * roundTrip + 2, 5);
            this.arriveLead = Math.min(latencyMs / 100 + 1, 5);
        }

        CrystalAuraModule.Side side(CrystalAuraModule.Step step) {
            if (step == CrystalAuraModule.Step.BREAK) {
                if (this.breakSide == null) {
                    this.breakSide = new CrystalAuraModule.Side(this.player, this.enemies, this.friends, this.breakLead);
                }

                return this.breakSide;
            } else {
                if (this.placeSide == null) {
                    this.placeSide = new CrystalAuraModule.Side(this.player, this.enemies, this.friends, this.placeLead);
                }

                return this.placeSide;
            }
        }

        Rotation lookAt(class_243 aim) {
            return this.fixedLook != null ? this.fixedLook : Rotation.toward(aim);
        }

        float lookCredit(CrystalAuraModule.Plan plan) {
            return this.lookBonus > 0.0F && CrystalAuraModule.this.hitAlong(this.player, plan, this.eye, this.serverLook) != null ? this.lookBonus : 0.0F;
        }
    }

        private static enum Look {
        INSTANT,
        SILENT,
        CROSSHAIR;
    }

        private record Plan(CrystalAuraModule.Step step, class_1511 crystal, class_2338 clicked, class_2338 target, class_243 aim, int route) {
        CrystalAuraModule.Plan withRoute(int newRoute) {
            return new CrystalAuraModule.Plan(this.step, this.crystal, this.clicked, this.target, this.aim, newRoute);
        }
    }

        private static final class Side {
        final List<CrystalDamage.Victim> enemies = new ArrayList<>();
        final List<CrystalDamage.Victim> friends = new ArrayList<>();
        final CrystalDamage.Victim self;

        Side(class_746 player, List<class_1309> targets, List<class_1309> friends, int leadTicks) {
            for (class_1309 target : targets) {
                this.enemies.add(new CrystalDamage.Victim(target, CrystalAuraModule.lead(target, leadTicks)));
            }

            for (class_1309 friend : friends) {
                this.friends.add(new CrystalDamage.Victim(friend, CrystalAuraModule.lead(friend, leadTicks)));
            }

            this.self = new CrystalDamage.Victim(player, CrystalAuraModule.lead(player, leadTicks));
        }
    }

        private static enum Step {
        BREAK,
        CRYSTAL,
        OBSIDIAN;
    }
}
