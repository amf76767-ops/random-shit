package dev.dihclient.glue;

import dev.dihclient.DIHClient;
import dev.dihclient.module.Category;
import dev.dihclient.module.Module;
import dev.dihclient.modules.fun.FakeWeather;
import dev.dihclient.setting.BoolSetting;
import dev.dihclient.setting.EnumSetting;
import dev.dihclient.util.Notifications;

public class Scenes extends Module {
    public enum Scene {
        VANILLA("Vanilla", null, null, Look.NONE, true),
        COZY("Cozy", 12700, FakeWeather.Weather.CLEAR, new Look("cozy", Look.rgb(1.0f, 0.90f, 0.74f), Look.rgb(1.25f, 0.85f, 0.55f),
                Look.rgb(0.95f, 0.75f, 0.85f), Look.rgb(0.22f, 0.08f, 0f), Look.rgb(0.05f, 0f, 0.06f), Look.rgb(1.3f, 0.9f, 0.75f), Look.rgb(0.08f, 0.03f, 0f)), true),
        CYBER("Cyber", 18000, FakeWeather.Weather.CLEAR, new Look("cyber", Look.rgb(0.85f, 0.9f, 1.0f), Look.rgb(1.0f, 1.0f, 1.0f),
                Look.rgb(1.0f, 1.0f, 1.0f), Look.rgb(0.62f, 0.05f, 0.72f), Look.rgb(0.0f, 0.08f, 0.30f), Look.rgb(1.0f, 1.0f, 1.0f), Look.rgb(0.35f, 0.04f, 0.45f)), true),
        HORROR("Horror", 14600, FakeWeather.Weather.THUNDER, new Look("horror", Look.rgb(0.62f, 0.78f, 0.70f), Look.rgb(0.55f, 0.6f, 0.55f),
                Look.rgb(0.4f, 0.45f, 0.45f), Look.rgb(0.28f, 0.0f, 0.0f), Look.rgb(0.0f, 0.02f, 0.02f), Look.rgb(0.45f, 0.5f, 0.5f), Look.rgb(0f, 0f, 0f)), false),
        WINTER("Winter", 5000, FakeWeather.Weather.SNOW, new Look("winter", Look.rgb(0.90f, 0.97f, 1.0f), Look.rgb(0.9f, 0.97f, 1.1f),
                Look.rgb(0.85f, 0.95f, 1.12f), Look.rgb(0.05f, 0.08f, 0.12f), Look.rgb(0f, 0.02f, 0.06f), Look.rgb(0.95f, 1.0f, 1.1f), Look.rgb(0.05f, 0.06f, 0.08f)), true),
        DREAMY("Dreamy", 1500, FakeWeather.Weather.CLEAR, new Look("dreamy", Look.rgb(1.0f, 0.95f, 1.0f), Look.rgb(1.1f, 0.9f, 1.1f),
                Look.rgb(1.0f, 0.9f, 1.15f), Look.rgb(0.25f, 0.10f, 0.20f), Look.rgb(0.10f, 0.06f, 0.20f), Look.rgb(1.2f, 1.0f, 1.2f), Look.rgb(0.10f, 0.04f, 0.10f)), true),
        SUNNY("Sunny", 6000, FakeWeather.Weather.CLEAR, new Look("sunny", Look.rgb(1.0f, 0.99f, 0.93f), Look.rgb(1.0f, 1.0f, 1.0f),
                Look.rgb(0.8f, 0.98f, 1.3f), Look.rgb(0f, 0f, 0f), Look.rgb(0f, 0f, 0f), Look.rgb(1.0f, 1.0f, 1.0f), Look.rgb(0f, 0f, 0f)), true);

        final String title;
        final Integer time;
        final FakeWeather.Weather weather;
        final Look look;

        final boolean fullbright;

        Scene(String title, Integer time, FakeWeather.Weather weather, Look look, boolean fullbright) {
            this.title = title;
            this.time = time;
            this.weather = weather;
            this.look = look;
            this.fullbright = fullbright;
        }
    }

