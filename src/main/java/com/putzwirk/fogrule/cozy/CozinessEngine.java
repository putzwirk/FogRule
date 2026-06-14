package com.putzwirk.fogrule.cozy;

import com.putzwirk.fogrule.FogRule;
import com.putzwirk.fogrule.abandoned.DecayContext;
import com.putzwirk.fogrule.abandoned.DecayRule;
import com.putzwirk.fogrule.abandoned.DecayRules;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = FogRule.MODID)
public class CozinessEngine {

    public static final float MINIMUM_COZINESS_THRESHOLD = 35.0f;
    private static final long DECAY_TRIGGER_TICKS = 240L;

    private static final Deque<PendingDecay> pendingDecayQueue = new ArrayDeque<>();
    private static final Set<ChunkPos> queuedDecayPositions = new HashSet<>();

    private record PendingDecay(ChunkPos pos, long elapsedTicks, float abandonedCoziness) {}

    private static int getBlockTypeCap(Block block) {
        if (block instanceof TorchBlock || block instanceof WallTorchBlock) return 10;
        if (block instanceof LanternBlock) return 8;
        if (block instanceof CampfireBlock) return 2;
        if (block instanceof WoolCarpetBlock) return 16;
        if (block instanceof FlowerPotBlock) return 6;
        return 200;
    }

