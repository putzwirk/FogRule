package com.putzwirk.fogrule;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;


@Mod(FogRule.MODID)
public class FogRule {
    public static final String MODID = "fogrule";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FogRule(IEventBus modEventBus, ModContainer modContainer) {

    }
}