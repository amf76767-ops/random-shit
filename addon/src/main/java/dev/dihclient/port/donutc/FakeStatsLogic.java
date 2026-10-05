package dev.dihclient.port.donutc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ported from an open-source client (GPL-3.0). */
public final class FakeStatsLogic {

    public record Hit(int stat, boolean inNumber, int start, int end) {
    }

    private static final Pattern MONEY = Pattern.compile("\\$\\s?(\\d[\\d.,]*[KkMmBbTtQq]?)");

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private FakeStatsLogic() {
    }

    public static Hit find(List<String[]> markers, String name, String number) {
        for (int i = 0; i < markers.size(); i++) {
            String[] stat = markers.get(i);
            int[] value = valueAfter(name, stat);
            if (value != null && value[0] < value[1]) {
                return new Hit(i, false, value[0], value[1]);
            }
            if (value != null) {

                return new Hit(i, true, 0, number.length());
            }
            value = valueAfter(number, stat);
            if (value != null) {
                return new Hit(i, true, value[0], value[1]);
            }
        }
        return null;
    }

    public static int[] valueAfter(String line, String[] markers) {
        for (String marker : markers) {
            int at = markerAt(line, marker);
            if (at >= 0) {
                int start = at + marker.length();
                int end = line.length();
                while (start < end && (Character.isWhitespace(line.charAt(start)) || line.charAt(start) == ':')) {
                    start++;
                }
                while (end > start && Character.isWhitespace(line.charAt(end - 1))) {
                    end--;
                }
                return new int[] {start, end};
            }
        }
        return null;
    }

    public static int markerAt(String line, String marker) {
        if (marker.isEmpty()) {
            return -1;
        }
        String text = folded(line);
        boolean wordStart = wordChar(marker.charAt(0));
        boolean wordEnd = wordChar(marker.charAt(marker.length() - 1));
        for (int at = 0; at + marker.length() <= text.length(); at++) {
            int end = at + marker.length();
            if (text.regionMatches(true, at, marker, 0, marker.length())
                    && (!wordStart || at <= 0 || !wordChar(text.charAt(at - 1)))
                    && (!wordEnd || end >= text.length() || !wordChar(text.charAt(end)))) {
                return at;
            }
        }
        return -1;
    }

    public static String folded(String line) {
        char[] out = line.toCharArray();
        for (int i = 0; i < out.length; i++) {
            int letter = SMALL_CAPS.indexOf(out[i]);
            if (letter >= 0) {
                out[i] = (char) ('a' + letter);
            }
        }
        return new String(out);
    }

    public static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public static String fit(String real, String custom) {
        String bare = custom.startsWith("$") ? custom.substring(1).strip() : custom;
        return real.startsWith("$") ? "$" + bare : bare;
    }

    public static List<int[]> moneyRanges(String text) {
        Matcher matcher = MONEY.matcher(text);
        List<int[]> ranges = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new int[] {matcher.start(1), matcher.end(1)});
        }
        return ranges;
    }

    public static String tabMoney(String custom, String sidebarMoney) {
        String shown = custom.strip().isEmpty() ? sidebarMoney : custom.strip();
        if (shown == null) {
            return null;
        }
        shown = shown.startsWith("$") ? shown.substring(1).strip() : shown.strip();
        return shown.isEmpty() ? null : shown;
    }

    public static String escaped(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c < 127) {
                out.append(c);
            } else {
                out.append(String.format("\\u%04X", (int) c));
            }
        }
        return out.toString();
    }

    public static String holderName(int row) {
        return new String(new char[] {'', (char) (0xE010 + row)});
    }

    public static String maskText(long seed, String text, int row, int[] number) {
        StringBuilder out = null;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (!Character.isDigit(cp)) {
                if (out != null) {
                    out.appendCodePoint(cp);
                }
                i += Character.charCount(cp);
                continue;
            }
            int end = i;
            while (end < text.length()) {
                int c = text.codePointAt(end);
                int width = Character.charCount(c);
                if (Character.isDigit(c)) {
                    end += width;
                } else if ((c == ',' || c == '.') && end + width < text.length() && Character.isDigit(text.codePointAt(end + width))) {
                    end += width;
                } else {
                    break;
                }
            }
            String raw = text.substring(i, end);
            String fake = fakeNumber(seed, row, number[0]++, raw);
            if (out == null) {
                if (!fake.equals(raw)) {
                    out = new StringBuilder(text.length()).append(text, 0, i).append(fake);
                }
            } else {
                out.append(fake);
            }
            i = end;
        }
        return out == null ? text : out.toString();
    }

    public static String fakeNumber(long seed, int row, int index, String raw) {
        int digits = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits++;
            }
        }
        if (digits == 0) {
            return raw;
        }
        if (digits > 15) {
            return shiftWide(seed, row, index, raw);
        }
        long value = 0L;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                value = value * 10L + (c - '0');
            }
        }
        long span = pow10(digits);
        long offset = Long.remainderUnsigned(mix(seed ^ row * 1315423911L ^ index * 2654435761L ^ digits), span);
        if (offset == 0L) {
            offset = 1L;
        }
        boolean leadingZero = raw.charAt(0) == '0' && digits > 1;
        long fake = shifted(value, offset, span, digits, leadingZero);
        if (fake == value) {
            fake = shifted(value, offset == span - 1L ? 1L : offset + 1L, span, digits, leadingZero);
        }
        String rendered = Long.toString(fake);
        if (rendered.length() < digits) {
            rendered = "0".repeat(digits - rendered.length()) + rendered;
        }
        StringBuilder result = new StringBuilder(raw.length());
        int cursor = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                result.append(rendered.charAt(cursor++));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static long shifted(long value, long offset, long span, int digits, boolean leadingZero) {
        long fake = Math.floorMod(value + offset, span);
        if (!leadingZero && digits > 1 && fake < span / 10L) {

            fake += span / 10L;
        }
        return fake;
    }

    private static String shiftWide(long seed, int row, int index, String raw) {
        long salt = mix(seed ^ row * 1315423911L ^ index);
        StringBuilder result = new StringBuilder(raw.length());
        int pos = 0;
        boolean first = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                int shift = (int) Long.remainderUnsigned(mix(salt + pos), 9L) + 1;
                int shown = (c - '0' + shift) % 10;
                if (first && shown == 0) {
                    shown = shift;
                }
                first = false;
                result.append((char) ('0' + shown));
                pos++;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static long pow10(int digits) {
        long value = 1L;
        for (int i = 0; i < digits; i++) {
            value *= 10L;
        }
        return value;
    }

    private static long mix(long z) {
        z = (z ^ z >>> 30) * -4658895280553007687L;
        z = (z ^ z >>> 27) * -7723592293110705685L;
        return z ^ z >>> 31;
    }
}
