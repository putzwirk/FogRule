package com.putzwirk.fogrule.cozy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.putzwirk.fogrule.FogRule;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ChunkCozinessData {
    private float coziness = 0.0f;
    private float abandonedCoziness = 0.0f;
    private long lastVisitedTime = 0L;
    private final IntSet packedPositions = new IntOpenHashSet();

    public ChunkCozinessData() {}

    public ChunkCozinessData(float coziness, float abandonedCoziness, long lastVisitedTime) {
        this.coziness = coziness;
        this.abandonedCoziness = abandonedCoziness;
        this.lastVisitedTime = lastVisitedTime;
    }

    public static int packPos(BlockPos pos) {
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        int localY = (pos.getY() + 64) & 511;
        return (localX << 13) | (localZ << 9) | localY;
    }

    public static BlockPos unpackPos(int packed, int chunkX, int chunkZ) {
        int localX = (packed >> 13) & 15;
        int localZ = (packed >> 9) & 15;
        int localY = (packed & 511) - 64;
        return new BlockPos((chunkX << 4) + localX, localY, (chunkZ << 4) + localZ);
    }

    public float getCoziness() { return coziness; }
    public void setCoziness(float val) { this.coziness = val; }

    public float getAbandonedCoziness() { return abandonedCoziness; }
    public void setAbandonedCoziness(float val) { this.abandonedCoziness = val; }

    public long getLastVisitedTime() { return lastVisitedTime; }
    public void setLastVisitedTime(long time) { this.lastVisitedTime = time; }

    public IntSet getPackedPositions() { return packedPositions; }

    public static final Codec<ChunkCozinessData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("coziness").forGetter(ChunkCozinessData::getCoziness),
            Codec.FLOAT.optionalFieldOf("abandoned_coziness", 0.0f).forGetter(ChunkCozinessData::getAbandonedCoziness),
            Codec.LONG.fieldOf("last_visited").forGetter(ChunkCozinessData::getLastVisitedTime)
    ).apply(instance, ChunkCozinessData::new));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FogRule.MODID);

    public static final java.util.function.Supplier<AttachmentType<ChunkCozinessData>> CHUNK_DATA =
            ATTACHMENT_TYPES.register("coziness_data", () -> AttachmentType.builder(ChunkCozinessData::new)
                    .serialize(CODEC)
                    .build());

    public static void register(IEventBus bus) {
        ATTACHMENT_TYPES.register(bus);
    }
}