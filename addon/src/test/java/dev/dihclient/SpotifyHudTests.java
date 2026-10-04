package dev.dihclient;

import dev.dihclient.port.spotify.AlbumArt;
import dev.dihclient.port.spotify.DisplayText;
import dev.dihclient.port.spotify.GuidBytes;
import dev.dihclient.port.spotify.LrclibTrack;
import dev.dihclient.port.spotify.LyricHighlight;
import dev.dihclient.port.spotify.LyricMotion;
import dev.dihclient.port.spotify.LyricView;
import dev.dihclient.port.spotify.Lyrics;
import dev.dihclient.port.spotify.LyricsDiskCache;
import dev.dihclient.port.spotify.LyricsMatcher;
import dev.dihclient.port.spotify.LyricsQuery;
import dev.dihclient.port.spotify.NowPlaying;
import dev.dihclient.port.spotify.PlaybackClock;
import dev.dihclient.port.spotify.SyncedLyrics;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;

/** Tests of the pure logic of the Spotify HUD port (no Minecraft, no JNA, no network). */
public final class SpotifyHudTests {
    static int failed;
    static int passed;

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        guids();
        lrc();
        lyricsOf();
        track();
        matcher();
        query();
        text();
        clock();
        nowPlaying();
        art();
        diskCache();
        lyricView();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---- GUID layout ----

    static void guids() {
        byte[] manager = GuidBytes.of("2050C4EE-11A0-57DE-AED7-C97C70338245");
        byte[] expected = {(byte) 0xEE, (byte) 0xC4, 0x50, 0x20, (byte) 0xA0, 0x11, (byte) 0xDE, 0x57, (byte) 0xAE, (byte) 0xD7, (byte) 0xC9,
                0x7C, 0x70, 0x33, (byte) 0x82, 0x45};
        check(Arrays.equals(manager, expected), "GUID: first three groups little-endian, rest as written");
        byte[] info = GuidBytes.of("{00000036-0000-0000-C000-000000000046}");
        check(info[0] == 0x36 && info[8] == (byte) 0xC0 && info[15] == 0x46 && info.length == 16, "GUID: braces accepted");
        boolean bad = false;
        try {
            GuidBytes.of("nope");
        } catch (IllegalArgumentException e) {
            bad = true;
        }
        check(bad, "GUID: garbage rejected");
    }

    // ---- LRC ----

    static void lrc() {
        SyncedLyrics s = SyncedLyrics.parse("[00:12.00]Hello\n[00:15.5]World\r\n[01:02]Last");
        check(s.size() == 3, "lrc: three lines");
        check(s.line(0).timeMs() == 12000 && s.line(1).timeMs() == 15500 && s.line(2).timeMs() == 62000, "lrc: times (.00, .5, none)");
        check(s.indexAt(11999) == -1 && s.indexAt(12000) == 0 && s.indexAt(15499) == 0 && s.indexAt(99999) == 2, "lrc: indexAt");
        check(SyncedLyrics.parse("[01:02.3]a").line(0).timeMs() == 62300 && SyncedLyrics.parse("[01:02.34]a").line(0).timeMs() == 62340
                && SyncedLyrics.parse("[01:02.345]a").line(0).timeMs() == 62345, "lrc: fraction digits");

        SyncedLyrics multi = SyncedLyrics.parse("[00:10.00][00:20.00]Chorus\n[00:15.00]Verse");
        check(multi.size() == 3 && multi.line(0).text().equals("Chorus") && multi.line(1).text().equals("Verse")
                && multi.line(2).timeMs() == 20000, "lrc: several stamps per line are sorted");

        SyncedLyrics shifted = SyncedLyrics.parse("[ar:Someone]\n[ti:Song]\n[offset:500]\n[00:10.00]x");
        check(shifted.size() == 1 && shifted.line(0).timeMs() == 9500, "lrc: metadata skipped, offset shifts earlier");
        check(SyncedLyrics.parse("[offset:-250]\n[00:10.00]x").line(0).timeMs() == 10250, "lrc: negative offset");
        check(SyncedLyrics.parse("[offset:99999]\n[00:01.00]x").line(0).timeMs() == 0, "lrc: time never negative");

        SyncedLyrics gap = SyncedLyrics.parse("[00:01.00]a\n[00:05.00]\n[00:09.00]b");
        check(gap.size() == 3 && gap.line(1).isGap() && gap.texts().equals(List.of("a", "b")), "lrc: empty line is a gap, left out of texts()");

        SyncedLyrics same = SyncedLyrics.parse("[00:05.00]one\n[00:05.00]two\n[00:08.00]three");
        check(same.firstWithTimeOf(1) == 0 && same.lastWithTimeOf(0) == 1 && same.firstWithTimeOf(2) == 2, "lrc: lines sharing a time");

        SyncedLyrics words = SyncedLyrics.parse("[00:10.00]<00:10.00>Hello <00:11.00>big <00:12.50>world");
        check(words.line(0).text().equals("Hello big world"), "lrc: word tags stripped from text");
        check(words.line(0).wordTimes().equals(List.of(10000L, 11000L, 12500L)), "lrc: word times");
        SyncedLyrics broken = SyncedLyrics.parse("[00:10.00]<00:10.00>Hello big <00:12.50>world");
        check(broken.line(0).wordTimes().isEmpty(), "lrc: word without tag drops the word times of the line");
        SyncedLyrics syllables = SyncedLyrics.parse("[00:10.00]<00:10.00>Hel<00:10.30>lo <00:11.00>you");
        check(syllables.line(0).text().equals("Hello you") && syllables.line(0).wordTimes().equals(List.of(10000L, 11000L)),
                "lrc: syllable tags do not start a new word");

        check(SyncedLyrics.parse("﻿[00:01.00]bom").size() == 1, "lrc: byte order mark");
        check(SyncedLyrics.parse(null).isEmpty() && SyncedLyrics.parse("  ").isEmpty() && SyncedLyrics.parse("no stamps here").isEmpty(),
                "lrc: nothing to parse");
        check(SyncedLyrics.parse("[00:01.00]  spaced   out  ").line(0).text().equals("spaced out"), "lrc: whitespace squeezed");
    }

