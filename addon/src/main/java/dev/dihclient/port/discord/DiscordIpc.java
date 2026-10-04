package dev.dihclient.port.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.Closeable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Ported from Anubis Client 0.9.8 (GPL-3.0).
 * Minimal client for Discord's local IPC (rich presence): a named pipe on Windows, a unix socket elsewhere. Nothing here
 * touches the internet; the desktop app does the talking to Discord. Not thread safe: one thread owns an instance.
 * The frame format is {@code [int32 LE opcode][int32 LE length][UTF-8 JSON]}.
 */
public final class DiscordIpc implements Closeable {
    public static final int OP_HANDSHAKE = 0;
    public static final int OP_FRAME = 1;
    public static final int OP_CLOSE = 2;
    public static final int OP_PING = 3;
    public static final int OP_PONG = 4;
    public static final int MAX_FRAME_BYTES = 1 << 20;

    private static final int RPC_VERSION = 1;
    private static final int SOCKET_SLOTS = 10;
    private static final String[] UNIX_TEMP_VARIABLES = {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"};
    private static final long REPLY_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long READ_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(20);
    private static final long PID = ProcessHandle.current().pid();
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final Pipe pipe;
    private final ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);

    private DiscordIpc(Pipe pipe) {
        this.pipe = pipe;
    }

    // ---- pure protocol helpers (unit tested) ----

    /** One frame on the wire. */
    public static byte[] encode(int opcode, byte[] body) {
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(opcode).putInt(body.length).put(body);
        return frame.array();
    }

