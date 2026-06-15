package com.putzwirk.fogrule.cozy;

import com.putzwirk.fogrule.ClearancePacket;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import com.putzwirk.fogrule.FogRule;
import com.putzwirk.fogrule.FogRuleConfig;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = FogRule.MODID)
public class CozinessEngine {

    private static long getDecayTriggerTicks() {
        return FogRuleConfig.DECAY_TRIGGER_TICKS.get();
    }

    private static float getMinimumCozinessThreshold() {
        return FogRuleConfig.MINIMUM_COZINESS_THRESHOLD.get().floatValue();
    }

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

                if (simulatedTicksPassed >= getDecayTriggerTicks() && !queuedDecayPositions.contains(cPos)) {
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
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

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

            syncCozinessToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos origin = event.getPos();
        LevelChunk chunk = serverLevel.getChunkSource().getChunk(origin.getX() >> 4, origin.getZ() >> 4, false);
        if (chunk == null) return;

        ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
        boolean layoutChanged = false;

        for (Direction dir : Direction.values()) {
            BlockPos sidePos = origin.relative(dir);
            int packed = ChunkCozinessData.packPos(sidePos);

            if (data.getPackedPositions().contains(packed)) {
                BlockState sideState = serverLevel.getBlockState(sidePos);
                if (sideState.isAir()) {
                    data.getPackedPositions().remove(packed);
                }
                layoutChanged = true;
            }
        }

        int originPacked = ChunkCozinessData.packPos(origin);
        if (data.getPackedPositions().contains(originPacked)) {
            BlockState originState = serverLevel.getBlockState(origin);
            if (originState.isAir()) {
                data.getPackedPositions().remove(originPacked);
            }
            layoutChanged = true;
        }

        if (layoutChanged) {
            rebuildChunkCozinessMetrics(serverLevel, chunk, data);
            for (ServerPlayer player : serverLevel.players()) {
                if (player.chunkPosition().equals(chunk.getPos())) {
                    syncCozinessToPlayer(player);
                }
            }
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

                    if (event.getEntity() instanceof ServerPlayer player) {
                        syncCozinessToPlayer(player);
                    }
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
                data.setCoziness(data.getCoziness() - value);
            }

            data.getPackedPositions().remove(packedPos);
            data.setAbandonedCoziness(data.getCoziness());
            data.setLastVisitedTime(serverLevel.getGameTime());
            chunk.setUnsaved(true);
        }

        if (event.getPlayer() instanceof ServerPlayer player) {
            syncCozinessToPlayer(player);
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

        if (lastVisited > 0L && elapsed >= getDecayTriggerTicks() && !data.getPackedPositions().isEmpty()) {
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

        // TO THIS:
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

        boolean wasCozy = abandonedCoziness > getMinimumCozinessThreshold();

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

        if (wasCozy && elapsedUnits >= FogRuleConfig.COBWEB_SPAWN_START_UNITS.get()) {
            float cobwebChance = (float) FogRuleConfig.COBWEB_SPAWN_CHANCE_MAX.getAsDouble()
                    * Math.min(1.0f, (float)(elapsedUnits - FogRuleConfig.COBWEB_SPAWN_START_UNITS.get())
                    / (float) FogRuleConfig.COBWEB_CHANCE_RAMP_UNITS.get());

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

        // Runs exactly once per second (every 20 ticks)
        if (player.tickCount % 20 == 0) {
            float clearance = computeBestClearanceForPlayer(player);
            PacketDistributor.sendToPlayer(player, new ClearancePacket(clearance));

            ServerLevel level = player.serverLevel();
            ChunkPos chunkPos = player.chunkPosition();
            long currentTime = level.getGameTime();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    LevelChunk neighbor = level.getChunkSource().getChunk(chunkPos.x + dx, chunkPos.z + dz, false);
                    if (neighbor != null) {
                        ChunkCozinessData data = neighbor.getData(ChunkCozinessData.CHUNK_DATA);
                        data.setLastVisitedTime(currentTime);
                        neighbor.setUnsaved(true);
                    }
                }
            }
        }
    }
    private static void syncCozinessToPlayer(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        ServerLevel world = player.serverLevel();
        int playerChunkX = pos.getX() >> 4;
        int playerChunkZ = pos.getZ() >> 4;

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

        float targetCx = (playerChunkX << 4) + 8.0f;
        float targetCz = (playerChunkZ << 4) + 8.0f;

        PacketDistributor.sendToPlayer(player, new CozinessPacket(targetCx, targetCz, neighborhoodTotalCoziness));
    }

    public static void rebuildChunkCozinessMetrics(ServerLevel level, LevelChunk chunk, ChunkCozinessData data) {
        ChunkPos cPos = chunk.getPos();
        data.getBlockCounts().clear();

        IntList invalidPositions = new IntArrayList();
        float totalCoziness = 0.0f;

        record ScoredBlock(int packedPos, BlockState state, float value) {}
        java.util.List<ScoredBlock> sortedBlocks = new java.util.ArrayList<>();

        for (int packed : data.getPackedPositions()) {
            BlockPos p = ChunkCozinessData.unpackPos(packed, cPos.x, cPos.z);
            BlockState activeState = level.getBlockState(p);

            if (activeState.isAir()) {
                invalidPositions.add(packed);
                continue;
            }

            float blockValue = CozinessRegistry.getCozinessValue(activeState);
            sortedBlocks.add(new ScoredBlock(packed, activeState, blockValue));
        }

        for (int invalid : invalidPositions) {
            data.getPackedPositions().remove(invalid);
        }

        sortedBlocks.sort((b1, b2) -> Float.compare(b2.value(), b1.value()));

        for (ScoredBlock scored : sortedBlocks) {
            Block block = scored.state().getBlock();
            int activeCount = data.getBlockCount(block);

            if (activeCount < getBlockTypeCap(block)) {
                totalCoziness += scored.value();
            }
            data.incrementBlockCount(block);
        }

        data.setCoziness(totalCoziness);
        data.setAbandonedCoziness(totalCoziness);
        data.setLastVisitedTime(level.getGameTime());
        chunk.setUnsaved(true);
    }

    private static float computeBestClearanceForPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        double px = playerPos.getX() + 0.5;
        double pz = playerPos.getZ() + 0.5;

        float bestEffective = 20f;

        int rangeBlocks = 300;
        int chunkRange = (rangeBlocks >> 4) + 1;
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;

        float minThreshold = FogRuleConfig.MINIMUM_COZINESS_THRESHOLD.get().floatValue();
        float clearanceMultiplier = FogRuleConfig.COZINESS_CLEARANCE_MULTIPLIER.get().floatValue();
        float minClearance = FogRuleConfig.MIN_COZY_CLEARANCE_RANGE.get().floatValue();
        float maxClearance = FogRuleConfig.MAX_COZY_CLEARANCE_RANGE.get().floatValue();

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centerChunkX + dx, centerChunkZ + dz, false);
                if (chunk == null) continue;

                ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                float coziness = data.getCoziness();
                if (coziness < minThreshold) continue;

                float clearance = coziness * clearanceMultiplier;
                clearance = Mth.clamp(clearance, minClearance, maxClearance);

                int chunkCenterX = (chunk.getPos().x << 4) + 8;
                int chunkCenterZ = (chunk.getPos().z << 4) + 8;
                double dist = Math.sqrt((chunkCenterX - px) * (chunkCenterX - px) + (chunkCenterZ - pz) * (chunkCenterZ - pz));
                float effective = clearance - (float) dist;
                if (effective > bestEffective) {
                    bestEffective = effective;
                }
            }
        }
        return Math.max(bestEffective, 20f);
    }
}