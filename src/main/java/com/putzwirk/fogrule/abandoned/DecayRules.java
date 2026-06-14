package com.putzwirk.fogrule.abandoned;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.putzwirk.fogrule.abandoned.DecayRule.Result;
import com.putzwirk.fogrule.cozy.ChunkCozinessData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class DecayRules {
    public static final long DELAY_MULTIPLIER = 1L;
    public static final float COZINESS_THRESHOLD = 35.0f;
    public static final List<DecayRule> RULES = new ArrayList<>();

    static {
        addRule(s -> s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COBWEB), ctx -> Result.NO_CHANGE);

        addRule(s -> s.getBlock() instanceof FlowerPotBlock p && p.getPotted() != Blocks.AIR && p.getPotted() != Blocks.DEAD_BUSH, ctx -> {
            if (ctx.elapsedUnits() < 250L) return Result.NO_CHANGE;
            var dead = ((FlowerPotBlock)ctx.state().getBlock()).getEmptyPot().getFullPotsView().get(BuiltInRegistries.BLOCK.getKey(Blocks.DEAD_BUSH));
            if (dead != null) { ctx.level().setBlock(ctx.pos(), dead.get().defaultBlockState(), 3); return Result.MUTATED_KEEP; }
            return Result.NO_CHANGE;
        });

        regMutate(s -> s.is(Blocks.COBBLESTONE), Blocks.MOSSY_COBBLESTONE, 500L, 10000L, 0.001f, Result.MUTATED_KEEP);

        addRule(s -> { String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath(); return id.contains("cobble") || s.getBlock() instanceof FenceBlock || id.endsWith("planks") || s.getBlock() instanceof WallBlock; }, ctx -> {
            if (ctx.elapsedUnits() >= 800L && ctx.abandonedCoziness() >= COZINESS_THRESHOLD) {
                var chunk = ctx.level().getChunkSource().getChunk(ctx.pos().getX() >> 4, ctx.pos().getZ() >> 4, false);
                if (chunk != null) {
                    ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                    if (ctx.random().nextFloat() < 0.02f) {
                        for (var dir : Direction.values()) {
                            var p = ctx.pos().relative(dir);
                            if (ctx.level().isEmptyBlock(p)) {
                                ctx.level().setBlock(p, Blocks.COBWEB.defaultBlockState(), 3);
                                break;
                            }
                        }
                    }
                }
            }
            return Result.NO_CHANGE;
        });

        regBreak(s -> s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE), 30L);
        regBreak(s -> s.getBlock() instanceof TorchBlock || s.getBlock() instanceof WallTorchBlock, 100L);
        regBreak(s -> s.getBlock() instanceof LanternBlock, 300L);
        regBreak(s -> s.is(Blocks.GLASS) || s.getBlock() instanceof IronBarsBlock || s.getBlock() instanceof StainedGlassBlock || s.getBlock() instanceof StainedGlassPaneBlock, 400L);
        regBreak(s -> s.is(Blocks.GLOWSTONE), 500L);
        regBreak(s -> s.is(Blocks.SHROOMLIGHT), 600L);

        addRule(s -> s.getBlock() instanceof DoorBlock, ctx -> {
            if (ctx.elapsedUnits() >= 400L && roll(ctx.random(), ctx.elapsedUnits(), 400L, 3000L, 0.002f)) {
                var p = ctx.pos();
                if (ctx.state().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) p = p.below();
                ctx.level().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                ctx.level().setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
                return Result.MUTATED_REMOVE;
            }
            return Result.NO_CHANGE;
        });
    }

    private static void addRule(Predicate<BlockState> match, java.util.function.Function<DecayContext, Result> apply) {
        RULES.add(new DecayRule() {
            public boolean matches(BlockState s) { return match.test(s); }
            public Result apply(DecayContext ctx) { return apply.apply(ctx); }
        });
    }

    private static void regBreak(Predicate<BlockState> match, long units) {
        addRule(match, ctx -> {
            if (ctx.elapsedUnits() >= units * DELAY_MULTIPLIER) {
                ctx.level().setBlock(ctx.pos(), Blocks.AIR.defaultBlockState(), 3);
                return Result.MUTATED_REMOVE;
            }
            return Result.NO_CHANGE;
        });
    }

    private static void regMutate(Predicate<BlockState> match, Block target, long base, long full, float chance, Result res) {
        addRule(match, ctx -> {
            if (ctx.elapsedUnits() >= base * DELAY_MULTIPLIER && roll(ctx.random(), ctx.elapsedUnits(), base * DELAY_MULTIPLIER, full * DELAY_MULTIPLIER, chance)) {
                ctx.level().setBlock(ctx.pos(), target.defaultBlockState(), 3);
                return res;
            }
            return Result.NO_CHANGE;
        });
    }

    private static boolean roll(RandomSource r, long elapsed, long base, long full, float chance) {
        if (elapsed >= full) return true;
        long active = elapsed - base;
        if (active <= 0) return false;
        return r.nextFloat() < 1.0f - (float) Math.pow(1.0f - chance, Math.min(active, 2000L));
    }
}