    /** Reads the 8 byte header and returns {opcode, length}; rejects lengths we would never accept. */
    public static int[] parseHeader(byte[] header) throws IOException {
        if (header == null || header.length < 8) {
            throw new EOFException("short frame header");
        }
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = b.getInt();
        int length = b.getInt();
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new IOException("Discord sent a frame of " + length + " bytes");
        }
        return new int[] {opcode, length};
    }

    /** Decodes exactly one complete frame. */
    public static Frame decode(byte[] data) throws IOException {
        int[] h = parseHeader(data);
        if (data.length != 8 + h[1]) {
            throw new IOException("frame length mismatch");
        }
        byte[] body = new byte[h[1]];
        System.arraycopy(data, 8, body, 0, h[1]);
        return new Frame(h[0], body);
    }

    public static String toJson(JsonElement element) {
        return GSON.toJson(element);
    }

    /** SET_ACTIVITY command; a null activity clears the presence. */
    public static JsonObject setActivityCommand(JsonObject activity, long pid, String nonce) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        args.add("activity", activity != null ? activity : JsonNull.INSTANCE);
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", nonce);
        return command;
    }

    public static JsonObject handshakeBody(String applicationId) {
        JsonObject hello = new JsonObject();
        hello.addProperty("v", RPC_VERSION);
        hello.addProperty("client_id", applicationId);
        return hello;
    }

    public static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    // ---- connection ----

    /** @return a ready connection, or null when Discord is not running (no pipe / socket found). */
    public static DiscordIpc connect(String applicationId) throws IOException {
        boolean windows = isWindows(System.getProperty("os.name"));
        return connect(applicationId, windows, windows ? List.of() : unixFolders());
    }

    /** Same, with the places to look in given by the caller (tests use a temp folder). */
    static DiscordIpc connect(String applicationId, boolean windows, Iterable<Path> folders) throws IOException {
        Pipe pipe = windows ? openWindowsPipe() : openUnixSocket(folders);
        if (pipe == null) {
            return null;
        }
        DiscordIpc ipc = new DiscordIpc(pipe);
        try {
            ipc.handshake(applicationId);
            return ipc;
        } catch (RuntimeException | IOException e) {
            ipc.close();
            throw e;
        }
    }

    /** @param activity the activity, or null to clear */
    public void setActivity(JsonObject activity) throws IOException, RefusedException {
        String nonce = UUID.randomUUID().toString();
        this.send(OP_FRAME, setActivityCommand(activity, PID, nonce));
        long deadline = System.nanoTime() + REPLY_TIMEOUT_NANOS;
        JsonObject reply;
        do {
            reply = this.answer(this.readFrame(deadline, true));
        } while (reply == null || !nonce.equals(string(reply, "nonce")));
        if ("ERROR".equals(string(reply, "evt"))) {
            throw new RefusedException(errorMessage(reply));
        }
    }

    /** Answers pings and notices a hang-up; call regularly while idle. */
    public void poll() throws IOException {
        Frame frame;
        while ((frame = this.readFrame(System.nanoTime() + REPLY_TIMEOUT_NANOS, false)) != null) {
            this.answer(frame);
        }
    }

    @Override
    public void close() {
        try {
            this.pipe.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    private void handshake(String applicationId) throws IOException {
        this.send(OP_HANDSHAKE, handshakeBody(applicationId));
        long deadline = System.nanoTime() + REPLY_TIMEOUT_NANOS;
        while (true) {
            JsonObject reply = this.answer(this.readFrame(deadline, true));
            if (reply != null && "DISPATCH".equals(string(reply, "cmd"))) {
                String event = string(reply, "evt");
                if ("READY".equals(event)) {
                    return;
                }
                if ("ERROR".equals(event)) {
                    throw new IOException("Discord refused the handshake: " + errorMessage(reply));
                }
            }
        }
    }

    private JsonObject answer(Frame frame) throws IOException {
        switch (frame.opcode()) {
            case OP_FRAME:
                return frame.json();
            case OP_CLOSE:
                throw new IOException("Discord closed the connection: " + errorMessage(frame.json()));
            case OP_PING:
                this.send(OP_PONG, frame.body());
                return null;
            default:
                return null;
        }
    }

    private Frame readFrame(long deadline, boolean wait) throws IOException {
        this.header.clear();
        if (!wait && this.pipe.readWaiting(this.header) == 0) {
            return null;
        }
        this.readFully(this.header, deadline);
        int[] h = parseHeader(this.header.array());
        ByteBuffer body = ByteBuffer.allocate(h[1]);
        this.readFully(body, deadline);
        return new Frame(h[0], body.array());
    }

    private void readFully(ByteBuffer buffer, long deadline) throws IOException {
        while (buffer.hasRemaining()) {
            if (this.pipe.readWaiting(buffer) <= 0) {
                if (System.nanoTime() - deadline >= 0L) {
                    throw new SocketTimeoutException("Discord did not answer in time");
                }
                LockSupport.parkNanos(READ_POLL_NANOS);
            }
        }
    }

    private void send(int opcode, JsonObject body) throws IOException {
        this.send(opcode, toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private void send(int opcode, byte[] body) throws IOException {
        this.pipe.write(ByteBuffer.wrap(encode(opcode, body)), System.nanoTime() + REPLY_TIMEOUT_NANOS);
    }

    private static String errorMessage(JsonObject body) {
        JsonElement data = body.get("data");
        String message = string(data != null && data.isJsonObject() ? data.getAsJsonObject() : body, "message");
        return message != null ? message : body.toString();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    // ---- finding the pipe ----

    private static Pipe openWindowsPipe() {
        for (int slot = 0; slot < SOCKET_SLOTS; slot++) {
            try {
                return new WindowsPipe(new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + slot, "rw"));
            } catch (FileNotFoundException ignored) {
                // slot not in use
            }
        }
        return null;
    }

    private static Pipe openUnixSocket(Iterable<Path> folders) {
        for (Path folder : folders) {
            for (int slot = 0; slot < SOCKET_SLOTS; slot++) {
                Path socket = folder.resolve("discord-ipc-" + slot);
                if (!Files.exists(socket)) {
                    continue;
                }
                try {
                    SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    try {
                        channel.connect(UnixDomainSocketAddress.of(socket));
                        channel.configureBlocking(false);
                        return new UnixPipe(channel);
                    } catch (IOException e) {
                        channel.close();
                        throw e;
                    }
                } catch (IOException | RuntimeException ignored) {
                    // stale socket file or no permission: try the next one
                }
            }
        }
        return null;
    }

    /** Where the desktop app puts its socket: the temp folders, plus the flatpak ({@code app/*}) and snap ({@code snap.*}) ones. */
    static Set<Path> unixFolders() {
        Set<Path> roots = new LinkedHashSet<>();
        for (String variable : UNIX_TEMP_VARIABLES) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                try {
                    roots.add(Path.of(value));
                } catch (InvalidPathException ignored) {
                    // bad variable, skip
                }
            }
        }
        roots.add(Path.of("/tmp"));
        Set<Path> folders = new LinkedHashSet<>();
        for (Path root : roots) {
            folders.add(root);
            addSubfolders(folders, root.resolve("app"), "*");
            addSubfolders(folders, root, "snap.*");
        }
        return folders;
    }

    private static void addSubfolders(Set<Path> folders, Path parent, String glob) {
        if (!Files.isDirectory(parent)) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(parent, glob)) {
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    folders.add(child);
                }
            }
        } catch (SecurityException | IOException ignored) {
            // unreadable folder
        }
    }

    // ---- types ----

    public record Frame(int opcode, byte[] body) {
        public JsonObject json() throws IOException {
            try {
                JsonElement parsed = JsonParser.parseString(new String(this.body, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) {
                    return parsed.getAsJsonObject();
                }
            } catch (JsonParseException ignored) {
                // falls through to the error below
            }
            throw new IOException("Discord sent a frame that is not a JSON object");
        }
    }

    private interface Pipe extends Closeable {
        /** Reads what is there without blocking; 0 when nothing is waiting. */
        int readWaiting(ByteBuffer into) throws IOException;

        void write(ByteBuffer from, long deadline) throws IOException;
    }

    public static final class RefusedException extends Exception {
        RefusedException(String message) {
            super(message);
        }
    }

    private record UnixPipe(SocketChannel channel) implements Pipe {
        @Override
        public int readWaiting(ByteBuffer into) throws IOException {
            int read = this.channel.read(into);
            if (read < 0) {
                throw new EOFException("Discord hung up");
            }
            return read;
        }

        @Override
        public void write(ByteBuffer from, long deadline) throws IOException {
            while (from.hasRemaining()) {
                if (this.channel.write(from) <= 0) {
                    if (System.nanoTime() - deadline >= 0L) {
                        throw new SocketTimeoutException("Discord stopped reading");
                    }
                    LockSupport.parkNanos(READ_POLL_NANOS);
                }
            }
        }

        @Override
        public void close() throws IOException {
            this.channel.close();
        }
    }

    private record WindowsPipe(RandomAccessFile file) implements Pipe {
        @Override
        public int readWaiting(ByteBuffer into) throws IOException {
            // length() of a pipe handle is the number of bytes waiting, which lets us read without blocking
            long waiting = this.file.length();
            if (waiting <= 0L) {
                return 0;
            }
            int read = this.file.read(into.array(), into.arrayOffset() + into.position(), (int) Math.min(waiting, into.remaining()));
            if (read < 0) {
                throw new EOFException("Discord hung up");
            }
            into.position(into.position() + read);
            return read;
        }

        @Override
        public void write(ByteBuffer from, long deadline) throws IOException {
            this.file.write(from.array(), from.arrayOffset() + from.position(), from.remaining());
            from.position(from.limit());
        }

        @Override
        public void close() throws IOException {
            this.file.close();
        }
    }
}
