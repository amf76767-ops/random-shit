package dev.dihclient.port.staff;

/** Ported from an open-source client (GPL-3.0). */
public final class AlarmPattern {
    public static final String SIREN = "Siren";
    public static final String BEEP = "Beep";
    public static final String BELL = "Bell";
    public static final String GUARDIAN = "Guardian";
    public static final String OFF = "Off";

    private AlarmPattern() {
    }

    public static int interval(String mode) {
        return switch (mode) {
            case BEEP -> 3;
            case BELL -> 10;
            case GUARDIAN -> 60;
            default -> 4;
        };
    }

    public static float pitch(String mode, int note) {
        return switch (mode) {
            case BEEP -> 1.6F;
            case BELL, GUARDIAN -> 1.0F;
            default -> note % 2 == 0 ? 1.5F : 1.0F;
        };
    }
}
