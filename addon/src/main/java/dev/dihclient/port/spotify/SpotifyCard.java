package dev.dihclient.port.spotify;

import dev.dihclient.DIHClient;
import dev.dihclient.hud.HudManager;
import dev.dihclient.hud.HudStyle;
import dev.dihclient.modules.render.NavigationHud;
import dev.dihclient.render.Gfx;
import dev.dihclient.util.ColorUtil;
import net.minecraft.class_10799;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Draws the Spotify card (cover, title, artist, transport row, progress bar, lyrics) or the one-line note shown while
 * nothing plays. Anubis drew this with ImGui inside its HUD renderer; this version uses the game's own GUI drawing and the
 * DIHClient HUD style. Everything is laid out in GUI pixels at scale 1 and scaled as a whole by the Scale setting.
 * All methods run on the render thread (the cover becomes a game texture here).
 */
final class SpotifyCard {
    private static final class_2960 ART_ID = class_2960.method_60655("dihclient", "spotify/cover");
    private static final int PAD = 6;
    private static final int GAP = 6;
    private static final int ROW = 9;
    private static final float SMALL = 0.8F;
    private static final int SMALL_ROW = 7;
    private static final int PROGRESS_H = 2;
    /** The cover is shown when the text column keeps at least this much room. */
    private static final int MIN_COLUMN_WITH_ART = 70;
    private static final float MARQUEE_PAUSE = 2.4F;
    private static final float MARQUEE_SPEED = 16.0F;
    private static final float LOOP_GAP = 20.0F;

    private final SpotifyHudModule module;
    private final PlaybackClock clock = new PlaybackClock();
    private final LyricView lyricView = new LyricView(text -> Gfx.width(text));
    private long lastFrameNs;
    private NowPlaying folded;
    private String title = "";
    private String artist = "";
    private float marqueeTime;
    private String marqueeTitle = "";
    private String marqueeArtist = "";
    private float lyricPlaying = 1.0F;
    // cover texture: the AlbumArt it was made from, and the one that failed (not retried every frame)
    private AlbumArt art;
    private AlbumArt failedArt;
    private boolean artRegistered;

    SpotifyCard(SpotifyHudModule module) {
        this.module = module;
    }

    /** Frees the cover texture. */
    void release() {
        this.art = null;
        if (this.artRegistered) {
            this.artRegistered = false;
            class_310.method_1551().method_1531().method_4615(ART_ID);
        }
    }

    // ---- frame ----

    void render(class_332 g, NowPlaying now) {
        float dt = this.frameDelta();
        long nowMs = System.currentTimeMillis();
        this.foldTrack(now);
        boolean card = now.active() && now.hasTrack() && now.source() == NowPlaying.Source.SESSION;
        Plan plan;
        if (card) {
            plan = this.planCard(now, nowMs, dt);
        } else {
            this.release();
            this.lyricView.reset();
            plan = this.planCompact(now, dt);
        }

        float sc = this.module.scale.getFloat();
        int sw = Math.round(plan.width * sc);
        int sh = Math.round(plan.height * sc);
        int screenW = g.method_51421();
        int screenH = g.method_51443();
        NavigationHud.Corner c = this.module.corner.get();
        int x = switch (c) {
            case TOP_LEFT, BOTTOM_LEFT -> this.module.offsetX.get();
            case TOP_RIGHT, BOTTOM_RIGHT -> screenW - sw - this.module.offsetX.get();
            case TOP_CENTER -> (screenW - sw) / 2 + this.module.offsetX.get();
        };
        int y = c == NavigationHud.Corner.BOTTOM_LEFT || c == NavigationHud.Corner.BOTTOM_RIGHT ? screenH - sh - this.module.offsetY.get()
                : this.module.offsetY.get();
        x = Math.max(0, Math.min(screenW - sw, x));
        y = Math.max(0, Math.min(screenH - sh, y));

        g.method_51448().pushMatrix();
        g.method_51448().translate(x, y);
        g.method_51448().scale(sc, sc);
        try {
            HudStyle.accentPanel(g, 0, 0, plan.width, plan.height);
            if (card) {
                this.drawCard(g, plan, now);
            } else {
                this.drawCompact(g, plan, now);
            }
        } finally {
            g.method_51448().popMatrix();
        }
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float dt = this.lastFrameNs == 0L ? 1.0F / 60.0F : (now - this.lastFrameNs) / 1.0e9F;
        this.lastFrameNs = now;
        return Math.max(0.0F, Math.min(0.1F, dt));
    }

