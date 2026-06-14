package com.putzwirk.fogrule;

import java.util.ArrayList;
import java.util.List;

public class ClearZone {

    private final float cx;
    private final float cz;
    private final float radius;

    public ClearZone(float cx, float cz, float radius) {
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
    }

    private static final List<ClearZone> ZONES = new ArrayList<>();

    static {
        ZONES.add(new ClearZone(0f, 0f, FogHandler.SAFE_RADIUS));
    }

    public float distanceBeyond(float px, float pz) {
        float dx = px - cx;
        float dz = pz - cz;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        return dist - radius;
    }

    public static float computeDanger(float px, float pz, float fogFadeDistance) {
        float minDist = Float.MAX_VALUE;
        for (ClearZone zone : ZONES) {
            float d = zone.distanceBeyond(px, pz);
            if (d < minDist) minDist = d;
        }
        if (minDist <= 0f) return 0f;
        return Math.min(minDist / fogFadeDistance, 1f);
    }
}
