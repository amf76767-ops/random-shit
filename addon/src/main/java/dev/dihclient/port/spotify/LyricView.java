package dev.dihclient.port.spotify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ported from an open-source client (GPL-3.0). */
public final class LyricView {

    public static final int PX = 9;
    static final float ROW_GAP = 2.0F;
    static final float LINE_GAP = 3.0F;

    public static final float BLOCK_GAP = 6.0F;

    static final float DESCENT = 1.0F;

    static final float STATUS_HEIGHT = 8.0F;
    private static final float RISE = 5.0F;
    private static final float TOP_FADE_SPAN = 5.0F;
    private static final float SPRING_SETTLED = 1.37F;
    private static final String[] NO_ROWS = new String[0];

    public interface Metrics {
        float width(String text);
    }

    public record Item(String text, float top, float highlight, float alpha, float progress, float fill) {
    }

    public record View(List<Item> items, String status, float height) {
    }

    private record Shift(float distance, float at, int start, float stagger) {
    }

    private record Words(int[] from, int[] to, long[] start, long[] sweep) {
    }

    private final Metrics metrics;
    private Lyrics prepared;
    private String[] texts = NO_ROWS;
    private String[][] rows = new String[0][];
    private int rowsWidth = -1;
    private Words[] words = new Words[0];
    private float charMs = 90.0F;
    private float viewH = -1.0F;
    private int viewLines;
    private SyncedLyrics source;
    private int start;
    private final List<Shift> shifts = new ArrayList<>(8);
    private float clock;
    private List<Item> fadeItems;
    private float fade = 1.0F;
    private List<Item> lastItems = List.of();
    private List<Item> leaveItems;
    private float leave;
    private float[] shown = new float[0];
    private float breath;
    private int currentFirst = -1;
    private int currentLast = -1;
    private float[] bright = new float[0];
    private int brightLo;
    private int brightHi = -1;
    private float blockH;
    private long loadingSince;

    public LyricView(Metrics metrics) {
        this.metrics = metrics;
    }

    public float breath() {
        return this.breath / 1.6F;
    }

    public void reset() {
        this.prepared = null;
        this.source = null;
        this.lastItems = List.of();
        this.leaveItems = null;
        this.fadeItems = null;
        this.shifts.clear();
        this.blockH = 0.0F;
        this.loadingSince = 0L;
        this.viewH = -1.0F;
    }

    public View update(Lyrics lyrics, boolean wanted, boolean online, long position, int lines, LyricMotion motion, LyricHighlight highlight, float innerW,
            float dt, long nowMs) {
        List<Item> items = List.of();
        String status = null;
        float content = -1.0F;
        boolean live = false;
        if (!wanted) {
            this.loadingSince = 0L;
            content = 0.0F;
        } else {
            if (lyrics == null || lyrics.status() != Lyrics.Status.LOADING) {
                this.loadingSince = 0L;
            } else if (this.loadingSince == 0L) {
                this.loadingSince = nowMs;
            }
            if (lyrics != null) {
                switch (lyrics.status()) {

                    case LOADING -> status = nowMs - this.loadingSince < 700L ? null : "Loading lyrics";
                    case SYNCED -> {
                        this.prepare(lyrics);
                        items = new ArrayList<>(16);
                        float height = this.viewHeight(lyrics.synced(), lines, innerW);
                        this.syncedItems(items, lyrics.synced(), position, height, motion, highlight, innerW, dt);
                        content = height - DESCENT;
                        live = true;
                    }
                    case PLAIN -> status = "No synced lyrics";
                    case INSTRUMENTAL -> status = "Instrumental";
                    case NOT_FOUND -> status = online ? "No lyrics found" : "No saved lyrics";
                    case UNAVAILABLE -> status = "Lyrics unavailable";
                }
            }
        }
        if (status != null) {
            content = STATUS_HEIGHT;
        }
        if (live) {
            this.lastItems = items;
            this.leaveItems = null;
        } else {
            items = this.leavingItems(status == null, dt);
        }
        float target = content < 0.0F ? this.blockH : (content > 0.0F ? BLOCK_GAP + Math.round(content) : 0.0F);
        return new View(items, status, this.easeBlock(target, dt));
    }

    private List<Item> leavingItems(boolean quiet, float dt) {
        this.source = null;
        if (!this.lastItems.isEmpty()) {
            this.leaveItems = this.lastItems;
            this.leave = 0.0F;
            this.lastItems = List.of();
        }
        if (this.leaveItems == null) {
            return List.of();
        }
        this.leave = Math.min(1.0F, this.leave + dt / 0.32F);
        if (quiet && this.leave < 1.0F) {
            List<Item> items = new ArrayList<>(this.leaveItems.size());
            addFaded(items, this.leaveItems, smooth(this.leave), RISE);
            return items;
        }
        this.leaveItems = null;
        return List.of();
    }

