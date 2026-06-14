package com.putzwirk.fogrule.cozy;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CozinessRegistry {
    public static float getCozinessValue(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.MOSSY_COBBLESTONE) {
            return -0.1f;
        }

        if (block == Blocks.COBBLESTONE) {
            return 0.15f;
        }

        if (block == Blocks.DIRT_PATH) {
            return 0.2f;
        }

        if (block == Blocks.COBWEB) {
            return -5.0f;
        }

        if (block instanceof FlowerPotBlock potBlock) {
            if (potBlock.getPotted() == Blocks.DEAD_BUSH) {
                return -2.0f;
            }
            return 1.2f;
        }

        if (block instanceof TorchBlock || block instanceof LanternBlock) {
            return 1.0f;
        }

        if (block instanceof CampfireBlock) {
            boolean isLit = state.getOptionalValue(BlockStateProperties.LIT).orElse(false);
            return isLit ? 3.0f : 0.1f;
        }

        if (block instanceof WoolCarpetBlock) {
            return 0.8f;
        }

        return 0.1f;
    }
}