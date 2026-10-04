package dev.dihclient.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Team codes for building together. A team is a file named after its code in one folder of the user's home directory, so
 * every game on this computer (the main account and the alts) finds it; nothing is sent over the network. The file says what
 * is built and where; each member keeps a small file of its own that it renews every few seconds, and a member that stopped
 * renewing is gone.
 */
public final class TeamStore {
    /** What the team builds. {@code rotation} and {@code mirror} are the names of the game's enums. */
    public record Spec(String code, String host, String server, String dimension, String schematicPath, String schematicName,
                       String sha256, int x, int y, int z, String rotation, String mirror, long created) {
    }

    public record Member(String id, String name, long joined, long seen) {
    }

    /** No 0/O, 1/I/L: a code is read aloud or copied by hand. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path root;

    public TeamStore(Path root) {
        this.root = root;
    }

    public static Path defaultRoot() {
        return Path.of(System.getProperty("user.home", "."), ".dihclient", "teams");
    }

    // ---------------------------------------------------------------- codes

    public static String newCode(Random random) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** "abcd efgh", "ABCD-EFGH" and "abcdefgh" all become "ABCD-EFGH"; anything else gives "". */
    public static String normalizeCode(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (s.length() != 8) {
            return "";
        }
        for (char c : s.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return "";
            }
        }
        return s.substring(0, 4) + "-" + s.substring(4);
    }

    // ---------------------------------------------------------------- team file

    private Path specFile(String code) {
        return this.root.resolve(code + ".json");
    }

    private Path memberDir(String code) {
        return this.root.resolve(code + ".members");
    }

    public void saveSpec(Spec spec) throws IOException {
        Files.createDirectories(this.root);
        Files.writeString(this.specFile(spec.code()), GSON.toJson(spec));
    }

    public Optional<Spec> loadSpec(String code) {
        try {
            Path file = this.specFile(code);
            return Files.exists(file) ? Optional.ofNullable(GSON.fromJson(Files.readString(file), Spec.class)) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- members

    /** Writes (or renews) the member's file. {@code joined} is kept from the first call. */
    public void heartbeat(String code, String id, String name, long now) throws IOException {
        Path dir = this.memberDir(code);
        Files.createDirectories(dir);
        Path file = dir.resolve(id + ".json");
        long joined = now;
        if (Files.exists(file)) {
            try {
                Member old = GSON.fromJson(Files.readString(file), Member.class);
                if (old != null) {
                    joined = old.joined();
                }
            } catch (IOException | RuntimeException ignored) {
                // starts again
            }
        }
        Files.writeString(file, GSON.toJson(new Member(id, name, joined, now)));
    }

    /** The members that renewed within {@code maxAgeMillis}, the one who came first at the front. */
    public List<Member> active(String code, long now, long maxAgeMillis) {
        List<Member> out = new ArrayList<>();
        Path dir = this.memberDir(code);
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) {
                try {
                    Member m = GSON.fromJson(Files.readString(p), Member.class);
                    if (m != null && m.id() != null && now - m.seen() <= maxAgeMillis) {
                        out.add(m);
                    }
                } catch (IOException | RuntimeException ignored) {
                    // a half written file is read again at the next look
                }
            }
        } catch (IOException ignored) {
            // no folder, no members
        }
        out.sort(Comparator.comparingLong(Member::joined).thenComparing(Member::id));
        return out;
    }

    public void leave(String code, String id) {
        try {
            Files.deleteIfExists(this.memberDir(code).resolve(id + ".json"));
        } catch (IOException ignored) {
            // it goes stale by itself
        }
    }

    /** Deletes teams that nobody has renewed for a long time. */
    public void cleanup(long now, long maxAgeMillis) {
        if (!Files.isDirectory(this.root)) {
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(this.root, "*.json")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                String code = name.substring(0, name.length() - ".json".length());
                Optional<Spec> spec = this.loadSpec(code);
                boolean old = spec.isEmpty() || now - spec.get().created() > maxAgeMillis;
                if (old && this.active(code, now, maxAgeMillis).isEmpty()) {
                    Files.deleteIfExists(p);
                    Path dir = this.memberDir(code);
                    if (Files.isDirectory(dir)) {
                        try (DirectoryStream<Path> members = Files.newDirectoryStream(dir)) {
                            for (Path m : members) {
                                Files.deleteIfExists(m);
                            }
                        }
                        Files.deleteIfExists(dir);
                    }
                }
            }
        } catch (IOException ignored) {
            // cleaning is optional
        }
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            for (int n = in.read(buf); n >= 0; n = in.read(buf)) {
                digest.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
