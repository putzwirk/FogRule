package com.putzwirk.fogrule.abandoned;

import com.putzwirk.fogrule.FogRuleConfig;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.putzwirk.fogrule.abandoned.DecayRule.Result;
import com.putzwirk.fogrule.cozy.ChunkCozinessData;
import com.putzwirk.fogrule.cozy.CozinessEngine;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class DecayRules {
    public static long DELAY_MULTIPLIER = 1L;
    public static float COZINESS_THRESHOLD = 35.0f;

    public static final List<DecayRule> RULES = new ArrayList<>();

    public static void reload() {
        RULES.clear();
        DELAY_MULTIPLIER = FogRuleConfig.DELAY_MULTIPLIER.get();

        for (String entry : FogRuleConfig.DECAY_RULES.get()) {
            String[] parts = entry.trim().split("\\s+");
            if (parts.length < 1) continue;
            DecayRule rule = parseRule(parts);
            if (rule != null) RULES.add(rule);
        }

        addRule(s -> s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COBWEB), ctx -> Result.NO_CHANGE);

        addRule(s -> s.getBlock() instanceof FlowerPotBlock p && p.getPotted() != Blocks.AIR && p.getPotted() != Blocks.DEAD_BUSH, ctx -> {
            if (ctx.elapsedUnits() < 250L) return Result.NO_CHANGE;
            var dead = ((FlowerPotBlock)ctx.state().getBlock()).getEmptyPot().getFullPotsView().get(BuiltInRegistries.BLOCK.getKey(Blocks.DEAD_BUSH));
            if (dead != null) { ctx.level().setBlock(ctx.pos(), dead.get().defaultBlockState(), 3); return Result.MUTATED_KEEP; }
            return Result.NO_CHANGE;
        });

        addRule(s -> s.getBlock() instanceof CampfireBlock && s.getOptionalValue(BlockStateProperties.LIT).orElse(false), ctx -> {
            if (ctx.elapsedUnits() >= 300L * DELAY_MULTIPLIER) {
                BlockState extinguishedState = ctx.state().setValue(BlockStateProperties.LIT, false);
                ctx.level().setBlock(ctx.pos(), extinguishedState, 3);

                LevelChunk chunk = ctx.level().getChunkSource().getChunk(ctx.pos().getX() >> 4, ctx.pos().getZ() >> 4, false);
                if (chunk != null) {
                    ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                    CozinessEngine.recalculateCoziness(ctx.level(), chunk, data);
                }
                return Result.MUTATED_KEEP;
            }
            return Result.NO_CHANGE;
        });;

        addRule(s -> { String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath(); return id.contains("cobble") || s.getBlock() instanceof FenceBlock || id.endsWith("planks") || s.getBlock() instanceof WallBlock; }, ctx -> {
            if (ctx.elapsedUnits() >= 800L && ctx.abandonedCoziness() >= COZINESS_THRESHOLD) {
                var chunk = ctx.level().getChunkSource().getChunk(ctx.pos().getX() >> 4, ctx.pos().getZ() >> 4, false);
                if (chunk != null) {
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

    private static DecayRule parseRule(String[] parts) {
        return switch (parts[0].toUpperCase()) {
            case "BREAK"  -> parseBreak(parts);
            case "MUTATE" -> parseMutate(parts);
            default       -> null;
        };
    }

    private static DecayRule parseBreak(String[] parts) {
        if (parts.length < 3) return null;
        Predicate<BlockState> sel = selectorPredicate(parts[1]);
        if (sel == null) return null;
        long units;
        try { units = Long.parseLong(parts[2]); } catch (NumberFormatException e) { return null; }
        final long threshold = units;
        return addRuleReturn(sel, ctx -> {
            if (ctx.elapsedUnits() >= threshold * DELAY_MULTIPLIER) {
                ctx.level().setBlock(ctx.pos(), Blocks.AIR.defaultBlockState(), 3);
                return Result.MUTATED_REMOVE;
            }
            return Result.NO_CHANGE;
        });
    }

    private static DecayRule parseMutate(String[] parts) {
        if (parts.length < 6) return null;
        Predicate<BlockState> sel = selectorPredicate(parts[1]);
        if (sel == null) return null;
        Block target;
        try {
            target = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(parts[2]));
        } catch (Exception e) { return null; }
        long base, full;
        float chance;
        try {
            base   = Long.parseLong(parts[3]);
            full   = Long.parseLong(parts[4]);
            chance = Float.parseFloat(parts[5]);
        } catch (NumberFormatException e) { return null; }
        final Block finalTarget = target;
        return addRuleReturn(sel, ctx -> {
            if (ctx.elapsedUnits() >= base * DELAY_MULTIPLIER
                    && roll(ctx.random(), ctx.elapsedUnits(), base * DELAY_MULTIPLIER, full * DELAY_MULTIPLIER, chance)) {
                ctx.level().setBlock(ctx.pos(), finalTarget.defaultBlockState(), 3);
                return Result.MUTATED_KEEP;
            }
            return Result.NO_CHANGE;
        });
    }

    private static Predicate<BlockState> selectorPredicate(String selector) {
        if (selector.startsWith("#")) {
            try {
                ResourceLocation loc = ResourceLocation.parse(selector.substring(1));
                TagKey<Block> tag = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, loc);
                return s -> s.is(tag);
            } catch (Exception e) { return null; }
        }
        try {
            ResourceLocation loc = ResourceLocation.parse(selector);
            final Block b = BuiltInRegistries.BLOCK.get(loc);
            return s -> s.getBlock() == b;
        } catch (Exception e) { return null; }
    }

    private static void addRule(Predicate<BlockState> match, java.util.function.Function<DecayContext, Result> apply) {
        RULES.add(new DecayRule() {
            public boolean matches(BlockState s) { return match.test(s); }
            public Result apply(DecayContext ctx) { return apply.apply(ctx); }
        });
    }

    private static DecayRule addRuleReturn(Predicate<BlockState> match, java.util.function.Function<DecayContext, Result> apply) {
        return new DecayRule() {
            public boolean matches(BlockState s) { return match.test(s); }
            public Result apply(DecayContext ctx) { return apply.apply(ctx); }
        };
    }

    private static boolean roll(RandomSource r, long elapsed, long base, long full, float chance) {
        if (elapsed >= full) return true;
        long active = elapsed - base;
        if (active <= 0) return false;
        return r.nextFloat() < 1.0f - (float) Math.pow(1.0f - chance, Math.min(active, 2000L));
    }
}