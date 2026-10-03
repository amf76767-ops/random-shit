package dev.dihclient.emote;

/** How far a body is moved and turned by an emote at one moment. Angles in degrees, height in blocks. */
public record EmotePose(float dy, float yaw, float pitch, float roll) {
    public static final EmotePose NONE = new EmotePose(0, 0, 0, 0);

    public boolean isNone() {
        return this.dy == 0 && this.yaw == 0 && this.pitch == 0 && this.roll == 0;
    }
}
