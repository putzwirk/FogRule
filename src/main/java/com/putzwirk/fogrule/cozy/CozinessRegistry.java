package com.putzwirk.fogrule.cozy;

import com.putzwirk.fogrule.FogRuleConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CozinessRegistry {
    public static float getCozinessValue(BlockState state) {
        Block block = state.getBlock();

        if (block instanceof FlowerPotBlock potBlock) {
            if (potBlock.getPotted() == Blocks.DEAD_BUSH) {
                return -2.0f;
            }
            if (potBlock.getPotted() == Blocks.AIR) {
                return 0.1f;
            }
            return 1.2f;
        }

        if (block instanceof CampfireBlock) {
            boolean isLit = state.getOptionalValue(BlockStateProperties.LIT).orElse(false);
            return isLit ? 3.0f : 0.1f;
        }

        for (String entry : FogRuleConfig.BLOCK_COZINESS_VALUES.get()) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length != 2) continue;
            try {
                if (matchesSelector(parts[0], state)) {
                    return Float.parseFloat(parts[1]);
                }
            } catch (NumberFormatException ignored) {}
        }

        return 0.1f;
    }

    public static boolean matchesSelector(String selector, BlockState state) {
        if (selector.startsWith("#")) {
            try {
                ResourceLocation loc = ResourceLocation.parse(selector.substring(1));
                TagKey<Block> tag = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, loc);
                return state.is(tag);
            } catch (Exception e) {
                return false;
            }
        }
        try {
            ResourceLocation loc = ResourceLocation.parse(selector);
            Block expected = BuiltInRegistries.BLOCK.get(loc);
            return state.getBlock() == expected;
        } catch (Exception e) {
            return false;
        }
    }
}