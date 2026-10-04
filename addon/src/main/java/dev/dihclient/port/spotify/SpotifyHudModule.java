package dev.dihclient.port.spotify;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.render.NavigationHud;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.setting.IntSetting;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3675;
import org.lwjgl.glfw.GLFW;

/**
 * Ported from an open-source client (GPL-3.0).
 * Shows what the Spotify desktop app is playing (cover, title, artist, progress) with synced lyrics, and can send
 * play/pause/next/previous. Windows only: the now-playing data comes from the Windows media session of the Spotify app
 * (WinRT, through the JNA library the game ships), with the title of the Spotify window as fallback. On other systems
 * the module does nothing and says nothing.
 * <p>
 * The only network traffic is the lyrics lookup at lrclib.net (artist, title, album and length of the playing track,
 * see {@link LyricsService}), which "Lyrics Online" switches off; lyrics found earlier stay readable from the disk cache.
 * <p>
 * Dropped from the original: the HUD layout editor (the card is placed with Position / Offset / Scale below), the chat note on
 * unsupported systems, the free key capture for the media keys (a fixed key list, see {@link MediaBind}), the glyph check
 * of the ImGui font (the game font falls back to Unifont by itself). "Card Width" is in GUI pixels now.
 */
public class SpotifyHudModule extends Module {
    private static final long POLL_INTERVAL_MS = 500L;
    private static final long WINDOW_POLL_MS = 1000L;
    private static final long REPOLL_AFTER_KEY_MS = 250L;
    private static final long KEY_COOLDOWN_MS = 200L;
    private static final int ART_MAX_SIDE = 256;
    private static final int STABLE_POLLS = 2;
    private static final int SESSION_HICCUPS = 2;
    private static final long ERROR_LOG_INTERVAL_MS = 60000L;

    public final BoolSetting showArtist = this.bool("Show Artist", "Artist under the title.", true);
    public final BoolSetting showAlbumArt = this.bool("Show Album Art", "Cover of the album next to the text.", true);
    public final BoolSetting showProgress = this.bool("Show Progress", "Elapsed time, time left and a progress bar.", true);
    public final BoolSetting scrollTitles = this.bool("Scroll Long Titles", "Scrolls titles that do not fit instead of cutting them off.", true);
    public final BoolSetting showLyrics = this.bool("Show Lyrics", "Synced lyrics below the card.", true);
    public final IntSetting lyricLines = this.integer("Lyric Lines", "How many lyric lines are visible.", 2, 1, 3);
    public final EnumSetting<LyricMotion> lyricMotion = this.mode("Lyric Motion", "How the lines move on.", LyricMotion.WAVE);
    public final EnumSetting<LyricHighlight> lyricHighlight = this.mode("Lyric Highlight", "How the line that is sung is marked.", LyricHighlight.KARAOKE);
    public final BoolSetting lyricsOnline = this.bool("Lyrics Online",
            "Look lyrics up at lrclib.net (sends title, artist, album and length of the track). Off: only lyrics saved earlier.", true);
    public final IntSetting cardWidth = this.integer("Card Width", "Width of the card in GUI pixels.", 200, 140, 400);
    public final ColorSetting titleColor = this.color("Title Color", "Colour of the track title.", -1447442);
    public final ColorSetting artistColor = this.color("Artist Color", "Colour of the artist name.", -6052949);
    public final ColorSetting lyricsColor = this.color("Lyrics Color", "Colour of the line that is sung.", -657929);
    public final ColorSetting upcomingLyricsColor = this.color("Upcoming Lyrics Color", "Colour of the lines that come next.", -6052949);
    public final ColorSetting highlightColor = this.color("Highlight Color", "Colour of the part that was sung already.", -14756000);
    public final BoolSetting showWhenIdle = this.bool("Show When Idle", "Also show a small note when nothing is playing or Spotify is closed.", false);
    public final BoolSetting mediaKeys = this.bool("Media Keys", "Keys below control Spotify while no screen is open.", false);
    public final EnumSetting<MediaBind> playPauseKey = this.mode("Play / Pause", "Key for play / pause.", MediaBind.NONE);
    public final EnumSetting<MediaBind> nextKey = this.mode("Next Track", "Key for the next track.", MediaBind.NONE);
    public final EnumSetting<MediaBind> previousKey = this.mode("Previous Track", "Key for the previous track.", MediaBind.NONE);
    public final EnumSetting<NavigationHud.Corner> corner = this.mode("Position", "Screen corner of the card.", NavigationHud.Corner.BOTTOM_RIGHT);
    public final IntSetting offsetX = this.integer("Offset X", "Distance from the screen edge.", 4, 0, 600);
    public final IntSetting offsetY = this.integer("Offset Y", "Distance from the screen edge.", 4, 0, 600);
    public final DoubleSetting scale = this.dbl("Scale", "Size of the card.", 1.0, 0.5, 2.0, 0.05);

