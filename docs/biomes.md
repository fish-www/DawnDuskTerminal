# 群系占位对照

## 约定：占位不改注册名

群系副本的 ID 永远等于它顶替掉的原版群系。想知道某个格子原来是什么，看 ID 就知道；玩家看到的名字由语言文件决定，等定稿再统一改。

所以暮色森林群系移植进来时，写的是 `birch_forest.json` 里装 `firefly_forest` 的内容，而不是新建 `firefly_forest.json`。只有完全没有同名原版格子的自建群系才用新 ID：`floatrees` `white_land` `blue_desert`。

## 替换链

维度不新增气候格：`dawnduskterminal:palette` 群系源的 delegate 就是原版主世界气候表。

1. 原版气候表选出 `minecraft:X`
2. 同名副本 `dawnduskterminal:X` 顶上，前提是它在 `palette_biomes` 标签里
3. 只有一格再改写，`PaletteBiomeSource#replacements()`

| 原版格子 | 实际群系 | 为什么改 |
|---|---|---|
| `minecraft:desert` | `dawnduskterminal:white_land` 白地 | 昼区的温度被钉在热档，气候表在那档里五档湿度全是沙漠，不改指整片都是沙 |

改指只决定某一格装哪个副本，选格仍然是气候表按六要素算的。极带（`|param| >= 0.8`）不用改指：那里的温度越过了原版参数表的覆盖范围，自建群系直接在 `extra` 里占了空地。

## 分带

群系落在哪一带不是靠替换表，而是靠温度：`ChronoTemperature` 把 `param` 分段映射到原版气候表的温度档，每一带只对应一档，详见 `docs/worldgen.md` 的「温带分带与温度映射」。

| 带 | `param` | 群系族 |
|---|---|---|
| 极夜 | `<= -0.8` | 极夜平原 `polar_dusk` 与蓝色沙漠 `blue_desert`（`extra` 里温度 `-2 ~ -1` 的两条，按湿度分开） |
| 夜区 | `-0.8 ~ -0.6` | 雪原 `snowy_plains` `snowy_taiga` `ice_spikes` 雪峰 冰川 |
| 寒带 | `-0.6 ~ -0.3` | `taiga` `plains` `old_growth_spruce_taiga` `grove` |
| 温带 | `-0.3 ~ 0.3` | `plains` `forest` `birch_forest` `dark_forest` `meadow` |
| 热带 | `0.3 ~ 0.6` | `savanna` `jungle` `sparse_jungle` `bamboo_jungle` |
| 昼区 | `0.6 ~ 0.8` | 白地 + 恶地族 |
| 极昼 | `>= 0.8` | 极昼平原 `polar_dawn`（`extra` 里温度 `1 ~ 2` 那条） |

## 装了暮色森林内容的副本

来源脚本 `tools/port_tf_biomes.py`。脚本保留副本原本的 `carvers`，把 `minecraft:*` 放置特征改指 `dawnduskterminal:*`，`twilightforest:*` 直接引用。

| 原版 ID | 装的暮色群系 | 当前显示名 | 设定稿名 |
|---|---|---|---|
| `dark_forest` | `dark_forest_center` | 黑森林 | 黑森林腹地 |
| `birch_forest` | `firefly_forest` | 萤火虫森林 | 萤火虫森林 |
| `flower_forest` | `dense_forest` | 繁花森林 | 繁密暮色森林 |
| `jungle` | `enchanted_forest` | 丛林 | 魔法森林 |
| `swamp` | `fire_swamp` | 沼泽 | 火焰沼泽 |
| `plains` | `clearing` | 平原 | 暮光平原 |
| `savanna` | `oak_savannah` | 热带草原 | 橡木热带草原 |
| `snowy_plains` | `glacier` | 雪原 | 暮色冰川 |
| `windswept_hills` | `highlands` | 风袭丘陵 | 荆棘高地 |
| `forest` | `twilightforest:forest` | 森林 | 暮色森林 |

最后一个 `forest` 不在脚本的对照表里，是手工移植的。设定稿那列的名字都还没进语言文件。

## 换了内容但没装暮色群系

| 原版 ID | 内容 | 显示名 |
|---|---|---|
| `old_growth_birch_forest` | 萤火虫森林那份移植再染红，`grass_color` `foliage_color` 给红色，`temperature` 0.8 | 红草树林 |

## 自建群系

没有同名原版格子，只能用新 ID。