    private void foldTrack(NowPlaying now) {
        if (now != this.folded) {
            this.folded = now;
            String folded = DisplayText.fold(now.title());
            this.title = folded.isEmpty() ? now.title() : folded;
            this.artist = DisplayText.fold(now.artist());
        }
    }

    // ---- plans ----

    private static final class Plan {
        int width;
        int height;
        String title = "";
        String artist = "";
        float titleW;
        float artistW;
        float titleOffset;
        float artistOffset;
        boolean scroll;
        // card
        boolean playing;
        boolean art;
        int header;
        int column;
        int artistY;
        int transportY;
        int progressY;
        boolean timeline;
        String elapsed = "";
        String remaining = "";
        float progress;
        boolean progressBar;
        LyricView.View lyrics;
        float lyricAlpha = 1.0F;
        // compact
        boolean idle;
        boolean pausedGlyph;
        boolean glyph;
    }

    private Plan planCard(NowPlaying track, long nowMs, float dt) {
        SpotifyHudModule m = this.module;
        Plan p = new Plan();
        boolean withArtist = m.showArtist.get();
        boolean withArt = m.showAlbumArt.get();
        p.scroll = m.scrollTitles.get();
        p.playing = track.playing();
        p.title = this.title;
        p.artist = withArtist ? this.artist : "";
        p.timeline = track.hasTimeline();
        this.clock.update(track, nowMs);
        long position = this.clock.positionAt(nowMs);
        p.progressBar = m.showProgress.get() && p.timeline;

        int y = 0;
        y += SMALL_ROW + 1;          // "NOW PLAYING"
        y += ROW;                    // title
        p.artistY = y + 1;
        if (!p.artist.isEmpty()) {
            y += 1 + SMALL_ROW;
        }
        p.transportY = y + 4;
        y = p.transportY + SMALL_ROW;
        p.progressY = y + 3;
        p.header = p.progressBar ? p.progressY + PROGRESS_H : y;

        p.width = m.cardWidth.get();
        p.art = withArt && p.width - PAD * 2 - p.header - GAP >= MIN_COLUMN_WITH_ART;
        p.column = p.width - PAD * 2 - (p.art ? p.header + GAP : 0);
        if (p.timeline) {
            long elapsedSeconds = position / 1000L;
            long totalSeconds = (track.durationMs() + 500L) / 1000L;
            p.elapsed = DisplayText.clock(elapsedSeconds);
            p.remaining = "−" + DisplayText.clock(Math.max(0L, totalSeconds - elapsedSeconds));
            p.progress = LyricView.clamp01((float) position / (float) track.durationMs());
        }

        p.titleW = Gfx.width(p.title);
        p.artistW = Gfx.width(p.artist) * SMALL;
        float[] offsets = this.marquee(p.title, p.artist, p.titleW, p.artistW, p.column, p.scroll, p.playing, dt);
        p.titleOffset = offsets[0];
        p.artistOffset = offsets[1];

        boolean wanted = m.wantsLyrics(track);
        Lyrics lyrics = wanted ? m.lyricsFor(track) : null;
        p.lyrics = this.lyricView.update(lyrics, wanted, m.lyricsOnline.get(), position, m.lyricLines.get(), m.lyricMotion.get(),
                m.lyricHighlight.get(), p.width - PAD * 2, dt, nowMs);
        this.lyricPlaying += ((p.playing ? 1.0F : 0.0F) - this.lyricPlaying) * (1.0F - (float) Math.exp(-8.0F * dt));
        p.lyricAlpha = 0.7F + 0.3F * this.lyricPlaying;
        p.height = Math.round(PAD * 2 + p.header + p.lyrics.height());
        return p;
    }