    static void lyricsOf() {
        LrclibTrack synced = new LrclibTrack(1, "T", "A", "", 200, false, "plain", "[00:01.00]a\n[00:02.00]b");
        Lyrics l = Lyrics.of(synced);
        check(l.status() == Lyrics.Status.SYNCED && l.synced().size() == 2 && l.sourceId() == 1, "lyrics: synced");
        check(Lyrics.of(new LrclibTrack(2, "T", "A", "", 200, false, "one\n\n\ntwo\nthree", "")).status() == Lyrics.Status.PLAIN, "lyrics: plain");
        Lyrics plain = Lyrics.of(new LrclibTrack(2, "T", "A", "", 200, false, "one\n\n\ntwo\nthree", ""));
        check(plain.plainLines().equals(List.of("one", "", "two", "three")), "lyrics: blank runs become one gap");
        check(Lyrics.of(new LrclibTrack(3, "T", "A", "", 200, true, "", "")).status() == Lyrics.Status.INSTRUMENTAL, "lyrics: instrumental");
        check(Lyrics.of(new LrclibTrack(4, "T", "A", "", 200, false, "", "")).status() == Lyrics.Status.NOT_FOUND, "lyrics: empty record is not found");
        check(Lyrics.of(null).status() == Lyrics.Status.NOT_FOUND, "lyrics: null is not found");
    }

    static void track() {
        String json = "{\"id\":42,\"trackName\":\"Halo\",\"artistName\":\"Beyonce\",\"albumName\":\"I Am\",\"duration\":261.5,\"instrumental\":false,"
                + "\"plainLyrics\":\"x\",\"syncedLyrics\":\"[00:01.00]x\"}";
        LrclibTrack t = LrclibTrack.fromJson(LrclibTrack.parse(json));
        check(t.id() == 42 && t.trackName().equals("Halo") && t.duration() == 261.5 && t.hasSynced() && t.hasPlain() && t.usable(), "track: fields");
        LrclibTrack again = LrclibTrack.fromJson(t.toJson());
        check(t.equals(again), "track: JSON round trip");
        check(LrclibTrack.fromJson(LrclibTrack.parse("[1]")) == null && LrclibTrack.fromJson(null) == null, "track: not an object");
        List<LrclibTrack> list = LrclibTrack.listFromJson(LrclibTrack.parse("[" + json + ",5,{\"id\":\"7\"}]"));
        check(list.size() == 2 && list.get(1).id() == 0 && !list.get(1).usable(), "track: list skips non-objects, tolerates bad fields");
        check(new LrclibTrack(1, null, null, null, -5, false, null, null).duration() == 0, "track: negative duration and nulls sanitised");
    }

    // ---- matching ----

    static LrclibTrack cand(long id, String name, String artist, double duration, boolean synced) {
        return new LrclibTrack(id, name, artist, "", duration, false, "plain text", synced ? "[00:01.00]x" : "");
    }