    public final EnumSetting<Scene> scene = this.mode("Scene",
            "Vanilla · Cozy (golden evening) · Cyber (neon night) · Horror (dark, thunder) · Winter (snow) · Dreamy (pastel morning) · Sunny (deep blue noon).",
            Scene.VANILLA).onChange(this::apply);
    public final BoolSetting applyLook = this.bool("Colours", "Sky, clouds and light colour (needs the VisualPack, it is switched on for you).", true).onChange(this::apply);
    public final BoolSetting applyLight = this.bool("Light", "Horror turns Fullbright off so it is really dark, the others turn it on.", true).onChange(this::apply);
    public final BoolSetting applyTime = this.bool("Time", "Sets the time of day with Fake Time.", true).onChange(this::apply);
    public final BoolSetting applyWeather = this.bool("Weather", "Sets the weather with Fake Weather.", true).onChange(this::apply);

    private boolean dirty;
    private boolean timeByUs;
    private boolean weatherByUs;
    private boolean packByUs;

    public Scenes() {
        super("Scenes", Category.RENDER, "One switch for the mood of the game: sky, clouds, light colour, time of day and weather. Only on your screen.");
    }

    @Override
    protected void onEnable() {
        this.dirty = true;
    }

    @Override
    protected void onDisable() {
        this.dirty = false;
        this.release();
    }

    @Override
    public String getInfo() {
        return this.scene.get().title;
    }

    @Override
    public void onTick() {
        if (this.dirty && mc.field_1724 != null) {
            this.dirty = false;
            this.run();
        }
    }

    private void apply() {
        this.dirty = true;
    }

    private void release() {
        VisualPack pack = VisualPack.instance();
        if (pack != null) {
            pack.setLook(Look.NONE);
        }
        FakeTime time = DIHClient.modules().get(FakeTime.class);
        if (this.timeByUs && time != null && time.isEnabled()) {
            time.setEnabledSilently(false);
        }
        FakeWeather weather = DIHClient.modules().get(FakeWeather.class);
        if (this.weatherByUs && weather != null && weather.isEnabled()) {
            weather.setEnabledSilently(false);
        }
        this.timeByUs = this.weatherByUs = false;
    }

    private void run() {
        try {
            Scene s = this.scene.get();
            VisualPack pack = VisualPack.instance();
            if (pack != null && this.applyLook.get()) {
                if (s != Scene.VANILLA && !pack.isEnabled()) {
                    pack.setEnabled(true);
                    this.packByUs = true;
                }
                pack.setLook(s.look);
                if (this.applyLight.get()) {
                    pack.fullbright.set(s.fullbright);
                }
            } else if (pack != null) {
                pack.setLook(Look.NONE);
            }

            FakeTime time = DIHClient.modules().get(FakeTime.class);
            if (time != null) {
                if (this.applyTime.get() && s.time != null) {
                    time.mode.set(FakeTime.Mode.FIXED);
                    time.time.set(s.time);
                    if (!time.isEnabled()) {
                        time.setEnabledSilently(true);
                        this.timeByUs = true;
                    }
                } else if (this.timeByUs && time.isEnabled()) {
                    time.setEnabledSilently(false);
                    this.timeByUs = false;
                }
            }
            FakeWeather weather = DIHClient.modules().get(FakeWeather.class);
            if (weather != null) {
                if (this.applyWeather.get() && s.weather != null) {
                    weather.weather.set(s.weather);
                    if (!weather.isEnabled()) {
                        weather.setEnabledSilently(true);
                        this.weatherByUs = true;
                    }
                } else if (this.weatherByUs && weather.isEnabled()) {
                    weather.setEnabledSilently(false);
                    this.weatherByUs = false;
                }
            }
            Notifications.info(this.name(), s.title);
        } catch (Throwable t) {
            DIHClient.LOG.warn("[DIHClient] Scenes failed", t);
            Notifications.warn(this.name(), "Failed: " + t.getMessage());
        }
    }
}
