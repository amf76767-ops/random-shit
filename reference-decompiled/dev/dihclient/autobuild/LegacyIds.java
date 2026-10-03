package dev.dihclient.autobuild;

final class LegacyIds {
   private static final String[] COLORS = new String[]{
      "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
   };
   private static final String[] WOODS = new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

   private LegacyIds() {
   }

   static String get(int var0, int var1) {
      var1 &= 15;

      return switch (var0) {
         case 0 -> "minecraft:air";
         case 1 -> {
            switch (var1) {
               case 1:
                  yield "minecraft:granite";
               case 2:
                  yield "minecraft:polished_granite";
               case 3:
                  yield "minecraft:diorite";
               case 4:
                  yield "minecraft:polished_diorite";
               case 5:
                  yield "minecraft:andesite";
               case 6:
                  yield "minecraft:polished_andesite";
               default:
                  yield "minecraft:stone";
            }
         }
         case 2 -> "minecraft:grass_block";
         case 3 -> var1 == 1 ? "minecraft:coarse_dirt" : (var1 == 2 ? "minecraft:podzol" : "minecraft:dirt");
         case 4 -> "minecraft:cobblestone";
         case 5 -> "minecraft:" + WOODS[Math.min(5, var1)] + "_planks";
         case 6 -> "minecraft:" + WOODS[Math.min(5, var1 & 7)] + "_sapling";
         case 7 -> "minecraft:bedrock";
         case 8, 9 -> "minecraft:water";
         case 10, 11 -> "minecraft:lava";
         case 12 -> var1 == 1 ? "minecraft:red_sand" : "minecraft:sand";
         case 13 -> "minecraft:gravel";
         case 14 -> "minecraft:gold_ore";
         case 15 -> "minecraft:iron_ore";
         case 16 -> "minecraft:coal_ore";
         case 17 -> "minecraft:" + WOODS[var1 & 3] + "_log";
         case 18 -> "minecraft:" + WOODS[var1 & 3] + "_leaves";
         case 19 -> "minecraft:sponge";
         case 20 -> "minecraft:glass";
         case 21 -> "minecraft:lapis_ore";
         case 22 -> "minecraft:lapis_block";
         case 23 -> "minecraft:dispenser";
         case 24 -> var1 == 1 ? "minecraft:chiseled_sandstone" : (var1 == 2 ? "minecraft:cut_sandstone" : "minecraft:sandstone");
         case 25 -> "minecraft:note_block";
         default -> null;
         case 27 -> "minecraft:powered_rail";
         case 28 -> "minecraft:detector_rail";
         case 29 -> "minecraft:sticky_piston";
         case 30 -> "minecraft:cobweb";
         case 33 -> "minecraft:piston";
         case 35 -> "minecraft:" + COLORS[var1] + "_wool";
         case 37 -> "minecraft:dandelion";
         case 38 -> "minecraft:poppy";
         case 41 -> "minecraft:gold_block";
         case 42 -> "minecraft:iron_block";
         case 43 -> "minecraft:smooth_stone";
         case 44 -> {
            switch (var1 & 7) {
               case 1:
                  yield "minecraft:sandstone_slab";
               case 2:
               default:
                  yield "minecraft:smooth_stone_slab";
               case 3:
                  yield "minecraft:cobblestone_slab";
               case 4:
                  yield "minecraft:brick_slab";
               case 5:
                  yield "minecraft:stone_brick_slab";
               case 6:
                  yield "minecraft:nether_brick_slab";
               case 7:
                  yield "minecraft:quartz_slab";
            }
         }
         case 45 -> "minecraft:bricks";
         case 46 -> "minecraft:tnt";
         case 47 -> "minecraft:bookshelf";
         case 48 -> "minecraft:mossy_cobblestone";
         case 49 -> "minecraft:obsidian";
         case 50 -> "minecraft:torch";
         case 53 -> "minecraft:oak_stairs";
         case 54 -> "minecraft:chest";
         case 55 -> "minecraft:redstone_wire";
         case 56 -> "minecraft:diamond_ore";
         case 57 -> "minecraft:diamond_block";
         case 58 -> "minecraft:crafting_table";
         case 61, 62 -> "minecraft:furnace";
         case 65 -> "minecraft:ladder";
         case 66 -> "minecraft:rail";
         case 67 -> "minecraft:cobblestone_stairs";
         case 69 -> "minecraft:lever";
         case 70 -> "minecraft:stone_pressure_plate";
         case 72 -> "minecraft:oak_pressure_plate";
         case 73, 74 -> "minecraft:redstone_ore";
         case 75, 76 -> "minecraft:redstone_torch";
         case 77 -> "minecraft:stone_button";
         case 78 -> "minecraft:snow";
         case 79 -> "minecraft:ice";
         case 80 -> "minecraft:snow_block";
         case 81 -> "minecraft:cactus";
         case 82 -> "minecraft:clay";
         case 85 -> "minecraft:oak_fence";
         case 86 -> "minecraft:carved_pumpkin";
         case 87 -> "minecraft:netherrack";
         case 88 -> "minecraft:soul_sand";
         case 89 -> "minecraft:glowstone";
         case 91 -> "minecraft:jack_o_lantern";
         case 95 -> "minecraft:" + COLORS[var1] + "_stained_glass";
         case 96 -> "minecraft:oak_trapdoor";
         case 98 -> {
            switch (var1) {
               case 1:
                  yield "minecraft:mossy_stone_bricks";
               case 2:
                  yield "minecraft:cracked_stone_bricks";
               case 3:
                  yield "minecraft:chiseled_stone_bricks";
               default:
                  yield "minecraft:stone_bricks";
            }
         }
         case 101 -> "minecraft:iron_bars";
         case 102 -> "minecraft:glass_pane";
         case 103 -> "minecraft:melon";
         case 106 -> "minecraft:vine";
         case 107 -> "minecraft:oak_fence_gate";
         case 108 -> "minecraft:brick_stairs";
         case 109 -> "minecraft:stone_brick_stairs";
         case 110 -> "minecraft:mycelium";
         case 112 -> "minecraft:nether_bricks";
         case 113 -> "minecraft:nether_brick_fence";
         case 114 -> "minecraft:nether_brick_stairs";
         case 121 -> "minecraft:end_stone";
         case 123, 124 -> "minecraft:redstone_lamp";
         case 125 -> "minecraft:" + WOODS[Math.min(5, var1 & 7)] + "_planks";
         case 126 -> "minecraft:" + WOODS[Math.min(5, var1 & 7)] + "_slab";
         case 128 -> "minecraft:sandstone_stairs";
         case 129 -> "minecraft:emerald_ore";
         case 130 -> "minecraft:ender_chest";
         case 133 -> "minecraft:emerald_block";
         case 134 -> "minecraft:spruce_stairs";
         case 135 -> "minecraft:birch_stairs";
         case 136 -> "minecraft:jungle_stairs";
         case 138 -> "minecraft:beacon";
         case 139 -> var1 == 1 ? "minecraft:mossy_cobblestone_wall" : "minecraft:cobblestone_wall";
         case 145 -> "minecraft:anvil";
         case 146 -> "minecraft:trapped_chest";
         case 152 -> "minecraft:redstone_block";
         case 153 -> "minecraft:nether_quartz_ore";
         case 154 -> "minecraft:hopper";
         case 155 -> var1 == 1 ? "minecraft:chiseled_quartz_block" : (var1 >= 2 ? "minecraft:quartz_pillar" : "minecraft:quartz_block");
         case 156 -> "minecraft:quartz_stairs";
         case 158 -> "minecraft:dropper";
         case 159 -> "minecraft:" + COLORS[var1] + "_terracotta";
         case 160 -> "minecraft:" + COLORS[var1] + "_stained_glass_pane";
         case 161 -> "minecraft:" + (var1 % 2 == 0 ? "acacia" : "dark_oak") + "_leaves";
         case 162 -> "minecraft:" + (var1 % 2 == 0 ? "acacia" : "dark_oak") + "_log";
         case 163 -> "minecraft:acacia_stairs";
         case 164 -> "minecraft:dark_oak_stairs";
         case 165 -> "minecraft:slime_block";
         case 167 -> "minecraft:iron_trapdoor";
         case 168 -> var1 == 1 ? "minecraft:prismarine_bricks" : (var1 == 2 ? "minecraft:dark_prismarine" : "minecraft:prismarine");
         case 169 -> "minecraft:sea_lantern";
         case 170 -> "minecraft:hay_block";
         case 171 -> "minecraft:" + COLORS[var1] + "_carpet";
         case 172 -> "minecraft:terracotta";
         case 173 -> "minecraft:coal_block";
         case 174 -> "minecraft:packed_ice";
         case 179 -> "minecraft:red_sandstone";
         case 180 -> "minecraft:red_sandstone_stairs";
         case 201 -> "minecraft:purpur_block";
         case 202 -> "minecraft:purpur_pillar";
         case 203 -> "minecraft:purpur_stairs";
         case 206 -> "minecraft:end_stone_bricks";
         case 213 -> "minecraft:magma_block";
         case 214 -> "minecraft:nether_wart_block";
         case 215 -> "minecraft:red_nether_bricks";
         case 216 -> "minecraft:bone_block";
         case 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250 -> "minecraft:" + COLORS[var0 - 235] + "_glazed_terracotta";
         case 251 -> "minecraft:" + COLORS[var1] + "_concrete";
         case 252 -> "minecraft:" + COLORS[var1] + "_concrete_powder";
      };
   }
}