    static void matcher() {
        check(LyricsMatcher.normalize("Café Del Mar").equals("cafe del mar"), "normalize: accents");
        check(LyricsMatcher.normalize("Don't Stop").equals("dont stop") && LyricsMatcher.normalize("Don’t Stop").equals("dont stop"), "normalize: apostrophes");
        check(LyricsMatcher.normalize("Simon & Garfunkel").equals("simon and garfunkel"), "normalize: ampersand");
        check(LyricsMatcher.normalize("  Hi--There!! ").equals("hi there") && LyricsMatcher.normalize(null).isEmpty(), "normalize: punctuation, null");

        check(LyricsMatcher.baseTitle("Song (feat. Someone)").equals("Song"), "baseTitle: feat in brackets");
        check(LyricsMatcher.baseTitle("Song - 2011 Remastered").equals("Song"), "baseTitle: remaster suffix");
        check(LyricsMatcher.baseTitle("Song - Live").equals("Song - Live"), "baseTitle: live is a different recording");
        check(LyricsMatcher.baseTitle("Song feat. Someone").equals("Song"), "baseTitle: plain feat");
        check(LyricsMatcher.baseTitle("(feat. X)").equals("(feat. X)"), "baseTitle: never empty");
        check(LyricsMatcher.primaryArtist("A, B & C").equals("A") && LyricsMatcher.primaryArtist("Solo").equals("Solo"), "primaryArtist");

        check(LyricsMatcher.titleScore("Halo", "halo") == 40, "title: exact");
        check(LyricsMatcher.titleScore("Halo (Remastered)", "Halo") == 30, "title: same base");
        check(LyricsMatcher.titleScore("Halo", "Halo Live At Wembley") == 12, "title: contained as words");
        check(LyricsMatcher.titleScore("Halo", "Haloween") == 0 && LyricsMatcher.titleScore("", "x") == 0, "title: no match");
        check(LyricsMatcher.artistScore("Beyoncé", "beyonce") == 30, "artist: exact");
        check(LyricsMatcher.artistScore("Artist A, Artist B", "Artist B") == 20, "artist: one shared name");
        check(LyricsMatcher.artistScore("Artist A", "Someone Else") == 0, "artist: different");

        LyricsQuery q = new LyricsQuery("Halo", "Beyonce", "I Am", 261);
        LrclibTrack good = cand(1, "Halo", "Beyonce", 262, true);
        LrclibTrack plainOnly = cand(2, "Halo", "Beyonce", 261, false);
        LrclibTrack far = cand(3, "Halo", "Beyonce", 300, true);
        LrclibTrack other = cand(4, "Halo", "Someone", 261, true);
        check(LyricsMatcher.best(List.of(plainOnly, far, other, good), q) == good, "best: synced within 3 s beats plain, wrong length/artist dropped");
        check(LyricsMatcher.score(far, q) == -1 && LyricsMatcher.score(other, q) == -1, "score: too far / other artist is -1");
        check(LyricsMatcher.best(List.of(far, other), q) == null && LyricsMatcher.best(List.of(), q) == null && LyricsMatcher.best(null, q) == null,
                "best: nothing fits");
        LrclibTrack closer = cand(5, "Halo", "Beyonce", 261, true);
        check(LyricsMatcher.best(List.of(good, closer), q) == closer, "best: smaller drift wins a tie");
        check(LyricsMatcher.score(new LrclibTrack(6, "Halo", "Beyonce", "", 261, false, "", ""), q) == -1, "score: empty record is not usable");
        LrclibTrack noDuration = cand(7, "Halo", "Beyonce", 0, true);
        check(LyricsMatcher.score(noDuration, q) > 0, "score: candidate without length is accepted");
        check(LyricsMatcher.searchTerms(q).size() == 1, "searchTerms: one when nothing to simplify");
        List<String[]> terms = LyricsMatcher.searchTerms(new LyricsQuery("Halo (feat. Y)", "Beyonce, Y", "", 0));
        check(terms.size() == 2 && terms.get(1)[0].equals("Halo") && terms.get(1)[1].equals("Beyonce"), "searchTerms: second try with simplified names");
    }

