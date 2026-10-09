package dev.dihclient.port.regionmap;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Gfx;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.ColorSetting;
import dev.dihclient.setting.DoubleSetting;
import dev.dihclient.setting.IntSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.class_1937;
import net.minecraft.class_332;

public class RegionMap extends Module {
    private static final int GRID = 36;
    private static final double RADIUS = 225000.0;
    private static final Map<String, int[][]> REGIONS = new LinkedHashMap<>();
    private static final String[][] CELLS = new String[GRID][GRID];
    private static final int[][] SHARD_IDS = new int[GRID][GRID];
    private static final List<Shard> SHARDS = new ArrayList<>();

    private record Shard(String region, int number, int col, int row, int width, int height) {
    }

    static {
        REGIONS.put("NA West", new int[][]{{4, 0, 2, 4}, {6, 0, 2, 4}, {8, 0, 2, 4}, {10, 0, 2, 4}, {12, 0, 2, 4}, {14, 0, 2, 4}, {4, 4, 2, 4}, {6, 4, 2, 4}, {8, 4, 2, 4}, {10, 4, 2, 4}, {12, 4, 2, 4}, {14, 4, 2, 4}, {4, 8, 2, 4}, {6, 8, 2, 4}, {8, 8, 2, 4}, {10, 8, 2, 4}, {12, 8, 2, 4}, {14, 8, 2, 4}, {12, 12, 2, 4}, {14, 12, 2, 4}});
        REGIONS.put("NA East", new int[][]{{16, 0, 2, 4}, {18, 0, 2, 4}, {20, 0, 2, 4}, {22, 0, 2, 4}, {24, 0, 4, 4}, {28, 0, 2, 4}, {30, 0, 2, 4}, {32, 0, 2, 4}, {34, 0, 2, 4}, {16, 4, 2, 2}, {18, 4, 2, 2}, {20, 4, 2, 2}, {22, 4, 2, 2}, {24, 4, 2, 2}, {26, 4, 2, 4}, {28, 4, 2, 2}, {30, 4, 2, 2}, {32, 4, 2, 4}, {34, 4, 2, 4}, {16, 6, 2, 2}, {18, 6, 2, 2}, {20, 6, 2, 2}, {22, 6, 2, 2}, {24, 6, 2, 2}, {28, 6, 2, 2}, {30, 6, 2, 2}, {16, 8, 2, 4}, {18, 8, 2, 4}, {20, 8, 4, 4}, {24, 8, 2, 4}, {26, 8, 2, 4}, {28, 8, 2, 4}, {30, 8, 2, 4}, {32, 8, 2, 4}, {34, 8, 2, 4}, {16, 12, 2, 4}, {18, 12, 2, 4}, {20, 12, 4, 4}, {24, 12, 2, 4}, {26, 12, 2, 4}, {28, 12, 2, 4}, {30, 12, 2, 4}, {32, 12, 2, 4}, {34, 12, 2, 4}, {16, 16, 4, 4}, {20, 16, 2, 4}, {22, 16, 2, 2}, {24, 16, 4, 4}, {28, 16, 2, 4}, {30, 16, 2, 4}, {32, 16, 2, 4}, {34, 16, 2, 4}, {22, 18, 2, 2}, {32, 20, 2, 4}, {34, 20, 2, 4}, {32, 24, 2, 4}, {34, 24, 2, 4}});
        REGIONS.put("Oceania", new int[][]{{0, 0, 2, 2}, {2, 0, 2, 4}, {0, 2, 2, 2}, {0, 4, 2, 2}, {2, 4, 2, 4}, {0, 6, 2, 2}, {0, 8, 2, 2}, {2, 8, 2, 4}, {0, 10, 2, 2}, {0, 12, 2, 2}, {2, 12, 2, 4}, {4, 12, 2, 4}, {6, 12, 2, 4}, {8, 12, 2, 4}, {10, 12, 2, 4}, {0, 14, 2, 2}});
        REGIONS.put("Asia", new int[][]{{0, 16, 2, 4}, {2, 16, 2, 4}, {4, 16, 2, 4}, {6, 16, 2, 4}, {8, 16, 2, 2}, {10, 16, 2, 2}, {12, 16, 2, 2}, {14, 16, 2, 2}, {8, 18, 2, 2}, {10, 18, 2, 2}, {12, 18, 2, 2}, {14, 18, 2, 2}, {0, 20, 2, 4}, {2, 20, 2, 4}, {0, 24, 2, 4}, {2, 24, 2, 4}});
        REGIONS.put("EU West", new int[][]{{4, 20, 2, 4}, {6, 20, 2, 4}, {8, 20, 2, 4}, {10, 20, 2, 4}, {4, 24, 2, 4}, {6, 24, 2, 4}, {8, 24, 2, 4}, {10, 24, 2, 4}, {4, 28, 2, 4}, {6, 28, 2, 4}, {4, 32, 2, 4}, {6, 32, 2, 2}, {8, 32, 2, 2}, {10, 32, 2, 2}, {12, 32, 2, 2}, {14, 32, 2, 4}, {16, 32, 2, 4}, {18, 32, 2, 2}, {20, 32, 2, 4}, {22, 32, 2, 4}, {24, 32, 2, 4}, {26, 32, 2, 4}, {6, 34, 2, 2}, {8, 34, 2, 2}, {10, 34, 2, 2}, {12, 34, 2, 2}, {18, 34, 2, 2}});
        REGIONS.put("EU Central", new int[][]{{12, 20, 2, 4}, {14, 20, 2, 4}, {16, 20, 2, 4}, {18, 20, 2, 4}, {20, 20, 2, 4}, {22, 20, 2, 2}, {26, 20, 2, 4}, {28, 20, 2, 4}, {30, 20, 2, 2}, {22, 22, 2, 2}, {24, 22, 1, 2}, {25, 22, 1, 2}, {12, 24, 2, 4}, {14, 24, 2, 4}, {16, 24, 2, 4}, {18, 24, 2, 4}, {20, 24, 2, 4}, {22, 24, 2, 4}, {24, 24, 2, 4}, {28, 24, 2, 2}, {30, 24, 2, 4}, {26, 26, 2, 2}, {0, 28, 4, 1}, {8, 28, 2, 2}, {10, 28, 2, 4}, {12, 28, 2, 2}, {14, 28, 2, 2}, {16, 28, 2, 2}, {18, 28, 2, 2}, {20, 28, 2, 2}, {22, 28, 2, 2}, {24, 28, 2, 2}, {26, 28, 2, 4}, {28, 28, 2, 2}, {30, 28, 2, 2}, {32, 28, 2, 2}, {0, 29, 4, 1}, {0, 30, 2, 2}, {2, 30, 2, 2}, {16, 30, 2, 2}, {18, 30, 2, 2}, {22, 30, 2, 2}, {24, 30, 2, 2}, {32, 30, 2, 2}, {34, 30, 2, 2}, {0, 32, 2, 2}, {28, 32, 2, 4}, {32, 32, 2, 4}, {0, 34, 4, 2}, {30, 34, 2, 2}, {34, 34, 2, 2}, {26, 24, 2, 2}});
        REGIONS.put("Europe", new int[][]{{24, 20, 2, 2}, {30, 22, 2, 2}, {28, 26, 2, 2}, {34, 28, 2, 2}, {8, 30, 2, 2}, {12, 30, 2, 2}, {14, 30, 2, 2}, {20, 30, 2, 2}, {28, 30, 2, 2}, {30, 30, 2, 2}, {2, 32, 2, 2}, {30, 32, 2, 2}, {34, 32, 2, 2}});
        for (int[] row : SHARD_IDS) {
            Arrays.fill(row, -1);
        }
        for (Map.Entry<String, int[][]> entry : REGIONS.entrySet()) {
            int[][] rects = entry.getValue().clone();
            Arrays.sort(rects, Comparator.<int[]>comparingInt(r -> r[1]).thenComparingInt(r -> r[0]));
            int number = 1;
            for (int[] rect : rects) {
                int id = SHARDS.size();
                SHARDS.add(new Shard(entry.getKey(), number++, rect[0], rect[1], rect[2], rect[3]));
                for (int r = rect[1]; r < rect[1] + rect[3]; r++) {
                    for (int c = rect[0]; c < rect[0] + rect[2]; c++) {
                        CELLS[r][c] = entry.getKey();
                        SHARD_IDS[r][c] = id;
                    }
                }
            }
        }
    }

