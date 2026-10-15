package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.dawnduskterminal.world.ChronoVeinPalette;
import com.dawnduskterminal.world.PaletteBiomeSource;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The chrono dimension owns its terrain and its biomes: the noise settings already name the mod's
 * blocks, and the biomes are faithful copies whose feature lists place those blocks too. Between them
 * the dimension generates its own palette directly, with no pass over the finished chunk.
 *
 * <p>Note: {@code GameTestServer} builds its world from {@code WorldPresets.FLAT}, which only carries
 * the vanilla dimensions, so anything needing the chrono {@code ServerLevel} has to cope with it
 * being absent here.
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class WorldgenTests {
    private static final String MOD_ID = "dawnduskterminal";
    private static final ResourceLocation CHRONO = ResourceLocation.fromNamespaceAndPath(MOD_ID, "chrono");

    /** Every density function the ported Overworld noise router needs. */
    private static final String[] PORTED_DENSITY_FUNCTIONS = {
        "shift_x",
        "shift_z",
        "y",
        "overworld/base_3d_noise",
        "overworld/continents",
        "overworld/erosion",
        "overworld/depth",
        "overworld/factor",
        "overworld/jaggedness",
        "overworld/offset",
        "overworld/ridges",
        "overworld/ridges_folded",
        "overworld/sloped_cheese",
        "overworld/caves/entrances",
        "overworld/caves/noodle",
        "overworld/caves/pillars",
        "overworld/caves/spaghetti_2d",
        "overworld/caves/spaghetti_2d_thickness_modulator",
        "overworld/caves/spaghetti_roughness_function",
    };

    /** The seed-bearing noise parameters those density functions read. */
    private static final String[] PORTED_NOISE_PARAMETERS = {
        "continentalness", "erosion", "ridge", "temperature", "vegetation", "jagged", "offset",
        "surface", "surface_swamp", "calcite", "gravel", "powder_snow", "ice", "packed_ice",
        "cave_cheese", "cave_layer", "cave_entrance", "spaghetti_2d", "spaghetti_roughness",
        "noodle", "pillar", "ore_vein_a", "ore_vein_b", "ore_veininess", "ore_gap",
        "aquifer_barrier", "aquifer_fluid_level_floodedness", "aquifer_fluid_level_spread", "aquifer_lava",
    };

    /** A sample of the overworld biomes the dimension has to own a copy of. */
    private static final String[] COPIED_BIOMES = {
        "plains", "sunflower_plains", "desert", "ocean", "deep_ocean", "frozen_ocean", "forest",
        "taiga", "jungle", "swamp", "mushroom_fields", "stony_peaks", "deep_dark", "lush_caves",
        "dripstone_caves",
    };

    private static NoiseGeneratorSettings chronoSettings(GameTestHelper helper) {
        Registry<NoiseGeneratorSettings> registry = helper.getLevel().getServer().registryAccess()
            .registryOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings settings = registry.get(CHRONO);
        helper.assertTrue(settings != null, "noise settings " + CHRONO + " missing");
        return settings;
    }

    /** Collects the noise keys a density function tree reads. */
    private static Set<ResourceLocation> noiseKeys(DensityFunction function) {
        Set<ResourceLocation> keys = new LinkedHashSet<>();
        function.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction inner) {
                return inner;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
                holder.noiseData().unwrapKey().ifPresent(key -> keys.add(key.location()));
                return holder;
            }
        });
        return keys;
    }

    @GameTest(template = "ddt_empty")
    public static void chronoDimensionTypeIsRegistered(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        Registry<DimensionType> types = access.registryOrThrow(Registries.DIMENSION_TYPE);
        helper.assertTrue(types.containsKey(CHRONO), "chrono dimension type must be registered");
        helper.assertTrue(access.registryOrThrow(Registries.NOISE_SETTINGS).containsKey(CHRONO),
            "chrono noise settings must be registered");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portedDensityFunctionsAreRegistered(GameTestHelper helper) {
        Registry<DensityFunction> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
        for (String id : PORTED_DENSITY_FUNCTIONS) {
            helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, id)),
                "ported density function missing: " + id);
        }
        // the port is a copy, not a replacement: the vanilla originals must still be intact
        helper.assertTrue(registry.containsKey(ResourceLocation.withDefaultNamespace("overworld/sloped_cheese")),
            "vanilla overworld/sloped_cheese must stay registered");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portedNoiseParametersAreRegistered(GameTestHelper helper) {
        Registry<NormalNoise.NoiseParameters> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.NOISE);
        for (String id : PORTED_NOISE_PARAMETERS) {
            helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, id)),
                "ported noise parameter missing: " + id);
        }
        helper.succeed();
    }

    /**
     * The whole point of moving the noise parameters into our namespace. The engine builds every
     * noise field as {@code NormalNoise.create(randomFactory.fromHashOf(key.location()), params)},
     * seeded by the world seed, so a {@code dawnduskterminal:*} key yields a different, seed-derived
     * field than the {@code minecraft:*} one would - while the vanilla original keeps its own field.
     */
    @GameTest(template = "ddt_empty")
    public static void terrainNoiseIsSeedDerived(GameTestHelper helper) {
        NoiseRouter router = chronoSettings(helper).noiseRouter();
        Set<ResourceLocation> keys = new LinkedHashSet<>();
        for (DensityFunction function : List.of(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                router.temperature(),
                router.vegetation(),
                router.continents(),
                router.erosion(),
                router.depth(),
                router.ridges(),
                router.initialDensityWithoutJaggedness(),
                router.finalDensity(),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap())) {
            keys.addAll(noiseKeys(function));
        }
        helper.assertTrue(!keys.isEmpty(), "the chrono router must read at least one noise parameter");
        for (ResourceLocation key : keys) {
            helper.assertTrue(MOD_ID.equals(key.getNamespace()),
                "router noise " + key + " is still in the vanilla namespace, so its field would be "
                    + "identical to the Overworld's instead of seed-derived");
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void paletteBiomeCopiesAreRegistered(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        for (String name : COPIED_BIOMES) {
            helper.assertTrue(biomes.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, name)),
                "chrono biome copy missing: " + name);
            helper.assertTrue(biomes.containsKey(ResourceLocation.withDefaultNamespace(name)),
                "the vanilla biome must stay untouched next to its copy: " + name);
        }
        helper.succeed();
    }

    /**
     * The point of forking the biomes: the copies must place *our* features, which is what makes the
     * dimension's granite/diorite/andesite/tuff/lava/snow arrive as mod blocks while generating.
     */
    @GameTest(template = "ddt_empty")
    public static void paletteBiomeCopiesCarryOurFeatures(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        Biome plains = biomes.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, "plains"));
        helper.assertTrue(plains != null, MOD_ID + ":plains must be registered");
        Set<ResourceLocation> featureIds = new LinkedHashSet<>();
        for (HolderSet<PlacedFeature> step : plains.getGenerationSettings().features()) {
            for (Holder<PlacedFeature> holder : step) {
                holder.unwrapKey().ifPresent(key -> featureIds.add(key.location()));
            }
        }
        helper.assertTrue(!featureIds.isEmpty(), "the biome copy must keep its feature list");
        helper.assertTrue(featureIds.stream().anyMatch(id -> MOD_ID.equals(id.getNamespace())),
            "the biome copy must place our own features, found " + featureIds);
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void paletteBiomeSourceIsRegistered(GameTestHelper helper) {
        Registry<MapCodec<? extends BiomeSource>> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME_SOURCE);
        helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, "palette")),
            "the chrono biome source type must be registered");
        helper.succeed();
    }

    /**
     * The dimension's biome source wraps the vanilla overworld preset rather than replacing its
     * climate table - that table only exists in code, so it cannot be authored in a datapack.
     * {@code GameTestServer} has no modded dimensions, so this skips there.
     */
    @GameTest(template = "ddt_empty")
    public static void chronoUsesThePaletteBiomeSource(GameTestHelper helper) {
        ServerLevel chrono = helper.getLevel().getServer().getLevel(ModDimensions.CHRONO);
        if (chrono == null) {
            helper.succeed();
            return;
        }
        BiomeSource source = chrono.getChunkSource().getGenerator().getBiomeSource();
        helper.assertTrue(source instanceof PaletteBiomeSource,
            "chrono must use the palette biome source, got " + source);
        helper.assertTrue(((PaletteBiomeSource) source).delegate() instanceof MultiNoiseBiomeSource,
            "the palette source must delegate to the vanilla overworld multi_noise preset");
        helper.succeed();
    }

    /** Kept so the ported feature ordering stays exercised even though nothing else needs it. */
    @GameTest(template = "ddt_empty")
    public static void configuredFeaturesAreRegistered(GameTestHelper helper) {
        Registry<ConfiguredFeature<?, ?>> registry =
            helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        helper.assertTrue(registry.containsKey(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ore_granite")),
            "the palette copy of the granite ore feature must be registered");
        helper.assertTrue(registry.containsKey(ResourceLocation.withDefaultNamespace("ore_granite")),
            "the vanilla granite ore feature must stay registered for the overworld");
        helper.succeed();
    }

    /** Vanilla's large ore veins hardcode their fillers in {@code OreVeinifier}; these are the swaps. */
    @GameTest(template = "ddt_empty")
    public static void veinFillersUseThePalette(GameTestHelper helper) {
        helper.assertTrue(ChronoVeinPalette.remap(Blocks.GRANITE.defaultBlockState())
                .is(ModTerrainBlocks.OCHRE.get()),
            "the copper vein filler (granite) must become ochre");
        helper.assertTrue(ChronoVeinPalette.remap(Blocks.TUFF.defaultBlockState())
                .is(ModTerrainBlocks.TWILIGHT_STONE.get()),
            "the iron vein filler (tuff) must become twilight_stone");
        helper.assertTrue(ChronoVeinPalette.remap(Blocks.STONE.defaultBlockState()).is(Blocks.STONE),
            "unrelated blocks must pass through untouched");
        // the chunk block rule returns null for "leave the default block", which is the common case
        helper.assertTrue(ChronoVeinPalette.remap(null) == null,
            "a null block state must pass through: it means 'leave the default block here'");
        // the wrapper must forward that null, not NPE on it - the bug that killed chunk generation
        helper.assertTrue(ChronoVeinPalette.wrap(context -> null).calculate(null) == null,
            "the wrapped rule must forward a null result untouched");
        helper.assertTrue(ChronoVeinPalette.wrap(context -> Blocks.GRANITE.defaultBlockState())
                .calculate(null).is(ModTerrainBlocks.OCHRE.get()),
            "the wrapped rule must still remap a real vein filler");
        helper.succeed();
    }

    /**
     * Proves the biome substitution actually happens: build the same source the dimension declares and
     * check that every biome the vanilla overworld preset can report comes back as our copy. If this
     * fails, the dimension is generating with vanilla biomes and therefore vanilla features.
     */
    @GameTest(template = "ddt_empty")
    public static void paletteBiomeSourceSubstitutesThePreset(GameTestHelper helper) {
        var access = helper.getLevel().getServer().registryAccess();
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        Registry<MultiNoiseBiomeSourceParameterList> presets =
            access.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        Holder<MultiNoiseBiomeSourceParameterList> overworld = presets.getHolderOrThrow(ResourceKey.create(
            Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, ResourceLocation.withDefaultNamespace("overworld")));
        HolderSet<Biome> palette = biomes.getTag(TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "palette_biomes"))).orElseThrow();
        helper.assertTrue(palette.size() > 40,
            "the palette tag must resolve to the biome copies, got " + palette.size());

        PaletteBiomeSource source = new PaletteBiomeSource(MultiNoiseBiomeSource.createFromPreset(overworld), palette);
        Set<ResourceLocation> leaked = new LinkedHashSet<>();
        int sampled = 0;
        for (double temperature : new double[] {-1.0D, -0.5D, 0.0D, 0.5D, 1.0D}) {
            for (double humidity : new double[] {-1.0D, 0.0D, 1.0D}) {
                for (double continentalness : new double[] {-1.0D, -0.5D, 0.0D, 0.5D, 1.0D}) {
                    for (double erosion : new double[] {-1.0D, 0.0D, 1.0D}) {
                        for (double weirdness : new double[] {-1.0D, -0.5D, 0.0D, 0.5D, 1.0D}) {
                            Climate.Sampler sampler = new Climate.Sampler(
                                DensityFunctions.constant(temperature),
                                DensityFunctions.constant(humidity),
                                DensityFunctions.constant(continentalness),
                                DensityFunctions.constant(erosion),
                                DensityFunctions.constant(0.0D),
                                DensityFunctions.constant(weirdness),
                                List.of());
                            Holder<Biome> got = source.getNoiseBiome(0, 16, 0, sampler);
                            sampled++;
                            ResourceLocation key = got.unwrapKey().map(ResourceKey::location).orElse(null);
                            if (key == null || !MOD_ID.equals(key.getNamespace())) {
                                leaked.add(key);
                            }
                        }
                    }
                }
            }
        }
        helper.assertTrue(leaked.isEmpty(),
            "these biomes were not substituted out of " + sampled + " samples: " + leaked);
        helper.succeed();
    }
}
