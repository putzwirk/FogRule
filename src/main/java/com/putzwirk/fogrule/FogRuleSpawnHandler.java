package com.putzwirk.fogrule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

import java.util.List;

import static com.putzwirk.fogrule.cozy.CozinessEngine.isInsideCozyZone;

@EventBusSubscriber(modid = FogRule.MODID)
public class FogRuleSpawnHandler {

    @SubscribeEvent
    public static void onSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        boolean isHostile = event.getEntity() instanceof Monster;
        if (!isHostile) return;

        BlockPos pos = new BlockPos((int)event.getX(), (int)event.getY(), (int)event.getZ());
        boolean isSafe = isInsideCozyZone(pos, level);

        if (isSafe) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            return;
        }

        if (level.getBrightness(LightLayer.SKY, pos) < 14) return;

        ResourceLocation entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        String entityIdString = entityKey.toString();
        List<? extends String> allowedMobs = FogRuleConfig.FOG_ALLOWED_MOBS.get();

        if (level.isDay()) {
            if (allowedMobs.contains(entityIdString)) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
                event.getEntity().getPersistentData().putBoolean("FogRule_ImmuneToSun", true);
            } else {
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            }
        }
    }

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        boolean isHostile = event.getEntityType().getCategory() == MobCategory.MONSTER;
        if (!isHostile) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        boolean isSafe = isInsideCozyZone(pos, level);

        if (isSafe) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            return;
        }

        if (level.getBrightness(LightLayer.SKY, pos) < 14) return;

        ResourceLocation entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType());
        String entityIdString = entityKey.toString();
        List<? extends String> allowedMobs = FogRuleConfig.FOG_ALLOWED_MOBS.get();

        if (level.isDay()) {
            if (allowedMobs.contains(entityIdString)) {
                int blockLightLimit = level.dimensionType().monsterSpawnBlockLightLimit();
                if (level.getBrightness(LightLayer.BLOCK, pos) <= blockLightLimit) {
                    event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
                } else {
                    event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
                }
            } else {
                event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            }
        }
    }
}