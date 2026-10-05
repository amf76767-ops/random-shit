package dev.dihclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dihclient.merge.ConfigMigration;
import java.util.List;
import java.util.Map;

public final class MergeTests {
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

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    public static void main(String[] args) {

        JsonObject root = json("{\"modules\":{\"tunnel\":{\"enabled\":true,\"key\":72,\"settings\":{\"width\":5,\"reach\":4.0,\"torches\":false}},"
                + "\"automine\":{\"enabled\":false,\"key\":-1,\"settings\":{\"reach\":3.5,\"vein_mine\":true}}}}");
        List<ConfigMigration.Rule> rules = List.of(
                new ConfigMigration.Rule("automine", "miner", Map.of(), null, "mode", "ORES", true, false),
                new ConfigMigration.Rule("tunnel", "miner", Map.of("reach", "tunnel_reach"), null, "mode", "TUNNEL", true, false));
        ConfigMigration.apply(root, rules);
        JsonObject miner = root.getAsJsonObject("modules").getAsJsonObject("miner");
        JsonObject ms = miner.getAsJsonObject("settings");
        check(ms.get("width").getAsInt() == 5, "tunnel width carried over");
        check(ms.get("tunnel_reach").getAsDouble() == 4.0, "tunnel reach carried over under the new name");
        check(ms.get("reach").getAsDouble() == 3.5, "ore reach stays");
        check(ms.get("vein_mine").getAsBoolean(), "vein mine carried over");
        check("TUNNEL".equals(ms.get("mode").getAsString()), "mode follows the module that was on");
        check(miner.get("enabled").getAsBoolean(), "miner is on because tunnel was");
        check(miner.get("key").getAsInt() == 72, "key binding taken over");

        JsonObject again = json("{\"modules\":{\"miner\":{\"enabled\":false,\"key\":5,\"settings\":{\"width\":9,\"mode\":\"ORES\"}},"
                + "\"tunnel\":{\"enabled\":true,\"key\":72,\"settings\":{\"width\":5}}}}");
        ConfigMigration.apply(again, rules);
        JsonObject s2 = again.getAsJsonObject("modules").getAsJsonObject("miner").getAsJsonObject("settings");
        check(s2.get("width").getAsInt() == 9 && "ORES".equals(s2.get("mode").getAsString()), "a second run changes nothing that is there");
        check(again.getAsJsonObject("modules").getAsJsonObject("miner").get("key").getAsInt() == 5, "own key kept");

        JsonObject sr = json("{\"modules\":{\"saferoute\":{\"enabled\":true,\"settings\":{\"avoid_mobs\":false}}}}");
        ConfigMigration.apply(sr, List.of(new ConfigMigration.Rule("saferoute", "goto", Map.of(), "safe_route", null, null, false, false)));
        JsonObject gs = sr.getAsJsonObject("modules").getAsJsonObject("goto").getAsJsonObject("settings");
        check(gs.get("safe_route").getAsBoolean() && !gs.get("avoid_mobs").getAsBoolean(), "option switch turned on, settings moved");
        check(!sr.getAsJsonObject("modules").getAsJsonObject("goto").has("enabled"), "an option does not turn the host on");

        JsonObject rn = json("{\"modules\":{\"flipfinder\":{\"enabled\":true,\"key\":-1,\"settings\":{\"x\":1}}}}");
        ConfigMigration.apply(rn, List.of(ConfigMigration.Rule.rename("flipfinder", "autoflipper")));
        check(rn.getAsJsonObject("modules").getAsJsonObject("autoflipper").get("enabled").getAsBoolean(), "rename keeps enabled");
        check(rn.getAsJsonObject("modules").getAsJsonObject("autoflipper").getAsJsonObject("settings").get("x").getAsInt() == 1, "rename keeps settings");

        ConfigMigration.apply(json("{}"), rules);
        ConfigMigration.apply(null, rules);
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
