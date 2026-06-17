package com.putzwirk.fogrule;

import com.putzwirk.fogrule.cozy.ChunkCozinessData;
import com.putzwirk.fogrule.cozy.CozinessEngine;
import com.putzwirk.fogrule.cozy.CozyDatabase;
import com.putzwirk.fogrule.abandoned.DecayRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(FogRule.MODID)
public class FogRule {
    public static final String MODID = "fogrule";

    public FogRule(IEventBus modEventBus, ModContainer modContainer) {
        ChunkCozinessData.register(modEventBus);
        modEventBus.addListener(this::registerNetworkPayloads);
        modContainer.registerConfig(ModConfig.Type.SERVER, FogRuleConfig.SPEC, "fogrule-server.toml");
        modEventBus.addListener(this::onConfigLoad);

        CozyDatabase.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(new FogHandler());
            NeoForge.EVENT_BUS.register(DebugMenuHandler.class);
            NeoForge.EVENT_BUS.register(FogRuleSpawnHandler.class);
        }
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getType() == ModConfig.Type.SERVER && !(event instanceof ModConfigEvent.Unloading)) {
            DecayRules.reload();
        }
        if (event.getConfig().getSpec() == FogRuleConfig.SPEC) {
            CozinessEngine.invalidateConfigCache();
        }
    }

    private void registerNetworkPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                ClearancePacket.TYPE,
                ClearancePacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> FogHandler.updateClearance(payload.clearanceEnd()))
        );
    }
}