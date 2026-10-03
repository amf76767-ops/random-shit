package dev.dihclient.glue;

import dev.dihclient.autobuild.BuildPilot;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.nav.Nav;
import dev.dihclient.render.Render3D;
import java.util.List;
import net.minecraft.class_238;

/** Draws the route AutoBuild is walking: green line to a spot that sees blocks to place, orange when it only leads as close as it can. */
public class BuildPath extends Module {
    public BuildPath() {
        super("BuildPath", Category.RENDER, "Shows the route AutoBuild plans to the next stand spot.");
    }

    @Override
    public void onRender3D(Render3D r) {
        List<Nav.Cell> path = BuildPilot.routeForPreview();
        if (path == null || path.size() < 2) {
            return;
        }
        int color = BuildPilot.routeCovers() ? 0xFF55E07A : 0xFFFFA64D;
        for (int i = 0; i + 1 < path.size(); i++) {
            Nav.Cell a = path.get(i), b = path.get(i + 1);
            r.line(a.x + 0.5, a.y + 0.1, a.z + 0.5, b.x + 0.5, b.y + 0.1, b.z + 0.5, color, true);
        }
        Nav.Cell end = path.get(path.size() - 1);
        r.boxOutline(new class_238(end.x + 0.1, end.y, end.z + 0.1, end.x + 0.9, end.y + 0.05, end.z + 0.9), color, true);
    }
}
