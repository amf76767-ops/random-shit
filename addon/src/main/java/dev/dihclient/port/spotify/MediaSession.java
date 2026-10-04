package dev.dihclient.port.spotify;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Reads (and controls) the Windows "System Media Transport Controls" session of the Spotify app through WinRT:
 * GlobalSystemMediaTransportControlsSessionManager -> the session whose app id contains "spotify" -> title, artist,
 * album, timeline, playback state, thumbnail. No network involved; everything stays on this PC. All calls must come
 * from one thread (the poller), which owns the COM apartment.
 */
public final class MediaSession {
    private static final String MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
    private static final Memory IID_MANAGER_STATICS = WinRt.iid("2050C4EE-11A0-57DE-AED7-C97C70338245");
    private static final int STATICS_REQUEST_ASYNC = 6;
    private static final int MANAGER_GET_CURRENT_SESSION = 6;
    private static final int MANAGER_GET_SESSIONS = 7;
    private static final int VECTOR_VIEW_GET_AT = 6;
    private static final int VECTOR_VIEW_GET_SIZE = 7;
    private static final int SESSION_GET_SOURCE_APP_USER_MODEL_ID = 6;
    private static final int SESSION_TRY_GET_MEDIA_PROPERTIES_ASYNC = 7;
    private static final int SESSION_GET_TIMELINE_PROPERTIES = 8;
    private static final int SESSION_GET_PLAYBACK_INFO = 9;
    private static final int SESSION_TRY_SKIP_NEXT_ASYNC = 16;
    private static final int SESSION_TRY_SKIP_PREVIOUS_ASYNC = 17;
    private static final int SESSION_TRY_TOGGLE_PLAY_PAUSE_ASYNC = 20;
    private static final int PROPERTIES_GET_TITLE = 6;
    private static final int PROPERTIES_GET_ARTIST = 9;
    private static final int PROPERTIES_GET_ALBUM_TITLE = 10;
    private static final int PROPERTIES_GET_THUMBNAIL = 15;
    private static final int TIMELINE_GET_START_TIME = 6;
    private static final int TIMELINE_GET_END_TIME = 7;
    private static final int TIMELINE_GET_POSITION = 10;
    private static final int TIMELINE_GET_LAST_UPDATED_TIME = 11;
    private static final int PLAYBACK_INFO_GET_PLAYBACK_STATUS = 7;
    private static final int PLAYBACK_INFO_GET_PLAYBACK_RATE = 10;
    private static final int REFERENCE_GET_VALUE = 6;
    private static final int STREAM_REFERENCE_OPEN_READ_ASYNC = 6;
    private static final Memory IID_RANDOM_ACCESS_STREAM = WinRt.iid("905A0FE1-BC53-11DF-8C49-001E4FC686DA");
    private static final int RANDOM_ACCESS_STREAM_GET_SIZE = 6;
    private static final Memory IID_INPUT_STREAM = WinRt.iid("905A0FE2-BC53-11DF-8C49-001E4FC686DA");
    private static final Memory IID_CONTENT_TYPE_PROVIDER = WinRt.iid("97D098A5-3B99-4DE9-88A5-E11D2F50C795");
    private static final int CONTENT_TYPE_PROVIDER_GET_CONTENT_TYPE = 6;
    private static final String DATA_READER_CLASS = "Windows.Storage.Streams.DataReader";
    private static final Memory IID_DATA_READER_FACTORY = WinRt.iid("D7527847-57DA-4E15-914C-06806699A098");
    private static final int DATA_READER_FACTORY_CREATE = 6;
    private static final int DATA_READER_PUT_INPUT_STREAM_OPTIONS = 12;
    private static final int DATA_READER_READ_BYTES = 14;
    private static final int DATA_READER_LOAD_ASYNC = 29;
    private static final int INPUT_STREAM_OPTIONS_PARTIAL = 1;
    private static final long REQUEST_TIMEOUT_MS = 3000L;
    private static final long PROPERTIES_TIMEOUT_MS = 1000L;
    private static final long THUMBNAIL_TIMEOUT_MS = 1500L;
    private static final long CONTROL_TIMEOUT_MS = 1000L;
    private static final long MANAGER_RETRY_MS = 5000L;
    private static final int MAX_SESSIONS = 64;
    private static final int THUMBNAIL_CHECKS = 3;
    private static final long THUMBNAIL_RECHECK_MS = 2000L;
    private static final long THUMBNAIL_SETTLE_MS = 1500L;
    private static final int THUMBNAIL_CHUNK = 65536;
    private static final int THUMBNAIL_MAX_BYTES = 4194304;
    private static final long UNIX_EPOCH_TICKS = 116444736000000000L;
    private static final long TICKS_PER_MS = 10000L;
    private final String appFilter;
    private volatile Thread owner;
    private boolean ownerInitialized;
    private volatile boolean closed;
    private volatile boolean unsupported;
    private volatile String lastError;
    private Pointer manager;
    private Pointer dataReaderFactory;
    private long managerRetryAt;
    private String sessionApp = "";
    private String trackKey;
    private String trackAlbum = "";
    private long trackChangedAt;
    private byte[] thumbnail;
    private String thumbnailType = "";
    private int thumbnailVersion;
    private int thumbnailChecksLeft;
    private long nextThumbnailCheckAt;
    private byte[] staleThumbnail;

