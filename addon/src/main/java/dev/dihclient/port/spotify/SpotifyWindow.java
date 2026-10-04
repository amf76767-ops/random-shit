package dev.dihclient.port.spotify;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Fallback for the media session: finds the window of spotify.exe and reads its title ("Artist - Title" while playing,
 * "Spotify" when paused), and can tap the global media keys. Win32 only, declared against plain JNA (Anubis used
 * jna-platform). Nothing leaves this PC.
 */
public final class SpotifyWindow {
    private static final String EXECUTABLE = "spotify.exe";
    private static final String MAIN_CLASS = "Chrome_WidgetWin_0";
    private static final String CLASS_FAMILY = "Chrome_WidgetWin";
    private static final long PID_CACHE_MS = 30000L;
    private static final int PID_CACHE_MAX = 512;
    private static final long RECHECK_MS = 10000L;
    private static final String TRACK_SEPARATOR = " - ";
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int INPUT_KEYBOARD = 1;
    private static final int KEYEVENTF_EXTENDEDKEY = 1;
    private static final int KEYEVENTF_KEYUP = 2;
    private final char[] textBuffer = new char[512];
    private final char[] classBuffer = new char[64];
    private final char[] pathBuffer = new char[1024];
    private final IntByReference pidOut = new IntByReference();
    private final IntByReference sizeInOut = new IntByReference();
    private final Map<Integer, Boolean> spotifyPids = new HashMap<>();
    private long pidCacheSince;
    private Pointer window;
    private long foundAt;
    private Pointer best;
    private int bestScore;
    // JNA only keeps a weak link to a callback object: this field keeps it alive
    private final EnumProc finder = this::consider;

    /** Title of the Spotify window, null when there is none. Slow path (window enumeration) at most every 10 s. */
    public String readTitle() {
        long now = System.currentTimeMillis();
        if (this.window != null && User32.INSTANCE.IsWindow(this.window) && this.isSpotify(this.processOf(this.window))) {
            String title = this.text(this.window);
            if (title.contains(TRACK_SEPARATOR) || !title.isEmpty() && now - this.foundAt < RECHECK_MS) {
                return title;
            }
        }
        this.window = this.find();
        this.foundAt = now;
        return this.window == null ? null : this.text(this.window);
    }

    /** Press and release a media key as if it were on the keyboard (the OS hands it to the media session). */
    public static void tap(MediaKey key) {
        send(key.virtualKey, KEYEVENTF_EXTENDEDKEY);
        send(key.virtualKey, KEYEVENTF_EXTENDEDKEY | KEYEVENTF_KEYUP);
    }

    private Pointer find() {
        long now = System.currentTimeMillis();
        if (now - this.pidCacheSince > PID_CACHE_MS || this.spotifyPids.size() > PID_CACHE_MAX) {
            this.spotifyPids.clear();
            this.pidCacheSince = now;
        }
        this.best = null;
        this.bestScore = 0;
        User32.INSTANCE.EnumWindows(this.finder, null);
        Pointer found = this.best;
        this.best = null;
        return found;
    }

    private boolean consider(Pointer hwnd, Pointer data) {
        if (!this.isSpotify(this.processOf(hwnd))) {
            return true;
        }
        String title = this.text(hwnd);
        if (title.isEmpty() || title.equals("Default IME") || title.equals("MSCTFIME UI") || title.startsWith("GDI+ Window")) {
            return true;
        }
        String windowClass = this.className(hwnd);
        int score = 1;
        if (windowClass.equals(MAIN_CLASS)) {
            score += 4;
        } else if (windowClass.startsWith(CLASS_FAMILY)) {
            score += 2;
        }
        if (title.contains(TRACK_SEPARATOR)) {
            score += 3;
        } else if (title.startsWith("Spotify")) {
            score++;
        }
        if (User32.INSTANCE.IsWindowVisible(hwnd)) {
            score++;
        }
        if (score > this.bestScore) {
            this.bestScore = score;
            this.best = hwnd;
        }
        return true;
    }

    private int processOf(Pointer hwnd) {
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, this.pidOut);
        return this.pidOut.getValue();
    }

    private boolean isSpotify(int pid) {
        if (pid <= 0) {
            return false;
        }
        Boolean cached = this.spotifyPids.get(pid);
        if (cached != null) {
            return cached;
        }
        boolean spotify = EXECUTABLE.equals(this.imageName(pid));
        this.spotifyPids.put(pid, spotify);
        return spotify;
    }

    private String imageName(int pid) {
        Pointer process = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
        if (process == null) {
            return "";
        }
        try {
            this.sizeInOut.setValue(this.pathBuffer.length);
            if (Kernel32.INSTANCE.QueryFullProcessImageNameW(process, 0, this.pathBuffer, this.sizeInOut)) {
                String path = new String(this.pathBuffer, 0, Math.min(this.sizeInOut.getValue(), this.pathBuffer.length));
                return path.substring(path.lastIndexOf('\\') + 1).toLowerCase(Locale.ROOT);
            }
            return "";
        } finally {
            Kernel32.INSTANCE.CloseHandle(process);
        }
    }

    private String text(Pointer hwnd) {
        int length = User32.INSTANCE.GetWindowTextW(hwnd, this.textBuffer, this.textBuffer.length);
        return length <= 0 ? "" : new String(this.textBuffer, 0, Math.min(length, this.textBuffer.length));
    }

    private String className(Pointer hwnd) {
        int length = User32.INSTANCE.GetClassNameW(hwnd, this.classBuffer, this.classBuffer.length);
        return length <= 0 ? "" : new String(this.classBuffer, 0, Math.min(length, this.classBuffer.length));
    }

    /** One keyboard INPUT record written by hand: 40 bytes on 64-bit Windows, 28 on 32-bit (the union is padded to a pointer). */
    private static void send(int virtualKey, int flags) {
        int pointer = Native.POINTER_SIZE;
        int unionAt = pointer;
        int size = unionAt + (pointer == 8 ? 32 : 24);
        Memory input = new Memory(size);
        input.clear();
        input.setInt(0L, INPUT_KEYBOARD);
        input.setShort(unionAt, (short) virtualKey);
        input.setShort(unionAt + 2L, (short) 0);
        input.setInt(unionAt + 4L, flags);
        input.setInt(unionAt + 8L, 0);
        User32.INSTANCE.SendInput(1, input, size);
    }

    public enum MediaKey {
        PLAY_PAUSE(0xB3),
        NEXT(0xB0),
        PREVIOUS(0xB1);

        final int virtualKey;

        MediaKey(int virtualKey) {
            this.virtualKey = virtualKey;
        }
    }

    public interface EnumProc extends StdCallLibrary.StdCallCallback {
        boolean callback(Pointer hwnd, Pointer data);
    }

    interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean EnumWindows(EnumProc proc, Pointer data);

        boolean IsWindow(Pointer hwnd);

        boolean IsWindowVisible(Pointer hwnd);

        int GetWindowTextW(Pointer hwnd, char[] text, int max);

        int GetClassNameW(Pointer hwnd, char[] text, int max);

        int GetWindowThreadProcessId(Pointer hwnd, IntByReference pid);

        int SendInput(int count, Pointer inputs, int size);
    }

    interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        Pointer OpenProcess(int access, boolean inherit, int pid);

        boolean QueryFullProcessImageNameW(Pointer process, int flags, char[] path, IntByReference size);

        boolean CloseHandle(Pointer handle);
    }
}
