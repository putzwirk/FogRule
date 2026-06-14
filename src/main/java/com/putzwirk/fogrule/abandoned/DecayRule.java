package com.putzwirk.fogrule.abandoned;

import net.minecraft.world.level.block.state.BlockState;

// interface for decay rules of the blocks.
public interface DecayRule {
    boolean matches(BlockState state);

    // apply a decay
    Result apply(DecayContext ctx);

    // type of the result
    enum Result {
        MUTATED_KEEP, // block mutating in other block
        MUTATED_REMOVE, // block removing
        NO_CHANGE
    }
}