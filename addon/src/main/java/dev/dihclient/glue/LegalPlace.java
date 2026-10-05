package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.autobuild.BuildRuntime;
import dev.dihclient.autobuild.PlacementSolver;
import dev.dihclient.modules.world.AutoBuild;
import dev.dihclient.setting.DoubleSetting;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_636;
import net.minecraft.class_746;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class LegalPlace {
    private static long lastNote;
    private static int refused;
    private static class_1269 refusedResult;

    private LegalPlace() {
    }

    public static class_1269 interact(class_636 manager, class_746 player, class_1268 hand, class_3965 hit) {
        if (!legal(player, hit)) {
            refused++;
            note();
            return refusedResult();
        }
        return manager.method_2896(player, hand, hit);
    }

    private static boolean legal(class_746 player, class_3965 hit) {
        class_310 mc = class_310.method_1551();
        if (player == null || hit == null || mc.field_1687 == null) {
            return false;
        }
        class_243 eye = player.method_33571();
        class_243 target = hit.method_17784();
        if (!LegalPlaceMath.inReach(target.field_1352 - eye.field_1352, target.field_1351 - eye.field_1351, target.field_1350 - eye.field_1350)) {
            return false;
        }
        double[] look = LegalPlaceMath.look(player.method_36454(), player.method_36455());
        double len = LegalPlaceMath.REACH + 0.5;
        class_243 end = eye.method_1031(look[0] * len, look[1] * len, look[2] * len);
        class_3965 ray = mc.field_1687.method_17742(new class_3959(eye, end, class_3960.field_17559, class_242.field_1348, player));
        if (ray == null || ray.method_17783() != class_240.field_1332) {
            return false;
        }
        class_243 at = ray.method_17784();
        return ray.method_17777().equals(hit.method_17777())
            && ray.method_17780() == hit.method_17780()
            && LegalPlaceMath.inReach(at.field_1352 - eye.field_1352, at.field_1351 - eye.field_1351, at.field_1350 - eye.field_1350);
    }

    private static class_1269 refusedResult() {
        if (refusedResult == null) {
            for (Field f : class_1269.class.getFields()) {
                try {
                    if (Modifier.isStatic(f.getModifiers()) && class_1269.class.isAssignableFrom(f.getType())) {
                        class_1269 r = (class_1269) f.get(null);
                        if (r != null && !r.method_23665()) {
                            refusedResult = r;
                            break;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {

                }
            }
        }
        if (refusedResult == null) {

            throw new IllegalStateException("no 'not accepted' constant found in class_1269");
        }
        return refusedResult;
    }

    private static void note() {
        long now = System.currentTimeMillis();
        if (now - lastNote > 5000) {
            lastNote = now;
            DIHClient.LOG.info("[DIHClient] AutoBuild: {} placements not sent because they would not have been legal", refused);
        }
    }

    public static void enforce(BuildRuntime.Settings s) {
        s.strict = true;
        s.airPlace = false;
        s.anyFace = false;
        s.legitFallback = false;
        s.giveCommand = false;
        s.reach = Math.min(s.reach, LegalPlaceMath.REACH);
        PlacementSolver.visibleOnly = true;
    }

    public static void prepare(AutoBuild build) throws ReflectiveOperationException {
        build.airPlace.visibleWhen(() -> false);
        build.legitFallback.visibleWhen(() -> false);
        build.giveCommand.visibleWhen(() -> false);
        build.giveCommand.set(false);
        build.visibleOnly.visibleWhen(() -> false);
        DoubleSetting reach = build.reach;
        Field max = DoubleSetting.class.getDeclaredField("max");
        max.setAccessible(true);
        max.setDouble(reach, LegalPlaceMath.REACH);
        reach.set(Math.min(reach.get(), LegalPlaceMath.REACH));

        dev.dihclient.setting.BoolSetting smart = new dev.dihclient.setting.BoolSetting("Smart Path",
                "Plans where to stand so the most blocks are in reach and walks there on a real path (around walls, steps, drops).", true);
        smart.onChange(() -> dev.dihclient.autobuild.BuildPilot.enabled = smart.get());
        java.lang.reflect.Method add = dev.dihclient.module.Module.class.getDeclaredMethod("add", dev.dihclient.setting.Setting.class);
        add.setAccessible(true);
        add.invoke(build, smart);
        dev.dihclient.autobuild.BuildPilot.enabled = smart.get();
    }
}
