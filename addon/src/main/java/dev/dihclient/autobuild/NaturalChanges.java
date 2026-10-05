package dev.dihclient.autobuild;

public final class NaturalChanges {
    private NaturalChanges() {
    }

    public static boolean accepts(String planned, String world) {
        if (planned.equals(world)) {
            return false;
        }
        switch (planned) {
            case "dirt":
                return world.equals("grass_block") || world.equals("podzol") || world.equals("mycelium");
            case "farmland":
                return world.equals("dirt");
            case "dirt_path":
                return world.equals("dirt");
            case "kelp":
                return world.equals("kelp_plant");
            case "kelp_plant":
                return world.equals("kelp");
            case "weeping_vines":
            case "twisting_vines":
            case "cave_vines":
                return world.equals(planned + "_plant");
            case "weeping_vines_plant":
            case "twisting_vines_plant":
            case "cave_vines_plant":
                return world.equals(planned.substring(0, planned.length() - "_plant".length()));
            case "pumpkin_stem":
            case "melon_stem":
                return world.equals("attached_" + planned);
            default:
                break;
        }
        if (planned.contains("coral") && !planned.startsWith("dead_")) {
            return world.equals("dead_" + planned);
        }
        if (!planned.startsWith("waxed_") && planned.contains("copper") && world.contains("copper")) {
            for (String stage : new String[]{"exposed_", "weathered_", "oxidized_"}) {
                if (world.startsWith(stage) && !planned.startsWith(stage)) {
                    String base = world.substring(stage.length());
                    if (base.equals(planned) || (base.equals("copper") && planned.equals("copper_block"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
