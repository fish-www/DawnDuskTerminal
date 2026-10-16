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
 * <p>副本的位置也可以在这里改写：{@link #nightDesert()} 把蓝沙沙漠副本挪到极夜侧的极低温区，
 * 热区原版沙漠那格改指恶地副本。改位置只能走这条路，原版气候表的温度参数点只覆盖 {@code [-1, 1]}，
 * 越过边界的温度在 {@code Climate.Parameter.distance} 那里只是离边界远了一点，并不会选中新格子，
 * 见 {@code docs/worldgen.md} 的「群系温度与晨昏线偏移」
 */
public final class PaletteBiomeSource extends BiomeSource {
    /** 极夜侧的门槛，与 {@code polar_dusk} 进度和天光衰减用的是同一个数 */
    private static final float POLAR_NIGHT_PARAM = -0.6F;

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
    private volatile Holder<Biome> nightDesert;

    public PaletteBiomeSource(BiomeSource delegate, HolderSet<Biome> palette) {
        this.delegate = delegate;
        this.palette = palette;
    }

    /**
     * 主世界的气候表是代码构建的（OverworldBiomeBuilder），数据包无法把群系加进自定义气候区间。所以
     * 本群系源的两个特例都搭在气候表之前：采样深度低于阈值时给 floatrees，采样点落在极夜侧时给蓝沙沙漠
     */
    private Holder<Biome> floatrees() {
        Holder<Biome> holder = this.floatrees;
        if (holder == null) {
            this.floatrees = holder = copyOf("floatrees");
        }
        return holder;
    }

    /**
     * 蓝沙沙漠副本，只出现在极夜侧
     *
     * <p>判定读晨昏线的 {@code param}，不读采样到的温度。温度那条路走不通：温度偏移量只是
     * {@code param * 1.5}，而原版气候表的温度参数点只覆盖 {@code [-1, 1]}，越过边界的温度只会让那几个
     * 极端参数点以固定距离胜出，换不出新群系
     *
     * <p>读 {@code param} 还有个好处：这条边界与极夜进度、{@link ChronoSkyLight} 的衰减落在同一个数上，
     * 改动只有一处
     */
    private Holder<Biome> nightDesert() {
        Holder<Biome> holder = this.nightDesert;
        if (holder == null) {
            this.nightDesert = holder = copyOf("desert");
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
            // 蓝沙沙漠已经由 nightDesert() 挪去极夜侧，热区那格再指回它就会让同一个群系在冷热两端都出现
            // 改指恶地副本。不这么做的话热区会退回原版 minecraft:desert，把原版沙子与原版特征带进维度
            Holder<Biome> badlands = built.get(ResourceLocation.withDefaultNamespace("badlands"));
            if (badlands != null) {
                built.put(ResourceLocation.withDefaultNamespace("desert"), badlands);
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
            if (depth < FLOATREES_DEPTH) {
                return floatreesHolder;
            }
        }
        // 偏移量是按方块坐标算的（ChronoTemperature 用的也是 blockX），这里按四格粒度还原成方块坐标
        Holder<Biome> nightDesertHolder = nightDesert();
        if (nightDesertHolder != null
            && ChronoLineState.serverLine().param(quartX * 4, quartZ * 4) <= POLAR_NIGHT_PARAM) {
            return nightDesertHolder;
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
