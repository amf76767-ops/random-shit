package dev.dihclient.port.crystal;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Arrays;
import net.minecraft.class_1268;
import net.minecraft.class_1293;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1320;
import net.minecraft.class_1322;
import net.minecraft.class_1324;
import net.minecraft.class_1511;
import net.minecraft.class_1661;
import net.minecraft.class_1799;
import net.minecraft.class_1887;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2824;
import net.minecraft.class_2868;
import net.minecraft.class_310;
import net.minecraft.class_5134;
import net.minecraft.class_638;
import net.minecraft.class_6880;
import net.minecraft.class_746;
import net.minecraft.class_9698;
import net.minecraft.class_9701;
import net.minecraft.class_9711;
import net.minecraft.class_9723;
import net.minecraft.class_1297.class_5529;
import net.minecraft.class_2824.class_5908;

/**
 * Ported from an open-source client (GPL-3.0).
 * Removes a crystal on your screen as soon as your own hit is sure to break it, so the next crystal can be placed
 * without waiting for the server. It only does so when the hit really deals damage (weapon, enchants, effects, recent hand swaps).
 */
public class CrystalOptimizerModule extends Module {
    private static final int SAMPLE_CAPACITY = 64;
    private final class_1799[] heldSamples = new class_1799[SAMPLE_CAPACITY];
    private final int[] sampleTicks = new int[SAMPLE_CAPACITY];
    private int sampleCursor;
    private int sampleCount;
    private int ticks;
    private int trackedSince;
    private class_746 trackedPlayer;
    private class_638 trackedLevel;
    private boolean attackSent;
    private final AttackProbe attackProbe = new AttackProbe();
    /** one listener object, so it can be removed again */
    private final AttackHooks.Listener attackListener = this::onAttack;

    public CrystalOptimizerModule() {
        super("Crystal Optimizer", Category.COMBAT, "Removes crystals client-side as soon as your hit breaks them.");
    }

    @Override
    protected void onEnable() {
        this.reset();
        AttackHooks.add(this.attackListener);
    }

    @Override
    protected void onDisable() {
        AttackHooks.remove(this.attackListener);
        this.reset();
    }

    @Override
    public void onWorldChange() {
        this.reset();
    }

    @Override
    public void onTick() {
        this.attackSent = false;
        class_310 mc = class_310.method_1551();
        class_746 player = mc.field_1724;
        class_638 level = mc.field_1687;
        if (player != null && level != null) {
            if (player != this.trackedPlayer || level != this.trackedLevel) {
                this.reset();
                this.trackedPlayer = player;
                this.trackedLevel = level;
            }

            this.ticks++;
            this.sample(player.method_6047());
        } else if (this.trackedPlayer != null || this.sampleCount > 0) {
            this.reset();
        }
    }

    @Override
    public boolean onPacketSend(class_2596<?> packet) {
        class_310 mc = class_310.method_1551();
        if (mc.method_18854()) {
            if (packet instanceof class_2824 interact) {
                this.attackSent = this.attackProbe.isAttack(interact);
            } else if (packet instanceof class_2868 carried) {
                class_746 player = mc.field_1724;
                if (player != null && player == this.trackedPlayer && class_1661.method_7380(carried.method_12442())) {
                    this.sample(player.method_31548().method_5438(carried.method_12442()));
                }
            }
        }
        return false;
    }

    /** Runs when the game starts handling an attack of the local player (after the attack packet has been sent). */
    private void onAttack(class_1297 target) {
        boolean sent = this.attackSent;
        this.attackSent = false;
        if (sent && target instanceof class_1511 crystal) {
            class_310 mc = class_310.method_1551();
            class_746 player = mc.field_1724;
            class_638 level = mc.field_1687;
            if (player != null && level != null && player == this.trackedPlayer && crystal.method_73183() == level) {
                try {
                    if (this.breaksOnServer(mc, player, level, crystal)) {
                        level.method_2945(crystal.method_5628(), class_5529.field_26999);
                    }
                } catch (Throwable t) {
                    DIHClient.LOG.warn("[DIHClient] Crystal Optimizer failed", t);
                }
            }
        }
    }