    private Plan planCompact(NowPlaying now, float dt) {
        SpotifyHudModule m = this.module;
        Plan p = new Plan();
        p.scroll = m.scrollTitles.get();
        switch (now.status()) {
            case PLAYING, PAUSED -> {
                p.glyph = true;
                p.playing = now.playing();
                p.title = now.hasTrack() ? this.title : "Not playing";
                p.artist = m.showArtist.get() ? this.artist : "";
            }
            case NOT_RUNNING -> {
                p.title = "Spotify is not open";
                p.idle = true;
            }
            default -> {
                p.title = "Nothing playing";
                p.idle = true;
            }
        }
        p.titleW = Gfx.width(p.title) * (p.idle ? SMALL : 1.0F);
        p.artistW = p.artist.isEmpty() ? 0.0F : Gfx.width(p.artist) * SMALL;
        int glyphW = p.glyph ? 12 : 0;
        int fixed = 8 * 2 + glyphW;
        int maxText = Math.max(40, m.cardWidth.get() - fixed);
        int textW = (int) Math.min(Math.max(p.titleW, p.artistW), maxText);
        p.width = (int) Math.ceil(fixed + textW);
        p.column = Math.max(1, p.width - fixed);
        p.height = p.artist.isEmpty() ? 20 : 6 * 2 + ROW + 2 + SMALL_ROW;
        float[] offsets = this.marquee(p.title, p.artist, p.titleW, p.artistW, p.column, p.scroll, now.playing(), dt);
        p.titleOffset = offsets[0];
        p.artistOffset = offsets[1];
        return p;
    }

    /** Scroll offsets of title and artist when they are wider than the column: wait, scroll, loop. */
    private float[] marquee(String title, String artist, float titleW, float artistW, float avail, boolean scroll, boolean advancing, float dt) {
        if (!title.equals(this.marqueeTitle) || !artist.equals(this.marqueeArtist)) {
            this.marqueeTitle = title;
            this.marqueeArtist = artist;
            this.marqueeTime = 0.0F;
        }
        float titleOffset = 0.0F;
        float artistOffset = 0.0F;
        float longest = Math.max(titleW > avail ? titleW : 0.0F, artistW > avail ? artistW : 0.0F);
        if (scroll && longest > 0.0F) {
            float period = MARQUEE_PAUSE + (longest + LOOP_GAP) / MARQUEE_SPEED;
            this.marqueeTime %= period;
            if (advancing || this.marqueeTime > MARQUEE_PAUSE) {
                this.marqueeTime = (this.marqueeTime + dt) % period;
            }
            float travelled = Math.max(0.0F, this.marqueeTime - MARQUEE_PAUSE) * MARQUEE_SPEED;
            if (titleW > avail) {
                titleOffset = Math.min(travelled, titleW + LOOP_GAP);
            }
            if (artistW > avail) {
                artistOffset = Math.min(travelled, artistW + LOOP_GAP);
            }
        } else {
            this.marqueeTime = 0.0F;
        }
        return new float[] {titleOffset, artistOffset};
    }

    // ---- drawing ----

