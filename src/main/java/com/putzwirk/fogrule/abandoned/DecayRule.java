package com.putzwirk.fogrule.abandoned;

import net.minecraft.world.level.block.state.BlockState;

public interface DecayRule {
    boolean matches(BlockState state);

    Result apply(DecayContext ctx);

    enum Result {
        MUTATED_KEEP,
        MUTATED_REMOVE,
        NO_CHANGE
    }
}