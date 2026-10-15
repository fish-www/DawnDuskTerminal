# 世界生成

维度 id `dawnduskterminal:chrono`

生成类型 `minecraft:noise`

群系源 `dawnduskterminal:chrono`

地形设置 `dawnduskterminal:chrono`

相关文件都在 `src/main/resources/data/dawnduskterminal/`

## 维度类型

文件 `dimension_type/chrono.json`

`fixed_time` 为 12000，锁在黄昏，维度内没有昼夜

`bed_works` 为 true，允许睡觉，只重置重生点

`coordinate_scale` 为 1.0，坐标一比一

`has_skylight` 为 true

`has_ceiling` 为 false

`has_raids` 为 false

`ambient_light` 为 0.0

`height` 与 `logical_height` 都是 368

`min_y` 为 -48

`effects` 指向 `dawnduskterminal:chrono`，客户端特效在 `client/ChronoDimensionEffects`

`monster_spawn_light_level` 是 0 到 7 的 uniform

## 地形设置

文件 `worldgen/noise_settings/chrono.json`

`default_block` 是 `dawnduskterminal:sky_stone`

`default_fluid` 是 `minecraft:water`

`aquifers_enabled` 是 true

`sea_level` 是 62

`ore_veins_enabled` 是 false

`lava` 噪声是常量 0.0

`noise` 的 `height` 368，`min_y` -48，`size_horizontal` 1，`size_vertical` 2

`disable_mob_generation` 是 false

`legacy_random_source` 是 false

`continents` `depth` `erosion` `ridges` 直接取原版主世界

`temperature` 与 `vegetation` 用原版噪声加偏移

`barrier` 用 `minecraft:aquifer_barrier`

`fluid_level_floodedness` 取含水层噪声与一条 8 到 40 的梯度中的较小值，所以在 y 小于等于 8 处恒为 -1，地下不生成水

`fluid_level_spread` 用 `minecraft:aquifer_fluid_level_spread`

`vein_toggle` `vein_ridged` `vein_gap` 保留但配合 `ore_veins_enabled=false` 不生成原版大矿脉

`surface_rule` 是单条，条件为 `above_preliminary_surface` 加 `stone_depth` 的 floor，产物是 `dawnduskterminal:sky_soil`

## 最终密度

文件顺序

`final_density = min( max( max(mainland_windowed, underground_island), sky_island_density ), void_band )`

分四个高度带

地下岛 `underground_island`

大陆 `mainland_windowed`

空置域由 `void_band` 归零

空岛 `sky_island_density`

### underground_island

文件 `density_function/underground_island.json`

用 `squeeze` 包 `interpolated` 包 `blend_density`

高度窗上沿 -45 到 -33 抬升，下沿 -27 到 0 收束

噪声是 `old_blended_noise`，`xz_factor` 80，`y_factor` 160，`smear_scale_multiplier` 8

### mainland

文件 `density_function/mainland_depth.json` `mainland_sloped_cheese.json` `vanilla_final_density.json` `mainland_window.json` `mainland_windowed.json`

`mainland_depth` 是 -7.6 到 205.8 的梯度，值 1.5 到 -1.5，再加原版 offset

`mainland_sloped_cheese` 用 `mainland_depth` 加原版 jaggedness 与 factor，再叠 base_3d_noise

`vanilla_final_density` 是原版主世界最终密度的拷贝，把 sloped_cheese 换成自写的 `mainland_sloped_cheese`

`mainland_window` 是 -8 到 0 的梯度，值 0 到 1

`mainland_windowed` 是 `vanilla_final_density` 乘窗口，窗口外减 10000，所以地面之下归为空气

### void_band

文件 `density_function/void_band.json`

`min(1.0, grad(126 到 128: 1 到 0) + grad(190 到 192: 0 到 1))`

结果是 y 在 128 到 190 之间恒为 0

所以 126 到 128 与 190 到 192 是两段过渡

### sky_island_density

文件 `density_function/sky_island_density.json`

用 `squeeze` 包 `interpolated` 包 `blend_density`

高度窗上沿 196 到 212 抬升，下沿 220 到 256 收束

噪声是 `old_blended_noise`，`xz_factor` 320，`y_factor` 160，`smear_scale_multiplier` 8

## 高度分带

维度 JSON 里的 `y_bands` 按高度强制群系

`-48` 到 `0` 是 `dawnduskterminal:earth_light`

`176` 到 `384` 是 `dawnduskterminal:sky_island`

`y_bands` 的优先级高于水平分区

## 自定义 Feature

全部在 `world/`，注册在 `registry/ModFeatures`

### turf

类 `TurfFeature`

配置 `min_y` `max_y` `soil_depth`

把 `sky_soil` 铺在 `sky_stone` 的露顶，下面几格换成 `ember_soil`

只处理是当前表面且上方是空气的 `sky_stone`

placed_feature `turf.json` 用 count 1 加 in_square 加 biome

### bedrock_layer

类 `BedrockLayerFeature`

