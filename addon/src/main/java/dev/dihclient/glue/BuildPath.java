package dev.dihclient.glue;

import dev.dihclient.autobuild.BuildPilot;
import dev.dihclient.autobuild.RoutePlans;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.nav.Nav;
import dev.dihclient.nav.RoutePlanner;
import dev.dihclient.render.Render3D;
import java.util.List;
import net.minecraft.class_238;

/** Draws the route AutoBuild is walking: green line to a spot that sees blocks to place, orange when it only leads as close as it can. */
public class BuildPath extends Module {
    public BuildPath() {
        super("BuildPath", Category.RENDER, "Shows the route AutoBuild plans to the next stand spot.");
    }

    private static final int[] LAYER_COLORS = {0xFF4DA6FF, 0xFFB36BFF, 0xFFFF6BB5, 0xFFFFD24D, 0xFF4DFFD2};

    /** The planned route of the whole build: a spot for every stop, joined in the order they are visited, coloured by layer. */
    private void drawPlan(Render3D r) {
        RoutePlanner.Result plan = RoutePlans.any();
        if (plan == null || plan.stops.isEmpty()) {
            return;
        }
        int n = Math.min(plan.stops.size(), 600);
        RoutePlanner.Stop prev = null;
        for (int i = 0; i < n; i++) {
            RoutePlanner.Stop stop = plan.stops.get(i);
            int color = (LAYER_COLORS[Math.floorMod(stop.layer(), LAYER_COLORS.length)] & 0x00FFFFFF) | 0x99000000;
            if (prev != null) {
                r.line(prev.x() + 0.5, prev.y() + 0.05, prev.z() + 0.5, stop.x() + 0.5, stop.y() + 0.05, stop.z() + 0.5, color, true);
            }
            r.boxOutline(new class_238(stop.x() + 0.3, stop.y(), stop.z() + 0.3, stop.x() + 0.7, stop.y() + 0.04, stop.z() + 0.7), color, true);
            prev = stop;
        }
        // supports the plan puts under floating parts (yellow) and blocks it cannot reach or support (red), visible through walls
        int shown = 0;
        for (int[] b : plan.supports) {
            if (shown++ > 1500) {
                break;
            }
            class_238 box = new class_238(b[0] + 0.15, b[1] + 0.15, b[2] + 0.15, b[0] + 0.85, b[1] + 0.85, b[2] + 0.85);
            r.boxFilled(box, 0x33FFD24D, true);
            r.boxOutline(box, 0xCCFFD24D, true);
        }
        shown = 0;
        for (int[] b : plan.problems) {
            if (shown++ > 1500) {
                break;
            }
            class_238 box = new class_238(b[0] + 0.05, b[1] + 0.05, b[2] + 0.05, b[0] + 0.95, b[1] + 0.95, b[2] + 0.95);
            r.boxFilled(box, 0x44FF3B3B, true);
            r.boxOutline(box, 0xEEFF3B3B, true);
        }
    }

    @Override
    public void onRender3D(Render3D r) {
        try {
            this.drawPlan(r);
        } catch (Throwable ignored) {
            // the drawing must never stop the game
        }
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