    private final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private volatile NowPlaying nowPlaying = NowPlaying.WAITING;
    private volatile TrackLyrics lyrics;
    private volatile Poller poller;
    private final Object publishLock = new Object();
    private final EnumSetting<?>[] keys = {this.playPauseKey, this.nextKey, this.previousKey};
    private final SpotifyWindow.MediaKey[] actions = {SpotifyWindow.MediaKey.PLAY_PAUSE, SpotifyWindow.MediaKey.NEXT,
            SpotifyWindow.MediaKey.PREVIOUS};
    private final boolean[] wasDown = new boolean[this.keys.length];
    private long lastKeyAt;
    private SpotifyCard card;
    private int failures;

    public SpotifyHudModule() {
        super("Spotify HUD", Category.CLIENT, "Shows what Spotify is playing, with synced lyrics and optional media keys (Windows only).");
        this.artistColor.visibleWhen(this.showArtist::get);
        this.lyricsColor.visibleWhen(() -> this.showLyrics.get() && this.lyricHighlight.get() != LyricHighlight.LINE);
        this.upcomingLyricsColor.visibleWhen(this.showLyrics::get);
        this.highlightColor.visibleWhen(() -> this.showLyrics.get() && this.lyricHighlight.get() != LyricHighlight.OFF);
        this.lyricLines.visibleWhen(this.showLyrics::get);
        this.lyricMotion.visibleWhen(this.showLyrics::get);
        this.lyricHighlight.visibleWhen(this.showLyrics::get);
        this.lyricsOnline.visibleWhen(this.showLyrics::get);
        this.lyricsOnline.onChange(() -> {
            Poller running = this.poller;
            if (running != null) {
                running.lyricsService.setOnline(this.lyricsOnline.get());
            }
        });
        this.playPauseKey.visibleWhen(this.mediaKeys::get);
        this.nextKey.visibleWhen(this.mediaKeys::get);
        this.previousKey.visibleWhen(this.mediaKeys::get);
    }

    @Override
    protected void onEnable() {
        Arrays.fill(this.wasDown, true);
        this.failures = 0;
        if (!this.windows) {
            synchronized (this.publishLock) {
                this.nowPlaying = NowPlaying.UNSUPPORTED;
                this.lyrics = null;
            }
            return;
        }
        Poller started = new Poller();
        synchronized (this.publishLock) {
            this.poller = started;
            this.nowPlaying = NowPlaying.WAITING;
            this.lyrics = null;
        }
        started.start();
    }

    @Override
    protected void onDisable() {
        Poller stopped;
        synchronized (this.publishLock) {
            stopped = this.poller;
            this.poller = null;
            this.nowPlaying = this.windows ? NowPlaying.WAITING : NowPlaying.UNSUPPORTED;
            this.lyrics = null;
        }
        if (stopped != null) {
            stopped.stop();
        }
        this.releaseCard();
    }

    @Override
    public void onWorldChange() {
        this.releaseCard();
    }

    @Override
    public String getInfo() {
        NowPlaying now = this.nowPlaying;
        return now.active() && now.hasTrack() ? Gfx.trim(now.title(), 90) : null;
    }