    private static void addFaded(List<Item> items, List<Item> frozen, float fade, float rise) {
        for (Item item : frozen) {
            float alpha = item.alpha() * (1.0F - fade);
            if (alpha > 0.004F) {
                items.add(new Item(item.text(), item.top() - rise * fade, item.highlight(), alpha, item.progress(), item.fill()));
            }
        }
    }

    private void syncedItems(List<Item> items, SyncedLyrics synced, long position, float viewH, LyricMotion motion, LyricHighlight highlight,
            float innerW, float dt) {
        int currentLast = synced.indexAt(position + 200L);
        int currentFirst = synced.firstWithTimeOf(currentLast);
        int start = Math.max(0, currentFirst);
        if (synced != this.source) {
            this.source = synced;
            this.start = start;
            this.shifts.clear();
            this.clock = 0.0F;
            this.fadeItems = null;
            this.fade = 1.0F;
            this.shown = new float[synced.size() * 2];
            this.bright = new float[synced.size()];
            this.brightLo = 0;
            this.brightHi = -1;
            for (int i = Math.max(0, currentFirst); i <= currentLast; i++) {
                this.bright[i] = 1.0F;
            }
            if (currentLast >= 0) {
                this.brightLo = Math.max(0, currentFirst);
                this.brightHi = currentLast;
            }
        }
        this.clock += dt;
        this.breath = (this.breath + dt) % 1.6F;
        this.currentFirst = currentFirst;
        this.currentLast = currentLast;
        float stagger = motion == LyricMotion.WAVE ? 0.045F : 0.0F;
        if (start != this.start) {

            float distance = start > this.start ? this.restDistance(this.start, start, innerW) : -1.0F;
            if (motion != LyricMotion.FADE && distance > 0.0F && distance <= viewH + LINE_GAP + 0.5F) {
                if (this.shifts.size() == 8) {
                    this.shifts.remove(0);
                }
                this.shifts.add(new Shift(distance, this.clock, start, stagger));
            } else {
                this.fadeItems = this.lastItems;
                this.fade = 0.0F;
                this.shifts.clear();
                this.showView(synced, start, viewH, innerW);
            }
            this.start = start;
        }
        this.updateBrightness(currentFirst, currentLast, dt);
        this.fade = Math.min(1.0F, this.fade + dt / 0.32F);
        if (this.fade >= 1.0F) {
            this.fadeItems = null;
        }
        this.shifts.removeIf(shift -> this.clock - shift.at() > SPRING_SETTLED);
        if (this.shifts.isEmpty()) {
            this.clock = 0.0F;
        }
        if (this.fadeItems != null) {
            float fade = smooth(this.fade);
            addFaded(items, this.fadeItems, fade, RISE);
            this.addView(items, synced, this.start, fade, RISE * (1.0F - fade), viewH, position, highlight, innerW, dt);
        } else {
            this.addView(items, synced, this.start, 1.0F, 0.0F, viewH, position, highlight, innerW, dt);
        }
    }

    private void addView(List<Item> items, SyncedLyrics synced, int from, float alpha, float rise, float viewH, long position,
            LyricHighlight mode, float innerW, float dt) {
        float slot = PX + LINE_GAP;
        boolean karaoke = mode == LyricHighlight.KARAOKE;
        float restTop = 0.0F;
        for (int i = from; i < synced.size() && restTop <= viewH + slot * 2.0F; i++) {
            String[] rows = this.rowsFor(i, innerW);
            int count = Math.max(1, rows.length);
            float lineTop = restTop + this.offset(i) + rise;
            float depth = 1.0F - 0.4F * clamp01(lineTop / slot - 1.0F);
            float glow = this.glow(i, mode);
            float progress = rows.length == 0 ? this.breakProgress(synced, i, position) : 1.0F;
            for (int r = 0; r < count; r++) {
                float rowRest = restTop + r * (PX + ROW_GAP);
                float shown = this.updateShown(i, r, rowRest + PX <= viewH + 0.5F, dt);
                float top = lineTop + r * (PX + ROW_GAP);
                float rowAlpha = alpha * depth * smooth(shown) * topFade(top) * bottomFade(top + PX - viewH);
                if (rowAlpha > 0.004F) {
                    float fill = karaoke && rows.length > 0 ? this.rowFill(synced, i, rows, r, position) : 0.0F;
                    items.add(new Item(rows.length == 0 ? null : rows[r], top, glow, rowAlpha, progress, fill));
                }
            }
            restTop += lineHeight(rows) + LINE_GAP;
        }

        float lineRest = 0.0F;
        for (int i = from - 1; i >= 0; i--) {
            String[] rows = this.rowsFor(i, innerW);
            int count = Math.max(1, rows.length);
            lineRest -= lineHeight(rows) + LINE_GAP;
            float lineTop = lineRest + this.offset(i) + rise;
            if (topFade(lineTop + (count - 1) * (PX + ROW_GAP)) <= 0.0F) {
                break;
            }
            float glow = this.glow(i, mode);
            float progress = rows.length == 0 ? this.breakProgress(synced, i, position) : 1.0F;
            for (int r = 0; r < count; r++) {
                float top = lineTop + r * (PX + ROW_GAP);
                float rowAlpha = alpha * smooth(this.shown[i * 2 + r]) * topFade(top);
                if (rowAlpha > 0.004F) {
                    float fill = karaoke && rows.length > 0 ? this.rowFill(synced, i, rows, r, position) : 0.0F;
                    items.add(new Item(rows.length == 0 ? null : rows[r], top, glow, rowAlpha, progress, fill));
                }
            }
        }
    }

