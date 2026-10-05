package dev.dihclient.port.discord;

import com.google.gson.JsonObject;

/** Ported from an open-source client (GPL-3.0). */
public record DiscordCard(String details, String state, long startedAt, String appId, String largeImage, String largeText) {

    static final int MIN_TEXT = 2;
    static final int MAX_TEXT = 128;

    public enum Place { MENU, SINGLEPLAYER, LAN, REALMS, MULTIPLAYER }

    public static final String PLAYING = "Playing Minecraft";
    public static final String IN_MENU = "In Main Menu";

    public static String details(Place place, String address, boolean showAddress) {
        switch (place) {
            case MENU:
                return IN_MENU;
            case SINGLEPLAYER:
                return showAddress ? "Singleplayer" : PLAYING;
            case LAN:
                return showAddress ? "LAN World" : PLAYING;
            case REALMS:
                return showAddress ? "Realms" : PLAYING;
            default:
                String shown = showAddress ? clip(address) : null;
                return shown != null ? shown : (showAddress ? "Multiplayer" : PLAYING);
        }
    }

    public static String clip(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (cp == '\t' || cp == '\n' || cp == '\r') {
                out.append(' ');
            } else if (!Character.isISOControl(cp)) {
                out.appendCodePoint(cp);
            }
        });
        String s = out.toString().strip();
        if (s.codePointCount(0, s.length()) > MAX_TEXT) {
            s = s.substring(0, s.offsetByCodePoints(0, MAX_TEXT)).strip();
        }
        return s.codePointCount(0, s.length()) >= MIN_TEXT ? s : null;
    }

    public static boolean validAppId(String id) {
        if (id == null) {
            return false;
        }
        String s = id.strip();
        if (s.length() < 17 || s.length() > 20) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    public static String assetKey(String key) {
        if (key == null) {
            return null;
        }
        String s = key.strip();
        if (s.isEmpty() || s.length() > 256) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i)) || Character.isISOControl(s.charAt(i))) {
                return null;
            }
        }
        return s;
    }

    public JsonObject activity() {
        return activity(this);
    }

    public static JsonObject activity(DiscordCard card) {
        JsonObject activity = new JsonObject();
        activity.addProperty("details", card.details());
        if (card.state() != null) {
            activity.addProperty("state", card.state());
        }
        if (card.startedAt() > 0) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", card.startedAt());
            activity.add("timestamps", timestamps);
        }
        if (card.largeImage() != null) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", card.largeImage());
            if (card.largeText() != null) {
                assets.addProperty("large_text", card.largeText());
            }
            activity.add("assets", assets);
        }
        return activity;
    }
}