    private final IntSetting x = this.integer("X", "Map position on screen.", 4, 0, 4000);
    private final IntSetting y = this.integer("Y", "Map position on screen.", 4, 0, 4000);
    private final IntSetting cellSize = this.integer("Cell Size", "Size of one grid box in pixels.", 4, 1, 12);
    private final BoolSetting showKey = this.bool("Show Key", "Shows the colour key under the map.", true);
    private final ColorSetting textColor = this.color("Text Colour", "Colour of the key text.", 0xFFFFFFFF);
    private final BoolSetting shardBorders = this.bool("Goliath Borders", "Draws lines between shards inside the same region.", true);
    private final ColorSetting shardBorderColor = this.color("Goliath Border Colour", "Colour of the lines between shards.", 0x5A000000);
    private final BoolSetting showNumbers = this.bool("Show Numbers", "Shows each shard's number in its centre.", true);
    private final DoubleSetting numberScale = this.dbl("Number Scale", "Largest text size. Numbers shrink further to fit small shards.", 0.6, 0.1, 2.0, 0.05);
    private final ColorSetting numberColor = this.color("Number Colour", "Colour of the shard numbers.", 0xFFFFFFFF);
    private final BoolSetting showMarker = this.bool("Show Marker", "Shows your position and view direction on the map (overworld only).", true);
    private final ColorSetting markerColor = this.color("Marker Colour", "Colour of the direction line.", 0xFFAA0000);
    private final ColorSetting pivotColor = this.color("Pivot Colour", "Colour of the dot at your position.", 0xFFFF4646);
    private final IntSetting markerLength = this.integer("Marker Length", "Length of the direction line in pixels.", 8, 1, 30);
    private final IntSetting markerThickness = this.integer("Marker Thickness", "Thickness of the line and size of the dot.", 2, 1, 5);
    private final Map<String, ColorSetting> regionColors = new LinkedHashMap<>();

