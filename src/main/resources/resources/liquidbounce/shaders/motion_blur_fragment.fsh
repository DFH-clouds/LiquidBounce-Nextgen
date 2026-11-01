#version 150

uniform sampler2D InSampler;
uniform float BlurAmount;
uniform int Samples;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    if (BlurAmount <= 0.001 || Samples < 2) {
        fragColor = texture(InSampler, texCoord);
        return;
    }

    vec2 center = vec2(0.5, 0.5);
    vec2 dir = texCoord - center;
    float radius = BlurAmount * 0.08;

    vec4 result = vec4(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < Samples; i++) {
        float t = float(i) / float(Samples - 1);
        float offset = t * radius;
        float weight = 1.0 - 0.3 * abs(t - 0.5) * 2.0;
        vec2 sampleUV = clamp(texCoord + dir * offset, 0.0, 1.0);
        result += texture(InSampler, sampleUV) * weight;
        totalWeight += weight;
    }

    fragColor = result / totalWeight;
}