    public static int forceTimeSkipForPlayer(ServerPlayer player, long simulatedTicksPassed) {
        BlockPos pos = player.blockPosition();
        ServerLevel world = player.serverLevel();
        int playerChunkX = pos.getX() >> 4;
        int playerChunkZ = pos.getZ() >> 4;
        int chunksAltered = 0;
        long currentTime = world.getGameTime();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                LevelChunk chunk = world.getChunkSource().getChunk(playerChunkX + dx, playerChunkZ + dz, false);
                if (chunk == null) continue;

                ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                if (data.getPackedPositions().isEmpty()) continue;

                ChunkPos cPos = chunk.getPos();
                long simulatedLastVisited = currentTime - simulatedTicksPassed;
                data.setLastVisitedTime(simulatedLastVisited);

                if (simulatedTicksPassed >= DECAY_TRIGGER_TICKS && !queuedDecayPositions.contains(cPos)) {
                    pendingDecayQueue.add(new PendingDecay(cPos, simulatedTicksPassed, data.getAbandonedCoziness()));
                    queuedDecayPositions.add(cPos);
                }
                chunk.setUnsaved(true);
                chunksAltered++;
            }
        }
        return chunksAltered;
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;

        LevelAccessor level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        BlockState placedState = event.getPlacedBlock();

        LevelChunk chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
        if (chunk == null) return;

        ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
        int packedPos = ChunkCozinessData.packPos(pos);

        if (data.getPackedPositions().add(packedPos)) {
            Block block = placedState.getBlock();
            int activeCount = data.getBlockCount(block);

            if (activeCount < getBlockTypeCap(block)) {
                float addition = CozinessRegistry.getCozinessValue(placedState);
                data.setCoziness(data.getCoziness() + addition);
            }

            data.incrementBlockCount(block);
            data.setAbandonedCoziness(data.getCoziness());
            data.setLastVisitedTime(serverLevel.getGameTime());
            chunk.setUnsaved(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        BlockState stateBefore = serverLevel.getBlockState(pos);

        if (stateBefore.getBlock() instanceof FlowerPotBlock) {
            serverLevel.getServer().execute(() -> {
                BlockState stateAfter = serverLevel.getBlockState(pos);

                if (stateAfter.getBlock() instanceof FlowerPotBlock) {
                    LevelChunk chunk = serverLevel.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
                    if (chunk == null) return;

                    ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                    int packedPos = ChunkCozinessData.packPos(pos);

                    data.getPackedPositions().add(packedPos);
                    rebuildChunkCozinessMetrics(serverLevel, chunk, data);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        LevelAccessor level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        BlockState brokenState = event.getState();

        LevelChunk chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
        if (chunk == null) return;

        ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
        int packedPos = ChunkCozinessData.packPos(pos);
        float value = CozinessRegistry.getCozinessValue(brokenState);

        if (data.getPackedPositions().contains(packedPos)) {
            Block block = brokenState.getBlock();
            data.decrementBlockCount(block);
            int remainingCount = data.getBlockCount(block);

            if (remainingCount < getBlockTypeCap(block)) {
                data.setCoziness(Math.max(0.0f, data.getCoziness() - value));
            }

            data.getPackedPositions().remove(packedPos);
            data.setAbandonedCoziness(data.getCoziness());
            data.setLastVisitedTime(serverLevel.getGameTime());
            chunk.setUnsaved(true);
        }
        else if (value < 0.0f) {
            data.setCoziness(data.getCoziness() - value);
            data.setAbandonedCoziness(data.getCoziness());
            data.setLastVisitedTime(serverLevel.getGameTime());
            chunk.setUnsaved(true);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) return;

        Set<LevelChunk> affectedChunks = new HashSet<>();

        for (BlockPos pos : event.getAffectedBlocks()) {
            LevelChunk chunk = serverLevel.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
            if (chunk == null) continue;

            ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
            int packedPos = ChunkCozinessData.packPos(pos);

            if (data.getPackedPositions().contains(packedPos)) {
                affectedChunks.add(chunk);
            }
        }

        // Defer metric cleanup to the next server tick execution frame to allow the explosion to finish clearing blocks safely
        if (!affectedChunks.isEmpty()) {
            serverLevel.getServer().execute(() -> {
                for (LevelChunk chunk : affectedChunks) {
                    ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                    rebuildChunkCozinessMetrics(serverLevel, chunk, data);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        LevelAccessor level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;

        ChunkAccess chunkAccess = event.getChunk();
        if (chunkAccess.getPersistedStatus() != ChunkStatus.FULL) return;
        if (!(chunkAccess instanceof LevelChunk chunk)) return;

        long currentTime = serverLevel.getGameTime();
        if (currentTime <= 0L) return;

        ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
        long lastVisited = data.getLastVisitedTime();
        float abandonedCoziness = data.getAbandonedCoziness();
        long elapsed = currentTime - lastVisited;

        if (lastVisited > 0L && elapsed < 0L) {
            data.setLastVisitedTime(currentTime);
            chunk.setUnsaved(true);
            return;
        }

        if (lastVisited > 0L && elapsed >= DECAY_TRIGGER_TICKS && !data.getPackedPositions().isEmpty()) {
            ChunkPos cPos = chunk.getPos();
            if (!queuedDecayPositions.contains(cPos)) {
                pendingDecayQueue.add(new PendingDecay(cPos, elapsed, abandonedCoziness));
                queuedDecayPositions.add(cPos);
            }
        }

        data.setLastVisitedTime(currentTime);
        chunk.setUnsaved(true);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (pendingDecayQueue.isEmpty()) return;

        int processed = 0;
        while (!pendingDecayQueue.isEmpty() && processed < 4) {
            PendingDecay pending = pendingDecayQueue.poll();
            queuedDecayPositions.remove(pending.pos());

            LevelChunk chunk = serverLevel.getChunkSource().getChunk(pending.pos().x, pending.pos().z, false);
            if (chunk == null) {
                processed++;
                continue;
            }

            ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
            evaluateChunkDecay(serverLevel, chunk, data, pending.elapsedTicks(), pending.abandonedCoziness());
            chunk.setUnsaved(true);
            processed++;
        }
    }

    private static void evaluateChunkDecay(ServerLevel level, LevelChunk chunk,
                                           ChunkCozinessData data,
                                           long elapsedTicks, float abandonedCoziness) {
        if (data.getPackedPositions().isEmpty()) return;

        ChunkPos cPos = chunk.getPos();
        RandomSource random = level.getRandom();
        long elapsedUnits = elapsedTicks / DecayRules.DELAY_MULTIPLIER;

        boolean wasCozy = abandonedCoziness > MINIMUM_COZINESS_THRESHOLD;

        IntList toRemove = new IntArrayList();
        int[] snapshot = data.getPackedPositions().toIntArray();

        for (int packed : snapshot) {
            BlockPos targetPos = ChunkCozinessData.unpackPos(packed, cPos.x, cPos.z);

            if ((targetPos.getX() >> 4) != cPos.x || (targetPos.getZ() >> 4) != cPos.z) {
                toRemove.add(packed);
                continue;
            }

            BlockState state = level.getBlockState(targetPos);
            DecayRule rule = null;
            for (DecayRule r : DecayRules.RULES) {
                if (r.matches(state)) { rule = r; break; }
            }

            if (rule == null) continue;

            DecayContext ctx = new DecayContext(
                    level, targetPos, state, random,
                    elapsedTicks, elapsedUnits,
                    abandonedCoziness, wasCozy);

            DecayRule.Result result = rule.apply(ctx);

            switch (result) {
                case MUTATED_REMOVE -> toRemove.add(packed);
                case MUTATED_KEEP, NO_CHANGE -> {}
            }
        }

        for (int p : toRemove) {
            data.getPackedPositions().remove(p);
        }

        if (wasCozy && elapsedUnits >= 800L) {
            float cobwebChance = 0.005f * Math.min(1.0f, (float)(elapsedUnits - 800L) / 5000f);

            for (int packed : data.getPackedPositions().toIntArray()) {
                BlockPos origin = ChunkCozinessData.unpackPos(packed, cPos.x, cPos.z);
                for (Direction dir : Direction.values()) {
                    BlockPos sidePos = origin.relative(dir);
                    if ((sidePos.getX() >> 4) != cPos.x || (sidePos.getZ() >> 4) != cPos.z) continue;
                    if (!level.isEmptyBlock(sidePos)) continue;

                    if (random.nextFloat() < cobwebChance) {
                        level.setBlock(sidePos, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        rebuildChunkCozinessMetrics(level, chunk, data);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = player.blockPosition();
        ServerLevel world = player.serverLevel();

        if (player.tickCount % 20 == 0) {
            int playerChunkX = pos.getX() >> 4;
            int playerChunkZ = pos.getZ() >> 4;

            LevelChunk currentChunk = world.getChunkSource().getChunk(playerChunkX, playerChunkZ, false);
            if (currentChunk != null && player.tickCount % 100 == 0) {
                ChunkCozinessData data = currentChunk.getData(ChunkCozinessData.CHUNK_DATA);
                if (!data.getPackedPositions().isEmpty()) {
                    rebuildChunkCozinessMetrics(world, currentChunk, data);
                }
            }

            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    LevelChunk chunk = world.getChunkSource().getChunk(playerChunkX + dx, playerChunkZ + dz, false);
                    if (chunk != null) {
                        ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                        data.setLastVisitedTime(world.getGameTime());
                    }
                }
            }

            float neighborhoodTotalCoziness = 0.0f;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    LevelChunk neighbor = world.getChunkSource().getChunk(playerChunkX + dx, playerChunkZ + dz, false);
                    if (neighbor != null) {
                        ChunkCozinessData data = neighbor.getData(ChunkCozinessData.CHUNK_DATA);
                        neighborhoodTotalCoziness += data.getCoziness();
                    }
                }
            }

            float targetCx = 0f, targetCz = 0f, maxRadius = 0f;

            if (neighborhoodTotalCoziness >= MINIMUM_COZINESS_THRESHOLD) {
                maxRadius = (float) (Math.log1p(neighborhoodTotalCoziness - MINIMUM_COZINESS_THRESHOLD) * 6.0);
                maxRadius = Math.clamp(maxRadius, 4.0f, 256.0f);

                targetCx = (playerChunkX << 4) + 8.0f;
                targetCz = (playerChunkZ << 4) + 8.0f;
            }

            PacketDistributor.sendToPlayer(player, new CozinessPacket(targetCx, targetCz, maxRadius));
        }

        if (player.tickCount % 100 == 0) {
            int playerChunkX = pos.getX() >> 4;
            int playerChunkZ = pos.getZ() >> 4;
            LevelChunk currentChunk = world.getChunkSource().getChunk(playerChunkX, playerChunkZ, false);
            if (currentChunk != null) {

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        LevelChunk n = world.getChunkSource().getChunk(playerChunkX + dx, playerChunkZ + dz, false);
                        if (n != null) n.getData(ChunkCozinessData.CHUNK_DATA);
                    }
                }
            }
        }
    }

    private static void rebuildChunkCozinessMetrics(ServerLevel level, LevelChunk chunk, ChunkCozinessData data) {
        ChunkPos cPos = chunk.getPos();
        data.getBlockCounts().clear();

        IntList invalidPositions = new IntArrayList();
        float totalCoziness = 0.0f;

        for (int packed : data.getPackedPositions()) {
            BlockPos p = ChunkCozinessData.unpackPos(packed, cPos.x, cPos.z);
            BlockState activeState = level.getBlockState(p);

            if (activeState.isAir()) {
                invalidPositions.add(packed);
                continue;
            }

            Block block = activeState.getBlock();
            int activeCount = data.getBlockCount(block);
            if (activeCount < getBlockTypeCap(block)) {
                totalCoziness += CozinessRegistry.getCozinessValue(activeState);
            }
            data.incrementBlockCount(block);
        }

        for (int invalid : invalidPositions) {
            data.getPackedPositions().remove(invalid);
        }

        data.setCoziness(totalCoziness);
        data.setAbandonedCoziness(totalCoziness);
        data.setLastVisitedTime(level.getGameTime());
        chunk.setUnsaved(true);
    }
}