    public MediaSession(String appFilter) {
        this.appFilter = appFilter == null ? null : appFilter.toLowerCase(Locale.ROOT);
    }

    public static MediaSession spotify() {
        return new MediaSession("spotify");
    }

    public MediaSession.Snapshot poll() {
        try {
            if (!this.prepare()) {
                return null;
            } else {
                this.lastError = null;
                return this.read();
            }
        } catch (WinRt.WinRtException e) {
            return this.fail(e.getMessage());
        } catch (LinkageError e) {
            this.unsupported = true;
            return this.fail("WinRT unavailable: " + e);
        } catch (RuntimeException e) {
            return this.fail(e.toString());
        }
    }

    public boolean togglePlayPause() {
        return this.control(SESSION_TRY_TOGGLE_PLAY_PAUSE_ASYNC, "TryTogglePlayPauseAsync");
    }

    public boolean skipNext() {
        return this.control(SESSION_TRY_SKIP_NEXT_ASYNC, "TrySkipNextAsync");
    }

    public boolean skipPrevious() {
        return this.control(SESSION_TRY_SKIP_PREVIOUS_ASYNC, "TrySkipPreviousAsync");
    }

    public String lastError() {
        return this.lastError;
    }

    public boolean isUnsupported() {
        return this.unsupported;
    }

    public void close() {
        this.closed = true;
        if (this.owner == Thread.currentThread()) {
            this.dispose();
        }
    }

    private boolean prepare() {
        Thread current = Thread.currentThread();
        if (this.owner != current) {
            if (this.owner != null && this.owner.isAlive()) {
                this.lastError = "called from a thread other than the owner";
                return false;
            }

            if (this.closed || this.unsupported) {
                return false;
            }

            int result = WinRt.initializeMultithreaded();
            if (result < 0 && result != WinRt.RPC_E_CHANGED_MODE) {
                throw new WinRt.WinRtException("RoInitialize", result);
            }

            this.owner = current;
            this.ownerInitialized = result >= 0;
        }

        if (this.closed) {
            this.dispose();
            return false;
        } else if (this.unsupported) {
            return false;
        } else {
            if (this.manager == null) {
                long now = System.currentTimeMillis();
                if (now < this.managerRetryAt) {
                    return false;
                }

                this.managerRetryAt = now + MANAGER_RETRY_MS;
                this.manager = this.requestManager();
            }

            return this.manager != null;
        }
    }

    private Pointer requestManager() {
        try (WinRt.Refs refs = new WinRt.Refs()) {
            Pointer statics;
            try {
                statics = refs.add(WinRt.activationFactory("Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager", IID_MANAGER_STATICS));
            } catch (WinRt.WinRtException e) {
                if (e.hresult == WinRt.REGDB_E_CLASSNOTREG || e.hresult == WinRt.E_NOINTERFACE) {
                    this.unsupported = true;
                }

                throw e;
            }

            Pointer operation = refs.add(WinRt.callForObject(statics, STATICS_REQUEST_ASYNC, "RequestAsync"));
            return WinRt.awaitObject(operation, REQUEST_TIMEOUT_MS, "RequestAsync");
        }
    }

