#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float ContrastGamma;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main()
{
    float coverage = texture(Sampler0, texCoord0).a;

    if (coverage < 0.004)
    {
        discard;
    }

    float gamma = max(0.5, ContrastGamma);
    float alpha = pow(coverage, gamma) * vertexColor.a * ColorModulator.a;

    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb, alpha);
}
