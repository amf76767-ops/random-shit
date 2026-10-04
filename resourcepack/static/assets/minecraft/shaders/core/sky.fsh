#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// DIH Visuals: Himmel mit Farbverlauf. Oben tiefer und blauer, am Horizont heller und weicher.
// SKY_GRADIENT = 0.0 ergibt wieder den flachen Vanilla-Himmel.
const float SKY_GRADIENT = 1.0;
// DIH Scenes (vom Modul Scenes gesetzt): Himmelsfarbe am Horizont und oben, erst malnehmen, dann dazuaddieren.
const vec3 SKY_MUL_H = vec3(1.0, 1.0, 1.0);
const vec3 SKY_MUL_Z = vec3(1.0, 1.0, 1.0);
const vec3 SKY_ADD_H = vec3(0.0, 0.0, 0.0);
const vec3 SKY_ADD_Z = vec3(0.0, 0.0, 0.0);

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec3 skyPosition;

out vec4 fragColor;

// DIH Visuals: Sternenhimmel in der Nacht (der Mond ist im Pack durchsichtig). STAR_AMOUNT = 0.0 schaltet ihn ab.
const float STAR_AMOUNT = 1.0;

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Weiche Punkte auf einem Raster von Richtungen: jede Zelle hat höchstens einen Stern an einer zufälligen Stelle.
float starLayer(vec3 dir, float cells, float chance, float size) {
    vec3 g = dir * cells;
    vec3 id = floor(g);
    float h = hash3(id);
    if (h < 1.0 - chance) {
        return 0.0;
    }
    vec3 centre = id + 0.25 + 0.5 * vec3(hash3(id + 1.7), hash3(id + 4.3), hash3(id + 9.1));
    float d = length(g - centre);
    float bright = 0.45 + 0.55 * fract(h * 97.0);
    return bright * smoothstep(size, 0.0, d);
}

vec3 starField(vec3 dir) {
    float s = starLayer(dir, 120.0, 0.050, 0.22) + starLayer(dir, 260.0, 0.030, 0.30) * 0.7;
    // ein schwacher, bläulich-violetter Streifen wie die Milchstraße
    vec3 axis = normalize(vec3(0.35, 0.25, 1.0));
    float band = exp(-pow(dot(dir, axis) / 0.16, 2.0));
    float dust = hash3(floor(dir * 90.0)) * 0.6 + hash3(floor(dir * 31.0)) * 0.4;
    vec3 milky = vec3(0.10, 0.09, 0.16) * band * (0.55 + 0.45 * dust);
    s += band * starLayer(dir, 420.0, 0.06, 0.32) * 0.8;
    vec3 tint = mix(vec3(0.80, 0.86, 1.0), vec3(1.0, 0.92, 0.80), hash3(floor(dir * 120.0) + 3.0));
    return tint * s + milky;
}

void main() {
    vec3 c = ColorModulator.rgb;
    float h = clamp(normalize(skyPosition).y, 0.0, 1.0);   // 0 = Horizont, 1 = Zenit
    float grey = dot(c, vec3(0.3333));
    vec3 horizon = mix(c, vec3(grey), 0.25) * 1.12 + vec3(0.03) * smoothstep(0.0, 0.3, grey);
    vec3 zenith = c * vec3(0.80, 0.88, 1.05);
    horizon = horizon * SKY_MUL_H + SKY_ADD_H;
    zenith = zenith * SKY_MUL_Z + SKY_ADD_Z;
    vec3 graded = mix(horizon, zenith, pow(h, 0.5));
    vec3 skyRgb = mix(c, graded, SKY_GRADIENT);
    // nur wenn der Himmel dunkel ist (Nacht), und zum Horizont hin ausgeblendet
    float night = 1.0 - smoothstep(0.03, 0.16, grey);
    float high = smoothstep(0.02, 0.25, h);
    skyRgb += starField(normalize(skyPosition)) * night * high * STAR_AMOUNT;
    vec4 base = vec4(clamp(skyRgb, 0.0, 1.0), ColorModulator.a);
    fragColor = apply_fog(base, sphericalVertexDistance, cylindricalVertexDistance, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);
}
