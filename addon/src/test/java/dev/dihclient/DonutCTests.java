package dev.dihclient;

import dev.dihclient.port.donutc.FakeStatsLogic;
import dev.dihclient.port.donutc.FakeStatsLogic.Hit;
import dev.dihclient.port.donutc.PlayerDetectionLogic;
import java.util.List;
import java.util.Set;

public final class DonutCTests {
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

    static final List<String[]> MARKERS = List.of(
            new String[] {"money", "balance", "$"},
            new String[] {"shards", "★"},
            new String[] {"kills", "🗡", "⚔"},
            new String[] {"team"});

    public static void main(String[] args) {
        markers();
        find();
        numbers();
        mask();
        money();
        detection();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void markers() {
        check(FakeStatsLogic.markerAt("Money: $5", "money") == 0, "marker at start");
        check(FakeStatsLogic.markerAt("MONEY", "money") == 0, "case-insensitive");
        check(FakeStatsLogic.markerAt("moneybag", "money") == -1, "whole word only");
        check(FakeStatsLogic.markerAt("my money", "money") == 3, "marker inside");
        check(FakeStatsLogic.markerAt("ᴍᴏɴᴇʏ $5", "money") == 0, "small caps folded");
        check(FakeStatsLogic.markerAt("cost $5", "$") == 5, "symbol marker needs no word boundary");
        check(FakeStatsLogic.markerAt("a", "") == -1, "empty marker never matches");
        check(FakeStatsLogic.folded("ᴀᴢ").equals("az"), "folded a..z");
        int[] v = FakeStatsLogic.valueAfter("Money:  12,000  ", new String[] {"money"});
        check(v != null && "Money:  12,000  ".substring(v[0], v[1]).equals("12,000"), "value after marker is trimmed");
        int[] none = FakeStatsLogic.valueAfter("Money:", new String[] {"money"});
        check(none != null && none[0] == none[1], "marker without value gives an empty range");
        check(FakeStatsLogic.valueAfter("hello", new String[] {"money"}) == null, "no marker, no range");
    }

    static void find() {
        Hit h = FakeStatsLogic.find(MARKERS, "Money: $5K", "");
        check(h != null && h.stat() == 0 && !h.inNumber() && "Money: $5K".substring(h.start(), h.end()).equals("$5K"), "value in the name column");
        h = FakeStatsLogic.find(MARKERS, "Money", "1,500");
        check(h != null && h.stat() == 0 && h.inNumber() && h.start() == 0 && h.end() == 5, "label alone: value is the number column");
        h = FakeStatsLogic.find(MARKERS, "Stats", "\u2605 77");
        check(h != null && h.stat() == 1 && h.inNumber() && "\u2605 77".substring(h.start(), h.end()).equals("77"), "marker in the number column");
        check(FakeStatsLogic.find(MARKERS, "Region", "EU") == null, "no stat in the row");
        h = FakeStatsLogic.find(MARKERS, "Team: Reds", "");
        check(h != null && h.stat() == 3, "team");
    }

    static void numbers() {
        for (long seed = 0; seed < 200; seed++) {
            for (String raw : new String[] {"5", "42", "1,500", "1.25", "007", "1234567", "999999999999999", "12345678901234567890", "0", "10"}) {
                String fake = FakeStatsLogic.fakeNumber(seed * 7919L, 3, 1, raw);
                if (fake.length() != raw.length() || fake.equals(raw)) {
                    check(false, "fake number '" + raw + "' -> '" + fake + "' (seed " + seed + ")");
                    return;
                }
                for (int i = 0; i < raw.length(); i++) {
                    boolean digit = Character.isDigit(raw.charAt(i));
                    if (digit != Character.isDigit(fake.charAt(i)) || !digit && raw.charAt(i) != fake.charAt(i)) {
                        check(false, "separators kept '" + raw + "' -> '" + fake + "'");
                        return;
                    }
                }
                if (raw.length() > 1 && raw.charAt(0) != '0' && fake.charAt(0) == '0') {
                    check(false, "no leading zero appears '" + raw + "' -> '" + fake + "'");
                    return;
                }
            }
        }
        check(true, "fake numbers keep shape and differ");
        check(FakeStatsLogic.fakeNumber(1, 2, 3, "1,500").equals(FakeStatsLogic.fakeNumber(1, 2, 3, "1,500")), "stable for one seed");
        check(!FakeStatsLogic.fakeNumber(1, 2, 3, "123456").equals(FakeStatsLogic.fakeNumber(2, 2, 3, "123456"))
                || !FakeStatsLogic.fakeNumber(1, 2, 3, "123456").equals(FakeStatsLogic.fakeNumber(3, 2, 3, "123456")), "seed changes the result");
        check(FakeStatsLogic.fakeNumber(1, 1, 1, "abc").equals("abc"), "no digits untouched");
    }

    static void mask() {
        int[] n = {0};
        String out = FakeStatsLogic.maskText(11L, "Kills: 12 (3.5/day)", 1, n);
        check(out.startsWith("Kills: ") && out.length() == "Kills: 12 (3.5/day)".length() && !out.equals("Kills: 12 (3.5/day)"), "mask changes numbers only");
        check(n[0] == 2, "two numbers counted: " + n[0]);
        check(out.endsWith("/day)"), "text after numbers is kept");
        int[] m = {0};
        check(FakeStatsLogic.maskText(11L, "no numbers", 1, m) == "no numbers", "no digits returns the same string");
        int[] k = {0};
        check(FakeStatsLogic.maskText(5L, "a1,b", 1, k).startsWith("a") && k[0] == 1, "comma not followed by a digit ends the number");
        check(FakeStatsLogic.holderName(0).length() == 2 && !FakeStatsLogic.holderName(0).equals(FakeStatsLogic.holderName(1)), "holder names differ");
        check(FakeStatsLogic.escaped("aé").equals("a\\u00E9"), "escaped non-ascii");
    }

    static void money() {
        List<int[]> r = FakeStatsLogic.moneyRanges("Bal $1,200K and $5");
        check(r.size() == 2 && "Bal $1,200K and $5".substring(r.get(0)[0], r.get(0)[1]).equals("1,200K"), "money ranges");
        check(FakeStatsLogic.moneyRanges("no money").isEmpty(), "no dollar sign, no range");
        check("9M".equals(FakeStatsLogic.tabMoney("$9M", "1")), "custom money wins, dollar removed");
        check("77".equals(FakeStatsLogic.tabMoney("", "$77")), "falls back to the sidebar value");
        check(FakeStatsLogic.tabMoney("", null) == null, "nothing to show");
        check(FakeStatsLogic.tabMoney("  ", "$") == null, "only a dollar sign is nothing");
        check("$5".equals(FakeStatsLogic.fit("$1", "5")) && "5".equals(FakeStatsLogic.fit("1", "$5")), "fit keeps the shape of the real value");
    }

    static void detection() {
        Set<String> ignored = PlayerDetectionLogic.parseNames(" Bob, ,ALICE ");
        check(ignored.equals(Set.of("bob", "alice")), "names parsed lower case");
        Set<String> got = PlayerDetectionLogic.relevant(List.of("Me", "bob", "Eve", "FreeCamera", "Dan"), "Me", ignored);
        check(List.copyOf(got).equals(List.of("Eve", "Dan")), "whitelist, self and free camera are skipped, order kept: " + got);
        check(PlayerDetectionLogic.relevant(List.of("Me"), "Me", ignored).isEmpty(), "only self: nobody");
        check(PlayerDetectionLogic.parseList(" Fly, fly ,, Speed ").equals(List.of("Fly", "Speed")), "list: trimmed, repeats dropped");
        check(PlayerDetectionLogic.parseList(null).isEmpty(), "null list");
        check("pay Bob 1m".equals(PlayerDetectionLogic.payCommand(" Bob ", "1m")), "pay command");
        check(PlayerDetectionLogic.payCommand("", "1m") == null && PlayerDetectionLogic.payCommand("Bob", " ") == null, "pay needs both");
        check(PlayerDetectionLogic.payCommand("Bob", "1 /op x") == null, "pay refuses whitespace in a part");
        check("a, b +2".equals(PlayerDetectionLogic.shortNames(List.of("a", "b", "c", "d"), 2)), "short names");
        check("a".equals(PlayerDetectionLogic.shortNames(List.of("a"), 3)), "short names, one");
    }
}
