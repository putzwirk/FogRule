package com.putzwirk.fogrule.cozy;

import com.putzwirk.fogrule.ClearancePacket;
import com.putzwirk.fogrule.FogRule;
import com.putzwirk.fogrule.FogRuleConfig;
import com.putzwirk.fogrule.abandoned.DecayContext;
import com.putzwirk.fogrule.abandoned.DecayRule;
import com.putzwirk.fogrule.abandoned.DecayRules;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = FogRule.MODID)
public class CozinessEngine {
    private static List<DiminishingGroup> compiledGroups = null;
    private static class DiminishingGroup {
        final List<String> selectors = new ArrayList<>();
        int maxCount;
    }
    public static final ConcurrentHashMap<ChunkPos, Float> loadedCoziness = new ConcurrentHashMap<>();
    private static final Set<ServerPlayer> dirtyPlayers = new HashSet<>();
    private static int tickCounter = 0;

    private static final Deque<PendingDecay> pendingDecayQueue = new ArrayDeque<>();
    private static final Set<ChunkPos> queuedDecayPositions = new HashSet<>();

    private record PendingDecay(ChunkPos pos, long elapsedTicks, float abandonedCoziness) {}

    private static long getDecayTriggerTicks() {
        return FogRuleConfig.DECAY_TRIGGER_TICKS.get();
    }

