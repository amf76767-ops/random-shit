package dev.dihclient.nav;

import java.util.Set;

public final class OverlayTerrain implements Nav.Terrain {
    private final Nav.Terrain base;
    private final Set<Long> solid;

    public OverlayTerrain(Nav.Terrain base, Set<Long> solid) {
        this.base = base;
        this.solid = solid;
    }

    @Override
    public boolean passable(int x, int y, int z) {
        return !this.solid.contains(Nav.key(x, y, z)) && this.base.passable(x, y, z);
    }

    @Override
    public boolean support(int x, int y, int z) {
        return this.solid.contains(Nav.key(x, y, z)) || this.base.support(x, y, z);
    }

    @Override
    public boolean hazard(int x, int y, int z) {
        return !this.solid.contains(Nav.key(x, y, z)) && this.base.hazard(x, y, z);
    }
}
