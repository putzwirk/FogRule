package com.putzwirk.fogrule.abandoned;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public record DecayContext(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        RandomSource random,

        // attribute - how many ticks elapsed since last chunk visit
        long elapsedTicks,

        // elapsedTicks * DECAY_MULTIPLIER
        long elapsedUnits,

        // coziness of the chunk before it was abandoned
        float abandonedCoziness,

        // if coziness was > then threshold
        boolean wasCozy
) {}