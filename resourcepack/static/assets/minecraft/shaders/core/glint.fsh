#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// DIH Visuals: Verzauberungsglanz wechselt langsam die Farbe. GLINT_SPEED = 0.0 stellt das ab.
const float GLINT_SPEED = 60.0;   // volle Farbkreise pro Minecraft-Tag (20 Minuten), also einer alle 20 Sekunden
const float GLINT_BOOST = 1.3;

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;

out vec4 fragColor;

// dreht eine Farbe um die Graustufen-Achse
vec3 hueRotate(vec3 c, float a) {
    const vec3 k = vec3(0.57735026);
    float s = sin(a);
    float co = cos(a);
    return c * co + cross(k, c) * s + k * dot(k, c) * (1.0 - co);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    float angle = GameTime * GLINT_SPEED * 6.2831853 + (texCoord0.x + texCoord0.y) * 1.5;
    color.rgb = max(hueRotate(color.rgb, angle), vec3(0.0)) * GLINT_BOOST;
    float fade = (1.0f - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd)) * GlintAlpha;
    fragColor = vec4(color.rgb * fade, color.a);
}
