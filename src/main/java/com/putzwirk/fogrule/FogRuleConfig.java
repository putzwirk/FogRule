package com.putzwirk.fogrule;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class FogRuleConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // -------------------------------------------------------------------------
    // FOG
    // -------------------------------------------------------------------------

    public static final ModConfigSpec.DoubleValue SAFE_RADIUS = BUILDER
            .comment("Safe-zone radius (blocks) around world origin (0,0). Players inside see clear sky.")
            .defineInRange("fog.safeRadius", 100.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue MAX_FOG_FADE_DISTANCE = BUILDER
            .comment("Distance (blocks) over which fog fades in at a safe zone edge. Lower = sharper wall.")
            .defineInRange("fog.maxFogFadeDistance", 20.0, 1.0, 500.0);

    public static final ModConfigSpec.DoubleValue BLEND_SPEED = BUILDER
            .comment("How fast fog interpolates toward its target each frame. Default ~2-second blend at 60fps.")
            .defineInRange("fog.blendSpeed", 0.0004167, 0.000001, 1.0);

    // -------------------------------------------------------------------------
    // COZINESS
    // -------------------------------------------------------------------------

    public static final ModConfigSpec.DoubleValue MINIMUM_COZINESS_THRESHOLD = BUILDER
            .comment("Minimum coziness score before a chunk pushes fog back. Below this = unsafe.")
            .defineInRange("coziness.minimumThreshold", 20.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue COZINESS_CLEARANCE_MULTIPLIER = BUILDER
            .comment("Coziness score * this = fog clearance range in blocks. Example: 40 * 3.0 = 120 blocks.")
            .defineInRange("coziness.clearanceMultiplier", 3.0, 0.1, 100.0);

    public static final ModConfigSpec.DoubleValue MIN_COZY_CLEARANCE_RANGE = BUILDER
            .comment("Minimum clearance range (blocks) granted by any qualifying cozy chunk.")
            .defineInRange("coziness.minClearanceRange", 60.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue MAX_COZY_CLEARANCE_RANGE = BUILDER
            .comment("Maximum clearance range (blocks) a cozy chunk can ever grant.")
            .defineInRange("coziness.maxClearanceRange", 300.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue MIN_SAFE_RADIUS_FROM_COZINESS = BUILDER
            .comment("Inner safe radius (blocks) at minimum qualifying coziness.")
            .defineInRange("coziness.minSafeRadius", 4.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue MAX_SAFE_RADIUS_FROM_COZINESS = BUILDER
            .comment("Inner safe radius (blocks) at maximum coziness (reached at coziness.scaleMax).")
            .defineInRange("coziness.maxSafeRadius", 100.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue COZINESS_SCALE_MIN = BUILDER
            .comment("Coziness value where the clearance/radius scale starts. Should match minimumThreshold.")
            .defineInRange("coziness.scaleMin", 20.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue COZINESS_SCALE_MAX = BUILDER
            .comment("Coziness value where the clearance/radius scale is fully maxed out.")
            .defineInRange("coziness.scaleMax", 100.0, 0.0, 100000.0);

    // -------------------------------------------------------------------------
    // DECAY
    // -------------------------------------------------------------------------

    public static final ModConfigSpec.LongValue DELAY_MULTIPLIER = BUILDER
            .comment("Global speed multiplier for all decay rules. 2 = everything decays twice as slow.")
            .defineInRange("decay.delayMultiplier", 1L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue DECAY_COZINESS_THRESHOLD = BUILDER
            .comment("Abandoned coziness required to trigger extra cobweb-growth effects during decay.")
            .defineInRange("decay.cozinessThreshold", 35.0, 0.0, 10000.0);

    public static final ModConfigSpec.LongValue DECAY_TRIGGER_TICKS = BUILDER
            .comment("Ticks a chunk must be unvisited before decay is evaluated. Default: 240 (12s at 20TPS).")
            .defineInRange("decay.triggerTicks", 240L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue COBWEB_SPAWN_CHANCE_MAX = BUILDER
            .comment("Max per-face chance per decay pass to spawn a cobweb near tracked blocks in cozy chunks.")
            .defineInRange("decay.cobwebSpawnChanceMax", 0.005, 0.0, 1.0);

    public static final ModConfigSpec.LongValue COBWEB_CHANCE_RAMP_UNITS = BUILDER
            .comment("Elapsed units beyond cobwebSpawnStartUnits to reach max cobweb chance. Default: 5000.")
            .defineInRange("decay.cobwebChanceRampUnits", 5000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue COBWEB_SPAWN_START_UNITS = BUILDER
            .comment("Elapsed units when cobweb spawning begins. Default: 800.")
            .defineInRange("decay.cobwebSpawnStartUnits", 800L, 0L, Long.MAX_VALUE);

    // =========================================================================
    // BLOCK COZINESS VALUES
    // =========================================================================
    //
    // Each line: "<block_id> <value>"
    //
    // block_id can be:
    //   minecraft:block_name         - exact block match
    //   #minecraft:tag_name          - any block in that tag
    //
    // Rules are checked top-to-bottom. First match wins.
    // Negative values reduce coziness. Positive values add coziness.
    //
    // COMPLEX BEHAVIORS (flower pot contents, campfire lit state, lantern type, etc.)
    // are handled automatically in code and cannot be overridden here.
    // For more complex customization, fork the mod and compile your own version:
    //   https://github.com/putzwirk/FogRule
    //
    // Examples:
    //   "minecraft:cobweb -5.0"
    //   "#minecraft:wool_carpets 0.8"
    //   "supplementaries:wall_lantern 1.5"

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_COZINESS_VALUES = BUILDER
            .comment(
                    "Coziness value per block. Format: \"namespace:block_id <value>\" or \"#namespace:tag <value>\"",
                    "First match wins. Complex behaviors (pots, campfires) are handled in code, not here.",
                    "Fork the mod for advanced customization: https://github.com/putzwirk/FogRule"
            )
            .defineListAllowEmpty(
                    "blockCozinessValues",
                    List.of(
                            "minecraft:cobweb -5.0",
                            "minecraft:mossy_cobblestone -0.1",

                            "minecraft:cobblestone 0.15",
                            "minecraft:dirt_path 0.2",

                            "#minecraft:wool_carpets 0.8"
                    ),
                    o -> o instanceof String s && isValidCozinessEntry(s)
            );

    // =========================================================================
    // DECAY RULES
    // =========================================================================
    //
    // Two rule types are supported:
    //
    //   BREAK <block_id> <elapsed_units>
    //       Replaces the block with air after the given elapsed units.
    //       Example: "BREAK minecraft:torch 100"
    //
    //   MUTATE <block_id> <target_block_id> <base_units> <full_units> <chance>
    //       Probabilistically replaces block with target.
    //       Chance ramps from 0 at base_units to guaranteed at full_units.
    //       Example: "MUTATE minecraft:cobblestone minecraft:mossy_cobblestone 500 10000 0.001"
    //
    // block_id can be:
    //   minecraft:block_name         - exact block match
    //   #minecraft:tag_name          - any block in that tag
    //
    // Complex decay behaviors (campfire extinguish, flower pot withering, door
    // removal, cobweb spreading) are handled automatically in code.
    // For more complex customization, fork the mod and compile your own version:
    //   https://github.com/putzwirk/FogRule

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DECAY_RULES = BUILDER
            .comment(
                    "Decay rules for abandoned chunks. Two formats:",
                    "  BREAK <block_id> <elapsed_units>",
                    "  MUTATE <block_id> <target_block_id> <base_units> <full_units> <chance>",
                    "block_id can be #namespace:tag. Complex behaviors are hardcoded.",
                    "Fork for advanced customization: https://github.com/putzwirk/FogRule"
            )
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

    static final ModConfigSpec SPEC = BUILDER.build();

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

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
