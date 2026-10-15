package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The chrono noise settings are a port of {@code minecraft:noise_settings/overworld.json} whose
 * terrain block, aquifer fluid and surface rule already name the chrono palette - the dimension
 * generates its own blocks directly, rather than generating a vanilla column that gets rewritten.
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class AquiferTests {
    private static final int VANILLA_SEA_LEVEL = 63;
    private static final ResourceLocation CHRONO = ResourceLocation.fromNamespaceAndPath("dawnduskterminal", "chrono");

    private static NoiseGeneratorSettings chronoSettings(GameTestHelper helper) {
        Registry<NoiseGeneratorSettings> registry = helper.getLevel().getServer().registryAccess()
            .registryOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings settings = registry.get(CHRONO);
        helper.assertTrue(settings != null, "noise settings " + CHRONO + " missing");
        return settings;
    }

    /** The generator itself must name the mod's blocks - that is what makes the palette native. */
    @GameTest(template = "ddt_empty")
    public static void terrainAndFluidUseTheModPalette(GameTestHelper helper) {
        NoiseGeneratorSettings settings = chronoSettings(helper);
        helper.assertTrue(settings.defaultBlock().is(ModTerrainBlocks.CANOPY_STONE.get()),
            "the chrono terrain must be generated as canopy_stone, got " + settings.defaultBlock());
        helper.assertTrue(settings.defaultFluid().is(ModBlocks.PORTAL_FLUID.get()),
            "aquifers and oceans must be generated as portal_fluid, got " + settings.defaultFluid());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void aquifersAreEnabledLikeVanilla(GameTestHelper helper) {
        NoiseGeneratorSettings settings = chronoSettings(helper);
        helper.assertTrue(settings.aquifersEnabled(), "chrono must keep aquifers enabled");
        helper.assertTrue(settings.seaLevel() == VANILLA_SEA_LEVEL,
            "sea level must match the vanilla overworld value " + VANILLA_SEA_LEVEL + ", got " + settings.seaLevel());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void oreVeinsAreEnabledLikeVanilla(GameTestHelper helper) {
        NoiseGeneratorSettings settings = chronoSettings(helper);
        helper.assertTrue(settings.oreVeinsEnabled(),
            "the port keeps vanilla ore veins; the old hand-rolled terrain disabled them to hide the "
                + "granite/andesite they blended with canopy_stone, which no longer applies");
        helper.succeed();
    }
}
