package dev.dihclient.port.spotify;

/** Ported from an open-source client (GPL-3.0). */
public enum MediaBind {
    NONE(-1, false),
    F6(295, false),
    F7(296, false),
    F8(297, false),
    F9(298, false),
    F10(299, false),
    F12(301, false),
    F13(302, false),
    F14(303, false),
    F15(304, false),
    INSERT(260, false),
    DELETE(261, false),
    HOME(268, false),
    END(269, false),
    PAGE_UP(266, false),
    PAGE_DOWN(267, false),
    ARROW_LEFT(263, false),
    ARROW_RIGHT(262, false),
    ARROW_UP(265, false),
    ARROW_DOWN(264, false),
    NUMPAD_4(324, false),
    NUMPAD_5(325, false),
    NUMPAD_6(326, false),
    LEFT_BRACKET(91, false),
    RIGHT_BRACKET(93, false),
    SEMICOLON(59, false),
    MOUSE_4(3, true),
    MOUSE_5(4, true);

    public final int code;
    public final boolean mouse;

    MediaBind(int code, boolean mouse) {
        this.code = code;
        this.mouse = mouse;
    }
}
