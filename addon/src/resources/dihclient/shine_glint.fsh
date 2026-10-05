#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

const float TWINKLE = @TWINKLE@;
const float LAYER = @LAYER@;
const float CHROMA = @CHROMA@;
const float PULSE = @PULSE@;
const float BOOST = @BOOST@;

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;

out vec4 fragColor;

float cell(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    float t = GameTime * 1200.0;
    vec2 uv = texCoord0;
    vec3 c;
    if (CHROMA > 0.0) {
        float o = CHROMA * 0.012;
        c = vec3(texture(Sampler0, uv + vec2(o, 0.0)).r, texture(Sampler0, uv).g, texture(Sampler0, uv - vec2(o, 0.0)).b);
    } else {
        c = texture(Sampler0, uv).rgb;
    }
    vec3 deep = texture(Sampler0, uv * 1.73 + vec2(0.31, -t * 0.02)).rgb;
    c = max(c, deep * LAYER);
    float tw = 1.0 + TWINKLE * (0.5 * sin(t * 0.55 + cell(floor(uv * 48.0)) * 6.2831853) + 0.25 * sin(t * 1.3 + cell(floor(uv * 96.0)) * 6.2831853));
    float pulse = 1.0 + PULSE * sin(t * 0.14);
    vec4 color = vec4(c * tw * pulse * BOOST, 1.0) * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    float fade = (1.0f - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd)) * GlintAlpha;
    fragColor = vec4(color.rgb * fade, color.a);
}
