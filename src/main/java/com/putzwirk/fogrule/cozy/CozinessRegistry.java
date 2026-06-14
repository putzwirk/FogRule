package com.putzwirk.fogrule.cozy;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public class CozinessRegistry {
    public static float getCozinessValue(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.LAVA || block == Blocks.NETHERRACK || block == Blocks.MOSSY_COBBLESTONE) {
            return -1.0f;
        }

        if (block == Blocks.COBWEB) {
            return -5.0f;
        }

        if (block instanceof FlowerPotBlock potBlock) {
            if (potBlock.getPotted() == Blocks.DEAD_BUSH) {
                return -2.0f;
            }
            return 0.9f;
        }

        if (block instanceof TorchBlock || block instanceof LanternBlock || block instanceof CampfireBlock) {
            return 1.0f;
        }

        if (block instanceof WoolCarpetBlock) {
            return 0.9f;
        }

        return 0.1f;
    }
}