    private void releaseCard() {
        SpotifyCard old = this.card;
        this.card = null;
        if (old != null) {
            try {
                old.release();
            } catch (Throwable t) {
                DIHClient.LOG.debug("[DIHClient] Spotify HUD: texture release failed", t);
            }
        }
    }

    private void publish(Poller from, NowPlaying state, TrackLyrics trackLyrics) {
        synchronized (this.publishLock) {
            if (from == this.poller) {
                this.nowPlaying = state;
                this.lyrics = trackLyrics;
            }
        }
    }

    // ---- what the card asks (render thread) ----

    NowPlaying nowPlaying() {
        return this.nowPlaying;
    }

    boolean shouldShowHud() {
        NowPlaying now = this.nowPlaying;
        if (now.status() == NowPlaying.Status.UNSUPPORTED || now.status() == NowPlaying.Status.WAITING) {
            return false;
        }
        return now.active() || this.showWhenIdle.get();
    }

    /** Lyrics of the track the poller published, null while the poller has not settled on this track yet. */
    Lyrics lyricsFor(NowPlaying now) {
        TrackLyrics current = this.lyrics;
        Poller running = this.poller;
        if (current != null && running != null && this.wantsLyrics(now)) {
            return current.title().equals(now.title()) && current.artist().equals(now.artist()) ? running.lyricsService.lookup(current.query()) : null;
        }
        return null;
    }

    boolean wantsLyrics(NowPlaying now) {
        return this.showLyrics.get() && now != null && now.lyricsEligible();
    }

    // ---- tick: media keys ----

    @Override
    public void onTick() {
        if (!this.windows) {
            return;
        }
        try {
            boolean accept = this.mediaKeys.get() && mc.field_1755 == null && mc.method_1569() && mc.field_1724 != null;
            for (int i = 0; i < this.keys.length; i++) {
                boolean down = isDown((MediaBind) this.keys[i].get());
                if (down && !this.wasDown[i] && accept) {
                    this.press(this.actions[i]);
                }
                this.wasDown[i] = down;
            }
        } catch (Throwable t) {
            if (this.failures++ < 3) {
                DIHClient.LOG.warn("[DIHClient] Spotify HUD key poll failed", t);
            }
        }
    }

    private void press(SpotifyWindow.MediaKey key) {
        long now = System.currentTimeMillis();
        Poller running = this.poller;
        if (running == null || now - this.lastKeyAt < KEY_COOLDOWN_MS) {
            return;
        }
        this.lastKeyAt = now;
        if (running.press(key) && key == SpotifyWindow.MediaKey.PLAY_PAUSE) {
            // show the new state at once; the next poll corrects it if the app did not follow
            synchronized (this.publishLock) {
                if (this.poller == running) {
                    this.nowPlaying = this.nowPlaying.toggled();
                }
            }
        }
    }

    private static boolean isDown(MediaBind bind) {
        if (bind == MediaBind.NONE) {
            return false;
        }
        class_310 client = class_310.method_1551();
        return bind.mouse ? GLFW.glfwGetMouseButton(client.method_22683().method_4490(), bind.code) == 1
                : class_3675.method_15987(client.method_22683(), bind.code);
    }

    // ---- HUD ----

    @Override
    public void onRender2D(class_332 g, float partialTicks) {
        if (!this.windows) {
            return;
        }
        if (!this.shouldShowHud()) {
            // frees the cover texture while nothing is shown
            this.releaseCard();
            return;
        }
        if (mc.field_1724 == null || mc.field_1690.field_1842) {
            return;
        }
        try {
            if (this.card == null) {
                this.card = new SpotifyCard(this);
            }
            this.card.render(g, this.nowPlaying);
        } catch (Throwable t) {
            if (this.failures++ < 3) {
                DIHClient.LOG.warn("[DIHClient] Spotify HUD draw failed", t);
            }
        }
    }

    // ---- poller (own thread) ----

    /** Lyrics query the poller settled on for the playing track. */
    private record TrackLyrics(String title, String artist, LyricsQuery query) {
    }

