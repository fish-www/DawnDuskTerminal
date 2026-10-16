# 世界生成

维度 id `dawnduskterminal:chrono`

生成类型 `minecraft:noise`

群系源 `dawnduskterminal:palette`

地形设置 `dawnduskterminal:chrono`

思路是把原版主世界世界生成整份移植进本模组命名空间，再把方块换成自己的调色板

相关文件都在 `src/main/resources/data/dawnduskterminal/`

## 维度类型

文件 `dimension_type/chrono.json`

`fixed_time` 为 12000，锁在黄昏，维度内没有昼夜

`bed_works` 为 true，允许睡觉，只重置重生点

`coordinate_scale` 为 1.0

`has_skylight` 为 true

`has_ceiling` 与 `has_raids` 为 false

`ambient_light` 为 0.0

`height` 与 `logical_height` 都是 384

`min_y` 为 -64

`effects` 指向 `dawnduskterminal:chrono`，客户端特效在 `client/ChronoDimensionEffects`

`monster_spawn_light_level` 是 0 到 7 的 uniform

## 地形设置

文件 `worldgen/noise_settings/chrono.json`

`default_block` 是 `dawnduskterminal:canopy_stone`

`default_fluid` 是 `dawnduskterminal:portal_fluid`，等级 0

`aquifers_enabled` 是 true

`sea_level` 是 63

`ore_veins_enabled` 是 true

`lava` 噪声指向 `dawnduskterminal:aquifer_lava`

`noise` 的 `height` 384，`min_y` -64

`noise_router` 取自移植后的原版主世界路由

`surface_rule` 是一张按群系分支的大表，默认产物是 `canopy_stone`

`spawn_target` 沿用原版主世界

## 移植文件

`tools/port_overworld_worldgen.py` 从 Gradle 缓存里的反编译客户端 jar 抽取原版
`density_function` `noise` `noise_settings`，改写命名空间后写入本模组

`density_function/` 含 `overworld/` 子目录

`noise/` 是移植过来的噪声参数

`multi_noise_biome_source_parameter_list/` 是原版主世界与下界的多噪声参数表副本

`configured_carver/` 是洞穴与峡谷

`configured_feature/` 与 `placed_feature/` 是移植过来的特征

移植脚本把群系里的放置特征引用、以及放置特征里的配置特征引用都改写到 `dawnduskterminal:` 命名空间

`configured_feature` 内部嵌套的引用不在改写范围内，`random_selector` 的分支仍指向 `minecraft:`，见「已知问题」

## 调色板群系源

`world/PaletteBiomeSource`，注册为 `dawnduskterminal:palette`

维度 JSON 里的 `biome_source` 有 `delegate` 与 `palette` 两个字段

`delegate` 是原版 `multi_noise` 的主世界预设

`palette` 指向 `#dawnduskterminal:palette_biomes`

`getNoiseBiome` 先问 delegate 要群系，再把 `minecraft:` 命名空间的结果换成同名副本

换名按路径匹配，例如 `minecraft:plains` 变成 `dawnduskterminal:plains`

副本携带本模组的方块特征表，所以维度在生成期就铺自己的方块，而不是事后打补丁

`worldgen/biome/` 里除 `floatrees` 外都是与主世界群系同名的副本

`floatrees` 不走替换表，采样深度低于阈值时直接返回

替换表在第一次使用时构建，因为 biome source 在数据包解析期创建，那一刻标签 holder 还没有 key

## 群系温度与晨昏线偏移

`NoiseChunkTemperatureMixin` 把维度的温度密度函数包进 `ChronoTemperature`

温度噪声是 `shifted_noise`，噪声 `dawnduskterminal:temperature`，`amplitudes` 为 `[1.5, 0, 1, 0, 0, 0]`，`firstOctave` 为 -10

它的声明值域是 `NormalNoise.maxValue`：`PerlinNoise.edgeValue(2.0)` 在这个振幅表上得到 1.7778，乘 2 再乘 `valueFactor` 1.25 得 `±4.4444`

`ChronoTemperature.offset` 是 `line.param(x, z) * 1.5`，而 `param` 已经钳在 `[-1, 1]`，所以偏移量恒在 `[-1.5, 1.5]`

