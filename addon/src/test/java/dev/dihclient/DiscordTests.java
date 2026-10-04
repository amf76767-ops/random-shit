package dev.dihclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dihclient.port.discord.DiscordCard;
import dev.dihclient.port.discord.DiscordCard.Place;
import dev.dihclient.port.discord.DiscordIpc;
import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Discord Presence: IPC frames, status JSON, address redaction, and the protocol against a fake Discord on a unix socket. */
public final class DiscordTests {
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

    static void frames() throws Exception {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] wire = DiscordIpc.encode(1, body);
        check(wire.length == 8 + body.length, "frame length");
        check(wire[0] == 1 && wire[1] == 0 && wire[2] == 0 && wire[3] == 0, "opcode is little endian");
        check(wire[4] == body.length && wire[5] == 0 && wire[6] == 0 && wire[7] == 0, "length is little endian");
        DiscordIpc.Frame f = DiscordIpc.decode(wire);
        check(f.opcode() == 1 && Arrays.equals(f.body(), body), "round trip");
        check(f.json().get("a").getAsInt() == 1, "json body");
        check(DiscordIpc.decode(DiscordIpc.encode(3, new byte[0])).body().length == 0, "empty body");
        int[] h = DiscordIpc.parseHeader(wire);
        check(h[0] == 1 && h[1] == body.length, "header parse");

