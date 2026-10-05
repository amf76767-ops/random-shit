package dev.dihclient.emote;

public record EmotePose(float dy, float yaw, float pitch, float roll) {
    public static final EmotePose NONE = new EmotePose(0, 0, 0, 0);

    public boolean isNone() {
        return this.dy == 0 && this.yaw == 0 && this.pitch == 0 && this.roll == 0;
    }
}
