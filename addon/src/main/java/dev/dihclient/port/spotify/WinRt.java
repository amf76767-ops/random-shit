package dev.dihclient.port.spotify;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Ported from an open-source client (GPL-3.0).
 * Just enough of the Windows Runtime (WinRT) to call COM objects through their vtables with plain JNA: activation
 * factories, IAsyncOperation waiting, HSTRINGs. Only ever loaded on Windows (see {@link MediaSession}).
 * The original used jna-platform's GUID type here; this version keeps a GUID as 16 bytes of native memory instead.
 */
final class WinRt {
    static final int RO_INIT_MULTITHREADED = 1;
    static final int RPC_E_CHANGED_MODE = 0x80010106;
    static final int REGDB_E_CLASSNOTREG = 0x80040154;
    static final int E_NOINTERFACE = 0x80004002;
    static final int E_ABORT = 0x80004004;
    static final int E_TIMEOUT = 0x800705B4;
    private static final int SLOT_QUERY_INTERFACE = 0;
    private static final int SLOT_RELEASE = 2;
    private static final int ASYNC_OPERATION_GET_RESULTS = 8;
    private static final Memory IID_ASYNC_INFO = iid("00000036-0000-0000-C000-000000000046");
    private static final int ASYNC_INFO_GET_STATUS = 7;
    private static final int ASYNC_INFO_GET_ERROR_CODE = 8;
    private static final int ASYNC_INFO_CANCEL = 9;
    private static final int ASYNC_COMPLETED = 1;
    private static final int ASYNC_CANCELED = 2;
    private static final long MAX_WAIT_STEP_MS = 5L;
    /** Function.ALT_CONVENTION: the vtable entries are stdcall on 32-bit Windows (no difference on 64-bit). */
    private static final int CALL_FLAGS = 63;

    private WinRt() {
    }

    /** Native copy of the GUID; keep a reference to it, the memory is freed when the object is collected. */
    static Memory iid(String text) {
        byte[] bytes = GuidBytes.of(text);
        Memory memory = new Memory(bytes.length);
        memory.write(0L, bytes, 0, bytes.length);
        return memory;
    }

    static int initializeMultithreaded() {
        return Combase.INSTANCE.RoInitialize(RO_INIT_MULTITHREADED);
    }

    static void uninitialize() {
        Combase.INSTANCE.RoUninitialize();
    }

    static Pointer activationFactory(String runtimeClass, Pointer iid) {
        Pointer name = createString(runtimeClass);
        try {
            PointerByReference factory = new PointerByReference();
            check(Combase.INSTANCE.RoGetActivationFactory(name, iid, factory), "RoGetActivationFactory(" + runtimeClass + ")");
            return owned(factory.getValue(), "RoGetActivationFactory(" + runtimeClass + ")");
        } finally {
            Combase.INSTANCE.WindowsDeleteString(name);
        }
    }

    /** Calls vtable entry {@code slot} of the COM object (slot 0-2 are IUnknown, 3-5 IInspectable, methods start at 6). */
    static int invoke(Pointer self, int slot, Object... args) {
        Pointer vtable = self.getPointer(0L);
        Function method = Function.getFunction(vtable.getPointer((long) slot * Native.POINTER_SIZE), CALL_FLAGS);
        Object[] full = new Object[args.length + 1];
        full[0] = self;
        System.arraycopy(args, 0, full, 1, args.length);
        return method.invokeInt(full);
    }

    static void check(int hresult, String step) {
        if (hresult < 0) {
            throw new WinRtException(step, hresult);
        }
    }

    static void release(Pointer pointer) {
        if (pointer != null) {
            invoke(pointer, SLOT_RELEASE);
        }
    }

    static Pointer queryInterface(Pointer self, Pointer iid, String step) {
        PointerByReference out = new PointerByReference();
        check(invoke(self, SLOT_QUERY_INTERFACE, iid, out), step);
        return owned(out.getValue(), step);
    }

    static Pointer tryQueryInterface(Pointer self, Pointer iid) {
        PointerByReference out = new PointerByReference();
        return invoke(self, SLOT_QUERY_INTERFACE, iid, out) < 0 ? null : out.getValue();
    }

    static Pointer callForObject(Pointer self, int slot, String step, Object... inArgs) {
        PointerByReference out = new PointerByReference();
        check(invoke(self, slot, append(inArgs, out)), step);
        return out.getValue();
    }

    static String getString(Pointer self, int slot, String step) {
        PointerByReference out = new PointerByReference();
        check(invoke(self, slot, out), step);
        Pointer hstring = out.getValue();
        if (hstring == null) {
            return "";
        }
        try {
            return readString(hstring);
        } finally {
            Combase.INSTANCE.WindowsDeleteString(hstring);
        }
    }

    static int getInt32(Pointer self, int slot, String step) {
        IntByReference out = new IntByReference();
        check(invoke(self, slot, out), step);
        return out.getValue();
    }