    public RegionMap() {
        super("Region Map", Category.DONUT, "Shows the DonutSMP shard region map (goliaths) on screen with your position.");
        int[] defaults = {0xFF4472C4, 0xFF5B9BD5, 0xFFED7D31, 0xFFFFC000, 0xFF38761D, 0xFF92D050, 0xFFDA70D6};
        int i = 0;
        for (String region : REGIONS.keySet()) {
            this.regionColors.put(region, this.color(region + " Colour", "Colour for " + region + ".", defaults[i++]));
        }
        this.textColor.visibleWhen(this.showKey::get);
        this.shardBorderColor.visibleWhen(this.shardBorders::get);
        this.numberScale.visibleWhen(this.showNumbers::get);
        this.numberColor.visibleWhen(this.showNumbers::get);
        this.markerColor.visibleWhen(this.showMarker::get);
        this.pivotColor.visibleWhen(this.showMarker::get);
        this.markerLength.visibleWhen(this.showMarker::get);
        this.markerThickness.visibleWhen(this.showMarker::get);
    }

    @Override
    public void onRender2D(class_332 g, float delta) {
        if (mc.field_1690.field_1842) {
            return;
        }
        int cell = this.cellSize.get();
        int ox = this.x.get();
        int oy = this.y.get();
        int size = GRID * cell;
        for (int r = 0; r < GRID; r++) {
            for (int c = 0; c < GRID; c++) {
                String region = CELLS[r][c];
                if (region != null) {
                    Gfx.fill(g, ox + c * cell, oy + r * cell, cell, cell, this.regionColors.get(region).get());
                }
            }
        }
        if (this.shardBorders.get()) {
            this.borders(g, ox, oy, cell, false, this.shardBorderColor.get());
        }
        this.borders(g, ox, oy, cell, true, 0xC8000000);
        if (this.shardBorders.get()) {
            Gfx.fill(g, ox, oy, size, 1, 0xC8000000);
            Gfx.fill(g, ox, oy + size - 1, size, 1, 0xC8000000);
            Gfx.fill(g, ox, oy, 1, size, 0xC8000000);
            Gfx.fill(g, ox + size - 1, oy, 1, size, 0xC8000000);
        }
        if (this.showNumbers.get()) {
            float max = this.numberScale.get().floatValue();
            for (Shard s : SHARDS) {
                String text = Integer.toString(s.number());
                float sw = s.width() * cell;
                float sh = s.height() * cell;
                float scale = Math.min(max, Math.min(sw * 0.9F / Math.max(1, Gfx.width(text)), sh * 0.9F / 9.0F));
                float tw = Gfx.width(text) * scale;
                float th = 8.0F * scale;
                Gfx.text(g, text, ox + s.col() * cell + (sw - tw) / 2.0F, oy + s.row() * cell + (sh - th) / 2.0F, this.numberColor.get(), scale);
            }
        }
        if (this.showKey.get()) {
            int ky = oy + size + 4;
            for (Map.Entry<String, ColorSetting> e : this.regionColors.entrySet()) {
                Gfx.fill(g, ox, ky, 9, 9, e.getValue().get());
                Gfx.text(g, e.getKey(), ox + 13, ky + 1, this.textColor.get());
                ky += 12;
            }
        }
        if (this.showMarker.get()) {
            this.marker(g, ox, oy, size);
        }
    }

