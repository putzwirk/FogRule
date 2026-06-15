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

        long elapsedTicks,

        long elapsedUnits,

        float abandonedCoziness,

        boolean wasCozy
) {}