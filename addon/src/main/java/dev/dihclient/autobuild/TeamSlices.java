package dev.dihclient.autobuild;

import dev.dihclient.team.Partition;
import java.util.ArrayList;
import java.util.List;

/** The part of a schematic that one member of a team builds: a strip along the longer horizontal side, all layers. */
public final class TeamSlices {
    private TeamSlices() {
    }

    public static Schematic slice(Schematic full, int index, int count) {
        if (count <= 1) {
            return full;
        }
        boolean alongX = full.sizeX >= full.sizeZ;
        int max = (alongX ? full.sizeX : full.sizeZ) - 1;
        List<Schematic.Entry> mine = new ArrayList<>();
        for (Schematic.Entry e : full.blocks) {
            if (Partition.mine(alongX ? e.x : e.z, 0, max, index, count)) {
                mine.add(e);
            }
        }
        Schematic out = new Schematic(full.file, full.format, mine, full.sizeX, full.sizeY, full.sizeZ);
        out.mapGrid = full.mapGrid;
        out.mapNoobline = full.mapNoobline;
        return out;
    }
}
