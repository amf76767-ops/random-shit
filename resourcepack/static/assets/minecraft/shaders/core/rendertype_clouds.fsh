#version 330

#moj_import <minecraft:fog.glsl>

// DIH Visuals: Wolken scheinen durch. CLOUD_ALPHA = 1.0 ergibt wieder die Vanilla-Wolken.
const float CLOUD_ALPHA = 0.55;

in float vertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor;
    color.a *= 1.0f - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    color.a *= CLOUD_ALPHA;
    fragColor = color;
}
