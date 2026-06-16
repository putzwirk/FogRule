package com.putzwirk.fogrule;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class FogRuleConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MIN_FOG_DISTANCE = BUILDER
            .defineInRange("fog.minFogDistance", 20.0, 1.0, 500.0);

    public static final ModConfigSpec.DoubleValue BLEND_SPEED = BUILDER
            .defineInRange("fog.blendSpeed", 0.004167, 0.000001, 1.0);

    public static final ModConfigSpec.IntValue PLAYER_CHUNK_RADIUS = BUILDER
            .defineInRange("fog.playerChunkRadius", 8, 1, 32);

    public static final ModConfigSpec.DoubleValue CLEARANCE_MULTIPLIER = BUILDER
            .defineInRange("fog.clearanceMultiplier", 3.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue MAX_CLEARANCE_RANGE = BUILDER
            .defineInRange("fog.maxClearanceRange", 300.0, 0.0, 10000.0);

    public static final ModConfigSpec.LongValue DELAY_MULTIPLIER = BUILDER
            .defineInRange("decay.delayMultiplier", 1L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue DECAY_COZINESS_THRESHOLD = BUILDER
            .defineInRange("decay.cozinessThreshold", 35.0, 0.0, 10000.0);

    public static final ModConfigSpec.LongValue DECAY_TRIGGER_TICKS = BUILDER
            .defineInRange("decay.triggerTicks", 1L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue COBWEB_SPAWN_CHANCE_MAX = BUILDER
            .defineInRange("decay.cobwebSpawnChanceMax", 0.005, 0.0, 1.0);

    public static final ModConfigSpec.LongValue COBWEB_CHANCE_RAMP_UNITS = BUILDER
            .defineInRange("decay.cobwebChanceRampUnits", 5000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue COBWEB_SPAWN_START_UNITS = BUILDER
            .defineInRange("decay.cobwebSpawnStartUnits", 800L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_COZINESS_VALUES = BUILDER
            .defineListAllowEmpty(
                    "blockCozinessValues",
                    List.of(
                            "minecraft:torch 1.0",
                            "minecraft:wall_torch 1.0",
                            "minecraft:lantern 1.0",
                            "minecraft:soul_lantern 1.0",
                            "minecraft:cobweb -5.0",
                            "minecraft:mossy_cobblestone -0.1",
                            "minecraft:cobblestone 0.15",
                            "minecraft:dirt_path 0.2",
                            "#minecraft:wool_carpets 0.8"
                    ),
                    o -> o instanceof String s && isValidCozinessEntry(s)
            );

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DECAY_RULES = BUILDER
            .defineListAllowEmpty(
                    "decayRules",
                    List.of(
                            "BREAK minecraft:fire 30",
                            "BREAK minecraft:soul_fire 30",
                            "BREAK minecraft:torch 100",
                            "BREAK minecraft:wall_torch 100",
                            "BREAK minecraft:lantern 300",
                            "BREAK minecraft:soul_lantern 300",
                            "BREAK minecraft:glass 400",
                            "BREAK #minecraft:stained_glass 400",
                            "BREAK #minecraft:stained_glass_panes 400",
                            "BREAK minecraft:iron_bars 400",
                            "BREAK minecraft:glowstone 500",
                            "BREAK minecraft:shroomlight 600",
                            "MUTATE minecraft:cobblestone minecraft:mossy_cobblestone 500 10000 0.001",
                            "MUTATE minecraft:dirt_path minecraft:grass_block 1000 10000 0.001"
                    ),
                    o -> o instanceof String s && isValidDecayEntry(s)
            );

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOG_ALLOWED_MOBS = BUILDER
            .defineListAllowEmpty(
                    "fogAllowedMobs",
                    List.of(
                            "minecraft:zombie",
                            "minecraft:skeleton",
                            "minecraft:spider",
                            "minecraft:creeper",
                            "minecraft:husk"
                    ),
                    o -> o instanceof String s && s.contains(":")
            );

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean isValidCozinessEntry(String s) {
        if (s == null || s.isBlank()) return false;
        String[] parts = s.trim().split("\\s+");
        if (parts.length != 2) return false;
        try {
            Float.parseFloat(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    private static boolean isValidDecayEntry(String s) {
        if (s == null || s.isBlank()) return false;
        String[] parts = s.trim().split("\\s+");
        if (parts.length < 1) return false;
        String action = parts[0].toUpperCase();
        return action.equals("BREAK") || action.equals("MUTATE");
    }
}