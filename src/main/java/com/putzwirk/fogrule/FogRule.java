package com.putzwirk.fogrule;

import com.putzwirk.fogrule.abandoned.DecayRules;
import com.putzwirk.fogrule.cozy.ChunkCozinessData;
import com.putzwirk.fogrule.cozy.CozinessPacket;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(FogRule.MODID)
public class FogRule {
    public static final String MODID = "fogrule";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FogRule(IEventBus modEventBus, ModContainer modContainer) {
        ChunkCozinessData.register(modEventBus);
        modEventBus.addListener(this::registerNetworkPayloads);
        modContainer.registerConfig(ModConfig.Type.SERVER, FogRuleConfig.SPEC, "fogrule-server.toml");
        modEventBus.addListener(this::onConfigLoad);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(new FogHandler());
            NeoForge.EVENT_BUS.register(DebugMenuHandler.class);
        }
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getType() == ModConfig.Type.SERVER && !(event instanceof ModConfigEvent.Unloading)) {
            DecayRules.reload();
        }
    }

    private void registerNetworkPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                ClearancePacket.TYPE,
                ClearancePacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> FogHandler.updateClearance(payload.clearanceEnd()))
        );

        registrar.playToClient(
                CozinessPacket.TYPE,
                CozinessPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    FogHandler.updateCoziness(payload.cx(), payload.cz(), payload.radius());
                })
        );
    }
}