    static long getInt64(Pointer self, int slot, String step) {
        LongByReference out = new LongByReference();
        check(invoke(self, slot, out), step);
        return out.getValue();
    }

    static double getDouble(Pointer self, int slot, String step) {
        DoubleByReference out = new DoubleByReference();
        check(invoke(self, slot, out), step);
        return out.getValue();
    }

    static Pointer awaitObject(Pointer operation, long timeoutMs, String step) {
        await(operation, timeoutMs, step);
        PointerByReference out = new PointerByReference();
        check(invoke(operation, ASYNC_OPERATION_GET_RESULTS, out), step + " results");
        return out.getValue();
    }

    static boolean awaitBoolean(Pointer operation, long timeoutMs, String step) {
        await(operation, timeoutMs, step);
        ByteByReference out = new ByteByReference();
        check(invoke(operation, ASYNC_OPERATION_GET_RESULTS, out), step + " results");
        return out.getValue() != 0;
    }

    static int awaitUInt32(Pointer operation, long timeoutMs, String step) {
        await(operation, timeoutMs, step);
        IntByReference out = new IntByReference();
        check(invoke(operation, ASYNC_OPERATION_GET_RESULTS, out), step + " results");
        return out.getValue();
    }

    /** Polls IAsyncInfo until the operation completed; cancels it on timeout or interrupt. */
    private static void await(Pointer operation, long timeoutMs, String step) {
        if (operation == null) {
            throw new WinRtException(step + " returned no operation", E_ABORT);
        }
        Pointer info = queryInterface(operation, IID_ASYNC_INFO, step + " IAsyncInfo");
        try {
            long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            long pause = 1L;
            while (true) {
                int status = getInt32(info, ASYNC_INFO_GET_STATUS, step + " status");
                if (status == ASYNC_COMPLETED) {
                    return;
                }
                if (status == ASYNC_CANCELED) {
                    throw new WinRtException(step + " canceled", E_ABORT);
                }
                if (status != 0) {
                    IntByReference code = new IntByReference();
                    int hresult = invoke(info, ASYNC_INFO_GET_ERROR_CODE, code);
                    throw new WinRtException(step, hresult < 0 ? hresult : code.getValue());
                }
                if (System.nanoTime() - deadline >= 0L) {
                    invoke(info, ASYNC_INFO_CANCEL);
                    throw new WinRtException(step + " timed out", E_TIMEOUT);
                }
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException e) {
                    invoke(info, ASYNC_INFO_CANCEL);
                    Thread.currentThread().interrupt();
                    throw new WinRtException(step + " interrupted", E_ABORT);
                }
                pause = Math.min(pause + 1L, MAX_WAIT_STEP_MS);
            }
        } finally {
            release(info);
        }
    }

    private static Pointer createString(String text) {
        PointerByReference out = new PointerByReference();
        check(Combase.INSTANCE.WindowsCreateString(new WString(text), text.length(), out), "WindowsCreateString");
        return out.getValue();
    }

    private static String readString(Pointer hstring) {
        IntByReference length = new IntByReference();
        Pointer buffer = Combase.INSTANCE.WindowsGetStringRawBuffer(hstring, length);
        int count = length.getValue();
        return buffer != null && count > 0 ? new String(buffer.getCharArray(0L, count)) : "";
    }

    private static Pointer owned(Pointer pointer, String step) {
        if (pointer == null) {
            throw new WinRtException(step + " returned null", E_NOINTERFACE);
        }
        return pointer;
    }

    private static Object[] append(Object[] args, Object last) {
        Object[] all = new Object[args.length + 1];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = last;
        return all;
    }

    interface Combase extends StdCallLibrary {
        Combase INSTANCE = Native.load("combase", Combase.class);

        int RoInitialize(int initType);

        void RoUninitialize();

        int RoGetActivationFactory(Pointer classId, Pointer iid, PointerByReference factory);

        int WindowsCreateString(WString source, int length, PointerByReference string);

        int WindowsDeleteString(Pointer string);

        Pointer WindowsGetStringRawBuffer(Pointer string, IntByReference length);
    }

    /** COM references taken during one call; released in reverse order when the block ends. */
    static final class Refs implements AutoCloseable {
        private final ArrayDeque<Pointer> held = new ArrayDeque<>();

        Pointer add(Pointer pointer) {
            if (pointer != null) {
                this.held.push(pointer);
            }
            return pointer;
        }

        @Override
        public void close() {
            while (!this.held.isEmpty()) {
                release(this.held.pop());
            }
        }
    }

    static final class WinRtException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int hresult;

        WinRtException(String step, int hresult) {
            super(step + " failed (0x" + Integer.toHexString(hresult).toUpperCase(Locale.ROOT) + ")", null, false, false);
            this.hresult = hresult;
        }
    }
}
