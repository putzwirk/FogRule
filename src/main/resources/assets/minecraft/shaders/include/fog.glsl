#version 150

uniform float FogDangerLevel;
uniform float FogNightLevel;

// Combines vanilla's dynamic environment color with precise sky-occlusion metrics
vec3 fogrule_fogColor(vec4 vanillaFog) {
    vec3 vanillaColor = vanillaFog.rgb;

    // 1. Calculate raw brightness of the environment
    float vanillaBrightness = max(vanillaColor.r, max(vanillaColor.g, vanillaColor.b));

    // 2. Identify cave presence using vanilla's alpha/occlusion channel.
    float caveFactor = smoothstep(0.01, 0.18, vanillaBrightness * vanillaFog.a);

    // 3. Keep your original, beautiful overworld colors completely intact when on the surface
    vec3 day   = vec3(0.92, 0.94, 0.90) * caveFactor;
    vec3 night = vec3(0.03, 0.04, 0.08);

    vec3 targetColor = mix(day, night, FogNightLevel);

    // Smoothly blend to pitch black if caveFactor approaches 0 (inside any cave, regardless of Y level)
    return mix(vanillaColor * caveFactor, targetColor, clamp(vanillaBrightness * 2.0, 0.0, 1.0));
}

vec3 desaturate(vec3 color, float amount) {
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    return mix(color, vec3(luma), amount);
}

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    // Pass the entire vec4 (including the critical .a channel) to evaluate cave status
    vec3 baseFog = fogrule_fogColor(fogColor);

    if (FogDangerLevel <= 0.0) {
        if (vertexDistance <= fogStart) return inColor;
        float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
        return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
    }

    // Global ambient desaturation — applied to ALL fragments regardless of distance
    vec3 ambient = desaturate(inColor.rgb, 0.72 * FogDangerLevel);

    // Suppress ambient glowing tints inside shallow and deep caves using the sky-occlusion coefficient
    float vanillaBrightness = max(fogColor.r, max(fogColor.g, fogColor.b));
    float caveSuppression = smoothstep(0.02, 0.22, vanillaBrightness * fogColor.a);

    ambient = mix(ambient, baseFog * 0.9, FogDangerLevel * 0.08 * caveSuppression);

    if (vertexDistance <= fogStart) {
        return vec4(ambient, inColor.a);
    }

    // For distant geometry: additionally darken and dissolve into fog
    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    vec3 darkened = ambient * mix(1.0, 0.28, fogValue * FogDangerLevel);
    vec3 activeFogColor = mix(fogColor.rgb, baseFog, FogDangerLevel);

    // --- OPAQUE ATMOSPHERIC WALL FIX ---
    // Instead of using vanilla's fogColor.a (which can let the sky bleed through),
    // we smoothly force the fog opacity to 1.0 as FogDangerLevel increases.
    // This creates a solid curtain of fog that completely blocks the background star field.
    float activeAlpha = mix(fogColor.a, 1.0, FogDangerLevel * smoothstep(0.1, 0.9, fogValue));

    return vec4(mix(darkened, activeFogColor, fogValue * activeAlpha), inColor.a);
}

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) return 1.0;
    if (vertexDistance >= fogEnd)   return 0.0;
    return smoothstep(fogEnd, fogStart, vertexDistance);
}

float cylindrical_distance(mat4 modelViewMat, vec3 pos) {
    float distXZ = length((modelViewMat * vec4(pos.x, 0.0, pos.z, 1.0)).xyz);
    float distY  = length((modelViewMat * vec4(0.0, pos.y, 0.0, 1.0)).xyz);
    return max(distXZ, distY);
}

float fog_distance(vec3 pos, int shape) {
    if (shape == 0) {
        return length(pos);
    } else {
        return length(pos.xz);
    }
}