    private void dispose() {
        Pointer oldManager = this.manager;
        Pointer oldFactory = this.dataReaderFactory;
        this.manager = null;
        this.dataReaderFactory = null;

        try {
            WinRt.release(oldManager);
            WinRt.release(oldFactory);
        } catch (RuntimeException | LinkageError ignored) {
        }

        this.forgetTrack();
        if (this.ownerInitialized) {
            this.ownerInitialized = false;

            try {
                WinRt.uninitialize();
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
    }

    private MediaSession.Snapshot fail(String message) {
        this.lastError = message;
        return null;
    }

    private void dropManager() {
        Pointer oldManager = this.manager;
        this.manager = null;

        try {
            WinRt.release(oldManager);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private MediaSession.Snapshot read() {
        try (WinRt.Refs refs = new WinRt.Refs()) {
            Pointer session = this.findSession(refs);
            if (session == null) {
                this.forgetTrack();
                return null;
            }

            String app = this.sessionApp;
            MediaSession.PlaybackState state = MediaSession.PlaybackState.CLOSED;
            double rate = 1.0;
            Pointer playback = refs.add(WinRt.callForObject(session, SESSION_GET_PLAYBACK_INFO, "GetPlaybackInfo"));
            if (playback != null) {
                state = MediaSession.PlaybackState.of(WinRt.getInt32(playback, PLAYBACK_INFO_GET_PLAYBACK_STATUS, "PlaybackStatus"));
                Pointer rateValue = refs.add(WinRt.callForObject(playback, PLAYBACK_INFO_GET_PLAYBACK_RATE, "PlaybackRate"));
                if (rateValue != null) {
                    double reported = WinRt.getDouble(rateValue, REFERENCE_GET_VALUE, "PlaybackRate value");
                    if (reported > 0.0 && reported <= 16.0) {
                        rate = reported;
                    }
                }
            }

            long startTicks = 0L;
            long endTicks = 0L;
            long positionTicks = 0L;
            long updatedTicks = 0L;
            Pointer timeline = refs.add(WinRt.callForObject(session, SESSION_GET_TIMELINE_PROPERTIES, "GetTimelineProperties"));
            if (timeline != null) {
                startTicks = WinRt.getInt64(timeline, TIMELINE_GET_START_TIME, "StartTime");
                endTicks = WinRt.getInt64(timeline, TIMELINE_GET_END_TIME, "EndTime");
                positionTicks = WinRt.getInt64(timeline, TIMELINE_GET_POSITION, "Position");
                updatedTicks = WinRt.getInt64(timeline, TIMELINE_GET_LAST_UPDATED_TIME, "LastUpdatedTime");
            }

            long timelineReadAt = System.currentTimeMillis();
            String title = "";
            String artist = "";
            String album = "";
            Pointer operation = refs.add(WinRt.callForObject(session, SESSION_TRY_GET_MEDIA_PROPERTIES_ASYNC, "TryGetMediaPropertiesAsync"));
            Pointer properties = refs.add(WinRt.awaitObject(operation, PROPERTIES_TIMEOUT_MS, "TryGetMediaPropertiesAsync"));
            if (properties != null) {
                title = WinRt.getString(properties, PROPERTIES_GET_TITLE, "Title").strip();
                artist = WinRt.getString(properties, PROPERTIES_GET_ARTIST, "Artist").strip();
                album = WinRt.getString(properties, PROPERTIES_GET_ALBUM_TITLE, "AlbumTitle").strip();
            }

            long now = System.currentTimeMillis();
            String key = app + "\n" + title + "\n" + artist + "\n" + album;
            if (!key.equals(this.trackKey)) {
                boolean sameAlbum = this.trackKey != null && !album.isEmpty() && album.equals(this.trackAlbum);
                this.staleThumbnail = sameAlbum ? null : this.thumbnail;
                if (!sameAlbum) {
                    this.setThumbnail(null, "");
                }

                this.trackKey = key;
                this.trackAlbum = album;
                this.trackChangedAt = now;
                this.thumbnailChecksLeft = THUMBNAIL_CHECKS;
                this.nextThumbnailCheckAt = now;
            }

            if (properties != null && this.thumbnailChecksLeft > 0 && now >= this.nextThumbnailCheckAt) {
                MediaSession.Thumbnail art = this.readThumbnail(properties);
                now = System.currentTimeMillis();
                boolean stale = art != null && this.staleThumbnail != null && Arrays.equals(art.bytes(), this.staleThumbnail);
                if ((art == null || stale) && now - this.trackChangedAt < THUMBNAIL_SETTLE_MS) {
                    this.nextThumbnailCheckAt = now;
                } else {
                    if (art != null) {
                        this.setThumbnail(art.bytes(), art.type());
                    }

                    this.staleThumbnail = null;
                    this.thumbnailChecksLeft--;
                    this.nextThumbnailCheckAt = now + THUMBNAIL_RECHECK_MS;
                }
            }

            long durationMs = Math.max(0L, (endTicks - startTicks) / TICKS_PER_MS);
            long positionMs = Math.max(0L, (positionTicks - startTicks) / TICKS_PER_MS);
            if (state == MediaSession.PlaybackState.PLAYING && durationMs > 0L) {
                long fromMs = updatedTicks > 0L ? (updatedTicks - UNIX_EPOCH_TICKS) / TICKS_PER_MS : timelineReadAt;
                long elapsed = now - fromMs;
                if (elapsed > 0L) {
                    positionMs += (long)(Math.min(elapsed, durationMs) * rate);
                }
            }

            if (durationMs > 0L) {
                positionMs = Math.min(positionMs, durationMs);
            }

            return new MediaSession.Snapshot(
                title, artist, album, durationMs, positionMs, now, state, rate, this.thumbnail, this.thumbnailType, this.thumbnailVersion
            );
        }

    }

    private Pointer findSession(WinRt.Refs refs) {
        if (this.appFilter == null) {
            Pointer session = refs.add(this.managerCall(MANAGER_GET_CURRENT_SESSION, "GetCurrentSession"));
            if (session != null) {
                this.sessionApp = WinRt.getString(session, SESSION_GET_SOURCE_APP_USER_MODEL_ID, "SourceAppUserModelId");
            }

            return session;
        } else {
            Pointer sessions = refs.add(this.managerCall(MANAGER_GET_SESSIONS, "GetSessions"));
            if (sessions == null) {
                return null;
            } else {
                int count = Math.min(WinRt.getInt32(sessions, VECTOR_VIEW_GET_SIZE, "Sessions.Size"), MAX_SESSIONS);

                for (int i = 0; i < count; i++) {
                    Pointer session = refs.add(WinRt.callForObject(sessions, VECTOR_VIEW_GET_AT, "Sessions.GetAt", i));
                    if (session != null) {
                        String app = WinRt.getString(session, SESSION_GET_SOURCE_APP_USER_MODEL_ID, "SourceAppUserModelId");
                        if (app.toLowerCase(Locale.ROOT).contains(this.appFilter)) {
                            this.sessionApp = app;
                            return session;
                        }
                    }
                }

                return null;
            }
        }
    }

    private Pointer managerCall(int slot, String step) {
        try {
            return WinRt.callForObject(this.manager, slot, step);
        } catch (WinRt.WinRtException e) {
            this.dropManager();
            throw e;
        }
    }

    private void forgetTrack() {
        this.trackKey = null;
        this.trackAlbum = "";
        this.thumbnailChecksLeft = 0;
        this.staleThumbnail = null;
        this.setThumbnail(null, "");
    }

    private void setThumbnail(byte[] bytes, String type) {
        if (bytes == null ? this.thumbnail != null : !Arrays.equals(bytes, this.thumbnail)) {
            this.thumbnail = bytes;
            this.thumbnailType = bytes == null ? "" : type;
            this.thumbnailVersion++;
        }
    }

    private MediaSession.Thumbnail readThumbnail(Pointer properties) {
        try {
            try (WinRt.Refs refs = new WinRt.Refs()) {
                Pointer reference = refs.add(WinRt.callForObject(properties, PROPERTIES_GET_THUMBNAIL, "Thumbnail"));
                if (reference == null) {
                    return null;
                }

                Pointer operation = refs.add(WinRt.callForObject(reference, STREAM_REFERENCE_OPEN_READ_ASYNC, "OpenReadAsync"));
                Pointer stream = refs.add(WinRt.awaitObject(operation, THUMBNAIL_TIMEOUT_MS, "OpenReadAsync"));
                if (stream == null) {
                    return null;
                }

                String type = "";
                Pointer typed = refs.add(WinRt.tryQueryInterface(stream, IID_CONTENT_TYPE_PROVIDER));
                if (typed != null) {
                    type = WinRt.getString(typed, CONTENT_TYPE_PROVIDER_GET_CONTENT_TYPE, "ContentType");
                }

                Pointer random = refs.add(WinRt.queryInterface(stream, IID_RANDOM_ACCESS_STREAM, "IRandomAccessStream"));
                long size = WinRt.getInt64(random, RANDOM_ACCESS_STREAM_GET_SIZE, "Stream.Size");
                if (size < 0L || size > THUMBNAIL_MAX_BYTES) {
                    return null;
                }

                Pointer input = refs.add(WinRt.queryInterface(stream, IID_INPUT_STREAM, "IInputStream"));
                Pointer reader = refs.add(WinRt.callForObject(this.dataReaderFactory(), DATA_READER_FACTORY_CREATE, "CreateDataReader", input));
                if (reader == null) {
                    return null;
                }

                WinRt.check(WinRt.invoke(reader, DATA_READER_PUT_INPUT_STREAM_OPTIONS, INPUT_STREAM_OPTIONS_PARTIAL), "InputStreamOptions");
                ByteArrayOutputStream out = new ByteArrayOutputStream(size > 0L ? (int)size : THUMBNAIL_CHUNK);

                while (size == 0L || out.size() < size) {
                    int want = size > 0L ? (int)Math.min(size - out.size(), THUMBNAIL_CHUNK) : THUMBNAIL_CHUNK;
                    Pointer load = WinRt.callForObject(reader, DATA_READER_LOAD_ASYNC, "LoadAsync", want);

                    int loaded;
                    try {
                        loaded = WinRt.awaitUInt32(load, THUMBNAIL_TIMEOUT_MS, "LoadAsync");
                    } finally {
                        WinRt.release(load);
                    }

                    if (loaded <= 0) {
                        break;
                    }

                    if (out.size() + loaded > THUMBNAIL_MAX_BYTES) {
                        return null;
                    }

                    byte[] chunk = new byte[loaded];
                    WinRt.check(WinRt.invoke(reader, DATA_READER_READ_BYTES, loaded, chunk), "ReadBytes");
                    out.write(chunk, 0, loaded);
                }

                if (out.size() == 0 || size > 0L && out.size() != size) {
                    return null;
                }

                return new MediaSession.Thumbnail(out.toByteArray(), type);
            }

        } catch (WinRt.WinRtException e) {
            this.lastError = "album art: " + e.getMessage();
            return null;
        }
    }

    private Pointer dataReaderFactory() {
        if (this.dataReaderFactory == null) {
            this.dataReaderFactory = WinRt.activationFactory("Windows.Storage.Streams.DataReader", IID_DATA_READER_FACTORY);
        }

        return this.dataReaderFactory;
    }

    private boolean control(int slot, String step, Object... args) {
        try {
            if (!this.prepare()) {
                return false;
            }

            try (WinRt.Refs refs = new WinRt.Refs()) {
                Pointer session = this.findSession(refs);
                if (session == null) {
                    return false;
                }

                Pointer operation = refs.add(WinRt.callForObject(session, slot, step, args));
                boolean accepted = WinRt.awaitBoolean(operation, CONTROL_TIMEOUT_MS, step);
                this.lastError = null;
                return accepted;
            }

        } catch (WinRt.WinRtException e) {
            this.fail(e.getMessage());
        } catch (LinkageError e) {
            this.unsupported = true;
            this.fail("WinRT unavailable: " + e);
        } catch (RuntimeException e) {
            this.fail(e.toString());
        }

        return false;
    }

    public static enum PlaybackState {
        CLOSED,
        OPENED,
        CHANGING,
        STOPPED,
        PLAYING,
        PAUSED;

        private static final MediaSession.PlaybackState[] VALUES = values();

        static MediaSession.PlaybackState of(int value) {
            return value >= 0 && value < VALUES.length ? VALUES[value] : CLOSED;
        }
    }

    public record Snapshot(
        String title,
        String artist,
        String album,
        long durationMs,
        long positionMs,
        long sampledAtMs,
        MediaSession.PlaybackState state,
        double rate,
        byte[] thumbnail,
        String thumbnailType,
        int thumbnailVersion
    ) {
        public boolean hasThumbnail() {
            return this.thumbnail != null;
        }
    }

    private record Thumbnail(byte[] bytes, String type) {
    }
}