    private float offset(int index) {
        float offset = 0.0F;
        for (Shift shift : this.shifts) {
            float delay = Math.max(0, Math.min(6, index - shift.start())) * shift.stagger();
            float time = this.clock - shift.at() - delay;
            offset += time <= 0.0F ? shift.distance() : shift.distance() * springLeft(time);
        }
        return offset;
    }

    public static float springLeft(float time) {
        float wt = 13.0F * time;
        return (1.0F + wt) * (float) Math.exp(-wt);
    }

    public static float topFade(float top) {
        return top >= 0.0F ? 1.0F : smooth(1.0F + top / TOP_FADE_SPAN);
    }

    public static float bottomFade(float below) {
        return below <= 0.5F ? 1.0F : smooth(1.0F - below / (PX * 0.6F));
    }

    private float updateShown(int index, int row, boolean fits, float dt) {
        int k = index * 2 + row;
        if (k >= this.shown.length) {
            return fits ? 1.0F : 0.0F;
        }
        float step = dt / 0.3F;
        float shown = fits ? Math.min(1.0F, this.shown[k] + step) : Math.max(0.0F, this.shown[k] - step);
        this.shown[k] = shown;
        return shown;
    }

    private void showView(SyncedLyrics synced, int from, float viewH, float innerW) {
        Arrays.fill(this.shown, 0.0F);
        float restTop = 0.0F;
        for (int i = from; i < synced.size() && restTop < viewH; i++) {
            String[] rows = this.rowsFor(i, innerW);
            int count = Math.max(1, rows.length);
            for (int r = 0; r < count; r++) {
                if (restTop + r * (PX + ROW_GAP) + PX <= viewH + 0.5F && i * 2 + r < this.shown.length) {
                    this.shown[i * 2 + r] = 1.0F;
                }
            }
            restTop += lineHeight(rows) + LINE_GAP;
        }
    }

    private float restDistance(int from, int to, float innerW) {
        float distance = 0.0F;
        for (int i = from; i < to; i++) {
            distance += lineHeight(this.rowsFor(i, innerW)) + LINE_GAP;
        }
        return distance;
    }

    private void updateBrightness(int currentFirst, int currentLast, float dt) {
        int lo = this.brightHi >= 0 ? this.brightLo : Integer.MAX_VALUE;
        int hi = this.brightHi;
        if (currentLast >= 0) {
            lo = Math.min(lo, Math.max(0, currentFirst));
            hi = Math.max(hi, currentLast);
        }
        float step = dt / 0.35F;
        int lit = Integer.MAX_VALUE;
        int last = -1;
        for (int i = Math.max(0, lo); i <= hi && i < this.bright.length; i++) {
            boolean sung = i >= currentFirst && i <= currentLast;
            float value = sung ? Math.min(1.0F, this.bright[i] + step) : Math.max(0.0F, this.bright[i] - step);
            this.bright[i] = value;
            if (value > 0.0F) {
                lit = Math.min(lit, i);
                last = i;
            }
        }
        this.brightLo = last < 0 ? 0 : lit;
        this.brightHi = last;
    }

    private float glow(int index, LyricHighlight mode) {
        if (mode != LyricHighlight.OFF && index >= 0 && index < this.currentFirst) {
            return 1.0F;
        }
        return index >= 0 && index < this.bright.length ? smooth(this.bright[index]) : 0.0F;
    }