    public static boolean isInsideCozyZone(BlockPos pos, ServerLevel level) {
        ServerPlayer nearestPlayer = (ServerPlayer) level.getNearestPlayer(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                -1.0,
                false
        );

        if (nearestPlayer == null) {
            return false;
        }

        int pX = nearestPlayer.chunkPosition().x;
        int pZ = nearestPlayer.chunkPosition().z;
        int r = FogRuleConfig.PLAYER_CHUNK_RADIUS.get();
        float maxCoziness = 0;
        int bestX = pX;
        int bestZ = pZ;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Float cz = loadedCoziness.get(new ChunkPos(pX + dx, pZ + dz));
                if (cz != null && cz > maxCoziness) {
                    maxCoziness = cz;
                    bestX = pX + dx;
                    bestZ = pZ + dz;
                }
            }
        }

        if (maxCoziness <= 0) {
            return false;
        }

        float targetRadius = Math.min(maxCoziness * FogRuleConfig.CLEARANCE_MULTIPLIER.get().floatValue(), FogRuleConfig.MAX_CLEARANCE_RANGE.get().floatValue());
        float cx = (bestX << 4) + 8f;
        float cz = (bestZ << 4) + 8f;

        float dx = (float) pos.getX() + 0.5f - cx;
        float dz = (float) pos.getZ() + 0.5f - cz;

        float distSqToCenter = dx * dx + dz * dz;

        float checkRadius = targetRadius + 20.0f;

        return distSqToCenter <= (checkRadius * checkRadius);
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
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);

        IntOpenHashSet positions = CozyDatabase.loadPositions(chunk.getPos().x, chunk.getPos().z);
        data.getPackedPositions().clear();
        data.getPackedPositions().addAll(positions);

        loadedCoziness.put(chunk.getPos(), data.getCoziness());

        if (!data.getPackedPositions().isEmpty()) {
            long currentTime = serverLevel.getGameTime();
            long oldLastVisited = data.getLastVisitedTime();
            long elapsedTicks = currentTime - oldLastVisited;

            if (elapsedTicks >= getDecayTriggerTicks() && !queuedDecayPositions.contains(chunk.getPos())) {
                ServerPlayer closest = null;
                double minDist = Double.MAX_VALUE;
                for (ServerPlayer p : serverLevel.players()) {
                    double d = p.blockPosition().distSqr(new BlockPos(chunk.getPos().getMiddleBlockX(), (int) p.getY(), chunk.getPos().getMiddleBlockZ()));
                    if (d < minDist) {
                        minDist = d;
                        closest = p;
                    }
                }
                if (closest != null && minDist < 65536) {
                    closest.sendSystemMessage(Component.literal("§c[FogRule Debug] Chunk [" + chunk.getPos().x + ", " + chunk.getPos().z + "] LastVisitedTime: " + oldLastVisited + " | Elapsed: " + elapsedTicks + " ticks"));
                }

                pendingDecayQueue.add(new PendingDecay(chunk.getPos(), elapsedTicks, data.getAbandonedCoziness()));
                queuedDecayPositions.add(chunk.getPos());
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);

        CozyDatabase.scheduleFlush(chunk.getPos().x, chunk.getPos().z, new IntOpenHashSet(data.getPackedPositions()));
        data.getPackedPositions().clear();
        loadedCoziness.remove(chunk.getPos());
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
            data.setLastVisitedTime(serverLevel.getGameTime());
            if (getMatchingGroupIndex(placedState) != -1) {
                recalculateCoziness(serverLevel, chunk, data);
            } else {
                float addition = CozinessRegistry.getCozinessValue(placedState);
                float newCoziness = data.getCoziness() + addition;
                data.setCoziness(newCoziness);
                loadedCoziness.put(chunk.getPos(), newCoziness);
                data.setAbandonedCoziness(newCoziness);
            }
            chunk.setUnsaved(true);
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
            recalculateCoziness(serverLevel, chunk, data);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            dirtyPlayers.add(player);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            long currentTime = serverLevel.getGameTime();
            for (ServerPlayer player : dirtyPlayers) {
                if (player.level() == serverLevel) {
                    updatePlayerFog(player);

                    int px = player.blockPosition().getX() >> 4;
                    int pz = player.blockPosition().getZ() >> 4;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            LevelChunk chunk = serverLevel.getChunkSource().getChunk(px + dx, pz + dz, false);
                            if (chunk != null) {
                                ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                                if (data.getLastVisitedTime() != currentTime) {
                                    data.setLastVisitedTime(currentTime);
                                    chunk.setUnsaved(true);
                                }
                            }
                        }
                    }
                }
            }
            dirtyPlayers.clear();
        }

        if (!pendingDecayQueue.isEmpty()) {
            PendingDecay pending = pendingDecayQueue.poll();
            if (pending != null) {
                queuedDecayPositions.remove(pending.pos());
                LevelChunk chunk = serverLevel.getChunkSource().getChunk(pending.pos().x, pending.pos().z, false);
                if (chunk != null) {
                    ChunkCozinessData data = chunk.getData(ChunkCozinessData.CHUNK_DATA);
                    processDecay(serverLevel, chunk, data, pending.elapsedTicks(), pending.abandonedCoziness());
                    data.setLastVisitedTime(serverLevel.getGameTime());
                    chunk.setUnsaved(true);
                }
            }
        }
    }

    private static void updatePlayerFog(ServerPlayer player) {
        int px = player.blockPosition().getX() >> 4;
        int pz = player.blockPosition().getZ() >> 4;
        int radius = FogRuleConfig.PLAYER_CHUNK_RADIUS.get();

        double weightedSum = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cPos = new ChunkPos(px + dx, pz + dz);
                Float coziness = loadedCoziness.get(cPos);
                if (coziness != null && coziness > 0) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    double weight = Math.max(0, 1.0 - (dist / (radius + 1.0)));
                    weightedSum += coziness * weight;
                }
            }
        }

        float clearance = (float) (Math.pow(weightedSum, 0.6) * FogRuleConfig.CLEARANCE_MULTIPLIER.get());
        clearance = Math.min(clearance, FogRuleConfig.MAX_CLEARANCE_RANGE.get().floatValue());

        PacketDistributor.sendToPlayer(player, new ClearancePacket(clearance));
    }

    private static void processDecay(ServerLevel level, LevelChunk chunk, ChunkCozinessData data, long elapsedTicks, float abandonedCoziness) {
        long elapsedUnits = elapsedTicks / getDecayTriggerTicks();
        if (elapsedUnits < 1) return;

        RandomSource random = RandomSource.create(chunk.getPos().toLong());
        IntArrayList packedList = new IntArrayList(data.getPackedPositions());

        for (int packed : packedList) {
            BlockPos pos = ChunkCozinessData.unpackPos(packed, chunk.getPos().x, chunk.getPos().z);
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) continue;

            DecayContext ctx = new DecayContext(level, pos, state, random, elapsedTicks, elapsedUnits, abandonedCoziness, data.getCoziness() > 0);

            for (DecayRule rule : DecayRules.RULES) {
                if (rule.matches(state)) {
                    DecayRule.Result result = rule.apply(ctx);
                    if (result == DecayRule.Result.MUTATED_REMOVE) {
                        data.getPackedPositions().remove(packed);
                    }
                    break;
                }
            }
        }
        recalculateCoziness(level, chunk, data);
    }

    public static void recalculateCoziness(ServerLevel level, LevelChunk chunk, ChunkCozinessData data) {
        ensureGroupsCompiled();
        float total = 0;
        IntOpenHashSet toRemove = new IntOpenHashSet();
        int[] groupCounts = new int[compiledGroups.size()];
        for (int packed : data.getPackedPositions()) {
            BlockPos pos = ChunkCozinessData.unpackPos(packed, chunk.getPos().x, chunk.getPos().z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                toRemove.add(packed);
            } else {
                float value = CozinessRegistry.getCozinessValue(state);
                int groupIdx = getMatchingGroupIndex(state);
                if (groupIdx != -1) {
                    groupCounts[groupIdx]++;
                    if (groupCounts[groupIdx] > compiledGroups.get(groupIdx).maxCount) {
                        value = 0.0f;
                    }
                }
                total += value;
            }
        }
        if (!toRemove.isEmpty()) {
            data.getPackedPositions().removeAll(toRemove);
        }
        data.setCoziness(total);
        loadedCoziness.put(chunk.getPos(), total);
        chunk.setUnsaved(true);
    }
    private static void ensureGroupsCompiled() {
        if (compiledGroups != null) return;
        compiledGroups = new ArrayList<>();
        List<? extends String> lines = FogRuleConfig.DIMINISHING_BLOCK_GROUPS.get();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) continue;
            DiminishingGroup group = new DiminishingGroup();
            for (int i = 0; i < parts.length - 1; i++) {
                group.selectors.add(parts[i]);
            }
            try {
                group.maxCount = Integer.parseInt(parts[parts.length - 1]);
                compiledGroups.add(group);
            } catch (NumberFormatException ignored) {}
        }
    }

    public static void invalidateConfigCache() {
        compiledGroups = null;
    }

    private static int getMatchingGroupIndex(BlockState state) {
        ensureGroupsCompiled();
        for (int i = 0; i < compiledGroups.size(); i++) {
            DiminishingGroup group = compiledGroups.get(i);
            for (String selector : group.selectors) {
                if (CozinessRegistry.matchesSelector(selector, state)) {
                    return i;
                }
            }
        }
        return -1;
    }



}