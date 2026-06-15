package com.putzwirk.fogrule;

import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.List;

public class ClearZone {

    private final float cx;
    private final float cz;
    private float targetRadius;
    private float currentRadius;

    public ClearZone(float cx, float cz, float radius) {
        this.cx = cx;
        this.cz = cz;
        this.targetRadius = radius;
        this.currentRadius = radius; // start instant on first load
    }

    private static final List<ClearZone> ZONES = new ArrayList<>(); //

    // Updates or adds the target radius sent by the server
    public static void setCozyZone(float cx, float cz, float radius) {
        if (ZONES.isEmpty()) {
            ZONES.add(new ClearZone(cx, cz, radius));
        } else {
            ClearZone zone = ZONES.get(0);
            // If the center changed drastically (e.g. teleport/chunk cross), snap it
            if (zone.cx != cx || zone.cz != cz) {
                ZONES.clear();
                ZONES.add(new ClearZone(cx, cz, radius));
            } else {
                zone.targetRadius = radius;
            }
        }
    }

    // NEW: Lerps the radius smoothly toward its target frame-by-frame
    public static void tick(float blendSpeed) {
        for (ClearZone zone : ZONES) {
            zone.currentRadius = Mth.lerp(blendSpeed, zone.currentRadius, zone.targetRadius);
        }
    }

    public float distanceBeyond(float px, float pz) { //
        float dx = px - cx; //
        float dz = pz - cz; //
        float dist = (float) Math.sqrt(dx * dx + dz * dz); //
        // CHANGED: Use currentRadius so the visual edge moves smoothly
        return dist - currentRadius;
    } //

    public static float computeDanger(float px, float pz, float fogFadeDistance) { //
        float minDist = Float.MAX_VALUE; //
        for (ClearZone zone : ZONES) { //
            float d = zone.distanceBeyond(px, pz); //
            if (d < minDist) minDist = d; //
        } //
        if (minDist <= 0f) return 0f; //
        return Math.min(minDist / fogFadeDistance, 1f); //
    } //
}