    private void drawCard(class_332 g, Plan p, NowPlaying now) {
        SpotifyHudModule m = this.module;
        int left = PAD;
        if (p.art) {
            this.drawArt(g, PAD, PAD, p.header, now.art());
            left += p.header + GAP;
        } else {
            this.release();
        }
        int top = PAD;
        text(g, p.playing ? "NOW PLAYING" : "PAUSED", left, top, Gfx.MUTED, SMALL);
        int titleTop = top + SMALL_ROW + 1;
        marqueeLine(g, p.title, left, titleTop, p.column, p.titleW, p.titleOffset, p.scroll, m.titleColor.get(), 1.0F);
        if (!p.artist.isEmpty()) {
            marqueeLine(g, p.artist, left, top + p.artistY, p.column, p.artistW, p.artistOffset, p.scroll, m.artistColor.get(), SMALL);
        }
        this.drawTransport(g, p, left, top + p.transportY);
        if (p.progressBar) {
            Gfx.bar(g, left, top + p.progressY, p.column, PROGRESS_H, p.progress, 0x1AFFFFFF, p.playing ? HudManager.accent() : Gfx.MUTED);
        }
        if (p.lyrics.height() > 0.5F) {
            int blockTop = Math.round(PAD + p.header + LyricView.BLOCK_GAP);
            this.drawLyrics(g, p.lyrics, PAD, blockTop, p.width - PAD * 2, p.height - 1, p.lyricAlpha);
        }
    }

    private void drawTransport(class_332 g, Plan p, int left, int top) {
        int elapsedW = 0;
        int remainingW = 0;
        if (p.timeline) {
            elapsedW = Math.round(Gfx.width(p.elapsed) * SMALL);
            remainingW = Math.round(Gfx.width(p.remaining) * SMALL);
            text(g, p.elapsed, left, top, Gfx.MUTED, SMALL);
            text(g, p.remaining, left + p.column - remainingW, top, Gfx.MUTED, SMALL);
        }
        int skip = 5;
        int play = 7;
        int spacing = 9;
        int cluster = skip * 2 + play + spacing * 2;
        int centre = left + p.column / 2;
        int margin = 6;
        if (centre - cluster / 2 < left + elapsedW + margin || centre + cluster / 2 > left + p.column - remainingW - margin) {
            return;
        }
        int centerY = top + SMALL_ROW / 2;
        int playLeft = centre - play / 2;
        drawSkip(g, playLeft - spacing - skip, centerY, skip, false, Gfx.MUTED);
        drawPlayPause(g, playLeft, centerY, play, p.playing, Gfx.TEXT);
        drawSkip(g, playLeft + play + spacing, centerY, skip, true, Gfx.MUTED);
    }

    private static void drawSkip(class_332 g, int left, int centerY, int size, boolean next, int color) {
        int top = centerY - size / 2;
        int bar = Math.max(1, Math.round(size * 0.2F));
        if (next) {
            triangle(g, left, top, size - bar, size, true, color);
            g.method_25294(left + size - bar, top, left + size, top + size, color);
        } else {
            g.method_25294(left, top, left + bar, top + size, color);
            triangle(g, left + bar, top, size - bar, size, false, color);
        }
    }

    private static void drawPlayPause(class_332 g, int left, int centerY, int size, boolean playing, int color) {
        int top = centerY - size / 2;
        if (playing) {
            int bar = Math.max(2, Math.round(size * 0.3F));
            int gap = Math.max(1, size - bar * 2);
            g.method_25294(left, top, left + bar, top + size, color);
            g.method_25294(left + bar + gap, top, left + bar * 2 + gap, top + size, color);
        } else {
            triangle(g, left + 1, top, size - 1, size, true, color);
        }
    }

    /** Filled triangle from 1-px rows (the GUI has no triangle primitive): a tip on the right, or on the left. */
    static void triangle(class_332 g, int x, int y, int width, int height, boolean pointRight, int color) {
        for (int row = 0; row < height; row++) {
            float centre = (row + 0.5F) / height;
            int rowW = Math.max(1, Math.round(width * (1.0F - Math.abs(centre * 2.0F - 1.0F))));
            if (pointRight) {
                g.method_25294(x, y + row, x + rowW, y + row + 1, color);
            } else {
                g.method_25294(x + width - rowW, y + row, x + width, y + row + 1, color);
            }
        }
    }