    static void query() {
        LyricsQuery a = new LyricsQuery(" Halo ", "Beyonce", "", 261);
        LyricsQuery b = new LyricsQuery("halo", "BEYONCE", "other album", 261);
        check(a.title().equals("Halo") && a.cacheKey().equals(b.cacheKey()), "query: key ignores case, spacing and album");
        check(!a.cacheKey().equals(new LyricsQuery("Halo", "Beyonce", "", 262).cacheKey()), "query: key depends on length");
        check(a.cacheKey().matches("[0-9a-f]{32}"), "query: key is 32 hex digits (safe file name)");
        check(new LyricsQuery("a", "b", "", 0).withDurationMillis(200_400).durationSeconds() == 200
                && new LyricsQuery("a", "b", "", 0).withDurationMillis(200_500).durationSeconds() == 201, "query: duration rounds to seconds");
        check(new LyricsQuery("a", "b", "", 0).withDurationMillis(4_000_000L).durationSeconds() == 0, "query: more than an hour is no duration");
        check(!new LyricsQuery("a", "b", "", 0).hasDuration() && new LyricsQuery("a", "b", "", 5).hasDuration(), "query: hasDuration");
        check(new LyricsQuery("a", "b", null, 0).searchable() && !new LyricsQuery("", "b", "", 0).searchable()
                && !new LyricsQuery("a", " ", "", 0).searchable(), "query: needs title and artist");
    }

    // ---- text ----

    static void text() {
        check(DisplayText.fold("Don’t – stop…").equals("Don't - stop..."), "fold: quotes, dashes, ellipsis");
        check(DisplayText.fold("♪ la  la la ♫").equals("la la la"), "fold: notes dropped, spaces squeezed");
        check(DisplayText.fold(null).isEmpty() && DisplayText.fold("").isEmpty() && DisplayText.fold("  x ").equals("x"), "fold: null, blank, strip");
        check(DisplayText.fold("Café", cp -> cp < 128).equals("Cafe"), "fold: undrawable letter falls back to base letter");
        check(DisplayText.fold("Café", cp -> true).equals("Café"), "fold: drawable letter kept");
        check(DisplayText.fold("ŁÓDŹ", cp -> cp < 128).equals("LODZ"), "fold: Polish capitals");
        check(DisplayText.fold("ж", cp -> cp < 128).equals("ж"), "fold: letters without base form stay");
        check(DisplayText.clock(0).equals("0:00") && DisplayText.clock(65).equals("1:05") && DisplayText.clock(3600).equals("1:00:00")
                && DisplayText.clock(3725).equals("1:02:05") && DisplayText.clock(-4).equals("0:00"), "clock format");
    }

    // ---- playback clock ----

    static NowPlaying session(NowPlaying.Status status, String title, long durationMs, long positionMs, long sampledAt, double rate) {
        return new NowPlaying(status, title, "Artist", "Album", NowPlaying.Source.SESSION, durationMs, positionMs, sampledAt, rate, null);
    }

    static void clock() {
        PlaybackClock c = new PlaybackClock();
        NowPlaying first = session(NowPlaying.Status.PLAYING, "T", 200_000, 10_000, 1000, 1.0);
        c.update(first, 1000);
        check(c.positionAt(1000) == 10_000 && c.positionAt(2500) == 11_500, "clock: runs with real time");

        // the next sample agrees within 1.2 s: no jump, the difference fades in over 1.5 s
        NowPlaying second = session(NowPlaying.Status.PLAYING, "T", 200_000, 11_100, 2000, 1.0);
        c.update(second, 2000);
        check(c.positionAt(2000) == 11_000, "clock: no jump on a small correction");
        check(c.positionAt(3500) == 12_600, "clock: correction fully applied after 1.5 s");
        check(c.positionAt(2750) == 11_000 + 750 + 50, "clock: correction half applied after 0.75 s");

        // a seek: snaps
        NowPlaying seek = session(NowPlaying.Status.PLAYING, "T", 200_000, 100_000, 4000, 1.0);
        c.update(seek, 4000);
        check(c.positionAt(4000) == 100_000, "clock: seek snaps");

        // pause freezes
        NowPlaying paused = session(NowPlaying.Status.PAUSED, "T", 200_000, 101_000, 5000, 1.0);
        c.update(paused, 5000);
        check(c.positionAt(5000) == 101_000 && c.positionAt(9000) == 101_000, "clock: paused stands still");

        // other track: snaps even if close
        NowPlaying next = session(NowPlaying.Status.PLAYING, "U", 180_000, 0, 6000, 1.0);
        c.update(next, 6000);
        check(c.positionAt(6000) == 0 && c.positionAt(7000) == 1000, "clock: new track starts at its position");

        // clamps to the length
        NowPlaying end = session(NowPlaying.Status.PLAYING, "U", 180_000, 179_000, 8000, 1.0);
        c.update(end, 8000);
        check(c.positionAt(20_000) == 180_000, "clock: never past the end");

        // rate
        PlaybackClock fast = new PlaybackClock();
        fast.update(session(NowPlaying.Status.PLAYING, "T", 200_000, 0, 0, 2.0), 0);
        check(fast.positionAt(1000) == 2000, "clock: playback rate");

        // window title source has no timeline
        PlaybackClock none = new PlaybackClock();
        none.update(new NowPlaying(NowPlaying.Status.PLAYING, "T", "A"), 0);
        check(none.positionAt(5000) == 0, "clock: no timeline, no position");
        none.update(null, 10);
        check(none.positionAt(5000) == 0, "clock: null sample");
    }

