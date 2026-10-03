package dev.dihclient.port.staff;

import dev.dihclient.DIHClient;
import java.util.ArrayDeque;
import net.minecraft.class_1109;
import net.minecraft.class_1113;
import net.minecraft.class_1144;
import net.minecraft.class_310;
import net.minecraft.class_3414;
import net.minecraft.class_3417;
import net.minecraft.class_3419;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * A short repeating alarm sound (a few seconds) that plays at full stereo, no matter where you stand. Game thread only.
 */
public final class StaffAlarm {
    private static final int MAX_RINGING = 8;

    private String mode = AlarmPattern.OFF;
    private float volume;
    private int ticksLeft;
    private int step;
    private final ArrayDeque<class_1109> ringing = new ArrayDeque<>(MAX_RINGING);

    public void start(String mode, float seconds, float volume) {
        if (mode == null || AlarmPattern.OFF.equals(mode) || volume <= 0.0F || seconds <= 0.0F) {
            return;
        }
        int ticks = Math.max(1, Math.round(seconds * 20.0F));
        this.volume = Math.min(1.0F, volume);
        if (this.isPlaying() && mode.equals(this.mode)) {
            this.ticksLeft = Math.max(this.ticksLeft, ticks);
        } else {
            this.stop();
            this.mode = mode;
            this.ticksLeft = ticks;
            this.step = 0;
        }
    }

    public void tick() {
        if (this.ticksLeft <= 0) {
            return;
        }
        int interval = AlarmPattern.interval(this.mode);
        if (this.step % interval == 0) {
            try {
                this.play(this.step / interval);
            } catch (Throwable t) {
                // a broken sound must never end the tick
                DIHClient.LOG.warn("[DIHClient] Staff List alarm sound failed", t);
                this.ticksLeft = 1;
            }
        }
        this.step++;
        if (--this.ticksLeft == 0) {
            this.step = 0;
        }
    }

    public void stop() {
        this.ticksLeft = 0;
        this.step = 0;
        if (!this.ringing.isEmpty()) {
            class_1144 sounds = class_310.method_1551().method_1483();
            if (sounds != null) {
                this.ringing.forEach(sounds::method_4870);
            }
            this.ringing.clear();
        }
    }

    public boolean isPlaying() {
        return this.ticksLeft > 0;
    }

    private void play(int note) {
        class_1144 sounds = class_310.method_1551().method_1483();
        if (sounds == null) {
            return;
        }
        class_3414 event = switch (this.mode) {
            case AlarmPattern.BEEP -> class_3417.field_18311.comp_349();
            case AlarmPattern.BELL -> class_3417.field_17265;
            case AlarmPattern.GUARDIAN -> class_3417.field_15203;
            default -> class_3417.field_14622.comp_349();
        };
        class_1109 sound = new class_1109(event.comp_3319(), class_3419.field_15250, this.volume, AlarmPattern.pitch(this.mode, note),
                class_1113.method_43221(), false, 0, class_1113.class_1114.field_5478, 0.0, 0.0, 0.0, true);
        if (this.ringing.size() >= MAX_RINGING) {
            this.ringing.removeFirst();
        }
        this.ringing.addLast(sound);
        sounds.method_4873(sound);
    }
}
