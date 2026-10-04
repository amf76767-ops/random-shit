package dev.dihclient.port.trident;

import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.DoubleSetting;

/**
 * Ported from an open-source client (GPL-3.0).
 * Use tridents out of water and charge them faster. The work is done by {@code TridentRiptideMixin}, which reads these settings.
 */
public class TridentUtilModule extends Module {
    public final DoubleSetting chargeScale = this.dbl("Charge Scale", "Charge time compared to vanilla (10 ticks). 0 = as fast as possible (1 tick).", 0.0, 0.0, 1.0, 0.1);
    public final BoolSetting noWater = this.bool("No Water", "Lets you throw or riptide a trident without water or rain (this client only).", true);

    public TridentUtilModule() {
        super("Trident Util", Category.DONUT, "Use tridents out of water and charge them faster.");
    }
}
