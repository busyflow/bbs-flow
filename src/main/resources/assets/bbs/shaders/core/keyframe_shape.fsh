#version 150

uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float sdRoundedSquare(vec2 p, float r)
{
    vec2 q = abs(p) - vec2(1.0 - r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float sdTriangle(vec2 p)
{
    /* Upward-pointing triangle matching (0, -1), (-1, 1), (1, 1) in Y-down screen space */
    vec2 p0 = vec2(0.0, -1.0);
    vec2 p1 = vec2(-1.0, 1.0);
    vec2 p2 = vec2(1.0, 1.0);

    vec2 e0 = p1 - p0, e1 = p2 - p1, e2 = p0 - p2;
    vec2 v0 = p - p0, v1 = p - p1, v2 = p - p2;

    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);

    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(
        vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
        vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
        vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));

    return -sqrt(d.x) * sign(d.y);
}

float sdStar(vec2 p, float n, float innerRatio)
{
    /* Rotate so first tip points straight up (0, -1) in Y-down screen space */
    vec2 q = vec2(p.x, -p.y);
    float an = 3.14159265 / n;
    float en = an;
    float a = atan(q.x, q.y);
    float bn = mod(a + an, 2.0 * an) - an;
    vec2 pt = length(q) * vec2(abs(sin(bn)), cos(bn));

    vec2 tip = vec2(0.0, 1.0);
    vec2 valley = innerRatio * vec2(sin(en), cos(en));
    vec2 e = valley - tip;
    vec2 w = pt - tip;
    vec2 proj = w - e * clamp(dot(w, e) / dot(e, e), 0.0, 1.0);
    float dist = length(proj);
    float side = w.x * e.y - w.y * e.x;

    return side < 0.0 ? -dist : dist;
}

void main()
{
    float shapeId = floor((texCoord0.x + 5.0) / 10.0);
    vec2 p = vec2(texCoord0.x - shapeId * 10.0, texCoord0.y);

    float d;

    if (shapeId < 0.5)
    {
        /* 0: Square with subtle crisp rounding */
        d = sdRoundedSquare(p, 0.14);
    }
    else if (shapeId < 1.5)
    {
        /* 1: Diamond */
        d = (abs(p.x) + abs(p.y) - 1.0) * 0.70710678;
    }
    else if (shapeId < 2.5)
    {
        /* 2: Circle */
        d = length(p) - 1.0;
    }
    else if (shapeId < 3.5)
    {
        /* 3: Triangle */
        d = sdTriangle(p);
    }
    else if (shapeId < 4.5)
    {
        /* 4: 3-point star */
        d = sdStar(p, 3.0, 0.36);
    }
    else if (shapeId < 5.5)
    {
        /* 5: 4-point star */
        d = sdStar(p, 4.0, 0.38);
    }
    else if (shapeId < 6.5)
    {
        /* 6: 5-point star */
        d = sdStar(p, 5.0, 0.42);
    }
    else
    {
        /* 7: 6-point star */
        d = sdStar(p, 6.0, 0.46);
    }

    float px = max(length(vec2(dFdx(d), dFdy(d))), 0.001);
    float alpha = 1.0 - smoothstep(-0.5 * px, 0.5 * px, d);
    float finalAlpha = alpha * vertexColor.a * ColorModulator.a;

    if (finalAlpha < 0.004)
    {
        discard;
    }

    fragColor = vec4(vertexColor.rgb * ColorModulator.rgb, finalAlpha);
}