| 模组 ID | 显示名 | 位置 | 说明 |
|---|---|---|---|
| `floatrees` | 天门 | 全维度 | 不走替换表，采样深度低于 -1.6 时直接返回，早于 delegate |
| `white_land` | 白地 | 昼区 `0.6 <= param < 0.8` | 裸地，地表 `siltstone`，恶魂支线 T2；靠替换表接管原版沙漠那格 |
| `blue_desert` | 蓝色沙漠 | 极夜 `param <= -0.8` 的干湿度一半 | 蓝沙地，地表 `road_sand`，带雪；`extra` 里温度 `-2 ~ -1`、湿度 `-1 ~ -0.35` 那条 |
| `polar_dawn` | 极昼平原 | 极昼 `param >= 0.8` | 占位，内容照白地；温度 `2.0`、不降水，近白天穹。靠 `extra` 里 `[1, 2]` 那条选中 |
| `polar_dusk` | 极夜平原 | 极夜 `param <= -0.8` | 占位，内容照白地；温度 `-0.45`、降水 `true`，所以会积雪结霜，天穹压暗。靠 `extra` 里 `[-2, -1]` 那条选中 |

这几份和 `forest` 一样是手工接的，`tools/port_overworld_worldgen.py` 不生成它们。重跑那个脚本会再吐一份 `desert.json` 副本并把 `dawnduskterminal:desert` 写进调色板标签，那格已经改指白地，所以重新生成之后要把多出来的 `desert.json` 与标签里那条删掉

## 换皮

其余 43 份副本同名同内容，只换了地表方块与矿石特征：

- 恶地族 `badlands` `eroded_badlands` `wooded_badlands`
- 雪地族 `snowy_beach` `snowy_slopes` `snowy_taiga` `ice_spikes` `frozen_peaks` `jagged_peaks` `grove`
- 海洋族 `ocean` `deep_ocean` `cold_ocean` `deep_cold_ocean` `lukewarm_ocean` `deep_lukewarm_ocean` `frozen_ocean` `deep_frozen_ocean` `warm_ocean`
- 河流与岸 `river` `frozen_river` `beach` `stony_shore`
- 其余 `sunflower_plains` `meadow` `cherry_grove` `mushroom_fields` `stony_peaks` `windswept_forest` `windswept_gravelly_hills` `windswept_savanna` `bamboo_jungle` `sparse_jungle` `mangrove_swamp` `taiga` `old_growth_pine_taiga` `old_growth_spruce_taiga` `savanna_plateau` `deep_dark` `dripstone_caves` `lush_caves`

例外一个：`desert` 那份同名副本改成了自建 ID `blue_desert`，于是不再带原版沙漠的仙人掌 枯灌木 沙漠水井，也不再走替换表（它的格子在 `extra` 里）

## 还没落地的名字

语言文件里有键、`worldgen/biome/` 里还没有对应文件的：

`polar_dawn` 极昼平原 `day` 昼区平原 `normal` 晨昏平原 `night` 夜区平原 `polar_dusk` 极夜平原 `refusal_ocean` 拒绝之海 `dawn_zone` 晨区 `dusk_zone` 昏区 `solidcanopy_ocean` 凝固苍穹的海洋 `earth_light` 地光 `sky_island` 空岛

## 附录：原版方块到模组方块

地表调色板的命名对照，方块都在 `ModTerrainBlocks` 里：

| 原版 | 模组 | id |
|---|---|---|
| 草方块 | 霞草 | `rosy_grass` |
| 泥土 | 霞土 | `rosy_dirt` |
| 石头 | 苍穹石 | `canopy_stone` |
| 花岗岩 | 赭石 | `ochre_block` |
| 闪长岩 | 绿片岩 | `greenschist` |
| 安山岩 | 榴辉岩 | `eclogite` |
| 深板岩 | 紫苏岩 | `charnockite` |
| 凝灰岩 | 暮光石 | `twilight_stone` |
| 沙子 | 路之沙 | `road_sand` |
| 红沙 | 不醒沙 | `unconscious_sand` |
| 砂岩 | 粉砂岩 | `siltstone` |
| 水 | 不稳定液体 | `portal_fluid` |
| 岩浆 | 热情的暮光 | `enthu_fluid` |
| 冰 | 凝固的苍穹 | `frozen_canopy` |
| 雪 | 苍穹霜 | `canopy_frost` |
