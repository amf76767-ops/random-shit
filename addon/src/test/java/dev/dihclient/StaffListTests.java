package dev.dihclient;

import dev.dihclient.port.staff.AlarmPattern;
import dev.dihclient.port.staff.GoliathMap;
import dev.dihclient.port.staff.StaffEntry;
import dev.dihclient.port.staff.StaffMatcher;
import dev.dihclient.port.staff.StaffNames;
import dev.dihclient.port.staff.StaffSighting;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StaffListTests {
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

    static StaffMatcher donut() {
        return new StaffMatcher(StaffMatcher.MODE_ALL, StaffMatcher.DEFAULT_RANK_WORDS, StaffMatcher.DEFAULT_MARKERS, false,
                StaffMatcher.DEFAULT_TEAM_RANK_MAX, String.join(",", StaffNames.DONUT_STAFF), "");
    }

    public static void main(String[] args) {

        check(StaffNames.isStaffName("fluffymaster07"), "name match ignores case");
        check(StaffNames.isStaffName("FluffyMaster07"), "exact name");
        check(!StaffNames.isStaffName("Notch") && !StaffNames.isStaffName(null) && !StaffNames.isStaffName(""), "non staff / null");
        UUID online = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID offline = UUID.fromString("00000000-0000-3000-8000-000000000001".replace("-3000-", "-2000-"));
        check(offline.version() == 2, "test uuid is version 2");
        check(StaffNames.isAccount(online, "Steve_1"), "plain account");
        check(StaffNames.isAccount(online, ".BedrockGuy") && StaffNames.isAccount(online, "*Bedrock"), "bedrock prefix");
        check(!StaffNames.isAccount(offline, "Steve"), "version 2 uuid is no account");
        check(!StaffNames.isAccount(online, "has space") && !StaffNames.isAccount(online, "waytoolongname_17x") && !StaffNames.isAccount(online, null),
                "bad names");

        check(StaffNames.formatAge(0).equals("0:00"), "age 0");
        check(StaffNames.formatAge(9_000).equals("0:09"), "age 9s");
        check(StaffNames.formatAge(65_000).equals("1:05"), "age 1:05");
        check(StaffNames.formatAge(3_600_000L).equals("1:00:00"), "age 1h");
        check(StaffNames.formatAge(3_725_000L).equals("1:02:05"), "age 1:02:05");
        check(StaffNames.formatAge(-5).equals("0:00"), "negative age");

        StaffMatcher m = donut();
        StaffMatcher.Match a = m.match("Bob", null, "[Admin] Bob");
        check(a != null && a.rank().equals("Admin"), "rank word in display name");
        StaffMatcher.Match b = m.match("Bob", null, "§c[Sr-Mod]§r Bob");
        check(b != null && b.rank().equals("Sr. Mod"), "legacy codes and hyphen word: " + (b == null ? null : b.rank()));
        StaffMatcher.Match c = m.match("Bob", null, "Bob the Moderator");
        check(c != null && c.rank().equals("Mod"), "moderator maps to Mod");
        check(a.seniority() < c.seniority(), "admin more senior than mod");
        check(m.match("Steve", null, "[Member] Steve") == null, "regular player is no staff");
        check(m.match("Steve", "99-default", "Steve") == null, "team rank above the limit");
        StaffMatcher.Match t = m.match("Steve", "03-store", "Steve");
        check(t != null && t.rank().equals("03"), "team rank prefix");
        StaffMatcher.Match k = m.match("FluffyMaster07", null, "FluffyMaster07");
        check(k != null && k.rank().equals("Staff"), "known name without rank text gets the generic rank");
        StaffMatcher.Match own = m.match("Moderator", null, "Moderator");
        check(own == null, "the player's own name is not a rank");
        check(m.match("Bob", null, "★ Bob") != null, "star marker");
        check(m.match("", null, "[Admin]") == null && m.match(null, null) == null, "empty name");
        StaffMatcher.Match sc = m.match("Bob", null, "ᴀᴅᴍɪɴ Bob");
        check(sc != null && sc.rank().equals("Admin"), "small caps text");
        check(StaffMatcher.teamRank("05-x") == 5 && StaffMatcher.teamRank("x") == -1 && StaffMatcher.teamRank(null) == -1, "teamRank parse");
        check(StaffMatcher.teamRankLabel(7).equals("07") && StaffMatcher.teamRankLabel(12).equals("12"), "teamRank label");
        check(StaffMatcher.parseMarkers("U+2605, \\u2606 x").containsAll(List.of(0x2605, 0x2606, (int) 'x')), "marker parsing");
        check(StaffMatcher.stripLegacyCodes("§aHi§r").equals("Hi"), "strip legacy codes");

        StaffSighting s = new StaffSighting();
        check(!s.marked(1000), "fresh sighting not marked");
        s.provenAt = 1000;
        check(s.marked(1000 + 599_999) && !s.marked(1000 + 600_000), "mark lasts 10 minutes");
        s.nearby = true;
        check(s.marked(9_999_999), "nearby is always marked");
        check(!s.coolingDown(5000), "no cooldown without alert");
        s.alertedAt = 5000;
        s.highlightUntil = 5000 + StaffSighting.HIGHLIGHT_MS;
        check(s.coolingDown(5000 + 119_999) && !s.coolingDown(5000 + 120_000), "alert cooldown 2 minutes");
        check(s.alertLevel(5000) == 1.0F && s.alertLevel(5000 + 2000) == 0.5F && s.alertLevel(99_999) == 0.0F, "alert level fades");

        long now = 10_000;
        StaffEntry nearby = entry("zed", 5, StaffEntry.Presence.ONLINE);
        StaffEntry region = entry("yan", 5, StaffEntry.Presence.ONLINE);
        StaffEntry hiddenSenior = entry("xia", 1, StaffEntry.Presence.VANISHED);
        StaffEntry hiddenJunior = entry("walt", 9, StaffEntry.Presence.SPECTATOR);
        StaffEntry plainB = entry("Bea", 3, StaffEntry.Presence.ONLINE);
        StaffEntry plainA = entry("amy", 3, StaffEntry.Presence.ONLINE);
        StaffEntry plainSenior = entry("zoe", 1, StaffEntry.Presence.ONLINE);
        StaffSighting nb = new StaffSighting();
        nb.nearby = true;
        StaffSighting rg = new StaffSighting();
        rg.provenAt = now - 1000;
        List<StaffEntry> list = new ArrayList<>(List.of(plainB, hiddenJunior, plainA, region, plainSenior, hiddenSenior, nearby));
        list.sort(StaffEntry.order(id -> id.equals(nearby.id) ? nb : id.equals(region.id) ? rg : null, now));
        String order = "";
        for (StaffEntry e : list) {
            order += e.name + " ";
        }
        check(order.equals("zed yan xia walt zoe amy Bea "), "list order: " + order);

        int centre = GoliathMap.goliathAt(0, 0);
        check(centre == GoliathMap.goliathAt(100, 100), "same cell, same goliath");
        check(GoliathMap.goliathAt(-300_000, 0) == -1 && GoliathMap.goliathAt(0, 300_000) == -1, "outside the map");
        check(GoliathMap.goliathAt(-225_000 + 4 * 12_500 + 1, -225_000 + 1) == 0, "first goliath is col 4 row 0");
        check(GoliathMap.goliathAt(-225_000 + 6 * 12_500 + 1, -225_000 + 1) == 1, "second goliath is col 6 row 0");
        check(GoliathMap.goliathAt(-225_000 + 5 * 12_500, -225_000 + 3 * 12_500 + 1) == 0, "rect 2x4 covers its cells");
        check(GoliathMap.goliathAt(-225_000 + 5 * 12_500, -225_000 + 4 * 12_500 + 1) != 0, "and not the one below");

        check(AlarmPattern.interval(AlarmPattern.GUARDIAN) == 60 && AlarmPattern.interval(AlarmPattern.BEEP) == 3
                && AlarmPattern.interval(AlarmPattern.BELL) == 10 && AlarmPattern.interval(AlarmPattern.SIREN) == 4, "alarm intervals");
        check(AlarmPattern.pitch(AlarmPattern.SIREN, 0) == 1.5F && AlarmPattern.pitch(AlarmPattern.SIREN, 1) == 1.0F, "siren alternates");
        check(AlarmPattern.pitch(AlarmPattern.GUARDIAN, 3) == 1.0F && AlarmPattern.pitch(AlarmPattern.BEEP, 3) == 1.6F, "fixed pitches");

        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static StaffEntry entry(String name, int seniority, StaffEntry.Presence presence) {
        StaffEntry e = new StaffEntry(UUID.nameUUIDFromBytes(name.getBytes()));
        e.name = name;
        e.seniority = seniority;
        e.presence = presence;
        return e;
    }
}
