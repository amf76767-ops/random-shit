package dev.dihclient.port.noinvleak;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

/** Ported from an open-source client (GPL-3.0). */
public final class Win32 {
    static final int WS_POPUP = 0x80000000;

    static final int EX_STYLE = 0x08000000 | 0x00080000 | 0x00000080 | 0x00000020;
    static final int WDA_EXCLUDEFROMCAPTURE = 0x11;
    static final int SW_HIDE = 0;
    static final int SW_SHOWNOACTIVATE = 4;

    static final int SWP_MOVE_ONLY = 0x01 | 0x04 | 0x10;
    static final int ULW_ALPHA = 2;
    static final int AC_SRC_ALPHA = 1;

    private Win32() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    public interface WindowProc extends StdCallLibrary.StdCallCallback {
        Pointer callback(Pointer hwnd, int message, Pointer wParam, Pointer lParam);
    }

    @Structure.FieldOrder({"cbSize", "style", "lpfnWndProc", "cbClsExtra", "cbWndExtra", "hInstance", "hIcon", "hCursor",
            "hbrBackground", "lpszMenuName", "lpszClassName", "hIconSm"})
    public static class WndClassEx extends Structure {
        public int cbSize;
        public int style;
        public WindowProc lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public Pointer hInstance;
        public Pointer hIcon;
        public Pointer hCursor;
        public Pointer hbrBackground;
        public WString lpszMenuName;
        public WString lpszClassName;
        public Pointer hIconSm;
    }

    @Structure.FieldOrder({"x", "y"})
    public static class Point extends Structure {
        public int x;
        public int y;
    }

    @Structure.FieldOrder({"cx", "cy"})
    public static class Size extends Structure {
        public int cx;
        public int cy;
    }

    @Structure.FieldOrder({"blendOp", "blendFlags", "sourceConstantAlpha", "alphaFormat"})
    public static class BlendFunction extends Structure {
        public byte blendOp;
        public byte blendFlags;
        public byte sourceConstantAlpha;
        public byte alphaFormat;
    }

    @Structure.FieldOrder({"biSize", "biWidth", "biHeight", "biPlanes", "biBitCount", "biCompression", "biSizeImage",
            "biXPelsPerMeter", "biYPelsPerMeter", "biClrUsed", "biClrImportant", "bmiColors"})
    public static class BitmapInfo extends Structure {
        public int biSize;
        public int biWidth;
        public int biHeight;
        public short biPlanes;
        public short biBitCount;
        public int biCompression;
        public int biSizeImage;
        public int biXPelsPerMeter;
        public int biYPelsPerMeter;
        public int biClrUsed;
        public int biClrImportant;
        public int bmiColors;
    }

    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        short RegisterClassExW(WndClassEx cls);

        Pointer CreateWindowExW(int exStyle, WString className, WString windowName, int style, int x, int y, int width, int height,
                Pointer parent, Pointer menu, Pointer instance, Pointer param);

        Pointer DefWindowProcW(Pointer hwnd, int message, Pointer wParam, Pointer lParam);

        boolean DestroyWindow(Pointer hwnd);

        boolean ShowWindow(Pointer hwnd, int command);

        boolean SetWindowPos(Pointer hwnd, Pointer insertAfter, int x, int y, int cx, int cy, int flags);

        boolean UpdateLayeredWindow(Pointer hwnd, Pointer dstDc, Point dstPos, Size size, Pointer srcDc, Point srcPos, int colorKey,
                BlendFunction blend, int flags);

        boolean SetWindowDisplayAffinity(Pointer hwnd, int affinity);
    }

    public interface Gdi32 extends StdCallLibrary {
        Gdi32 INSTANCE = Native.load("gdi32", Gdi32.class);

        Pointer CreateDIBSection(Pointer dc, BitmapInfo info, int usage, PointerByReference bits, Pointer section, int offset);

        Pointer CreateCompatibleDC(Pointer dc);

        Pointer SelectObject(Pointer dc, Pointer object);

        boolean DeleteObject(Pointer object);

        boolean DeleteDC(Pointer dc);
    }

    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        Pointer GetModuleHandleW(WString name);

        int GetLastError();
    }
}
