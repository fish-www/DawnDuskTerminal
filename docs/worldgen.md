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

`effects` 指向 `minecraft:overworld`，天空与雾色走原版特效

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

`configured_feature` 内部嵌套的引用不在改写范围内，所以 `random_selector` 的 `feature` 与 `default` 两个键指向原版的那些得手工补，见「已知问题」

## 调色板群系源

`world/PaletteBiomeSource`，注册为 `dawnduskterminal:palette`

维度 JSON 里的 `biome_source` 有 `delegate` 与 `palette` 两个字段

`delegate` 是原版 `multi_noise` 的主世界预设

`palette` 指向 `#dawnduskterminal:palette_biomes`

`getNoiseBiome` 先问 delegate 要群系，再把 `minecraft:` 命名空间的结果换成同名副本

换名按路径匹配，例如 `minecraft:plains` 变成 `dawnduskterminal:plains`

副本携带本模组的方块特征表，所以维度在生成期就铺自己的方块，而不是事后打补丁

这句话只对一部分特征成立：57 个 `configured_feature` 里 46 个引用了模组方块或模组方块标签（`ore_*` `glow_lichen` `ice_patch` `patch_berry_bush` `swamp_oak` 等），其余 11 个是树，只摆原版原木与树叶

地形的方块替换不靠特征表，靠 `noise_settings` 里的地表规则

`worldgen/biome/` 里除 `floatrees` `red_desert` `white_land` 外都是与主世界群系同名的副本

副本里的内容不等于同名的原版群系：暮色森林的群系直接写进对应的原版位置，ID 不改，显示名靠 lang。每个位置现在装的是什么，账本在 `docs/biomes.md`

`floatrees` 不走替换表，采样深度低于 -1.6 时直接返回，早于 delegate

替换表在第一次使用时构建，因为 biome source 在数据包解析期创建，那一刻标签 holder 还没有 key

### 蓝沙沙漠在极夜侧

`dawnduskterminal:desert`（地表是蓝色的 `road_sand`）不在热区，它只出现在极夜侧

`getNoiseBiome` 里第二个特例：`ChronoLineState.serverLine().param(quartX * 4, quartZ * 4) <= -0.6` 时直接返回沙漠副本，早于 delegate

读 `param` 而不读采样温度的理由见下节；`-0.6` 与 `polar_dusk` 进度、天光衰减是同一个数

`floatrees` 仍然排在这条判定之前，所以极夜侧的空岛还是空岛

原来那格热区沙漠改指红色沙漠副本 `dawnduskterminal:red_desert`。不改的话热区会退回原版 `minecraft:desert`，把原版沙子与原版特征带进维度

副本本身的 `temperature` 是 `-0.7`、`has_precipitation` 是 `true`，与维度里最冷的 `frozen_peaks` 同值，所以极夜侧是会积雪的寒漠

这两项必须改：它们决定 `Biome#coldEnoughToSnow` 与 `Biome#shouldSnow`，而 `ChronoFreezeFeature` 靠这两个判断铺 `canopy_frost` 与 `frozen_canopy`

原版沙漠的 `2.0` 与 `false` 会让极夜侧一片霜都没有，蓝沙上直接冒出海冰与雪层的前提是先把它改成冷湿

### 红色沙漠在极昼侧

`dawnduskterminal:red_desert` 是昼侧的对应物，地表是 `unconscious_sand`（原版红沙的副本）

`getNoiseBiome` 里第三个特例：`param(quartX * 4, quartZ * 4) >= 0.8` 时直接返回它

门槛比极夜侧的 `-0.6` 深，`0.6` 到 `0.8` 那一段有意留空，给后面的白地占位

`temperature` 是 `2.0`、`has_precipitation` 是 `false`，所以不会积雪，`ChronoFreezeFeature` 在这一侧什么也不铺

地表规则和蓝沙沙漠那三条一一对应，只把 `road_sand` 换成 `unconscious_sand`

它按原版沙漠的镜像进了 `minecraft` 那几条群系标签（`is_overworld` 与沙漠村庄、沙漠神庙那几条结构），否则结构白名单和刷怪规则认不出它