    static void nowPlaying() {
        NowPlaying w = NowPlaying.fromWindowTitle("Daft Punk - One More Time", null);
        check(w.status() == NowPlaying.Status.PLAYING && w.artist().equals("Daft Punk") && w.title().equals("One More Time")
                && w.source() == NowPlaying.Source.WINDOW, "window title: Artist - Title");
        NowPlaying keepTitle = NowPlaying.fromWindowTitle("Artist - A - B", null);
        check(keepTitle.artist().equals("Artist") && keepTitle.title().equals("A - B"), "window title: first separator splits");
        NowPlaying idle = NowPlaying.fromWindowTitle("Spotify Free", w);
        check(idle.status() == NowPlaying.Status.PAUSED && idle.title().equals("One More Time"), "window title: Spotify = paused, keeps the last track");
        check(NowPlaying.fromWindowTitle("Spotify", null).status() == NowPlaying.Status.PAUSED && !NowPlaying.fromWindowTitle("Spotify", null).hasTrack(),
                "window title: nothing known");
        check(NowPlaying.fromWindowTitle(null, w) == NowPlaying.NOT_RUNNING && NowPlaying.fromWindowTitle("  ", w) == NowPlaying.NOT_RUNNING,
                "window title: no window");
        check(NowPlaying.fromWindowTitle("Just A Title", null).title().equals("Just A Title"), "window title: no separator");

        NowPlaying s = session(NowPlaying.Status.PLAYING, "T", 200_000, 50_000, 1000, 1.0);
        check(s.lyricsEligible() && s.hasTimeline(), "eligible: normal track");
        check(!session(NowPlaying.Status.PLAYING, "T", 20_000, 0, 0, 1).lyricsEligible(), "eligible: too short");
        check(!session(NowPlaying.Status.PLAYING, "T", 4_000_000, 0, 0, 1).lyricsEligible(), "eligible: too long");
        check(!session(NowPlaying.Status.PLAYING, "Advertisement", 200_000, 0, 0, 1).lyricsEligible(), "eligible: ad");
        check(!new NowPlaying(NowPlaying.Status.PLAYING, "Song", "", "", NowPlaying.Source.SESSION, 200_000, 0, 0, 1, null).lyricsEligible(),
                "eligible: no artist");
        check(!new NowPlaying(NowPlaying.Status.PLAYING, "T", "A").lyricsEligible(), "eligible: window source has no length");
        check(s.positionAt(3000) == 52_000 && session(NowPlaying.Status.PAUSED, "T", 200_000, 50_000, 1000, 1).positionAt(9000) == 50_000,
                "positionAt: advances only while playing");
        check(s.positionAt(10_000_000) == 200_000, "positionAt: capped at the length");
        NowPlaying toggled = s.toggled();
        check(toggled.status() == NowPlaying.Status.PAUSED && toggled.positionMs() >= 50_000, "toggled: playing -> paused");
        check(toggled.toggled().status() == NowPlaying.Status.PLAYING, "toggled: paused -> playing");
        check(NowPlaying.WAITING.toggled() == NowPlaying.WAITING, "toggled: nothing to toggle");
        check(s.sameTrack(session(NowPlaying.Status.PAUSED, "T", 1, 0, 0, 1)) && !s.sameTrack(session(NowPlaying.Status.PLAYING, "U", 1, 0, 0, 1)),
                "sameTrack");
        NowPlaying odd = new NowPlaying(null, null, null, null, null, -5, 99, 0, -3, null);
        check(odd.status() == NowPlaying.Status.NOT_RUNNING && odd.title().isEmpty() && odd.durationMs() == 0 && odd.rate() == 1.0, "record: sanitised");
    }

    // ---- album art ----

