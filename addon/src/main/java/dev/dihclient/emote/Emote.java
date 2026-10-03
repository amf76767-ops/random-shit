package dev.dihclient.emote;

import java.util.Locale;

/** The emotes: each one is a movement of the whole body over a few seconds. */
public enum Emote {
    DANCE("Dance", 4.0f),
    SPIN("Spin", 1.6f),
    BACKFLIP("Backflip", 1.2f),
    BOW("Bow", 2.4f),
    HOP("Hop", 2.0f),
    SLEEP("Sleep", 4.0f),
    WIGGLE("Wiggle", 2.0f),
    SHAKE("No", 1.6f),
    FAINT("Faint", 3.0f);

    private static final float RAMP = 0.2f;

    public final String title;
    public final float seconds;

    Emote(String title, float seconds) {
        this.title = title;
        this.seconds = seconds;
    }

    public static Emote byName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().toUpperCase(Locale.ROOT);
        for (Emote e : values()) {
            if (e.name().equals(n)) {
                return e;
            }
        }
        return null;
    }

    private static double ease(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** 0 → 1 → hold → 0: up in {@code rise} seconds, down in the last {@code fall} seconds. */
    private static double bump(double t, double total, double rise, double fall) {
        return Math.min(ease(t / rise), ease((total - t) / fall));
    }

    /** The pose {@code t} seconds after the start; {@link EmotePose#NONE} when it is over. */
    public EmotePose at(double t) {
        if (t < 0 || t >= this.seconds) {
            return EmotePose.NONE;
        }
        double tau = Math.PI * 2;
        double dy = 0, yaw = 0, pitch = 0, roll = 0;
        switch (this) {
            case DANCE -> {
                dy = 0.10 * Math.abs(Math.sin(tau * 1.5 * t));
                yaw = 22 * Math.sin(tau * 0.75 * t);
                roll = 8 * Math.sin(tau * 1.5 * t + Math.PI / 2);
                pitch = 4 * Math.sin(tau * 3 * t);
            }
            case SPIN -> yaw = 720 * ease(t / this.seconds);
            case BACKFLIP -> {
                double u = t / this.seconds;
                pitch = -360 * ease(u);
                dy = 1.1 * Math.sin(Math.PI * u);
            }
            case BOW -> pitch = 38 * bump(t, this.seconds, 0.4, 0.5);
            case HOP -> {
                dy = 0.5 * Math.abs(Math.sin(Math.PI * t / 0.5));
                pitch = 6 * Math.sin(tau * 2 * t);
            }
            case SLEEP -> {
                double b = bump(t, this.seconds, 0.6, 0.6);
                pitch = 85 * b;
                dy = -0.75 * b;
                roll = 2 * Math.sin(tau * 0.4 * t) * b; // breathing
            }
            case WIGGLE -> {
                roll = 14 * Math.sin(tau * 3 * t);
                yaw = 10 * Math.sin(tau * 1.5 * t);
            }
            case SHAKE -> yaw = 28 * Math.sin(tau * 2.5 * t);
            case FAINT -> {
                double b = bump(t, this.seconds, 0.5, 0.7);
                pitch = -85 * b;
                dy = -0.8 * b;
            }
            default -> {
            }
        }
        // no jump at the start and the end of the show
        double env = Math.min(1.0, Math.min(t / RAMP, (this.seconds - t) / RAMP));
        if (this == SPIN || this == BACKFLIP || this == BOW || this == SLEEP || this == FAINT) {
            env = 1.0; // these already start and end at rest
        }
        return new EmotePose((float) (dy * env), (float) (yaw * env), (float) (pitch * env), (float) (roll * env));
    }
}
