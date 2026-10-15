package com.dawnduskterminal.world;

import com.dawnduskterminal.registry.ModFluids;
import com.dawnduskterminal.registry.ModScorchingTwilight;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;

/**
 * Registers the fluid contact reactions through {@link FluidInteractionRegistry}, the same hook
 * vanilla uses for {@code water + lava -> cobblestone / obsidian}.
 *
 * <p>Each reaction is directional, mirroring vanilla: the mod fluid (portal_fluid / enthu_fluid) is
 * the <em>source</em> and is replaced by the result block the moment a matching neighbour shows up,
 * exactly like a lava block is replaced by obsidian/cobblestone when it touches water. The neighbour
 * fluid is left untouched, so one contact yields exactly one block.
 *
 * <p>The reaction fires from {@code LiquidBlock#onPlace} / {@code LiquidBlock#neighborChanged}
 * (immediately on contact, no random tick), and is keyed by {@code FluidType} - which source and
 * flowing variants share - so source vs flowing is never distinguished.
 */
public final class FluidReactions {
    private FluidReactions() {}

    /** Registers every reaction once, after the fluid-type registry is populated. */
    public static void register() {
        // 不稳定液体 (portal_fluid)
        FluidInteractionRegistry.addInteraction(
            ModFluids.PORTAL_FLUID_TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                NeoForgeMod.WATER_TYPE.value(), ModTerrainBlocks.FRAGILE_CANOPY.get().defaultBlockState()));
        FluidInteractionRegistry.addInteraction(
            ModFluids.PORTAL_FLUID_TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                NeoForgeMod.LAVA_TYPE.value(), ModTerrainBlocks.CHAOS_STONE.get().defaultBlockState()));
        FluidInteractionRegistry.addInteraction(
            ModFluids.PORTAL_FLUID_TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                ModScorchingTwilight.TYPE.value(), ModTerrainBlocks.CANOPY_STONE.get().defaultBlockState()));

        // 热情暮光 (enthu_fluid)
        FluidInteractionRegistry.addInteraction(
            ModScorchingTwilight.TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                NeoForgeMod.WATER_TYPE.value(), auroraBlock().defaultBlockState()));
        FluidInteractionRegistry.addInteraction(
            ModScorchingTwilight.TYPE.value(),
            new FluidInteractionRegistry.InteractionInformation(
                NeoForgeMod.LAVA_TYPE.value(), ModTerrainBlocks.BLAZING_BLOCK.get().defaultBlockState()));
    }

    private static Block auroraBlock() {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("twilightforest:aurora_block"));
        return block == Blocks.AIR ? ModTerrainBlocks.BLAZING_BLOCK.get() : block;
    }
}
