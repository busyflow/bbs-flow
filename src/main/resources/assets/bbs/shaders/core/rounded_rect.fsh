#version 150

uniform vec4 ColorModulator;
uniform vec2 RectSize;
uniform float CornerRadius;
uniform float BorderWidth;
uniform float Feather;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float sdRoundedBox(vec2 p, vec2 b, float r)
{
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main()
{
    vec2 halfSize = RectSize * 0.5;
    float r = clamp(CornerRadius, 0.0, min(halfSize.x, halfSize.y));
    vec2 p = texCoord0 - halfSize;
    float d = sdRoundedBox(p, halfSize, r);

    float aa = max(fwidth(d), 0.001);
    float alpha;

    if (Feather > 0.0)
    {
        float t = clamp(1.0 - smoothstep(-aa, Feather, d), 0.0, 1.0);
        alpha = t * t;
    }
    else if (BorderWidth > 0.0)
    {
        float outer = 1.0 - smoothstep(-aa, aa, d);
        float inner = 1.0 - smoothstep(-aa, aa, d + BorderWidth);
        alpha = clamp(outer - inner, 0.0, 1.0);
    }
    else
    {
        alpha = 1.0 - smoothstep(-aa, aa, d);
    }

    float finalAlpha = alpha * vertexColor.a * ColorModulator.a;

    if (finalAlpha < 0.003)
    {
        discard;
    }

    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb, finalAlpha);
}
