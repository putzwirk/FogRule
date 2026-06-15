package com.putzwirk.fogrule;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ClearancePacket(float clearanceEnd) implements CustomPacketPayload {

    public static final Type<ClearancePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FogRule.MODID, "clearance_packet"));

    public static final StreamCodec<FriendlyByteBuf, ClearancePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeFloat(pkt.clearanceEnd),
            buf -> new ClearancePacket(buf.readFloat())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}