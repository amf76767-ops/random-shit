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

void main() {
    vec3 c = ColorModulator.rgb;
    float h = clamp(normalize(skyPosition).y, 0.0, 1.0);   // 0 = Horizont, 1 = Zenit
    float grey = dot(c, vec3(0.3333));
    vec3 horizon = mix(c, vec3(grey), 0.25) * 1.12 + vec3(0.03) * smoothstep(0.0, 0.3, grey);
    vec3 zenith = c * vec3(0.80, 0.88, 1.05);
    horizon = horizon * SKY_MUL_H + SKY_ADD_H;
    zenith = zenith * SKY_MUL_Z + SKY_ADD_Z;
    vec3 graded = mix(horizon, zenith, pow(h, 0.5));
    vec4 base = vec4(clamp(mix(c, graded, SKY_GRADIENT), 0.0, 1.0), ColorModulator.a);
    fragColor = apply_fog(base, sphericalVertexDistance, cylindricalVertexDistance, 0.0, FogSkyEnd, FogSkyEnd, FogSkyEnd, FogColor);
}