配置 `range_min_y` `range_max_y` `thickness` `seal_y` `pocket_grid_chunks` `pocket_min_chunks` `pocket_max_chunks`

当前配置 `range_min_y` -8，`range_max_y` 48，`thickness` 3，`seal_y` -1，`pocket_grid_chunks` 219

铺 `chrono_crust`

用 3 乘 3 区块的地形高度算裙边，`MAX_SKIRT` 8

在空置域边缘挖穿，把基岩与地壳换成空气，形成上下贯通的口

### root_pillar

类 `RootPillarFeature`

把悬空的原木向下补柱子

只补下方空气 2 到 64 格的段落

### hollow_pocket

类 `HollowPocketFeature`

配置 `grid_chunks` `min_chunks` `max_chunks` `floor_y` `top_y`

当前 `grid_chunks` 219，`min_chunks` 8，`max_chunks` 12，`floor_y` -48，`top_y` 128

按网格单元挖出不定形状的空腔，边缘带噪声抖动

`BAND` 为 24，球形半径按分瓣与多频值噪声调制

### sky_island

类 `SkyIslandFeature`

配置 `spacing` `min_y` `max_y` `min_radius` `max_radius` `min_thickness` `max_thickness`

按网格单元确定性放置，一个单元一个岛

`spacing` 单位是区块

半径与厚度取区间随机，中心对齐到区块种子

用 3 个尺度的值噪声决定实心范围

填充 `sky_stone`，并按高度与 roll 撒矿

placed_feature `sky_island.json` 在 192 到 256 之间，count 1，in_square，biome

## 群系分区

群系源 `world/ChronoBiomeSource`

维度 JSON 的 `biome_source` 字段

`delegate` 是原版 `multi_noise` 的 overworld preset

`bands` 是四个越界群系，少于 4 个会退回 delegate

`line_half_width` 200，线两侧 200 格是线上群系

`band_half_width` 1000，换算成 param 的内边界

`max_distance` 5000

边界加 `BOUNDARY_JITTER` 60 格抖动

抖动基于 `BOUNDARY_SCALE` 128 的栅格值噪声

判定顺序是先 `y_bands`，再线上群系，再内外分区

`side_shift` 按 param 正负把 delegate 群系换成 hot 或 cold 自定义群系

`replacements` 与 `fallback` 做位置哈希替换

## 群系

`polar_dawn` 极昼平原，太阳最高

`dawn_zone` 晨区

`day` 昼区平原

`normal` 晨昏平原，线附近

`night` 夜区平原

`dusk_zone` 昏区

`polar_dusk` 极夜平原，太阳最低

`refusal_ocean` 昼侧海洋

`solidcanopy_ocean` 夜侧海洋

`earth_light` 地下带

`sky_island` 高空带

`cave` 溶洞

`lush_cave` 繁茂洞穴

群系标签 `chrono_surface` 收录了除两个洞穴群系外的全部

`is_polar_dawn` 与 `is_polar_dusk` 各只含对应极区群系

## biome_modifier

在 `data/dawnduskterminal/neoforge/biome_modifier/`

`chrono_turf` 给 chrono_surface 加 turf

`chrono_bedrock_layer` 加 bedrock_layer

`chrono_root_pillars` 加 root_pillar

`chrono_hollow_pockets` 加 hollow_pocket

`chrono_custom_trees` 加自定义树

`chrono_sky_ores` 加 8 种高空矿

`chrono_molybdenum_ore` 加绯红钼矿

`chrono_ores` 加一批自定义矿石

`chrono_spawns` 加刷怪配置

`chrono_scorching_lakes` 加热情的暮光湖

`chrono_feature_filter` 用 `dawnduskterminal:chrono_feature_filter` 移除非本模组特征

`chrono_remove_lava` 移除原版岩浆湖与岩浆泉

`chrono_remove_vanilla_ores` 移除原版矿石

`chrono_remove_vanilla_trees` 移除原版树

引用的 feature ID 必须真实存在，否则注册表加载失败

## 标签

方块标签写在 `data/minecraft/tags/block/`

`base_stone_overworld` 加自定义岩石

`stone_ore_replaceables` 加可换矿的岩石

`deepslate_ore_replaceables` 加 `peridotite`

`dirt` 加 `sky_soil` 与 `ember_soil`

`dirt_replaceable` 再加两种沙

`sand` 指向 `siltstone`

`ice` 指向 `frozen_canopy`

`leaves` 与 `logs` 收录自定义树叶与木头

## 矿物

自定义矿石方块在 `registry/ModChronoOres`，前缀 `chrono_ore_`

高空矿用 `sky_ore_` 系列 placed_feature，高度 196 到 236

绯红钼矿 `scarlet_molybdenum_ore` 在 2 到 51 之间生成

空岛 Feature 内部也按 roll 撒原版矿与绯红钼矿

## 结构

排除与复用表在 `structure/StructureExclusions` 与 `structure/StructurePlacements`

复用的结构来自原版与暮色森林

`StructureExclusionMixin` 在晨昏线维度拦截排除表里的结构

两张表在服务端启动时校验，缺失只记日志