    private float breakProgress(SyncedLyrics synced, int index, long position) {
        if (index < this.currentFirst || index > this.currentLast) {
            return 1.0F;
        }
        int next = synced.lastWithTimeOf(index) + 1;
        if (next >= synced.size()) {
            return 1.0F;
        }
        long begin = synced.line(index).timeMs();
        long end = synced.line(next).timeMs() - 200L;
        return end <= begin ? 1.0F : clamp01((float) (position - begin) / (float) (end - begin));
    }

    private float rowFill(SyncedLyrics synced, int index, String[] rows, int row, long position) {
        if (index < this.currentFirst) {
            return Float.POSITIVE_INFINITY;
        }
        if (index > this.currentLast) {
            return 0.0F;
        }
        String text = rows[row];
        int rowStart = 0;
        if (row > 0) {
            String line = this.texts[index];
            rowStart = rows[0].length();
            while (rowStart < line.length() && line.charAt(rowStart) == ' ') {
                rowStart++;
            }
        }
        float lit = this.litChars(synced, index, position) - rowStart;
        if (lit <= 0.0F) {
            return 0.0F;
        }
        if (lit >= text.length()) {
            return Float.POSITIVE_INFINITY;
        }
        int whole = (int) lit;
        if (whole > 0 && Character.isLowSurrogate(text.charAt(whole))) {
            whole--;
        }
        int next = whole + Character.charCount(text.codePointAt(whole));
        float x0 = this.metrics.width(text.substring(0, whole));
        float x1 = this.metrics.width(text.substring(0, next));
        return x0 + (x1 - x0) * clamp01((lit - whole) / (next - whole));
    }

    private float litChars(SyncedLyrics synced, int index, long position) {
        Words words = this.words[index];
        if (words == null) {
            this.timeWords(synced, synced.firstWithTimeOf(index));
            words = this.words[index];
        }
        float lit = 0.0F;
        long[] start = words.start();
        for (int k = 0; k < start.length && position >= start[k]; k++) {
            float swept = smooth((float) (position - start[k]) / (float) words.sweep()[k]);
            lit = words.from()[k] + (words.to()[k] - words.from()[k]) * swept;
        }
        return lit;
    }

    private void timeWords(SyncedLyrics synced, int first) {
        int last = synced.lastWithTimeOf(first);
        int next = last + 1;
        long begin = synced.line(first).timeMs();
        long handover = next < synced.size() ? synced.line(next).timeMs() - 200L : Long.MAX_VALUE;
        int letters = 0;
        for (int i = first; i <= last; i++) {
            letters += letterCount(this.texts[i]);
        }
        long window = next < synced.size() ? Math.min(handover - begin, Math.round(letters * this.charMs * 2.0F))
                : Math.round(letters * this.charMs);
        window = Math.max(0L, window);
        int done = 0;
        for (int i = first; i <= last; i++) {
            String text = this.texts[i];
            int count = text.isEmpty() ? 0 : text.length() - letterCount(text) + 1;
            int[] from = new int[count];
            int[] to = new int[count];
            long[] start = new long[count];
            long[] sweep = new long[count];
            List<Long> timed = synced.line(i).wordTimes();
            boolean own = timed.size() == count;
            for (int k = 0, c = 0; k < count; k++) {
                from[k] = c;
                c = text.indexOf(32, c);
                if (c < 0) {
                    c = text.length();
                }
                to[k] = c++;
                long at = own ? timed.get(k) : begin + window * done / letters;
                done += to[k] - from[k];
                long until = own ? (k + 1 < count ? timed.get(k + 1) : Math.min(handover, at + 180L)) : begin + window * done / letters;
                start[k] = at;
                sweep[k] = Math.max(1L, Math.min(180L, until - at));
            }
            this.words[i] = new Words(from, to, start, sweep);
        }
    }

    private float pace(SyncedLyrics synced) {
        float[] paces = new float[synced.size()];
        int n = 0;
        int first = 0;
        while (first < synced.size()) {
            int last = synced.lastWithTimeOf(first);
            int letters = 0;
            for (int i = first; i <= last; i++) {
                letters += letterCount(this.texts[i]);
            }
            if (letters > 0 && last + 1 < synced.size()) {
                paces[n++] = (float) (synced.line(last + 1).timeMs() - synced.line(first).timeMs()) / letters;
            }
            first = last + 1;
        }
        if (n == 0) {
            return 90.0F;
        }
        Arrays.sort(paces, 0, n);
        return paces[n / 2];
    }

