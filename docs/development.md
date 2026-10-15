# 开发与构建

## 环境

JDK 21

用仓库自带的 `gradlew`，并保持它的可执行位

Minecraft 1.21.1

NeoForge 与 ModDevGradle 版本在 `gradle.properties` 与 `settings.gradle`

## 暮色森林依赖

运行时必须装暮色森林 1.21.1

构建期把 jar 放到 `libs/twilightforest-1.21.1.jar`

`build.gradle` 检测到该文件才加 `compileOnly` 与 `runtimeOnly`

代码里只按字符串 ID 引用暮色森林，没有 `import twilightforest.*`

所以缺 jar 也能编译，功能会在运行时缺失

`gradle.properties` 的 `twilight_dependency` 控制依赖类型，默认 `required`

## 构建与运行

`./gradlew build`，产物在 `build/libs/`

`./gradlew runClient` 目录 `run/client`

`./gradlew runServer` 目录 `run/server`

`./gradlew runData` 目录 `run/data`，输出到 `src/generated/resources`

`./gradlew runGameTestServer` 目录 `run/gametest`

首次构建会下载并反编译 Minecraft

纯资源改动可能报 UP-TO-DATE，要验证 jar 里的内容

## 游戏内测试

`./gradlew runGameTestServer`

命名空间 `dawnduskterminal`，模板 `ddt_empty`

共 34 个测试

### WorldgenTests

`chronoDimensionTypeIsRegistered` 维度类型已注册

`portedDensityFunctionsAreRegistered` 移植的密度函数已注册

`portedNoiseParametersAreRegistered` 移植的噪声参数已注册

`terrainNoiseIsSeedDerived` 地形噪声随种子变化

`paletteBiomeCopiesAreRegistered` 群系副本已注册

`paletteBiomeCopiesCarryOurFeatures` 副本带本模组特征

`paletteBiomeSourceIsRegistered` 调色板群系源已注册

`chronoUsesThePaletteBiomeSource` 维度用的是调色板群系源

`configuredFeaturesAreRegistered` 配置特征已注册

`veinFillersUseThePalette` 矿脉填充走调色板

`paletteBiomeSourceSubstitutesThePreset` 群系源替换原版预设

### AquiferTests

`terrainAndFluidUseTheModPalette` 地形与流体走模组调色板

`aquifersAreEnabledLikeVanilla` 含水层配置与原版一致

`oreVeinsAreEnabledLikeVanilla` 大矿脉已开启

### PortalShapeTests

`plantedPoolIsPortal` 带植物的水池是传送门

`barrenPoolIsNotPortal` 荒水池不是

`poolWithoutPlantsIsNotPortal` 没植物的水池不是

`loneWaterBlockIsNotPortal` 单个水方块不是

`buildFillsPoolWithPortalFluid` 建造会把水池换成传送门流体

`portalBlockSurvivesWithNeighbour` 有邻居的传送门方块存活

`isolatedPortalBlockSelfHeals` 孤立的传送门方块自愈

`portalFluidIsCompleteWithTwoNeighbours` 两个邻居即算完整

### FluidReactionTests

`portalFluidWithWater`

`portalFluidWithLava`

`portalFluidWithScorchingTwilight`

`scorchingTwilightWithWater`

`scorchingTwilightWithLava`

`portalFluidWithFlowingWater`

`flowingPortalFluidWithWater`

### CanopyFrostMeltTests

`canopyFrostSurvivesTheLightThatMeltsVanillaSnow` 苍穹霜不会像原版雪那样在亮光里融化

### TranslucentFaceCullingTests

`translucentCanopyCullsTheFaceBetweenTwoOfItself` 半透明方块之间剔除面

`cullingAgreesWithTheVanillaReference` 与原版参照一致

`differentBlocksAreNotCulledAgainstEachOther` 不同方块之间不剔除

`portalFluidCullsTheFaceBetweenTwoOfItself` 传送门流体之间剔除面

## 指令

`/ddt` 需要权限等级 2

`/ddt line` 打印当前位置的原点 法向 距离 参数 天光 维度

`/ddt tp <param>` 按投影参数在 -1 到 1 之间传送到维度内

## 移植脚本

`tools/port_overworld_worldgen.py` 从 Gradle 缓存里的反编译客户端 jar 抽取原版世界生成，改写命名空间后写入本模组

`tools/port_tf_biomes.py` 把暮色森林群系内容填进对应维度的群系副本，保留洞穴雕刻，重映射特征引用

`tools/check_tf_biomes.py` 校验移植群系，检查 JSON 合法 无残留 `minecraft:*` 特征 以及 `twilightforest:*` 特征能在 TF jar 内解析

## CI

`build.yml` 在 push main 与 v 标签 与 PR 触发，跑 `./gradlew build` 并上传 jar

`gametest.yml` 在 PR 触发，跑 `./gradlew runGameTestServer` 并上传日志

`release.yml` 在 v 标签触发，校验标签与 `mod_version` 一致，构建并 `gh release create`

concurrency 只在 PR 上 cancel-in-progress

## 踩坑

`noise_settings/chrono.json` 的 `default_fluid` 必须是 `portal_fluid`，多个 mixin 靠它识别维度

`ore_veins_enabled` 保持 true，矿脉填充由 `NoiseChunkVeinPaletteMixin` 换成调色板方块

feature 引用与 `neoforge:remove_features` 的 ID 必须真实存在，否则注册表加载期抛错

1.21.1 密度函数字段是 `argument`，不是 `input`

`gradlew` 必须是 100755，权限位丢了 Linux 上直接跑不了

天光烘焙进区块，改天光后老存档要重算

改世界生成后建议删掉 `run/<端>/world` 重建

## 新增内容放哪

新增方块放 `block/` 或 `registry/ModTerrainBlocks` 或 `registry/ModBlocks`

新增世界生成数据放 `src/main/resources/data/dawnduskterminal/worldgen/`

新增自定义 Feature 放 `world/`，注册在 `registry/ModFeatures`

新增矿石放 `registry/ModChronoOres`

新增物品放 `registry/ModItems`

新增流体放 `registry/ModFluids` 或 `registry/ModScorchingTwilight`

新增配置放 `config/DdtConfig`

新增指令放 `command/DdtCommands`

新增进度放 `data/dawnduskterminal/advancement/`
