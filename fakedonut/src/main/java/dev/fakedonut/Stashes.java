package dev.fakedonut;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.class_2248;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2505;
import net.minecraft.class_2507;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class Stashes {
    public static final class Stash {
        public String name;
        public int sx;
        public int sy;
        public int sz;
        public final List<int[]> pos = new ArrayList<>();
        public final List<class_2680> states = new ArrayList<>();
    }

    private static final List<Stash> ALL = new ArrayList<>();

    private Stashes() {
    }

    public static Path dir() {
        Path p = Config.dir().resolve("stashes");
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            FakeDonut.LOG.warn("cannot create {}", p, e);
        }
        return p;
    }

    public static synchronized List<Stash> all() {
        return new ArrayList<>(ALL);
    }

    public static synchronized void load() {
        ALL.clear();
        try (Stream<Path> files = Files.list(dir())) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))::iterator) {
                try {
                    Stash s = read(f);
                    if (s != null && !s.pos.isEmpty()) {
                        ALL.add(s);
                    }
                } catch (IOException | RuntimeException e) {
                    FakeDonut.LOG.warn("stash {} could not be read: {}", f.getFileName(), e.toString());
                }
            }
        } catch (IOException e) {
            FakeDonut.LOG.warn("stash folder could not be listed", e);
        }
        FakeDonut.LOG.info("{} stash schematic(s) loaded from {}", ALL.size(), dir());
    }

    private static class_2680 parse(class_2487 entry) {
        String name = entry.method_10558("Name").orElse("minecraft:air");
        class_2960 id = class_2960.method_12829(name);
        class_2248 block = id == null ? Ids.AIR : class_7923.field_41175.method_63535(id);
        class_2680 state = block == null ? Ids.AIR.method_9564() : block.method_9564();
        Optional<class_2487> props = entry.method_10562("Properties");
        if (props.isPresent()) {
            for (String key : props.get().method_10541()) {
                class_2769<?> p = state.method_26204().method_9595().method_11663(key);
                String val = props.get().method_10558(key).orElse(null);
                if (p != null && val != null) {
                    state = apply(state, p, val);
                }
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class_2680 apply(class_2680 state, class_2769<?> p, String val) {
        Optional<?> v = p.method_11900(val);
        return v.isPresent() ? (class_2680) state.method_11657((class_2769) p, (Comparable) v.get()) : state;
    }

    private static Stash read(Path file) throws IOException {
        class_2487 root = class_2507.method_30613(file, class_2505.method_53898());
        class_2487 regions = root.method_10562("Regions").orElse(null);
        if (regions == null) {
            return null;
        }
        Stash out = new Stash();
        out.name = file.getFileName().toString();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        List<Object[]> raw = new ArrayList<>();
        for (String key : regions.method_10541()) {
            class_2487 region = regions.method_10562(key).orElse(null);
            if (region == null) {
                continue;
            }
            class_2487 pos = region.method_10562("Position").orElseThrow();
            class_2487 size = region.method_10562("Size").orElseThrow();
            int px = pos.method_10550("x").orElse(0);
            int py = pos.method_10550("y").orElse(0);
            int pz = pos.method_10550("z").orElse(0);
            int sx = size.method_10550("x").orElse(0);
            int sy = size.method_10550("y").orElse(0);
            int sz = size.method_10550("z").orElse(0);
            int ax = Math.abs(sx);
            int ay = Math.abs(sy);
            int az = Math.abs(sz);
            int ox = px + (sx < 0 ? sx + 1 : 0);
            int oy = py + (sy < 0 ? sy + 1 : 0);
            int oz = pz + (sz < 0 ? sz + 1 : 0);
            class_2499 palette = region.method_10554("BlockStatePalette").orElseThrow();
            class_2680[] states = new class_2680[palette.size()];
            for (int i = 0; i < states.length; i++) {
                states[i] = parse(palette.method_10602(i).orElseThrow());
            }
            long[] data = region.method_10565("BlockStates").orElseThrow();
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, states.length - 1)));
            long mask = (1L << bits) - 1;
            long total = (long) ax * ay * az;
            for (long i = 0; i < total; i++) {
                long startBit = i * bits;
                int li = (int) (startBit >> 6);
                int off = (int) (startBit & 63);
                long v = data[li] >>> off;
                if (off + bits > 64 && li + 1 < data.length) {
                    v |= data[li + 1] << (64 - off);
                }
                int idx = (int) (v & mask);
                if (idx >= states.length) {
                    continue;
                }
                class_2680 s = states[idx];
                if (s.method_26215()) {
                    continue;
                }
                int x = (int) (i % ax);
                int z = (int) ((i / ax) % az);
                int y = (int) (i / ((long) ax * az));
                int wx = ox + x;
                int wy = oy + y;
                int wz = oz + z;
                raw.add(new Object[] {wx, wy, wz, s});
                minX = Math.min(minX, wx);
                minY = Math.min(minY, wy);
                minZ = Math.min(minZ, wz);
                maxX = Math.max(maxX, wx);
                maxY = Math.max(maxY, wy);
                maxZ = Math.max(maxZ, wz);
            }
        }
        if (raw.isEmpty()) {
            return null;
        }
        out.sx = maxX - minX + 1;
        out.sy = maxY - minY + 1;
        out.sz = maxZ - minZ + 1;
        for (Object[] r : raw) {
            out.pos.add(new int[] {(Integer) r[0] - minX, (Integer) r[1] - minY, (Integer) r[2] - minZ});
            out.states.add((class_2680) r[3]);
        }
        return out;
    }
}
