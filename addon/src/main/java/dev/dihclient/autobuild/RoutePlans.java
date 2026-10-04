package dev.dihclient.autobuild;

import dev.dihclient.nav.Nav;
import dev.dihclient.nav.RoutePlanner;
import java.util.Set;

/** The planned route of the build that is running, for the pilot (which spot next) and for the preview drawing. */
public final class RoutePlans {
    private static volatile BuildPlan owner;
    private static volatile RoutePlanner.Result result;

    private RoutePlans() {
    }

    public static void set(BuildPlan plan, RoutePlanner.Result r) {
        owner = plan;
        result = r;
    }

    public static void clear() {
        owner = null;
        result = null;
    }

    /** The plan of this build, or null when none was made (or it was made for another build). */
    public static RoutePlanner.Result of(BuildPlan plan) {
        return plan != null && plan == owner ? result : null;
    }

    public static RoutePlanner.Result any() {
        return result;
    }

    /**
     * The first stop of the layer that still has open blocks, as a cell the pilot can walk to; null when the plan has nothing
     * (left) for this layer and the pilot has to choose by itself.
     */
    public static Nav.Cell nextStop(BuildPlan plan, int layer, Set<Long> openKeys, Nav.Region region, Set<Long> avoid) {
        RoutePlanner.Result r = of(plan);
        if (r == null) {
            return null;
        }
        for (RoutePlanner.Stop stop : r.stops) {
            if (stop.layer() != layer) {
                continue;
            }
            boolean needed = false;
            for (int[] b : stop.blocks()) {
                if (openKeys.contains(Nav.key(b[0], b[1], b[2]))) {
                    needed = true;
                    break;
                }
            }
            if (!needed) {
                continue;
            }
            long key = Nav.key(stop.x(), stop.y(), stop.z());
            Nav.Cell cell = region.get(stop.x(), stop.y(), stop.z());
            if (cell != null && !avoid.contains(key)) {
                return cell;
            }
        }
        return null;
    }
}
