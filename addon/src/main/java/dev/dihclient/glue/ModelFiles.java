package dev.dihclient.glue;

import dev.dihclient.model3d.ModelLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The model files in .minecraft/dihclient/models. */
final class ModelFiles {
    private static final String DEFAULT = "tung_tung_tung_sahur.obj";

    private ModelFiles() {
    }

    static List<Path> list() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(CustomModel.modelDir())) {
            s.filter(p -> Files.isRegularFile(p) && ModelLoader.supported(p)).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // empty list
        }
        return out;
    }

    /** The file with that name; with an empty name the default model, else the first one. */
    static Path pick(String name) {
        List<Path> all = list();
        String want = name == null ? "" : name.trim();
        for (Path p : all) {
            if (p.getFileName().toString().equalsIgnoreCase(want.isEmpty() ? DEFAULT : want)) {
                return p;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }
}