        ByteBuffer big = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        big.putInt(1).putInt(DiscordIpc.MAX_FRAME_BYTES + 1);
        check(throwsIo(() -> DiscordIpc.parseHeader(big.array())), "oversized frame rejected");
        ByteBuffer neg = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        neg.putInt(1).putInt(-5);
        check(throwsIo(() -> DiscordIpc.parseHeader(neg.array())), "negative length rejected");
        check(throwsIo(() -> DiscordIpc.parseHeader(new byte[3])), "short header rejected");
        byte[] cut = Arrays.copyOf(wire, wire.length - 1);
        check(throwsIo(() -> DiscordIpc.decode(cut)), "truncated frame rejected");
        check(throwsIo(() -> DiscordIpc.decode(DiscordIpc.encode(1, "[1]".getBytes(StandardCharsets.UTF_8)).clone()).json()),
                "non-object json rejected");
        check(throwsIo(() -> DiscordIpc.decode(DiscordIpc.encode(1, "nope{".getBytes(StandardCharsets.UTF_8))).json()),
                "garbage json rejected");
        check(DiscordIpc.isWindows("Windows 11") && !DiscordIpc.isWindows("Linux") && !DiscordIpc.isWindows("Mac OS X")
                && !DiscordIpc.isWindows(null), "os detection");
    }

    interface Io {
        void run() throws Exception;
    }

    static boolean throwsIo(Io r) {
        try {
            r.run();
            return false;
        } catch (IOException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void commands() {
        JsonObject hello = DiscordIpc.handshakeBody("123456789012345678");
        check(hello.get("v").getAsInt() == 1 && hello.get("client_id").getAsString().equals("123456789012345678"), "handshake body");
        JsonObject act = new JsonObject();
        act.addProperty("details", "x");
        JsonObject cmd = DiscordIpc.setActivityCommand(act, 42, "n-1");
        check(cmd.get("cmd").getAsString().equals("SET_ACTIVITY") && cmd.get("nonce").getAsString().equals("n-1"), "set activity cmd");
        check(cmd.getAsJsonObject("args").get("pid").getAsLong() == 42, "pid");
        check(cmd.getAsJsonObject("args").getAsJsonObject("activity").get("details").getAsString().equals("x"), "activity inside");
        JsonObject clear = DiscordIpc.setActivityCommand(null, 42, "n-2");
        check(clear.getAsJsonObject("args").get("activity").isJsonNull(), "clear sends activity null");
        check(DiscordIpc.toJson(clear).contains("\"activity\":null"), "null is serialised, not dropped");
    }

    static void redaction() {
        check(DiscordCard.details(Place.MULTIPLAYER, "play.example.org", false).equals("Playing Minecraft"), "address hidden by default");
        check(DiscordCard.details(Place.MULTIPLAYER, "play.example.org", true).equals("play.example.org"), "address shown when allowed");
        check(DiscordCard.details(Place.MULTIPLAYER, null, true).equals("Multiplayer"), "no address: Multiplayer");
        check(DiscordCard.details(Place.MULTIPLAYER, "  ", true).equals("Multiplayer"), "blank address: Multiplayer");
        check(DiscordCard.details(Place.SINGLEPLAYER, null, false).equals("Playing Minecraft"), "singleplayer hidden");
        check(DiscordCard.details(Place.SINGLEPLAYER, null, true).equals("Singleplayer"), "singleplayer shown");
        check(DiscordCard.details(Place.LAN, "192.168.1.5:25565", true).equals("LAN World"), "LAN never shows its address");
        check(DiscordCard.details(Place.LAN, "192.168.1.5:25565", false).equals("Playing Minecraft"), "LAN hidden");
        check(DiscordCard.details(Place.REALMS, "realm-id", true).equals("Realms"), "Realms never shows its address");
        check(DiscordCard.details(Place.MENU, "play.example.org", false).equals("In Main Menu"), "menu");
        for (Place p : Place.values()) {
            if (p != Place.MENU) {
                check(!DiscordCard.details(p, "secret.example.org", false).contains("secret"), "no leak when hidden: " + p);
            }
        }
    }

    static void texts() {
        check(DiscordCard.clip(null) == null, "clip null");
        check(DiscordCard.clip("a") == null, "one char is too short for Discord");
        check(DiscordCard.clip("  ab  ").equals("ab"), "trim");
        check(DiscordCard.clip("a\u0000b\u0007c").equals("abc"), "control chars removed");
        check(DiscordCard.clip("a\nb").equals("a b"), "newline becomes space");
        check(DiscordCard.clip("x".repeat(300)).length() == 128, "cap at 128");
        String emoji = "😀".repeat(200);
        String c = DiscordCard.clip(emoji);
        check(c.codePointCount(0, c.length()) == 128 && !Character.isLowSurrogate(c.charAt(0)), "cap by code points, no split pair");
        check(DiscordCard.validAppId("1553391775840866314"), "default app id is valid");
        check(!DiscordCard.validAppId("") && !DiscordCard.validAppId(null) && !DiscordCard.validAppId("12345"), "short ids invalid");
        check(!DiscordCard.validAppId("15533917758408663a4") && !DiscordCard.validAppId("1".repeat(21)), "non-digit / long ids invalid");
        check(DiscordCard.validAppId(" 1553391775840866314 "), "id is trimmed");
        check("logo".equals(DiscordCard.assetKey(" logo ")), "asset key trimmed");
        check(DiscordCard.assetKey("") == null && DiscordCard.assetKey("a b") == null && DiscordCard.assetKey(null) == null, "bad asset keys dropped");
    }

    static void json() {
        DiscordCard full = new DiscordCard("play.example.org", "DIHClient v3", 1700000000L, "1553391775840866314", "logo", "DIHClient");
        JsonObject a = full.activity();
        check(a.get("details").getAsString().equals("play.example.org"), "details");
        check(a.get("state").getAsString().equals("DIHClient v3"), "state");
        check(a.getAsJsonObject("timestamps").get("start").getAsLong() == 1700000000L, "start");
        check(a.getAsJsonObject("assets").get("large_image").getAsString().equals("logo"), "large image");
        check(a.getAsJsonObject("assets").get("large_text").getAsString().equals("DIHClient"), "large text");
        check(!a.has("buttons") && !a.has("party") && !a.has("secrets") && !a.has("application_id"), "nothing beyond the fixed fields");
        check(a.keySet().equals(java.util.Set.of("details", "state", "timestamps", "assets")), "exact key set");
        DiscordCard bare = new DiscordCard("Playing Minecraft", null, 0L, "1553391775840866314", null, null);
        JsonObject b = bare.activity();
        check(!b.has("timestamps") && !b.has("assets") && !b.has("state"), "optional parts omitted");
        check(b.keySet().equals(java.util.Set.of("details")), "bare card has only details");
        check(full.equals(new DiscordCard("play.example.org", "DIHClient v3", 1700000000L, "1553391775840866314", "logo", "DIHClient")),
                "cards compare by value (change detection)");
    }

    // ---- a fake Discord on a unix socket ----

    static final class FakeDiscord implements Runnable {
        final ServerSocketChannel server;
        final List<String> received = new CopyOnWriteArrayList<>();
        final boolean refuseActivity;
        final boolean refuseHandshake;
        volatile boolean pingSeen;
        volatile boolean pongReceived;

        FakeDiscord(Path socket, boolean refuseActivity, boolean refuseHandshake) throws IOException {
            this.refuseActivity = refuseActivity;
            this.refuseHandshake = refuseHandshake;
            this.server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            this.server.bind(UnixDomainSocketAddress.of(socket));
        }

        @Override
        public void run() {
            try (SocketChannel ch = this.server.accept()) {
                DiscordIpc.Frame hello = read(ch);
                this.received.add("op" + hello.opcode() + " " + new String(hello.body(), StandardCharsets.UTF_8));
                if (this.refuseHandshake) {
                    write(ch, 1, "{\"cmd\":\"DISPATCH\",\"evt\":\"ERROR\",\"data\":{\"code\":4000,\"message\":\"Invalid Client ID\"}}");
                    return;
                }
                write(ch, 1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{\"v\":1}}");
                write(ch, 3, "{\"hello\":1}"); // ping before any command: must be answered with a pong
                while (true) {
                    DiscordIpc.Frame f = read(ch);
                    if (f.opcode() == DiscordIpc.OP_PONG) {
                        this.pongReceived = true;
                        continue;
                    }
                    String json = new String(f.body(), StandardCharsets.UTF_8);
                    this.received.add("op" + f.opcode() + " " + json);
                    JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                    String nonce = o.get("nonce").getAsString();
                    write(ch, 1, "{\"cmd\":\"SET_ACTIVITY\",\"evt\":" + (this.refuseActivity ? "\"ERROR\",\"data\":{\"message\":\"bad\"}" : "null")
                            + ",\"nonce\":\"" + nonce + "\"}");
                }
            } catch (EOFException | java.nio.channels.AsynchronousCloseException e) {
                // client hung up: normal end
            } catch (Exception e) {
                this.received.add("server error " + e);
            }
        }

        static DiscordIpc.Frame read(SocketChannel ch) throws IOException {
            ByteBuffer h = ByteBuffer.allocate(8);
            fill(ch, h);
            int[] hh = DiscordIpc.parseHeader(h.array());
            ByteBuffer b = ByteBuffer.allocate(hh[1]);
            fill(ch, b);
            return new DiscordIpc.Frame(hh[0], b.array());
        }

        static void fill(SocketChannel ch, ByteBuffer b) throws IOException {
            while (b.hasRemaining()) {
                if (ch.read(b) < 0) {
                    throw new EOFException();
                }
            }
        }

        static void write(SocketChannel ch, int op, String json) throws IOException {
            ch.write(ByteBuffer.wrap(DiscordIpc.encode(op, json.getBytes(StandardCharsets.UTF_8))));
        }
    }

    static void protocol() throws Exception {
        Path dir;
        try {
            dir = Files.createTempDirectory("dihdc");
            ServerSocketChannel probe = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            probe.close();
        } catch (Throwable t) {
            System.out.println("skip protocol tests (no unix sockets here): " + t);
            return;
        }
        try {
            // no socket at all: Discord not running -> null, no exception
            check(DiscordIpc.connect("1553391775840866314", false, List.of(dir)) == null, "missing socket gives null");
            check(DiscordIpc.connect("1553391775840866314", false, List.of(dir.resolve("nope"))) == null, "missing folder gives null");

            Path sock = dir.resolve("discord-ipc-0");
            FakeDiscord fake = new FakeDiscord(sock, false, false);
            Thread t = new Thread(fake, "fake-discord");
            t.setDaemon(true);
            t.start();
            DiscordIpc ipc = DiscordIpc.connect("1553391775840866314", false, List.of(dir.resolve("nope"), dir));
            check(ipc != null, "connected through the second folder");
            ipc.poll(); // answers the ping
            Thread.sleep(100);
            ipc.setActivity(new DiscordCard("Playing Minecraft", "DIHClient v3", 0L, "x", "logo", "DIHClient").activity());
            ipc.setActivity(null);
            ipc.close();
            t.join(2000);
            check(fake.pongReceived, "ping answered with pong");
            check(fake.received.size() >= 3, "server saw handshake and two commands: " + fake.received);
            if (fake.received.size() >= 3) {
                check(fake.received.get(0).startsWith("op0 ") && fake.received.get(0).contains("\"client_id\":\"1553391775840866314\"")
                        && fake.received.get(0).contains("\"v\":1"), "handshake frame");
                JsonObject set = JsonParser.parseString(fake.received.get(1).substring(4)).getAsJsonObject();
                check(fake.received.get(1).startsWith("op1 ") && set.get("cmd").getAsString().equals("SET_ACTIVITY"), "set activity frame");
                check(set.getAsJsonObject("args").getAsJsonObject("activity").get("details").getAsString().equals("Playing Minecraft"), "details on the wire");
                check(fake.received.get(2).contains("\"activity\":null"), "clear on the wire");
            }
            fake.server.close();
            Files.deleteIfExists(sock);

            // Discord refuses the activity
            FakeDiscord refusing = new FakeDiscord(sock, true, false);
            Thread t2 = new Thread(refusing);
            t2.setDaemon(true);
            t2.start();
            DiscordIpc ipc2 = DiscordIpc.connect("1553391775840866314", false, List.of(dir));
            boolean refused = false;
            try {
                ipc2.setActivity(new JsonObject());
            } catch (DiscordIpc.RefusedException e) {
                refused = e.getMessage().equals("bad");
            }
            check(refused, "ERROR reply raises RefusedException with Discord's message");
            ipc2.close();
            t2.join(2000);
            refusing.server.close();
            Files.deleteIfExists(sock);

            // Discord refuses the handshake (bad application id)
            FakeDiscord bad = new FakeDiscord(sock, false, true);
            Thread t3 = new Thread(bad);
            t3.setDaemon(true);
            t3.start();
            boolean threw = false;
            try {
                DiscordIpc.connect("1", false, List.of(dir));
            } catch (IOException e) {
                threw = e.getMessage().contains("Invalid Client ID");
            }
            check(threw, "refused handshake is an IOException with the reason");
            t3.join(2000);
            bad.server.close();
            Files.deleteIfExists(sock);

            // stale socket file (nobody listening) is skipped quietly
            Files.writeString(dir.resolve("discord-ipc-0"), "not a socket");
            check(DiscordIpc.connect("1553391775840866314", false, List.of(dir)) == null, "stale file gives null, not a crash");
        } finally {
            try (var s = Files.list(dir)) {
                for (Path p : new ArrayList<>(s.toList())) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(dir);
        }
    }

    public static void main(String[] args) throws Exception {
        frames();
        commands();
        redaction();
        texts();
        json();
        protocol();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