    static void art() {
        // 4x2 image: the centre square (columns 1..2) is kept
        ByteBuffer px = ByteBuffer.allocate(4 * 2 * 4);
        for (int i = 0; i < 8; i++) {
            px.put((byte) (i * 10)).put((byte) 0).put((byte) 0).put((byte) 255);
        }
        AlbumArt a = AlbumArt.crop(px, 4, 2, 256);
        check(a.size() == 2 && a.byteSize() == 16, "art: square crop of a wide image");
        check(a.argb(0, 0) == (0xFF000000 | 10 << 16) && a.argb(1, 0) == (0xFF000000 | 20 << 16) && a.argb(0, 1) == (0xFF000000 | 50 << 16),
                "art: centre columns, ARGB order");

        // 4x4 -> 2x2: each output pixel is the mean of a 2x2 block
        ByteBuffer big = ByteBuffer.allocate(4 * 4 * 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int v = x < 2 ? 0 : 200;
                big.put((byte) v).put((byte) (y < 2 ? 100 : 0)).put((byte) 7).put((byte) 255);
            }
        }
        AlbumArt small = AlbumArt.crop(big, 4, 4, 2);
        check(small.size() == 2 && small.argb(0, 0) == 0xFF000000 + (0 << 16) + (100 << 8) + 7 && small.argb(1, 1) == 0xFF000000 + (200 << 16) + 7,
                "art: box filter downscale");
        check(AlbumArt.crop(big, 4, 4, 100).size() == 4, "art: never upscaled");
    }

    // ---- disk cache ----

    static void diskCache() throws IOException {
        Path dir = Files.createTempDirectory("spotifyhud-cache");
        try {
            LyricsDiskCache cache = new LyricsDiskCache(dir.resolve("cache"), 2, 1_000_000);
            String k1 = "0123456789abcdef0123456789abcdef";
            String k2 = "1123456789abcdef0123456789abcdef";
            String k3 = "2123456789abcdef0123456789abcdef";
            LrclibTrack t = new LrclibTrack(9, "Halo", "Beyonce", "I Am", 261, false, "plain", "[00:01.00]x");
            check(cache.read(k1) == null, "cache: miss");
            cache.write(k1, t, 123456L);
            LyricsDiskCache.Entry e = cache.read(k1);
            check(e != null && e.found() && e.track().equals(t) && e.fetchedAt() == 123456L, "cache: round trip");
            cache.write(k2, null, 5L);
            LyricsDiskCache.Entry none = cache.read(k2);
            check(none != null && !none.found() && none.fetchedAt() == 5L, "cache: remembers 'not found'");
            check(cache.read("../evil") == null && cache.read(null) == null && cache.read("zz") == null, "cache: bad keys never touch the disk");
            cache.write("../escape", t, 1L);
            check(!Files.exists(dir.resolve("escape.json")), "cache: no write outside the folder");

            Files.writeString(dir.resolve("cache").resolve(k3 + ".json"), "{not json");
            check(cache.read(k3) == null, "cache: corrupt file is a miss");
            Files.writeString(dir.resolve("cache").resolve(k3 + ".json"), "{\"format\":99,\"fetchedAt\":5}");
            check(cache.read(k3) == null, "cache: unknown format is a miss");
            Files.writeString(dir.resolve("cache").resolve(k3 + ".json"), "{\"format\":1,\"fetchedAt\":5}");
            check(cache.read(k3) != null, "cache: valid minimal entry");

            // prune: oldest by use goes first, the limit is two files
            Files.setLastModifiedTime(dir.resolve("cache").resolve(k1 + ".json"), FileTime.fromMillis(1_000));
            Files.setLastModifiedTime(dir.resolve("cache").resolve(k2 + ".json"), FileTime.fromMillis(2_000));
            Files.setLastModifiedTime(dir.resolve("cache").resolve(k3 + ".json"), FileTime.fromMillis(3_000));
            Files.writeString(dir.resolve("cache").resolve("leftover.json.tmp"), "x");
            cache.prune();
            check(!Files.exists(dir.resolve("cache").resolve(k1 + ".json")) && Files.exists(dir.resolve("cache").resolve(k2 + ".json"))
                    && Files.exists(dir.resolve("cache").resolve(k3 + ".json")), "cache: prune removes the least recently used");
            check(!Files.exists(dir.resolve("cache").resolve("leftover.json.tmp")), "cache: prune removes temp files");
        } finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    // ---- lyric view ----

    static final LyricView.Metrics SIX = text -> text.length() * 6.0F;

    static Lyrics synced(String lrc) {
        return Lyrics.of(new LrclibTrack(1, "T", "A", "", 200, false, "", lrc));
    }

    static LyricView.View run(LyricView v, Lyrics l, long position, int frames, LyricMotion m, LyricHighlight h) {
        LyricView.View view = null;
        long now = 10_000;
        for (int i = 0; i < frames; i++) {
            view = v.update(l, true, true, position, 2, m, h, 200, 0.016F, now += 16);
        }
        return view;
    }

    static LyricView.Item item(LyricView.View view, String text) {
        for (LyricView.Item i : view.items()) {
            if (text.equals(i.text())) {
                return i;
            }
        }
        return null;
    }

    static void lyricView() {
        Lyrics l = synced("[00:00.00]alpha line\n[00:04.00]beta line\n[00:08.00]gamma line\n[00:12.00]delta line");
        LyricView v = new LyricView(SIX);
        LyricView.View view = run(v, l, 500, 60, LyricMotion.WAVE, LyricHighlight.KARAOKE);
        check(view.status() == null && !view.items().isEmpty(), "view: synced lyrics produce rows");
        check(Math.abs(view.height() - (LyricView.BLOCK_GAP + Math.round(2 * LyricView.PX + 3 - 1))) < 0.6F, "view: height settles at two rows plus the gap");
        LyricView.Item first = item(view, "alpha line");
        LyricView.Item second = item(view, "beta line");
        check(first != null && Math.abs(first.top()) < 0.5F && first.alpha() > 0.95F, "view: current line at the top, fully visible");
        check(second != null && second.top() > LyricView.PX && second.alpha() <= first.alpha(), "view: next line below the current one");
        check(first.highlight() > 0.95F && second.highlight() < 0.05F, "view: current line glows, next does not");
        check(first.fill() > 0.0F && first.fill() < 120.0F && second.fill() == 0.0F, "view: karaoke fills only the current line");

        // go on to the next line: the rows slide up, afterwards the old one is gone and the new one at the top
        LyricView.View sliding = v.update(l, true, true, 4100, 2, LyricMotion.WAVE, LyricHighlight.KARAOKE, 200, 0.016F, 20_000);
        LyricView.Item moving = item(sliding, "beta line");
        check(moving != null && moving.top() > 5.0F, "view: new current line starts below and slides in");
        LyricView.View settled = run(v, l, 4100, 120, LyricMotion.WAVE, LyricHighlight.KARAOKE);
        LyricView.Item now = item(settled, "beta line");
        check(now != null && Math.abs(now.top()) < 0.5F && now.highlight() > 0.95F, "view: slide ends at the top");
        LyricView.Item before = item(settled, "alpha line");
        check(before == null || before.fill() == Float.POSITIVE_INFINITY, "view: finished line is fully filled (or gone)");

        // FADE motion has no slide: the new line is at the top at once
        LyricView fadeView = new LyricView(SIX);
        run(fadeView, l, 500, 60, LyricMotion.FADE, LyricHighlight.OFF);
        LyricView.View faded = fadeView.update(l, true, true, 4100, 2, LyricMotion.FADE, LyricHighlight.OFF, 200, 0.016F, 30_000);
        // the cross-fade shows the old rows (fading out) and the new ones (fading in) at the same time: take the new one
        LyricView.Item direct = null;
        for (LyricView.Item i : faded.items()) {
            if ("beta line".equals(i.text()) && (direct == null || i.top() < direct.top())) {
                direct = i;
            }
        }
        check(direct != null && direct.top() > 4.0F && direct.top() <= 5.1F && direct.fill() == 0.0F,
                "view: fade mode: new line fades in from a small rise, no slide, no fill with highlight off");
        LyricView.View faded2 = run(fadeView, l, 4100, 60, LyricMotion.FADE, LyricHighlight.OFF);
        check(item(faded2, "beta line") != null && Math.abs(item(faded2, "beta line").top()) < 0.5F && item(faded2, "alpha line") == null,
                "view: fade mode settles, old line gone");

        // status texts
        LyricView st = new LyricView(SIX);
        check("No lyrics found".equals(st.update(Lyrics.NOT_FOUND, true, true, 0, 2, LyricMotion.WAVE, LyricHighlight.OFF, 200, 0.016F, 1000).status()),
                "status: not found");
        check("No saved lyrics".equals(st.update(Lyrics.NOT_FOUND, true, false, 0, 2, LyricMotion.WAVE, LyricHighlight.OFF, 200, 0.016F, 1016).status()),
                "status: not found offline");
        check("Lyrics unavailable".equals(st.update(Lyrics.UNAVAILABLE, true, true, 0, 2, LyricMotion.WAVE, LyricHighlight.OFF, 200, 0.016F, 1032).status()),
                "status: unavailable");
        check("Instrumental".equals(st.update(new Lyrics(Lyrics.Status.INSTRUMENTAL, null, null, 0), true, true, 0, 2, LyricMotion.WAVE,
                LyricHighlight.OFF, 200, 0.016F, 1048).status()), "status: instrumental");
        check("No synced lyrics".equals(st.update(new Lyrics(Lyrics.Status.PLAIN, null, List.of("x"), 0), true, true, 0, 2, LyricMotion.WAVE,
                LyricHighlight.OFF, 200, 0.016F, 1064).status()), "status: plain only");
        LyricView loading = new LyricView(SIX);
        check(loading.update(Lyrics.LOADING, true, true, 0, 2, LyricMotion.WAVE, LyricHighlight.OFF, 200, 0.016F, 5000).status() == null,
                "status: loading is silent at first");
        check("Loading lyrics".equals(loading.update(Lyrics.LOADING, true, true, 0, 2, LyricMotion.WAVE, LyricHighlight.OFF, 200, 0.016F, 5800).status()),
                "status: loading shown after 0.7 s");

        // not wanted: the block closes
        LyricView close = new LyricView(SIX);
        run(close, l, 500, 60, LyricMotion.WAVE, LyricHighlight.OFF);
        LyricView.View gone = null;
        for (int i = 0; i < 200; i++) {
            gone = close.update(null, false, true, 500, 2, LyricMotion.WAVE, LyricHighlight.OFF, 200, 0.016F, 50_000 + i * 16L);
        }
        check(gone.height() == 0.0F && gone.items().isEmpty(), "view: block closes when lyrics are switched off");

        // pause between lines
        Lyrics gap = synced("[00:00.00]one\n[00:02.00]\n[00:10.00]two");
        LyricView gv = new LyricView(SIX);
        LyricView.View inGap = run(gv, gap, 5000, 60, LyricMotion.WAVE, LyricHighlight.KARAOKE);
        boolean dots = false;
        for (LyricView.Item i : inGap.items()) {
            dots |= i.text() == null && i.progress() > 0.2F && i.progress() < 0.8F;
        }
        check(dots, "view: empty line is a row of dots with progress");

        // wrapping
        LyricView wrap = new LyricView(SIX);
        check(Arrays.equals(wrap.wrap("short", 200), new String[] {"short"}), "wrap: fits");
        String[] two = wrap.wrap("one two three four five six", 100);
        check(two.length == 2 && two[0].length() * 6 <= 100 && two[1].length() * 6 <= 100 && (two[0] + " " + two[1]).equals("one two three four five six"),
                "wrap: two even halves");
        String[] cut = wrap.wrap("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz", 100);
        check(cut.length == 2 && cut[0].length() * 6 <= 100, "wrap: no spaces cuts at the width");
        String[] ell = wrap.wrap("this is a very long line that cannot fit in two rows of sixteen chars at all", 100);
        check(ell.length == 2 && ell[1].endsWith("..."), "wrap: rest is shortened with an ellipsis");
        check(wrap.wrap("", 100).length == 0, "wrap: empty text has no rows");
        check(wrap.ellipsize("abcdefghijkl", 60).length() * 6 <= 60 && wrap.ellipsize("abcdefghijkl", 60).endsWith("..."), "ellipsize: fits the width");

        // helpers
        check(LyricView.springLeft(0) == 1.0F && LyricView.springLeft(1.37F) < 0.001F && LyricView.springLeft(0.1F) < LyricView.springLeft(0.05F),
                "spring: 1 at start, settled at 1.37 s, falling");
        check(LyricView.topFade(0) == 1.0F && LyricView.topFade(-10) == 0.0F && LyricView.topFade(-2.5F) > 0.0F && LyricView.topFade(-2.5F) < 1.0F,
                "topFade");
        check(LyricView.bottomFade(0) == 1.0F && LyricView.bottomFade(20) == 0.0F, "bottomFade");
        check(LyricView.smooth(-1) == 0.0F && LyricView.smooth(2) == 1.0F && LyricView.smooth(0.5F) == 0.5F, "smooth");
        check(LyricView.letterCount("ab cd ") == 4, "letterCount");

        // karaoke from word times: first word done, second half done
        Lyrics timed = synced("[00:10.00]<00:10.00>aaaa <00:11.00>bbbb\n[00:20.00]end");
        LyricView kv = new LyricView(SIX);
        LyricView.View mid = run(kv, timed, 10_400, 60, LyricMotion.WAVE, LyricHighlight.KARAOKE);
        LyricView.Item line = item(mid, "aaaa bbbb");
        // 400 ms into a 180 ms sweep: the first word is lit completely, the second not at all: fill = width of "aaaa" (4 chars)
        check(line != null && Math.abs(line.fill() - 24.0F) < 0.01F, "karaoke: word times drive the fill (first word done)");
        LyricView.View later = run(kv, timed, 11_090, 5, LyricMotion.WAVE, LyricHighlight.KARAOKE);
        LyricView.Item part = item(later, "aaaa bbbb");
        check(part != null && part.fill() > 24.0F + 6.0F && part.fill() < 54.0F, "karaoke: second word partly filled");
    }
}
