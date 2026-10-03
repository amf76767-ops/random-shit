package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.render.Render3D;
import net.minecraft.class_2596;
import net.minecraft.class_332;

/** Hidden, always on: runs the merged-in modules (see {@link Merge}) because the module manager no longer knows them. */
public class MergeBus extends Module {
    private static final java.lang.reflect.Method SEND = find();

    private static java.lang.reflect.Method find() {
        try {
            return Module.class.getMethod("onPacketSend", class_2596.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    public MergeBus() {
        super("MergeBus", Category.CLIENT, "Internal: keeps the folded-in modules running.");
        this.setHidden(true);
    }

    @Override
    public void onTick() {
        Merge.tickLinks();
        for (Module g : Merge.guests()) {
            if (g.isEnabled()) {
                try {
                    g.runPendingEnable();
                    g.onTick();
                } catch (Throwable t) {
                    DIHClient.LOG.error("[DIHClient] {} tick failed", g.name(), t);
                }
            }
        }
    }

    @Override
    public void onRender2D(class_332 context, float delta) {
        for (Module g : Merge.guests()) {
            if (g.isEnabled()) {
                try {
                    g.onRender2D(context, delta);
                } catch (Throwable t) {
                    DIHClient.LOG.error("[DIHClient] {} render2D failed", g.name(), t);
                }
            }
        }
    }

    @Override
    public void onRender3D(Render3D r) {
        for (Module g : Merge.guests()) {
            if (g.isEnabled()) {
                try {
                    g.onRender3D(r);
                } catch (Throwable t) {
                    DIHClient.LOG.error("[DIHClient] {} render3D failed", g.name(), t);
                }
            }
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean onPacketSend(class_2596 packet) {
        boolean cancel = false;
        for (Module g : Merge.guests()) {
            if (g.isEnabled()) {
                try {
                    cancel |= Boolean.TRUE.equals(SEND.invoke(g, packet));
                } catch (Throwable t) {
                    DIHClient.LOG.error("[DIHClient] {} packet hook failed", g.name(), t);
                }
            }
        }
        return cancel;
    }

    @Override
    public void onWorldChange() {
        for (Module g : Merge.guests()) {
            try {
                g.onWorldChange();
            } catch (Throwable t) {
                DIHClient.LOG.error("[DIHClient] {} world change failed", g.name(), t);
            }
        }
    }
}