    private boolean breaksOnServer(class_310 mc, class_746 player, class_638 level, class_1511 crystal) {
        if (crystal.method_31481() || crystal.method_6838() != null) {
            return false;
        } else if (!player.method_7325() && !player.method_29504() && !player.method_6123()) {
            class_1799 weapon = player.method_6047();
            if (weapon.method_45435(level.method_45162()) && !player.method_75202(weapon, 0)) {
                if (!level.method_8621().method_11952(crystal.method_24515())) {
                    return false;
                } else {
                    return !player.method_76729(crystal.method_5829(), 1.0)
                        ? false
                        : enchantsAddDamage(weapon) || this.recentHandsDealDamage(mc, player, weapon);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static boolean enchantsAddDamage(class_1799 weapon) {
        float bonus = 0.0F;

        for (Entry<class_6880<class_1887>> entry : weapon.method_58657().method_57539()) {
            class_1887 enchantment = (class_1887)((class_6880)entry.getKey()).comp_349();

            for (class_9698<class_9723> effect : enchantment.method_60034(class_9701.field_51661)) {
                if (effect.comp_2681().isPresent() || !(effect.comp_2680() instanceof class_9711 add)) {
                    return false;
                }

                bonus += add.comp_2704().method_60188(entry.getIntValue());
            }
        }

        return bonus > 0.0F;
    }

    private boolean recentHandsDealDamage(class_310 mc, class_746 player, class_1799 held) {
        if (!dealsDamage(player, held)) {
            return false;
        } else {
            int window = Crystals.confirmWindow(mc, player);
            boolean complete = this.ticks - this.trackedSince >= window;
            class_1799 checked = held;

            int walked;
            for (walked = 0; walked < this.sampleCount; walked++) {
                int index = Math.floorMod(this.sampleCursor - 1 - walked, SAMPLE_CAPACITY);
                if (this.ticks - this.sampleTicks[index] > window) {
                    break;
                }

                class_1799 sample = this.heldSamples[index];
                if (sample != checked) {
                    if (!dealsDamage(player, sample)) {
                        return false;
                    }

                    checked = sample;
                }
            }

            if (walked == SAMPLE_CAPACITY) {
                complete = false;
            }

            return complete || dealsDamage(player, class_1799.field_8037);
        }
    }

    private static boolean dealsDamage(class_746 player, class_1799 mainHand) {
        class_1324 damage = new class_1324(class_5134.field_23721, changed -> {});
        damage.method_6192(player.method_45326(class_5134.field_23721));

        for (class_1293 effect : player.method_6026()) {
            effect.method_5579().comp_349().method_55650(effect.method_5578(), (attribute, modifier) -> include(damage, attribute, modifier));
        }

        for (class_1304 slot : class_1304.field_54086) {
            class_1799 stack = slot == class_1304.field_6173 ? mainHand : player.method_6118(slot);
            if (!stack.method_7960() && !stack.method_61657()) {
                stack.method_57354(slot, (attribute, modifier) -> include(damage, attribute, modifier));
            }
        }

        return (float)damage.method_6194() > 0.0F;
    }

    private static void include(class_1324 damage, class_6880<class_1320> attribute, class_1322 modifier) {
        if (class_5134.field_23721.equals(attribute)) {
            damage.method_6200(modifier.comp_2447());
            damage.method_26835(modifier);
        }
    }

    private void sample(class_1799 stack) {
        this.heldSamples[this.sampleCursor] = stack;
        this.sampleTicks[this.sampleCursor] = this.ticks;
        this.sampleCursor = (this.sampleCursor + 1) % SAMPLE_CAPACITY;
        if (this.sampleCount < SAMPLE_CAPACITY) {
            this.sampleCount++;
        }
    }

    private void reset() {
        Arrays.fill(this.heldSamples, null);
        this.sampleCursor = 0;
        this.sampleCount = 0;
        this.trackedSince = this.ticks;
        this.trackedPlayer = null;
        this.trackedLevel = null;
        this.attackSent = false;
    }

        private static final class AttackProbe implements class_5908 {
        private boolean attack;

        boolean isAttack(class_2824 packet) {
            this.attack = false;
            packet.method_34209(this);
            return this.attack;
        }

        @Override
        public void method_34219(class_1268 hand) {
        }

        @Override
        public void method_34220(class_1268 hand, class_243 location) {
        }

        @Override
        public void method_34218() {
            this.attack = true;
        }
    }
}
