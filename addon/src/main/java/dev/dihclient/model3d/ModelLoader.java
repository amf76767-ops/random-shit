package dev.dihclient.model3d;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Picks the right reader for a model file. */
public final class ModelLoader {
    public static final String[] EXTENSIONS = {".glb", ".gltf", ".obj"};

    private ModelLoader() {
    }

    public static boolean supported(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String e : EXTENSIONS) {
            if (n.endsWith(e)) {
                return true;
            }
        }
        return false;
    }

    public static Model load(Path p) throws IOException {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".obj")) {
            return ObjLoader.load(p);
        }
        if (n.endsWith(".glb") || n.endsWith(".gltf")) {
            return GltfLoader.load(p);
        }
        if (n.endsWith(".blend")) {
            throw new IOException(".blend can't be read. In Blender: File > Export > glTF 2.0 (.glb)");
        }
        throw new IOException("Unknown file type (use .glb, .gltf or .obj)");
    }
}
