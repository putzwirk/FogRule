package com.putzwirk.fogrule.cozy;

import com.putzwirk.fogrule.FogRule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record CozinessPacket(float cx, float cz, float radius) implements CustomPacketPayload {

    public static final Type<CozinessPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(FogRule.MODID, "coziness_packet"));

    public static final StreamCodec<FriendlyByteBuf, CozinessPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeFloat(pkt.cx);
                buf.writeFloat(pkt.cz);
                buf.writeFloat(pkt.radius);
            },
            buf -> new CozinessPacket(buf.readFloat(), buf.readFloat(), buf.readFloat())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}