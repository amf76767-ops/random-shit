package dev.dihclient;

import dev.dihclient.port.packets.PacketFilter;
import java.util.Arrays;

public final class PacketLogTests {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) {
        check(Arrays.equals(PacketFilter.parseList(" Keep_Alive , minecraft:set_time,, "), new String[]{"keep_alive", "set_time"}), "parse");
        check(PacketFilter.parseList("").length == 0 && PacketFilter.parseList(" , ").length == 0, "empty list");
        String[] list = PacketFilter.parseList("move_player, keep_alive");
        check(PacketFilter.matches("move_player_pos_rot", list), "part of an id matches");
        check(PacketFilter.matches("KEEP_ALIVE", list), "case-insensitive");
        check(!PacketFilter.matches("block_update", list), "no match");
        check(!PacketFilter.matches("block_update", new String[0]), "empty list matches nothing");
        check(PacketFilter.cut("abcdef", 3).equals("abc..."), "cut long");
        check(PacketFilter.cut("abc", 3).equals("abc"), "keep exact length");
        System.out.println("PacketLogTests ok");
    }
}
