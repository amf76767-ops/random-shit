package dev.dihclient;

import dev.dihclient.autobuild.NaturalChanges;
import dev.dihclient.team.Partition;
import dev.dihclient.team.TeamStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class TeamTests {
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

    public static void main(String[] args) throws Exception {
        // natural block changes
        check(NaturalChanges.accepts("dirt", "grass_block"), "dirt may become grass");
        check(NaturalChanges.accepts("dirt", "podzol") && NaturalChanges.accepts("dirt", "mycelium"), "dirt may become podzol / mycelium");
        check(!NaturalChanges.accepts("grass_block", "dirt"), "grass planned, dirt in the world is not accepted");
        check(!NaturalChanges.accepts("dirt", "dirt"), "the same block is not a variant");
        check(!NaturalChanges.accepts("dirt", "stone") && !NaturalChanges.accepts("stone", "dirt"), "unrelated blocks stay wrong");
        check(NaturalChanges.accepts("farmland", "dirt"), "farmland dries to dirt");
        check(NaturalChanges.accepts("kelp", "kelp_plant") && NaturalChanges.accepts("kelp_plant", "kelp"), "kelp grows");
        check(NaturalChanges.accepts("weeping_vines", "weeping_vines_plant") && NaturalChanges.accepts("cave_vines_plant", "cave_vines"), "vines grow");
        check(NaturalChanges.accepts("melon_stem", "attached_melon_stem"), "stems attach");
        check(NaturalChanges.accepts("brain_coral_block", "dead_brain_coral_block") && NaturalChanges.accepts("tube_coral_fan", "dead_tube_coral_fan"), "coral dies");
        check(!NaturalChanges.accepts("dead_tube_coral", "tube_coral"), "dead coral does not revive");
        check(NaturalChanges.accepts("copper_block", "oxidized_copper") && NaturalChanges.accepts("cut_copper", "weathered_cut_copper"), "copper oxidises");
        check(NaturalChanges.accepts("cut_copper_stairs", "exposed_cut_copper_stairs"), "copper stairs oxidise");
        check(!NaturalChanges.accepts("waxed_copper_block", "exposed_copper") && !NaturalChanges.accepts("copper_block", "waxed_copper_block"), "waxed copper does not change");
        check(!NaturalChanges.accepts("copper_block", "exposed_cut_copper"), "a different copper block is wrong");

        // codes
        Random r = new Random(7);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String code = TeamStore.newCode(r);
            check(code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}") && !code.matches(".*[01OIL].*"), "code shape " + code);
            check(TeamStore.normalizeCode(code).equals(code), "a code normalizes to itself");
            seen.add(code);
        }
        check(seen.size() > 490, "codes differ");
        check(TeamStore.normalizeCode("abcd efgh").equals("ABCD-EFGH") && TeamStore.normalizeCode("abcdefgh").equals("ABCD-EFGH"), "typed codes are cleaned");
        check(TeamStore.normalizeCode("ABC").isEmpty() && TeamStore.normalizeCode("ABCD-EFG0").isEmpty() && TeamStore.normalizeCode(null).isEmpty(), "bad codes refused");

        // strips
        for (int n = 1; n <= 6; n++) {
            int[] count = new int[n];
            for (int c = 0; c < 37; c++) {
                int s = Partition.strip(c, 0, 36, n);
                check(s >= 0 && s < n, "strip in range");
                count[s]++;
                check(Partition.mine(c, 0, 36, s, n), "mine agrees with strip");
            }
            int total = 0, min = Integer.MAX_VALUE, max = 0;
            for (int k : count) {
                total += k;
                min = Math.min(min, k);
                max = Math.max(max, k);
            }
            check(total == 37 && max - min <= 1, "strips are even for " + n + ": " + java.util.Arrays.toString(count));
        }
        check(Partition.strip(5, 0, 0, 3) == 0 && Partition.strip(-9, 0, 10, 3) == 0 && Partition.strip(99, 0, 10, 3) == 2, "edges clamp");

        // the store
        Path dir = Files.createTempDirectory("teams");
        TeamStore store = new TeamStore(dir);
        String code = "ABCD-EFGH";
        TeamStore.Spec spec = new TeamStore.Spec(code, "Host", "play.example.net", "minecraft:overworld", "/tmp/house.litematic", "house",
                "abc", 10, 64, -20, "CLOCKWISE_90", "NONE", 1000L);
        store.saveSpec(spec);
        check(store.loadSpec(code).isPresent() && store.loadSpec(code).get().equals(spec), "spec round trip");
        check(store.loadSpec("ZZZZ-ZZZZ").isEmpty(), "unknown code");

        store.heartbeat(code, "b", "Alt", 2000L);
        store.heartbeat(code, "a", "Host", 1000L);
        store.heartbeat(code, "a", "Host", 5000L); // renewal keeps the join time
        List<TeamStore.Member> members = store.active(code, 6000L, 15000L);
        check(members.size() == 2 && members.get(0).id().equals("a") && members.get(0).joined() == 1000L, "first to join is first");
        check(store.active(code, 100000L, 15000L).isEmpty(), "stale members are gone");
        store.heartbeat(code, "c", "Late", 6000L);
        check(store.active(code, 6500L, 4000L).size() == 2, "only recent heartbeats count: " + store.active(code, 6500L, 4000L).size());
        store.leave(code, "a");
        check(store.active(code, 6500L, 15000L).stream().noneMatch(m -> m.id().equals("a")), "leaving removes the member");

        Path file = dir.resolve("f.bin");
        Files.write(file, "hello".getBytes());
        check(TeamStore.sha256(file).equals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"), "sha256");

        store.cleanup(1000L + 3 * 86_400_000L, 86_400_000L);
        check(store.loadSpec(code).isEmpty(), "old teams are cleaned up");
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