    public static int letterCount(String text) {
        int letters = text.length();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                letters--;
            }
        }
        return letters;
    }

    private float viewHeight(SyncedLyrics synced, int lines, float innerW) {
        this.prepareRows(innerW);
        if (this.viewH >= 0.0F && this.viewLines == lines) {
            return this.viewH;
        }
        int slots = lines;
        int start = 0;
        while (start < synced.size()) {
            int rows = 0;
            int last = synced.lastWithTimeOf(start);
            for (int i = start; i <= last; i++) {
                rows += Math.max(1, this.rowsFor(i, innerW).length);
            }
            slots = Math.max(slots, Math.min(4, rows));
            start = last + 1;
        }
        this.viewH = slots * PX + (slots - 1) * LINE_GAP;
        this.viewLines = lines;
        return this.viewH;
    }

    private static float lineHeight(String[] rows) {
        return rows.length <= 1 ? PX : rows.length * PX + (rows.length - 1) * ROW_GAP;
    }

    private float easeBlock(float target, float dt) {
        float diff = target - this.blockH;
        if (Math.abs(diff) < 0.5F) {
            this.blockH = target;
        } else {
            float speed = diff > 0.0F ? 14.0F : 9.0F;
            this.blockH = this.blockH + diff * (1.0F - (float) Math.exp(-speed * dt));
        }
        return this.blockH;
    }

    private void prepare(Lyrics lyrics) {
        if (lyrics == this.prepared) {
            return;
        }
        this.prepared = lyrics;
        this.viewH = -1.0F;
        SyncedLyrics synced = lyrics.synced();
        this.texts = new String[synced.size()];
        this.rows = new String[synced.size()][];
        this.rowsWidth = -1;
        this.words = new Words[synced.size()];
        for (int i = 0; i < synced.size(); i++) {
            this.texts[i] = DisplayText.fold(synced.line(i).text());
        }
        this.charMs = this.pace(synced);
    }

    private void prepareRows(float maxW) {
        int widthKey = (int) maxW;
        if (widthKey != this.rowsWidth) {
            Arrays.fill(this.rows, null);
            this.rowsWidth = widthKey;
            this.viewH = -1.0F;
        }
    }

    private String[] rowsFor(int index, float maxW) {
        this.prepareRows(maxW);
        String[] rows = this.rows[index];
        if (rows == null) {
            rows = this.wrap(this.texts[index], this.rowsWidth);
            this.rows[index] = rows;
        }
        return rows;
    }

    public String[] wrap(String text, float maxW) {
        if (text.isEmpty()) {
            return NO_ROWS;
        }
        if (this.metrics.width(text) <= maxW) {
            return new String[] {text};
        }
        int best = -1;
        float bestW = Float.MAX_VALUE;
        for (int i = text.indexOf(32); i > 0; i = text.indexOf(32, i + 1)) {
            float firstW = this.metrics.width(text.substring(0, i).stripTrailing());
            if (firstW > maxW) {
                break;
            }
            float secondW = this.metrics.width(text.substring(i + 1).stripLeading());
            if (secondW <= maxW && Math.max(firstW, secondW) < bestW) {
                bestW = Math.max(firstW, secondW);
                best = i;
            }
        }
        if (best > 0) {
            return new String[] {text.substring(0, best).stripTrailing(), text.substring(best + 1).stripLeading()};
        }
        int cut = this.fitPrefix(text, maxW);
        String first = text.substring(0, cut).stripTrailing();
        String rest = text.substring(cut).stripLeading();
        return rest.isEmpty() ? new String[] {first} : new String[] {first, this.metrics.width(rest) <= maxW ? rest : this.ellipsize(rest, maxW)};
    }

    private int fitPrefix(String text, float maxW) {
        int lastBreak = -1;
        for (int i = text.indexOf(32); i > 0 && this.metrics.width(text.substring(0, i)) <= maxW; i = text.indexOf(32, i + 1)) {
            lastBreak = i;
        }
        if (lastBreak > 0) {
            return lastBreak + 1;
        }
        int lo = 1;
        int hi = text.length();
        while (lo < hi) {
            int mid = lo + hi + 1 >>> 1;
            if (this.metrics.width(text.substring(0, mid)) <= maxW) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (lo > 1 && lo < text.length() && Character.isHighSurrogate(text.charAt(lo - 1))) {
            lo--;
        }
        return lo;
    }

    public String ellipsize(String text, float maxW) {
        float room = maxW - this.metrics.width("...");
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = lo + hi + 1 >>> 1;
            if (this.metrics.width(text.substring(0, mid)) <= room) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo).stripTrailing() + "...";
    }

    public static float smooth(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    public static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
