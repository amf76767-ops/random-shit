package dev.dihclient.port.noinvleak;

import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.lwjgl.glfw.GLFWNativeWin32;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * <p>
 * A click-through, layered pop-up window owned by the game window. {@code SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)}
 * makes screen capture (OBS window/display capture, Discord, the Game Bar, screenshots) skip it, while the player still sees it
 * on the monitor. Pixels are premultiplied ARGB in a DIB section that {@code UpdateLayeredWindow} blends onto the desktop.
 * Windows 10 2004 (build 19041) or newer; older builds reject the affinity flag and the constructor throws.
 */
final class OverlayWindow {
    private static final String CLASS_NAME = "DIHNoInvLeakOverlay";
    /** Strong reference: JNA only keeps a weak one to a callback. */
    private static final Win32.WindowProc PROC = (hwnd, message, wParam, lParam) -> Win32.User32.INSTANCE.DefWindowProcW(hwnd, message, wParam, lParam);
    private static boolean classRegistered;

    private final Win32.Point destination = new Win32.Point();
    private final Win32.Point source = new Win32.Point();
    private final Win32.Size size = new Win32.Size();
    private final Win32.BlendFunction blend = new Win32.BlendFunction();
    private Pointer hwnd;
    private Pointer memory;
    private Pointer bitmap;
    private Pointer previousBitmap;
    private Pointer bitsPointer;
    private IntBuffer bits;
    private int capacityWidth;
    private int capacityHeight;
    private boolean visible;

    OverlayWindow(long glfwWindow) {
        Pointer instance = Win32.Kernel32.INSTANCE.GetModuleHandleW(null);
        if (!classRegistered) {
            Win32.WndClassEx cls = new Win32.WndClassEx();
            cls.cbSize = cls.size();
            cls.lpfnWndProc = PROC;
            cls.hInstance = instance;
            cls.lpszClassName = new WString(CLASS_NAME);
            if (Win32.User32.INSTANCE.RegisterClassExW(cls) == 0) {
                throw failure("RegisterClassEx");
            }
            classRegistered = true;
        }
        Pointer owner = Pointer.createConstant(GLFWNativeWin32.glfwGetWin32Window(glfwWindow));
        this.hwnd = Win32.User32.INSTANCE.CreateWindowExW(Win32.EX_STYLE, new WString(CLASS_NAME), new WString(""), Win32.WS_POPUP,
                0, 0, 1, 1, owner, null, instance, null);
        if (this.hwnd == null) {
            throw failure("CreateWindowEx");
        }
        if (!Win32.User32.INSTANCE.SetWindowDisplayAffinity(this.hwnd, Win32.WDA_EXCLUDEFROMCAPTURE)) {
            RuntimeException failure = failure("SetWindowDisplayAffinity");
            this.destroy();
            throw failure;
        }
        this.blend.blendOp = 0;
        this.blend.sourceConstantAlpha = (byte) 255;
        this.blend.alphaFormat = (byte) Win32.AC_SRC_ALPHA;
    }

    /** Cleared pixel buffer for the next picture; its row length is {@link #stride()}. */
    IntBuffer pixels(int width, int height) {
        if (width > this.capacityWidth || height > this.capacityHeight) {
            int nextWidth = Math.max(width, this.capacityWidth);
            int nextHeight = Math.max(height, this.capacityHeight);
            this.releaseBitmap();
            Win32.BitmapInfo info = new Win32.BitmapInfo();
            info.biSize = 40;
            info.biWidth = nextWidth;
            info.biHeight = -nextHeight; // negative: top-down rows
            info.biPlanes = 1;
            info.biBitCount = 32;
            info.biCompression = 0;
            PointerByReference bitsOut = new PointerByReference();
            this.bitmap = Win32.Gdi32.INSTANCE.CreateDIBSection(null, info, 0, bitsOut, null, 0);
            if (this.bitmap == null) {
                throw failure("CreateDIBSection");
            }
            if (this.memory == null) {
                this.memory = Win32.Gdi32.INSTANCE.CreateCompatibleDC(null);
                if (this.memory == null) {
                    throw failure("CreateCompatibleDC");
                }
            }
            this.previousBitmap = Win32.Gdi32.INSTANCE.SelectObject(this.memory, this.bitmap);
            this.capacityWidth = nextWidth;
            this.capacityHeight = nextHeight;
            this.bitsPointer = bitsOut.getValue();
            this.bits = this.bitsPointer.getByteBuffer(0L, (long) nextWidth * nextHeight * 4L).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        }
        this.bitsPointer.setMemory(0L, (long) this.capacityWidth * height * 4L, (byte) 0);
        return this.bits;
    }

    int stride() {
        return this.capacityWidth;
    }

    void present(int screenX, int screenY, int width, int height) {
        this.destination.x = screenX;
        this.destination.y = screenY;
        this.size.cx = width;
        this.size.cy = height;
        if (!Win32.User32.INSTANCE.UpdateLayeredWindow(this.hwnd, null, this.destination, this.size, this.memory, this.source, 0,
                this.blend, Win32.ULW_ALPHA)) {
            throw failure("UpdateLayeredWindow");
        }
        if (!this.visible) {
            Win32.User32.INSTANCE.ShowWindow(this.hwnd, Win32.SW_SHOWNOACTIVATE);
            this.visible = true;
        }
    }

    void move(int screenX, int screenY) {
        Win32.User32.INSTANCE.SetWindowPos(this.hwnd, null, screenX, screenY, 0, 0, Win32.SWP_MOVE_ONLY);
    }

    void hide() {
        if (this.visible) {
            Win32.User32.INSTANCE.ShowWindow(this.hwnd, Win32.SW_HIDE);
            this.visible = false;
        }
    }

    void destroy() {
        this.releaseBitmap();
        if (this.memory != null) {
            Win32.Gdi32.INSTANCE.DeleteDC(this.memory);
            this.memory = null;
        }
        if (this.hwnd != null) {
            Win32.User32.INSTANCE.DestroyWindow(this.hwnd);
            this.hwnd = null;
        }
        this.visible = false;
    }

    private void releaseBitmap() {
        if (this.bitmap != null) {
            if (this.previousBitmap != null) {
                Win32.Gdi32.INSTANCE.SelectObject(this.memory, this.previousBitmap);
            }
            Win32.Gdi32.INSTANCE.DeleteObject(this.bitmap);
            this.bitmap = null;
            this.previousBitmap = null;
            this.bitsPointer = null;
            this.bits = null;
            this.capacityWidth = 0;
            this.capacityHeight = 0;
        }
    }

    private static RuntimeException failure(String call) {
        return new IllegalStateException(call + " failed with error " + Win32.Kernel32.INSTANCE.GetLastError());
    }
}