    /** Polls the media session twice a second on its own thread: WinRT calls may block and must never run on the render thread. */
    private final class Poller {
        final ScheduledThreadPoolExecutor executor;
        final LyricsService lyricsService = new LyricsService();
        private MediaSession media;
        private boolean mediaFailed;
        private SpotifyWindow window;
        private boolean windowFailed;
        private long nextWindowReadAt;
        private AlbumArt art;
        private int artVersion;
        private boolean artDecoded;
        private LyricsQuery lyricsCandidate;
        private int lyricsCandidatePolls;
        private LyricsQuery lyricsQuery;
        private TrackLyrics published;
        private int sessionFailures;
        private String lastError;
        private long lastErrorLoggedAt;

        Poller() {
            this.lyricsService.setOnline(SpotifyHudModule.this.lyricsOnline.get());
            this.executor = new ScheduledThreadPoolExecutor(1, runnable -> {
                Thread thread = new Thread(runnable, "DIHClient-Spotify");
                thread.setDaemon(true);
                return thread;
            });
            this.executor.setRemoveOnCancelPolicy(true);
            this.executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        void start() {
            this.executor.scheduleWithFixedDelay(this::poll, 0L, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        void stop() {
            try {
                // the WinRT objects must be released on the thread that created them
                this.executor.execute(this::closeMedia);
            } catch (RejectedExecutionException ignored) {
                // already shut down
            }
            this.executor.shutdown();
            this.lyricsService.close();
        }

        private void closeMedia() {
            if (this.media != null) {
                this.media.close();
            }
        }

        boolean press(SpotifyWindow.MediaKey key) {
            try {
                this.executor.execute(() -> this.control(key));
                this.executor.schedule(this::poll, REPOLL_AFTER_KEY_MS, TimeUnit.MILLISECONDS);
                return true;
            } catch (RejectedExecutionException e) {
                return false;
            }
        }

        private void poll() {
            if (SpotifyHudModule.this.poller != this) {
                return;
            }
            try {
                NowPlaying next = this.readSession();
                if (next == null) {
                    // a short hiccup of the session keeps the last good state instead of falling back to the window title
                    if (this.sessionFailures > 0 && this.sessionFailures <= SESSION_HICCUPS
                            && SpotifyHudModule.this.nowPlaying.source() == NowPlaying.Source.SESSION) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (SpotifyHudModule.this.nowPlaying.source() != NowPlaying.Source.SESSION && now < this.nextWindowReadAt) {
                        return;
                    }
                    this.nextWindowReadAt = now + WINDOW_POLL_MS;
                    next = this.readWindow();
                }
                if (next != null) {
                    SpotifyHudModule.this.publish(this, next, this.lyricsFor(next));
                }
            } catch (LinkageError | RuntimeException e) {
                DIHClient.LOG.debug("[DIHClient] Spotify HUD poll failed", e);
            }
        }

        private NowPlaying readSession() {
            if (this.mediaFailed) {
                return null;
            }
            if (this.media == null) {
                try {
                    this.media = MediaSession.spotify();
                } catch (LinkageError e) {
                    DIHClient.LOG.warn("[DIHClient] Spotify HUD: media session unavailable ({})", e.toString());
                    this.mediaFailed = true;
                    return null;
                }
            }
            if (this.media.isUnsupported()) {
                return null;
            }
            MediaSession.Snapshot snapshot = this.media.poll();
            if (snapshot != null) {
                this.sessionFailures = 0;
                return NowPlaying.fromSession(snapshot, this.artFor(snapshot), SpotifyHudModule.this.nowPlaying);
            }
            String error = this.media.lastError();
            this.logError(error);
            this.sessionFailures = error != null && !this.media.isUnsupported() ? this.sessionFailures + 1 : 0;
            return null;
        }

        private AlbumArt artFor(MediaSession.Snapshot snapshot) {
            if (!SpotifyHudModule.this.showAlbumArt.get()) {
                this.art = null;
                this.artDecoded = false;
                return null;
            }
            if (!this.artDecoded || snapshot.thumbnailVersion() != this.artVersion) {
                this.artDecoded = true;
                this.artVersion = snapshot.thumbnailVersion();
                this.art = null;
                if (snapshot.hasThumbnail()) {
                    try {
                        this.art = ArtDecoder.decode(snapshot.thumbnail(), ART_MAX_SIDE);
                    } catch (RuntimeException | LinkageError e) {
                        DIHClient.LOG.debug("[DIHClient] Spotify HUD: album art could not be decoded", e);
                    }
                }
            }
            return this.art;
        }

        private NowPlaying readWindow() {
            if (!this.windowFailed && this.window == null) {
                try {
                    this.window = new SpotifyWindow();
                } catch (LinkageError e) {
                    this.windowFailed(e);
                }
            }
            if (this.window != null) {
                try {
                    return NowPlaying.fromWindowTitle(this.window.readTitle(), SpotifyHudModule.this.nowPlaying);
                } catch (LinkageError e) {
                    this.windowFailed(e);
                }
            }
            if (this.media != null && !this.mediaFailed && !this.media.isUnsupported()) {
                return NowPlaying.NOT_RUNNING;
            }
            // neither source works on this system: stop polling for good
            this.executor.shutdown();
            return NowPlaying.UNSUPPORTED;
        }

        private void windowFailed(LinkageError failure) {
            DIHClient.LOG.warn("[DIHClient] Spotify HUD: Win32 access unavailable ({})", failure.toString());
            this.window = null;
            this.windowFailed = true;
        }

        /**
         * The lyrics are only looked up when the same track was seen on two polls in a row, so skipping through a playlist
         * does not send a request for every title that flashes by.
         */
        private TrackLyrics lyricsFor(NowPlaying now) {
            if (!SpotifyHudModule.this.wantsLyrics(now)) {
                this.lyricsCandidate = null;
                this.lyricsCandidatePolls = 0;
                this.lyricsQuery = null;
                this.published = null;
                return null;
            }
            LyricsQuery query = new LyricsQuery(now.title(), now.artist(), now.album(), 0).withDurationMillis(now.durationMs());
            if (query.equals(this.lyricsCandidate)) {
                this.lyricsCandidatePolls++;
            } else {
                this.lyricsCandidate = query;
                this.lyricsCandidatePolls = 1;
            }
            if (this.lyricsCandidatePolls >= STABLE_POLLS) {
                this.lyricsQuery = this.lyricsCandidate;
            }
            if (this.lyricsQuery != null && this.lyricsQuery.title().equals(now.title()) && this.lyricsQuery.artist().equals(now.artist())) {
                if (this.published == null || !this.published.query().equals(this.lyricsQuery) || !this.published.title().equals(now.title())
                        || !this.published.artist().equals(now.artist())) {
                    this.published = new TrackLyrics(now.title(), now.artist(), this.lyricsQuery);
                }
                return this.published;
            }
            return this.published;
        }

        private void control(SpotifyWindow.MediaKey key) {
            if (SpotifyHudModule.this.poller != this) {
                return;
            }
            if (this.media != null && SpotifyHudModule.this.nowPlaying.source() == NowPlaying.Source.SESSION) {
                boolean accepted = switch (key) {
                    case PLAY_PAUSE -> this.media.togglePlayPause();
                    case NEXT -> this.media.skipNext();
                    case PREVIOUS -> this.media.skipPrevious();
                };
                if (accepted || this.media.lastError() == null) {
                    return;
                }
                DIHClient.LOG.debug("[DIHClient] Spotify HUD: media session control failed ({}); sending the media key", this.media.lastError());
            }
            try {
                SpotifyWindow.tap(key);
            } catch (RuntimeException | LinkageError e) {
                DIHClient.LOG.debug("[DIHClient] Spotify HUD media key failed", e);
            }
        }

        private void logError(String error) {
            if (error == null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!Objects.equals(error, this.lastError) || now - this.lastErrorLoggedAt >= ERROR_LOG_INTERVAL_MS) {
                this.lastError = error;
                this.lastErrorLoggedAt = now;
                DIHClient.LOG.debug("[DIHClient] Spotify HUD media session: {}", error);
            }
        }
    }
}
