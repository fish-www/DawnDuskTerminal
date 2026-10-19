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
 *
 * <p>分带交给温度：{@link ChronoTemperature} 把 {@code param} 分段映射到原版气候表的温度档，每一带只
 * 对应一档温度，于是群系全部按六要素（温度 湿度 大陆性 侵蚀 深度 怪异度）从表里选出来
 *
 * <p>唯一的干预是 {@link #replacements()} 里改指三个格子：热档的沙漠换白地（昼区），冻结档的海岸与
 * 河床换蓝沙沙漠（夜区）。白地与蓝沙沙漠都是自建 ID，原版气候表里没有它们的格子，只能这么挂上去。改指只决定某一格装哪个副本，选格仍然是六要素算出来的，所以极带
 * （{@code |param| >= 0.8}）眼下与相邻带同群系，极昼极夜专用的群系留到以后再加
 *
 * <p>占位群系不改注册名，显示名在 lang 里换。分带表见 {@code docs/worldgen.md} 的「温带分带与温度映射」
 */
public final class PaletteBiomeSource extends BiomeSource {
    /** 天门（空岛）的深度门槛 */
    private static final float FLOATREES_DEPTH = -1.6F;

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
     * 自建的空岛群系只能搭在气候表之前：采样深度低于阈值时给 floatrees
     */
    private Holder<Biome> floatrees() {
        Holder<Biome> holder = this.floatrees;
        if (holder == null) {
            this.floatrees = holder = copyOf("floatrees");
        }
        return holder;
    }

    /** 从调色板标签里按下标路径找同名副本，找不到返回 null */
    private Holder<Biome> copyOf(String path) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, DawnDuskTerminal.id(path));
        for (Holder<Biome> candidate : this.palette) {
            if (candidate.is(key)) {
                return candidate;
            }
        }
        return null;
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
            // 同名格子自己就对上了，只有三格要改指。热档那格原版是遍地的沙漠，换成白地：昼区
            // 0.6~0.8 的温度被钉在 0.55~1.0，那档里五档湿度原版全是沙漠，不改指就整片是沙
            remap(built, "desert", copyOf("white_land"));
            // 蓝沙沙漠的地表是自建的 road_sand，原版气候表里没有它的格子，所以借冻结档的两个格子生成：
            // 夜区 -0.8~-0.6 的海岸与河床。它自己的 ID 是 blue_desert，跟原版 desert 那格没有关系
            Holder<Biome> blueSand = copyOf("blue_desert");
            remap(built, "snowy_beach", blueSand);
            remap(built, "frozen_river", blueSand);
            this.replacements = map = Map.copyOf(built);
        }
        return map;
    }

    /** 把原版某一格改指到副本；副本不在调色板标签里就保持原样 */
    private static void remap(Map<ResourceLocation, Holder<Biome>> map, String vanillaPath, Holder<Biome> copy) {
        if (copy != null) {
            map.put(ResourceLocation.withDefaultNamespace(vanillaPath), copy);
        }
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
            if (depth < FLOATREES_DEPTH) {
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
