package dev.dihclient.autobuild;

import dev.dihclient.DIHClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;

public final class BuildLog {
    private static final int MEMORY = 300;
    private static final int FILE_LINES = 2000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static Path file;
    private static boolean opened;
    private static String lastLine = "";
    private static int repeats;

    private BuildLog() {
    }

    public static synchronized void add(String text) {
        if (text.equals(lastLine)) {
            repeats++;
            return;
        }
        flushRepeats();
        lastLine = text;
        write(text);
    }

    private static void flushRepeats() {
        if (repeats > 0) {
            write("  (same again x" + repeats + ")");
            repeats = 0;
        }
    }

    private static void write(String text) {
        String line = TIME.format(LocalTime.now()) + "  " + text;
        LINES.addLast(line);
        while (LINES.size() > MEMORY) {
            LINES.removeFirst();
        }
        try {
            Path f = path();
            if (f == null) {
                return;
            }
            if (!opened) {
                opened = true;
                trim(f);
                Files.writeString(f, "---- new game " + TIME.format(LocalTime.now()) + " ----\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
            Files.writeString(f, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {

        }
    }

    private static void trim(Path f) throws IOException {
        if (Files.isRegularFile(f)) {
            List<String> all = Files.readAllLines(f, StandardCharsets.UTF_8);
            if (all.size() > FILE_LINES) {
                Files.write(f, all.subList(all.size() - FILE_LINES, all.size()), StandardCharsets.UTF_8);
            }
        }
    }

    private static Path path() {
        if (file == null) {
            try {
                Path dir = DIHClient.config().dir();
                Files.createDirectories(dir);
                file = dir.resolve("build-log.txt");
            } catch (Throwable t) {
                return null;
            }
        }
        return file;
    }

    public static synchronized List<String> last(int n) {
        flushRepeats();
        List<String> all = new ArrayList<>(LINES);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    public static synchronized String where() {
        Path f = path();
        return f == null ? "(no file)" : f.toString();
    }
}
