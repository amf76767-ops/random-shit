package dev.dihclient.port.staff;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Timing and pitch of the alarm modes of {@link StaffAlarm}; kept apart so it needs no Minecraft classes.
 */
public final class AlarmPattern {
    public static final String SIREN = "Siren";
    public static final String BEEP = "Beep";
    public static final String BELL = "Bell";
    public static final String GUARDIAN = "Guardian";
    public static final String OFF = "Off";

    private AlarmPattern() {
    }

    /** Ticks between two sounds. */
    public static int interval(String mode) {
        return switch (mode) {
            case BEEP -> 3;
            case BELL -> 10;
            case GUARDIAN -> 60;
            default -> 4;
        };
    }

    /** The siren alternates between a high and a low note, the other modes keep one pitch. */
    public static float pitch(String mode, int note) {
        return switch (mode) {
            case BEEP -> 1.6F;
            case BELL, GUARDIAN -> 1.0F;
            default -> note % 2 == 0 ? 1.5F : 1.0F;
        };
    }
}
