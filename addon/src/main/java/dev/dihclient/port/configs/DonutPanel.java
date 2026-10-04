package dev.dihclient.port.configs;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Finds a free place for the window of the new Donut category the first time the GUI opens. The GUI's own default
 * placement starts at the top left and would put the window on top of Combat when the other windows already have saved spots.
 */
public final class DonutPanel {
    private static boolean done;

    private DonutPanel() {
    }

    /** @param scaledWidth the width of the GUI in its own (scaled) pixels */
    public static void place(Object screen, int scaledWidth) {
        if (done) {
            return;
        }
        done = true;
        try {
            Class<?> gui = Class.forName("dev.dihclient.gui.MeteorGuiScreen");
            Field panelsField = gui.getDeclaredField("PANELS");
            panelsField.setAccessible(true);
            Map<?, ?> panels = (Map<?, ?>) panelsField.get(null);
            Object mine = panels.get(Category.DONUT);
            if (mine == null) {
                return;
            }
            Class<?> panel = mine.getClass();
            Field fx = panel.getDeclaredField("x");
            Field fy = panel.getDeclaredField("y");
            fx.setAccessible(true);
            fy.setAccessible(true);
            if (fx.getInt(mine) >= 0 && fy.getInt(mine) >= 0) {
                return; // has a saved place
            }
            int bestX = -1;
            int bestY = 30;
            for (Map.Entry<?, ?> e : panels.entrySet()) {
                if (e.getKey() == Category.DONUT) {
                    continue;
                }
                int x = fx.getInt(e.getValue());
                if (x > bestX) {
                    bestX = x;
                    bestY = fy.getInt(e.getValue());
                }
            }
            if (bestX >= 0 && bestX + 124 + 118 <= scaledWidth - 4) {
                fx.setInt(mine, bestX + 124);
                fy.setInt(mine, bestY);
            }
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] could not place the Donut window", t);
        }
    }
}
