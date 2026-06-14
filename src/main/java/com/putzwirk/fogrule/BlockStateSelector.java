package com.putzwirk.fogrule;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;

/**
 * Parses a selector string from config and tests it against a BlockState.
 *
 * Supported selector formats:
 *   namespace:block_id
 *   namespace:block_id[prop=val,prop2=val2]
 *   #namespace:tag_id
 *   @class_alias   (see ALIASES map below)
 */
public class BlockStateSelector {

    private final String raw;

    private BlockStateSelector(String raw) {
        this.raw = raw.trim();
    }

    public static BlockStateSelector of(String raw) {
        return new BlockStateSelector(raw);
    }

    public boolean matches(BlockState state) {
        if (raw.startsWith("@")) {
            return matchesAlias(raw.substring(1), state);
        }
        if (raw.startsWith("#")) {
            return matchesTag(raw.substring(1), state);
        }
        return matchesBlockId(raw, state);
    }

    // -------------------------------------------------------------------------

    private static boolean matchesAlias(String alias, BlockState state) {
        Block b = state.getBlock();
        return switch (alias.toLowerCase()) {
            case "torch"        -> b instanceof TorchBlock || b instanceof WallTorchBlock;
            case "lantern"      -> b instanceof LanternBlock;
            case "campfire"     -> b instanceof CampfireBlock;
            case "carpet"       -> b instanceof WoolCarpetBlock;
            case "flower_pot"   -> b instanceof FlowerPotBlock;
            case "door"         -> b instanceof DoorBlock;
            case "cobble_like"  -> isCobbleLike(state);
            case "glass_like"   -> isGlassLike(state);
            default             -> false;
        };
    }

    private static boolean isCobbleLike(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof FenceBlock || b instanceof WallBlock) return true;
        String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
        return path.contains("cobble") || path.endsWith("planks");
    }

    private static boolean isGlassLike(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.GLASS
                || b instanceof IronBarsBlock
                || b instanceof StainedGlassBlock
                || b instanceof StainedGlassPaneBlock;
    }

    private static boolean matchesTag(String tagId, BlockState state) {
        try {
            ResourceLocation loc = ResourceLocation.parse(tagId);
            TagKey<Block> tag = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, loc);
            return state.is(tag);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean matchesBlockId(String selector, BlockState state) {
        int bracketIdx = selector.indexOf('[');
        String blockId = bracketIdx >= 0 ? selector.substring(0, bracketIdx) : selector;

        ResourceLocation loc;
        try {
            loc = ResourceLocation.parse(blockId);
        } catch (Exception e) {
            return false;
        }

        Block expected = BuiltInRegistries.BLOCK.get(loc);
        if (expected == null || expected == Blocks.AIR && !loc.getPath().equals("air")) return false;
        if (state.getBlock() != expected) return false;

        if (bracketIdx < 0) return true;

        String propsStr = selector.substring(bracketIdx + 1);
        if (propsStr.endsWith("]")) propsStr = propsStr.substring(0, propsStr.length() - 1);

        StateDefinition<Block, BlockState> def = expected.getStateDefinition();
        for (String pair : propsStr.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) continue;
            Property<?> prop = def.getProperty(kv[0].trim());
            if (prop == null) return false;
            if (!statePropertyMatches(state, prop, kv[1].trim())) return false;
        }

        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Comparable<T>> boolean statePropertyMatches(
            BlockState state, Property<T> prop, String valueStr) {
        return prop.getValue(valueStr)
                .map(v -> state.getValue(prop).equals(v))
                .orElse(false);
    }
}