    private void drawArt(class_332 g, int x, int y, int size, AlbumArt cover) {
        boolean drawn = false;
        if (cover != null && this.ensureTexture(cover)) {
            int side = cover.size();
            g.method_25302(class_10799.field_56883, ART_ID, x, y, 0.0F, 0.0F, size, size, side, side, side, side);
            drawn = true;
        }
        if (!drawn) {
            // no cover (yet): a dim square with three bars
            Gfx.rect(g, x, y, size, size, 3, 0x13FFFFFF);
            int bar = Math.max(2, Math.round(size * 0.1F));
            int gap = Math.max(2, Math.round(bar * 0.8F));
            int base = y + Math.round(size * 0.62F);
            int l = x + (size - bar * 3 - gap * 2) / 2;
            float[] heights = {0.2F, 0.34F, 0.26F};
            int color = fade(Gfx.MUTED, 0.45F);
            for (int i = 0; i < heights.length; i++) {
                int bx = l + i * (bar + gap);
                g.method_25294(bx, base - Math.round(size * heights[i]), bx + bar, base, color);
            }
        }
        Gfx.outline(g, x, y, size, size, 3, 0x18FFFFFF);
    }

    /** Turns the decoded cover into a game texture (once per cover). False when that failed. */
    private boolean ensureTexture(AlbumArt cover) {
        if (cover == this.art && this.artRegistered) {
            return true;
        }
        if (cover == this.failedArt) {
            return false;
        }
        this.release();
        try {
            int side = cover.size();
            class_1011 image = new class_1011(side, side, true);
            for (int py = 0; py < side; py++) {
                for (int px = 0; px < side; px++) {
                    image.method_61941(px, py, cover.argb(px, py));
                }
            }
            // the texture owns the image from here on
            class_310.method_1551().method_1531().method_4616(ART_ID, new class_1043(() -> "dihclient spotify cover", image));
            this.art = cover;
            this.artRegistered = true;
            return true;
        } catch (RuntimeException | LinkageError e) {
            DIHClient.LOG.warn("[DIHClient] Spotify HUD: cover texture failed", e);
            this.failedArt = cover;
            return false;
        }
    }

    private void drawCompact(class_332 g, Plan p, NowPlaying now) {
        SpotifyHudModule m = this.module;
        int x = 8;
        if (p.glyph) {
            int centerY = p.height / 2;
            if (p.playing) {
                triangle(g, x, centerY - 4, 7, 8, true, HudManager.accent());
            } else {
                g.method_25294(x + 1, centerY - 4, x + 3, centerY + 4, Gfx.MUTED);
                g.method_25294(x + 5, centerY - 4, x + 7, centerY + 4, Gfx.MUTED);
            }
            x += 12;
        }
        if (p.idle) {
            text(g, p.title, x, (p.height - SMALL_ROW) / 2, Gfx.MUTED, SMALL);
            return;
        }
        boolean twoLines = !p.artist.isEmpty();
        int contentH = twoLines ? ROW + 2 + SMALL_ROW : ROW;
        int titleTop = (p.height - contentH) / 2;
        float dim = p.playing ? 1.0F : 0.62F;
        marqueeLine(g, p.title, x, titleTop, p.column, p.titleW, p.titleOffset, p.scroll, fade(m.titleColor.get(), dim), 1.0F);
        if (twoLines) {
            marqueeLine(g, p.artist, x, titleTop + ROW + 2, p.column, p.artistW, p.artistOffset, p.scroll, m.artistColor.get(), SMALL);
        }
    }

