package com.putzwirk.fogrule;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@OnlyIn(Dist.CLIENT)
public class FogHandler {

    public static float currentDanger = 0f;
    public static float currentNight = 0f;

    public static float targetClearanceEnd = 32f;
    public static float currentClearanceEnd = 32f;

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        float px = (float) player.getX();
        float pz = (float) player.getZ();
        float blendSpeed = FogRuleConfig.BLEND_SPEED.get().floatValue();

        ClearZone.tick(blendSpeed);

        currentClearanceEnd = Mth.lerp(blendSpeed, currentClearanceEnd, targetClearanceEnd);

        float targetDanger = ClearZone.computeDanger(px, pz, (float) FogRuleConfig.MAX_FOG_FADE_DISTANCE.getAsDouble());
        currentDanger = Mth.lerp(blendSpeed, currentDanger, targetDanger);

        long time = mc.level.getDayTime() % 24000;
        float targetNight = 0f;
        if (time > 13000 && time < 23000) {
            if (time < 14000) targetNight = (time - 13000) / 1000f;
            else if (time > 22000) targetNight = (23000 - time) / 1000f;
            else targetNight = 1f;
        }
        currentNight = Mth.lerp(0.05f, currentNight, targetNight);

        pushUniforms(currentDanger, currentNight);
    }

    @SubscribeEvent
    public void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (currentDanger <= 0f) return;

        float t = currentDanger;
        float nightFactor = currentNight;

        float fogR = Mth.lerp(nightFactor, 0.5f, 0.02f);
        float fogG = Mth.lerp(nightFactor, 0.5f, 0.02f);
        float fogB = Mth.lerp(nightFactor, 0.5f, 0.04f);

        event.setRed(Mth.lerp(t, event.getRed(), fogR));
        event.setGreen(Mth.lerp(t, event.getGreen(), fogG));
        event.setBlue(Mth.lerp(t, event.getBlue(), fogB));
    }

    @SubscribeEvent
    public void onRenderFog(ViewportEvent.RenderFog event) {
        boolean isSkyPass = event.getMode() == FogRenderer.FogMode.FOG_SKY;
        float vanillaEnd = event.getFarPlaneDistance();

        if (isSkyPass) {
            float alternativeEnd = Mth.lerp(currentDanger, vanillaEnd, 4f);
            event.setNearPlaneDistance(0f);
            event.setFarPlaneDistance(alternativeEnd);
            event.setFogShape(FogShape.SPHERE);
        } else {
            float finalEnd = Math.min(currentClearanceEnd, vanillaEnd);
            float finalStart = finalEnd * 0.05f;
            event.setNearPlaneDistance(finalStart);
            event.setFarPlaneDistance(finalEnd);
        }
        event.setCanceled(true);
    }

    private static void pushUniforms(float danger, float night) {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) return;

        Uniform u1 = shader.getUniform("FogDangerLevel");
        if (u1 != null) u1.set(danger);

        Uniform u2 = shader.getUniform("NightTimeFactor");
        if (u2 != null) u2.set(night);
    }

    public static void updateClearance(float clearanceEnd) {
        targetClearanceEnd = clearanceEnd;
    }

    public static void updateCoziness(float cx, float cz, float radius) {
        ClearZone.setCozyZone(cx, cz, radius);
    }
}