package com.dawnduskterminal.world;

import com.dawnduskterminal.DawnDuskTerminal;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Points the chrono dimension at its own copies of the overworld biomes.
 *
 * <p>The overworld's climate-to-biome table cannot be expressed in a datapack: {@code
 * MultiNoiseBiomeSourceParameterList.DIRECT_CODEC} has a single {@code preset} field and the table
 * itself is built in code by {@code OverworldBiomeBuilder}. So rather than replacing the table, this
 * source keeps the vanilla preset and swaps every biome it reports for the copy of the same name in
 * the {@code dawnduskterminal} namespace.
 *
 * <p>Those copies carry the chrono block palette in their feature lists, which is what makes the
 * dimension place its own blocks while it generates instead of generating vanilla ones and patching
 * them up afterwards.
 */
public final class PaletteBiomeSource extends BiomeSource {
    public static final MapCodec<PaletteBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        BiomeSource.CODEC.fieldOf("delegate").forGetter(PaletteBiomeSource::delegate),
        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("palette").forGetter(PaletteBiomeSource::palette)
    ).apply(inst, PaletteBiomeSource::new));

    private final BiomeSource delegate;
    private final HolderSet<Biome> palette;
    /**
     * Built lazily, and deliberately so: the biome source is constructed while the datapack is still
     * being parsed, and at that moment the holders coming out of the {@code #tag} have no keys yet -
     * {@code unwrapKey()} is empty, so building this map in the constructor silently produced an empty
     * map and the substitution did nothing. By the first chunk the tag is bound and the keys are
     * there. Volatile because chunk generation is multi-threaded; the build is idempotent.
     */
    private volatile Map<ResourceLocation, Holder<Biome>> replacements;
    private volatile Holder<Biome> floatrees;

    public PaletteBiomeSource(BiomeSource delegate, HolderSet<Biome> palette) {
        this.delegate = delegate;
        this.palette = palette;
    }

    /**
     * The overworld climate table is built in code (OverworldBiomeBuilder), so no datapack can add a
     * biome to a custom climate range. The floatrees biome therefore rides on this source: wherever the
     * sampled depth lands in (-1.5, -1.3) it is returned directly, before the vanilla delegate runs.
     */
    private Holder<Biome> floatrees() {
        Holder<Biome> holder = this.floatrees;
        if (holder == null) {
            for (Holder<Biome> candidate : this.palette) {
                if (candidate.is(ResourceKey.create(Registries.BIOME, DawnDuskTerminal.id("floatrees")))) {
                    holder = candidate;
                    break;
                }
            }
            this.floatrees = holder;
        }
        return holder;
    }

    private Map<ResourceLocation, Holder<Biome>> replacements() {
        Map<ResourceLocation, Holder<Biome>> map = this.replacements;
        if (map == null) {
            Map<ResourceLocation, Holder<Biome>> built = new HashMap<>();
            for (Holder<Biome> holder : this.palette) {
                holder.unwrapKey().ifPresent(key -> built.put(
                    ResourceLocation.withDefaultNamespace(key.location().getPath()), holder));
            }
            if (built.isEmpty()) {
                DawnDuskTerminal.LOGGER.error(
                    "DawnDuskTerminal palette biome source resolved no biome copies ({} entries in the tag); "
                        + "the chrono dimension would generate with vanilla biomes", this.palette.size());
            }
            this.replacements = map = Map.copyOf(built);
        }
        return map;
    }

    public BiomeSource delegate() {
        return this.delegate;
    }

    public HolderSet<Biome> palette() {
        return this.palette;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(this.delegate.possibleBiomes().stream(), this.palette.stream());
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        Holder<Biome> floatreesHolder = floatrees();
        if (floatreesHolder != null) {
            float depth = Climate.unquantizeCoord(sampler.sample(quartX, quartY, quartZ).depth());
            if (depth < -1.6F) {
                return floatreesHolder;
            }
        }
        Holder<Biome> base = this.delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        ResourceLocation key = base.unwrapKey().map(ResourceKey::location).orElse(null);
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            return base;
        }
        Holder<Biome> replacement = replacements().get(key);
        return replacement != null ? replacement : base;
    }
}
