package dev.dihclient.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A DIHClient version such as {@code 5.7.0+mc1.21.11}, {@code v5.7.0} or {@code 5.7.0-beta.1+mc1.21.11}.
 * Compared by the numeric core, then by pre-release (a pre-release is lower than the release).
 * The Minecraft part is kept separately and never takes part in the ordering.
 */
public final class Version implements Comparable<Version> {
    private static final Pattern MC = Pattern.compile("(?i)\\+?mc[-_]?(\\d+(?:\\.\\d+){1,2})");

    private final int[] nums;
    private final String pre;
    private final String mc;
    private final String raw;

    private Version(int[] nums, String pre, String mc, String raw) {
        this.nums = nums;
        this.pre = pre;
        this.mc = mc;
        this.raw = raw;
    }

    /** Returns null when the text has no usable version number. */
    public static Version parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        String mc = null;
        Matcher m = MC.matcher(s);
        if (m.find()) {
            mc = m.group(1);
            s = (s.substring(0, m.start()) + s.substring(m.end())).trim();
        }
        int plus = s.indexOf('+');
        if (plus >= 0) {
            s = s.substring(0, plus);
        }
        Matcher core = Pattern.compile("(?i)v?(\\d+(?:\\.\\d+){0,3})(?:[-_.]?((?:alpha|beta|rc|pre|snapshot|dev)[\\w.]*))?").matcher(s);
        if (!core.find()) {
            return null;
        }
        List<Integer> parts = new ArrayList<>();
        for (String p : core.group(1).split("\\.")) {
            try {
                parts.add(Integer.parseInt(p));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        int[] nums = new int[parts.size()];
        for (int i = 0; i < nums.length; i++) {
            nums[i] = parts.get(i);
        }
        String pre = core.group(2) == null ? null : core.group(2).toLowerCase(Locale.ROOT);
        return new Version(nums, pre, mc, text.trim());
    }

    /** The Minecraft version this build is for, or null when the text does not say. */
    public String minecraft() {
        return mc;
    }

    public boolean isPreRelease() {
        return pre != null;
    }

    public String raw() {
        return raw;
    }

    /** {@code 5.7.0} or {@code 5.7.0-beta.1}, without the Minecraft part. */
    public String display() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < nums.length; i++) {
            if (i > 0) {
                b.append('.');
            }
            b.append(nums[i]);
        }
        if (pre != null) {
            b.append('-').append(pre);
        }
        return b.toString();
    }

    @Override
    public int compareTo(Version o) {
        int n = Math.max(nums.length, o.nums.length);
        for (int i = 0; i < n; i++) {
            int a = i < nums.length ? nums[i] : 0;
            int b = i < o.nums.length ? o.nums[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        if (pre == null && o.pre == null) {
            return 0;
        }
        if (pre == null) {
            return 1;
        }
        if (o.pre == null) {
            return -1;
        }
        return pre.compareTo(o.pre);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Version v && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        return display().hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