    private void drawLyrics(class_332 g, LyricView.View view, int left, int top, int width, int bottom, float alpha) {
        SpotifyHudModule m = this.module;
        int clipTop = Math.round(top - LyricView.BLOCK_GAP + 2);
        if (bottom <= clipTop || alpha <= 0.001F) {
            return;
        }
        LyricHighlight mode = m.lyricHighlight.get();
        int highlight = m.highlightColor.get();
        int lyric = mode == LyricHighlight.LINE ? highlight : m.lyricsColor.get();
        int upcoming = m.upcomingLyricsColor.get();
        int lit = mode == LyricHighlight.OFF ? m.lyricsColor.get() : highlight;
        int clipLeft = left - 2;
        int clipRight = left + width + 2;
        g.method_44379(clipLeft, clipTop, clipRight, bottom);
        try {
            if (view.status() != null) {
                Gfx.text(g, view.status(), left, top, fade(Gfx.MUTED, alpha * 0.85F));
            }
            for (LyricView.Item item : view.items()) {
                float a = alpha * item.alpha();
                if (a <= 0.004F) {
                    continue;
                }
                int rowTop = top + Math.round(item.top());
                if (item.text() == null) {
                    this.drawBreak(g, left, rowTop + LyricView.PX / 2, upcoming, lit, item.highlight(), item.progress(), a);
                    continue;
                }
                int color = fade(ColorUtil.blend(upcoming, lyric, item.highlight()), a);
                int litColor = fade(lit, a);
                float fill = item.fill();
                if (fill <= 0.0F) {
                    Gfx.text(g, item.text(), left, rowTop, color);
                } else {
                    int edge = Float.isInfinite(fill) ? clipRight : Math.min(clipRight, left + Math.round(fill));
                    if (edge >= clipRight) {
                        Gfx.text(g, item.text(), left, rowTop, litColor);
                    } else {
                        // the sung part in the highlight colour, the rest as before
                        g.method_44379(clipLeft, clipTop, edge, bottom);
                        Gfx.text(g, item.text(), left, rowTop, litColor);
                        g.method_44380();
                        g.method_44379(edge, clipTop, clipRight, bottom);
                        Gfx.text(g, item.text(), left, rowTop, color);
                        g.method_44380();
                    }
                }
            }
        } finally {
            g.method_44380();
        }
    }

    /** Three dots for an instrumental pause; they light up one after the other until the next line. */
    private void drawBreak(class_332 g, int left, int centerY, int upcoming, int lit, float highlight, float progress, float alpha) {
        float breath = 0.5F - 0.5F * (float) Math.cos(this.lyricView.breath() * 2.0 * Math.PI);
        for (int i = 0; i < 3; i++) {
            float on = LyricView.smooth(progress * 3.0F - i) * highlight;
            int color = fade(ColorUtil.blend(upcoming, lit, on), alpha);
            int size = on + 0.12F * breath * highlight > 0.5F ? 4 : 3;
            int cx = left + 1 + i * 6;
            Gfx.rect(g, cx - size / 2, centerY - size / 2, size, size, size / 2, color);
        }
    }

    // ---- text helpers ----

    private static void text(class_332 g, String text, int x, int y, int color, float scale) {
        if (scale == 1.0F) {
            Gfx.text(g, text, x, y, color);
        } else {
            Gfx.text(g, text, (float) x, (float) y, color, scale);
        }
    }

    /** One line of text that fits the column: as it is, cut off with an ellipsis, or scrolling (a clip rectangle hides the overflow). */
    private static void marqueeLine(class_332 g, String text, int x, int y, int avail, float textW, float offset, boolean scroll, int color,
            float scale) {
        if (textW <= avail + 0.5F) {
            text(g, text, x, y, color, scale);
        } else if (!scroll) {
            text(g, Gfx.trim(text, Math.round(avail / scale)), x, y, color, scale);
        } else {
            g.method_44379(x, y - 1, x + avail, y + ROW + 1);
            try {
                text(g, text, Math.round(x - offset), y, color, scale);
                if (offset > 0.0F) {
                    text(g, text, Math.round(x - offset + textW + LOOP_GAP), y, color, scale);
                }
            } finally {
                g.method_44380();
            }
        }
    }

    static int fade(int argb, float alpha) {
        return withAlpha(argb, Math.round((argb >>> 24) * Math.max(0.0F, Math.min(1.0F, alpha))));
    }

    private static int withAlpha(int argb, int alpha) {
        return ColorUtil.withAlpha(argb, alpha);
    }
}
