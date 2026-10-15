# 开发与构建

## 环境

JDK 21

用仓库自带的 `gradlew`

Minecraft 1.21.1

NeoForge 21.1.252，范围在 `gradle.properties` 的 `neo_version_range`

ModDevGradle 2.0.95，定义在 `settings.gradle`

## 暮色森林依赖

运行时必须装暮色森林 1.21.1

构建期把 jar 放到 `libs/twilightforest-1.21.1.jar`

`build.gradle` 检测到该文件才加 `compileOnly` 与 `runtimeOnly`

代码里只按字符串 ID 引用暮色森林，没有 `import twilightforest.*`

所以缺 jar 也能编译，功能会在运行时缺失

`gradle.properties` 的 `twilight_dependency` 控制依赖类型，默认 `required`

## 构建

`./gradlew build`

产物在 `build/libs/`

纯资源改动可能报 UP-TO-DATE，要验证 jar 里的内容

## 运行

`./gradlew runClient` 目录 `run/client`

`./gradlew runServer` 目录 `run/server`，带 `--nogui`

`./gradlew runData` 目录 `run/data`，输出到 `src/generated/resources`

`./gradlew runGameTestServer` 目录 `run/gametest`

首次构建会下载并反编译 Minecraft，约 1 到 2 分钟

首次 `runServer` 还会额外下载资源

## 游戏内测试

`./gradlew runGameTestServer`

命名空间 `dawnduskterminal`，模板 `ddt_empty`，结构在 `structure/ddt_empty.nbt`

共 20 个测试，分五组

### WorldgenTests

`densityFunctionsAreRegistered` 密度函数已注册

`chronoDimensionTypeIsRegistered` 维度类型已注册

`pocketZoneIsStableAndBounded` 空置域稳定且有界

`bedrockSealCoversPocketFreeColumns` 基岩封住非空腔的列

### AquiferTests

`aquifersAreEnabledLikeVanilla` 断言含水层开启 默认流体为水 海平面 62

`undergroundStaysDry` 断言 y 小于等于 8 处 floodedness 为 -1，lava 恒为 0

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

`portalFluidWithWater` `portalFluidWithLava` `portalFluidWithScorchingTwilight`

`scorchingTwilightWithWater` `scorchingTwilightWithLava`

### ChronoFeatureInjectionTests

`chronoBiomesReceiveIslandAndTerrainFeatures` 断言目标群系含必需特征且不含禁用特征

## 指令

`/ddt` 需要权限等级 2

`/ddt line` 打印当前位置的原点 法向 距离 参数 天光 维度

`/ddt void` 打印最近的空置域中心与尺寸

`/ddt void tp` 传送到最近的空置域中心

`/ddt sky` 打印 3072 格内最近空岛

`/ddt sky tp` 传送到最近空岛

`/ddt tp <param>` 按投影参数在 -1 到 1 之间传送到维度内

## CI

`build.yml` 在 push main 与 v 标签 与 PR 触发，跑 `./gradlew build` 并上传 jar

`gametest.yml` 在 PR 触发，跑 `./gradlew runGameTestServer` 并上传日志

`release.yml` 在 v 标签触发，校验标签与 `mod_version` 一致，构建并 `gh release create`

concurrency 只在 PR 上 cancel-in-progress，避免主分支历史出现红叉

## 踩坑

`noise_settings/chrono.json` 的 `default_fluid` 当前是 `water`，`aquifers_enabled` 是 true，`sea_level` 是 62

地下不灌水改由 `fluid_level_floodedness` 在 y 小于等于 8 钳到 -1 实现

`AquiferTests` 会断言以上行为，改前先看测试

根目录 `DEVNOTES.md` 里「default_fluid 必须是 air」是旧结论，已过时

`ore_veins_enabled` 必须 false，否则生成 granite 与 andesite 杂石

`neoforge:remove_features` 与 biome_modifier 引用的 feature ID 必须真实存在，否则注册表加载期抛错，整个存档起不来

1.21.1 密度函数字段是 `argument`，不是 `input`

`squeeze` 值域是 x 除以 2 的三次方再乘 3，x 为 1 时只有 0.375

天光烘焙进区块，改天光后老存档要重算

客户端自绘天空会被光影盖掉，`ShaderGuard` 检测 iris oculus optifine

`ChronoBiomeSource` 的 `bands` 少于 4 个会静默退回 delegate

改世界生成后建议删掉 `run/<端>/world` 重建

## 新增内容放哪

新增方块放 `block/` 或 `registry/ModTerrainBlocks` 或 `registry/ModBlocks`

新增世界生成 Feature 放 `world/`，注册在 `registry/ModFeatures`

新增自定义群系放 `data/dawnduskterminal/worldgen/biome/`

新增矿石放 `registry/ModChronoOres`

新增配置放 `config/DdtConfig`

新增指令放 `command/DdtCommands`

新增进度放 `data/dawnduskterminal/advancement/`
