package dev.dihclient.model3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockPalette {
    public enum Set { CONCRETE, WOOL, TERRACOTTA, MIXED, WOOD_AND_STONE }

    public record Entry(String id, int rgb) {
    }

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] CONCRETE = {0xCFD5D6, 0xE06100, 0xA9309F, 0x2489C7, 0xF0AF15, 0x5EA818, 0xD5658E, 0x373A3E, 0x7D7D73,
            0x157788, 0x64209C, 0x2D2F8F, 0x603B1F, 0x495B24, 0x8E2121, 0x080A0F};
    private static final int[] WOOL = {0xEAECED, 0xF17614, 0xBE45B4, 0x3AAFD9, 0xF9C628, 0x70B91A, 0xED8DAC, 0x3E4447, 0x8E8E87,
            0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA12722, 0x141519};
    private static final int[] TERRACOTTA = {0xD1B2A1, 0xA25426, 0x95586C, 0x716C89, 0xBA8523, 0x677534, 0xA14E4E, 0x392A23, 0x876B62,
            0x575B5B, 0x764656, 0x4A3B5B, 0x4D3324, 0x4C532A, 0x8F3D2F, 0x251610};
    private static final Object[][] OTHER = {
            {"minecraft:stone", 0x7D7D7D}, {"minecraft:cobblestone", 0x7F7F7F}, {"minecraft:smooth_stone", 0x9E9E9E},
            {"minecraft:stone_bricks", 0x7A7A7A}, {"minecraft:bricks", 0x966153}, {"minecraft:sandstone", 0xDFD2A4},
            {"minecraft:sand", 0xDBCFA3}, {"minecraft:snow_block", 0xF9FEFE}, {"minecraft:terracotta", 0x985E43},
            {"minecraft:oak_planks", 0xA2824E}, {"minecraft:spruce_planks", 0x725430}, {"minecraft:birch_planks", 0xC0AF79},
            {"minecraft:dark_oak_planks", 0x42301A}, {"minecraft:jungle_planks", 0xB88764}, {"minecraft:acacia_planks", 0xA85A32},
            {"minecraft:oak_log", 0x6C5232}, {"minecraft:coal_block", 0x101010}, {"minecraft:iron_block", 0xDCDCDC},
            {"minecraft:gold_block", 0xF9D43F}, {"minecraft:glowstone", 0xAB8354}, {"minecraft:netherrack", 0x6F3636},
            {"minecraft:deepslate", 0x505053}, {"minecraft:dirt", 0x866043}, {"minecraft:grass_block", 0x7FB238}};

    private static final Map<Set, List<Entry>> CACHE = new HashMap<>();

    private BlockPalette() {
    }

    public static synchronized List<Entry> entries(Set set) {
        return CACHE.computeIfAbsent(set, BlockPalette::build);
    }

    private static List<Entry> build(Set set) {
        List<Entry> out = new ArrayList<>();
        if (set == Set.CONCRETE || set == Set.MIXED) {
            add(out, "concrete", CONCRETE);
        }
        if (set == Set.WOOL || set == Set.MIXED) {
            add(out, "wool", WOOL);
        }
        if (set == Set.TERRACOTTA || set == Set.MIXED) {
            add(out, "terracotta", TERRACOTTA);
            out.add(new Entry("minecraft:terracotta", 0x985E43));
        }
        if (set == Set.WOOD_AND_STONE) {
            for (Object[] o : OTHER) {
                out.add(new Entry((String) o[0], (Integer) o[1]));
            }
        }
        if (set == Set.MIXED) {
            for (Object[] o : OTHER) {
                String id = (String) o[0];
                if (id.endsWith("snow_block") || id.endsWith("sand") || id.endsWith("planks") || id.endsWith("stone")) {
                    out.add(new Entry(id, (Integer) o[1]));
                }
            }
        }
        return out;
    }

    private static void add(List<Entry> out, String kind, int[] rgb) {
        for (int i = 0; i < COLORS.length; i++) {
            out.add(new Entry("minecraft:" + COLORS[i] + "_" + kind, rgb[i] & 0xFFFFFF));
        }
    }

    public static Entry nearest(List<Entry> palette, int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        Entry best = palette.get(0);
        long bestD = Long.MAX_VALUE;
        for (Entry e : palette) {
            int er = e.rgb() >> 16 & 255, eg = e.rgb() >> 8 & 255, eb = e.rgb() & 255;
            long mean = (r + er) / 2;
            long dr = r - er, dg = g - eg, db = b - eb;
            long d = (512 + mean) * dr * dr + 1024 * dg * dg + (767 - mean) * db * db;
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }
}