包装器的 `minValue` / `maxValue` 取 `delegate ± 1.5`，声明值域因此变成 `±5.9444`。这一步是对的，扩宽确实发生了

### 群系选择会饱和

值域扩宽了，但群系选择看不到 `±5.9444`

`Climate.quantizeCoord` 把温度乘 10000 取整

原版 `OverworldBiomeBuilder` 的温度参数点只覆盖 `[-1.0, 1.0]`，分五段 `[-1, -0.45] [-0.45, -0.15] [-0.15, 0.2] [0.2, 0.55] [0.55, 1]`

`Climate.Parameter.distance` 在区间内返回 0，在区间外返回到最近边界的距离。所以超出 `±1.0` 的温度不是被钳到某一个固定群系，而是让那几个极端温度参数点以固定距离胜出

结果是昼侧深处与夜侧深处各有一大片区域落在极端参数点上，偏移再大也不再改变选择。想知道「极昼侧」到底有多宽，看 `line.param` 就行，不用看温度

### 想加群系得绕过气候表

`PaletteBiomeSource` 的 `delegate` 是原版 `multi_noise` 预设，参数表由 `OverworldBiomeBuilder` 在代码里构建，数据包改不了

所以在 `[-1, 1]` 之外的温度区间上没法用气候表加群系，改了 `PaletteBiomeSource` 的替换表也没用，那张表只做同名替换

要往扩宽过的值域里加群系，只能在 `PaletteBiomeSource.getNoiseBiome` 里自己判定，直接返回目标群系

判定的输入可以有两种：`sampler.sample(...)` 的采样值，或 `ChronoLineState.serverLine().param(x, z)`

本模组用的是后者。`param` 是纯坐标函数，不受噪声影响，边界是一条直的晨昏线而不是一条噪声曲线，而且与极夜进度、天光衰减共用同一个 `-0.6`

目前用了这条路的两个群系：`floatrees`（按 `depth`）与 `dawnduskterminal:desert`（按 `param`）

## 标签

`tags/worldgen/biome/palette_biomes.json` 列出全部群系副本

`tags/block/` 是本模组方块标签，供移植过的特征引用

`data/minecraft/tags/worldgen/biome/` 把副本挂进结构生成与刷怪规则

`data/minecraft/tags/block/` 把模组方块加进原版石类 土类 沙类 树叶等标签

## 世界生成补丁

数据包够不到的原版硬编码由 mixin 接管，逐条见 `architecture.md`

`LavaBandPaletteMixin` 处理 y 以下整条岩浆带

`LavaAquiferPaletteMixin` 处理深部岩浆含水层

`PillarsFluidBlockMixin` 让石柱洞穴保持干燥

`NoiseChunkVeinPaletteMixin` 处理大矿脉的花岗岩与凝灰岩

`NoiseChunkTemperatureMixin` 给温度噪声加晨昏线偏移，群系随昼夜侧分布

`MineshaftHeightMixin` 与 `MineshaftChestLootMixin` 限制废弃矿井高度并换战利品表

识别维度统一靠噪声设置的 `default_fluid` 是不是 `portal_fluid`

## 结构

`structure/StructureExclusions` 是白名单，只放行废弃传送门 沉船 前哨站 沙漠神殿 雪屋 废弃矿井，以及暮色森林的树洞 倒下空心木 蘑菇城堡 营地 树篱迷宫 空心矿山

`StructureExclusionMixin` 排除不在白名单里的结构

同一文件里的 `UNMAPPED` 与 `LANDMARKS` 记录未放行的暮色森林结构与地标，只服务日志，不影响行为

## 高度与空置域

地形高度范围 -64 到 320

原版主世界的密度函数被整份移植，所以地形形态与原版一致

空置域与空岛属于旧设计，已在移植中移除

## 已知问题

`data/dawnduskterminal/tags/worldgen/biome/` 下的旧标签只保留 `palette_biomes`

`configured_feature` 里部分 `random_selector` 分支仍指向 `minecraft:`，对应的配置特征副本没有任何特征引用

## 改地形注意

改世界生成后建议删掉 `run/<端>/world` 重建

天光烘焙进区块，改天光后老存档要重算

`AquiferTests` 与 `WorldgenTests` 会断言调色板与流体，改前先看测试
