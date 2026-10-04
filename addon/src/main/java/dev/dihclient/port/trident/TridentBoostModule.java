package dev.dihclient.port.trident;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;

/**
 * Ported from an open-source client (GPL-3.0).
 * Boosts you when using riptide with a trident. The work is done by {@code TridentRiptideMixin}, which reads these settings.
 */
public class TridentBoostModule extends Module {
    public final DoubleSetting boost = this.dbl("Boost", "Multiplier for the riptide launch speed. 1 = vanilla.", 2.0, 0.1, 10.0, 0.1);
    public final BoolSetting outOfWater = this.bool("Out Of Water", "Lets the riptide work without water or rain (this client only).", true);

    public TridentBoostModule() {
        super("Trident Boost", Category.DONUT, "Boosts you when using riptide with a trident.");
    }
}