### 恶地照旧

`dawnduskterminal:badlands` 一族不再兼任热区沙漠，只走原版气候表里恶地那几格

### 白地在极昼侧 0.6 到 0.8

`dawnduskterminal:white_land` 占极昼侧的红沙漠与温和带之间那一段，是恶魂支线 T2 群系的位置

分档抽在 `PaletteBiomeSource#dayBand(double)` 里，纯函数，边界由 `WorldgenTests` 直接断言：0.6 起白地，0.8 起红沙漠

白地是裸地，地表规则铺 `siltstone`，特征是恶地副本削掉地表装饰之后的样子，矿石与洞穴照旧

`temperature` 是 `2.0`、`has_precipitation` 是 `false`，所以不积雪也不长霜

### 萤火虫森林

`dawnduskterminal:birch_forest` 装的是暮色森林 `firefly_forest` 的移植结果（树 结构 灌木 浆果 萤火虫粒子都原样），`temperature` 0.5、`has_precipitation` `true`

ID 仍是 `birch_forest`，因为占位不改注册名，看 ID 就知道它顶的是哪格。显示名在 lang 里改成「萤火虫森林」

### 红草树林

`dawnduskterminal:old_growth_birch_forest` 装的是上面那份萤火虫森林内容，只把草地与树叶染红：`grass_color` 与 `foliage_color` 给的是红色，`temperature` 提到 `0.8`

同样不改 ID，显示名在 lang 里改成「红草树林」。这样 `birch_tall` 这个特征就没有人引用了，见下文「特征移植」

### 黑森林腹地

`dawnduskterminal:dark_forest` 就是暮色森林的 `dark_forest_center`，见 `tools/port_tf_biomes.py` 的对照表

树叶按 `ChronoFoliageColors` 固定染成 `0xE94E14`，草方块走暮色森林的 `dark_forest_center` 调色器

### 天门松

`configured_feature/tree/skygate_pine.json` 是文竹式分层的树：20~30 格直筒树干，4~8 根枝撒在树干 28% 到 90% 的高度上，每根枝顶一团压扁的樱花叶团。

树干用模组自己的 `dawnduskterminal:skygate_branching`（`world/SkygateBranchTrunkPlacer`）：原版 `cherry_trunk_placer` 最多三根枝且贴顶，暮色森林的 `branching_trunk_placer` 分枝是逐格往上叠的连排，都铺不满树干中下部，所以高度是代码里算的。枝的方向按方位角逐根随机，落点取在圆周上再走单格轴向的斜线，所以斜枝（45 度、30 度这类）也会长。可调项：

| 字段 | 作用 |
|---|---|
| `branch_count` | 分枝根数，`IntProvider` |
| `branch_length` | 每根枝在圆周上的伸出半径，`IntProvider` |
| `branch_band` | `{min, max}`，分枝高度带占树干高度的比例 |
| `branch_rise_chance` | 每走一格同时抬高一格的概率 |
| `radius_offsets.branch` | 每根枝的树冠水平尺寸偏移，`IntProvider`，逐枝抖动 |
| `radius_offsets.crown` | 树干顶上那团的水平尺寸偏移，固定值，比枝顶大一档用来封顶 |

叶团是 `minecraft:cherry_foliage_placer`：`height` 取到该 placer 允许的下限 4（原版樱花是 5），垂直尺寸就定死在这儿；`radius` 随树浮动，再叠上 `radius_offsets`，所以横向比原版樱花大一圈而且每团不一样大。注意 placer 内部的 `i = radius + radiusOffset - 1`，最宽的一层在下数第二层、半径就是 `i`：`radius` 取 3、`branch` 取 -1 时 `i` 只剩 1，叶团会缩成 3x3 的薄片，别再往下调。

