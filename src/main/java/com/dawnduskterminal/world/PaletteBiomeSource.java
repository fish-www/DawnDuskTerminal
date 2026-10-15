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
 * 让晨昏线维度用自己那份主世界群系副本
 *
 * <p>主世界的气候到群系表没法用数据包表达：{@code MultiNoiseBiomeSourceParameterList.DIRECT_CODEC} 只有
 * 一个 {@code preset} 字段，表本身由 {@code OverworldBiomeBuilder} 在代码里构建。所以这里不替换表，而
 * 是保留原版预设，把它报出的每个群系换成 {@code dawnduskterminal} 命名空间下的同名副本
 *
 * <p>这些副本的特征表里带着晨昏线方块调色板，维度因此能在生成时就铺自己的方块，而不是先生成原版的再
 * 事后打补丁
 */
public final class PaletteBiomeSource extends BiomeSource {
    public static final MapCodec<PaletteBiomeSource> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        BiomeSource.CODEC.fieldOf("delegate").forGetter(PaletteBiomeSource::delegate),
        RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("palette").forGetter(PaletteBiomeSource::palette)
    ).apply(inst, PaletteBiomeSource::new));

    private final BiomeSource delegate;
    private final HolderSet<Biome> palette;
    /**
     * 有意延迟构建：群系源是在数据包还在解析时构造的，那一刻从 {@code #tag} 取出的 holder 还没有 key，
     * {@code unwrapKey()} 是空的，所以在构造函数里建这张表只会静默得到空表，替换等于没做。到第一个区块
     * 时标签已绑定，key 就在了。用 volatile 是因为区块生成是多线程的，构建本身幂等
     */
    private volatile Map<ResourceLocation, Holder<Biome>> replacements;
    private volatile Holder<Biome> floatrees;

    public PaletteBiomeSource(BiomeSource delegate, HolderSet<Biome> palette) {
        this.delegate = delegate;
        this.palette = palette;
    }

    /**
     * 主世界的气候表是代码构建的（OverworldBiomeBuilder），数据包无法把群系加进自定义气候区间。所以
     * floatrees 群系搭在这个群系源上：采样深度低于 -1.6 时直接返回它，早于原版 delegate 运行
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