    private void borders(class_332 g, int ox, int oy, int cell, boolean regionLevel, int color) {
        for (int r = 0; r < GRID; r++) {
            for (int c = 0; c < GRID; c++) {
                if (c < GRID - 1 && differs(r, c, r, c + 1, regionLevel)) {
                    Gfx.fill(g, ox + (c + 1) * cell, oy + r * cell, 1, cell, color);
                }
                if (r < GRID - 1 && differs(r, c, r + 1, c, regionLevel)) {
                    Gfx.fill(g, ox + c * cell, oy + (r + 1) * cell, cell, 1, color);
                }
            }
        }
    }

    private static boolean differs(int r1, int c1, int r2, int c2, boolean regionLevel) {
        boolean same = Objects.equals(CELLS[r1][c1], CELLS[r2][c2]);
        return regionLevel ? !same : same && SHARD_IDS[r1][c1] != SHARD_IDS[r2][c2];
    }

    private void marker(class_332 g, int ox, int oy, int size) {
        if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1687.method_27983() != class_1937.field_25179) {
            return;
        }
        double fx = Math.max(0.0, Math.min(1.0, (mc.field_1724.method_23317() + RADIUS) / (RADIUS * 2.0)));
        double fz = Math.max(0.0, Math.min(1.0, (mc.field_1724.method_23321() + RADIUS) / (RADIUS * 2.0)));
        double mx = ox + fx * size;
        double my = oy + fz * size;
        double yaw = Math.toRadians(mc.field_1724.method_36454());
        double dx = -Math.sin(yaw);
        double dy = Math.cos(yaw);
        int t = this.markerThickness.get();
        int length = this.markerLength.get();
        for (double d = 0.0; d <= length; d += 0.5) {
            Gfx.fill(g, (int) Math.round(mx + dx * d - t / 2.0), (int) Math.round(my + dy * d - t / 2.0), t, t, this.markerColor.get());
        }
        Gfx.fill(g, (int) Math.round(mx - t / 2.0), (int) Math.round(my - t / 2.0), t, t, this.pivotColor.get());
    }
}