挂藤用原版的 `minecraft:leave_vine`：它在树叶侧面的空位挂藤，再往下垂一到四格，正好是「藤长在树叶边上」。它只有一个 `probability`，是**每片树叶每个侧面各掷一次**，所以密度随树冠大小线性涨：`0.25`（原版丛林树的取值）在这棵树上是几百个藤方块，`0.01` 实测 16 个藤方块、7 条藤（叶约 450 片）。调密度按线性换算。

别用 `minecraft:trunk_vine`：它挂的是树干，每根原木每面 2/3，二十多格的树干会挂成藤柱。本模组曾经自己写过 `sparse_leaf_vine`，按「条数」随机挑树叶再往旁边挂，结果绝大多数随机数落在叶团内部（旁边不是空气），实际挂出来的条数远少于配置值，已删除。

`minimum_size` 是 `limit 1` / `lower_size 0` / `upper_size 2`，跟原版樱花一致；旧配置的 `limit 20` + `upper_size 5` 等于要求树干高处 11x11 全是空地，树几乎长不出来。

形状由 `SkygatePineTests` 断言：树高 20~30、分枝至少覆盖四个不同高度且同时落在树干下半与上三分之一。

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

## 高度

地形高度范围 -64 到 320

原版主世界的密度函数被整份移植，所以地形形态与原版一致

空岛与地下浮岛还在：`sloped_cheese` 里叠加的 `sky_island_density` 与 `subterranean` 分别管 y 高处与 y<0

早期的四带设计（大陆 空置域 空岛 地下岛）在接管地形噪声那一次被整份替换，`VoidRegionFeature` 一并删除

## 已知问题

`data/dawnduskterminal/tags/worldgen/biome/` 下的旧标签只保留 `palette_biomes`

### 引用指向哪个注册表

`random_selector` 的两个位置看着像，指向的注册表不同：

| 形态 | 指向 |
|---|---|
| `{"feature": "X", "placement": [...]}` 里的 `X` | `configured_feature` |
| 裸字符串 `"default": "X"` 或不在内联对象里的 `"feature": "X"` | `placed_feature` |

移植脚本只改了「群系 → 放置特征」和「放置特征 → 配置特征」两层，`configured_feature` 内部的引用没动

后果不是报错而是静默：模组副本把树下的土换成了 `dawnduskterminal:rosy_dirt`，原版那份不会，所以同一片林子里一半的树长在霞土上，另一半长在原版泥土上

已经补完，两件事：

- 内联对象里指向原版的配置特征引用改成模组副本
- 裸字符串指向的那几个原版放置特征（`birch_bees_0002` `oak_bees_002` `jungle_tree` 等 11 个）在模组里补了对应的 `placed_feature` 副本，再把引用指过去

模组现在剩 `swamp_oak` 一个 `configured_feature` 副本没人引用

`birch_tall` 也要算上，但它有个自己那份 `placed_feature` 引用着，而那份 `placed_feature` 已经没群系要了，工具的字符串匹配看不出这种间接孤儿

`tools/check_worldgen_refs.py --list-dead` 会打印它的判定结果

`tools/check_worldgen_refs.py --list-dead` 会打印这两个名字

### 没人引用的放置特征

有 18 个放置特征副本没有任何群系引用：`ore_coal_lower` `ore_coal_upper` `ore_copper` `ore_copper_large` `ore_gold` `ore_gold_extra` `ore_gold_lower` `ore_iron_middle` `ore_iron_small` `ore_iron_upper` `bamboo_light` `dark_forest_vegetation` `flower_flower_forest` `flower_swamp` `red_mushroom_swamp` `trees_swamp` `forest_flowers` `patch_grass_forest`

它们在原版主世界群系里，而本维度的群系特征表已经整份换成暮色森林内容（`tools/port_tf_biomes.py`），所以煤 铜 金 铁四种矿在本维度不生成，改由暮色森林的浆果矿与模组自己的矿替代

这是设计选择不是 bug，但副本本身是移植残留，删不删待定

## 改地形注意

改世界生成后建议删掉 `run/<端>/world` 重建

天光烘焙进区块，改天光后老存档要重算

`AquiferTests` 与 `WorldgenTests` 会断言调色板与流体，改前先看测试
