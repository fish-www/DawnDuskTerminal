# AGENTS.md

面向在本仓库工作的 AI 与协作者，给最短上手路径与硬约束

玩家说明在 `README.md`

开发细节在 `docs/`

历史笔记在 `DEVNOTES.md`，部分内容已过时

## 项目

Minecraft 1.21.1 NeoForge 模组

新增「晨昏线」维度，世界被一条线切成昼侧与夜侧

太阳高度由坐标在这条线法向上的投影决定，不随时间变化

维度的地形与群系是把原版主世界世界生成移植到本模组命名空间后，用调色板群系源替换方块

`mod_id` 为 `dawnduskterminal`

主类 `com.dawnduskterminal.DawnDuskTerminal`

Java 21

NeoForge 21.1.252 与 ModDevGradle，版本写在 `gradle.properties`

## 目录

`src/main/java/com/dawnduskterminal/` 代码

`src/main/resources/assets/dawnduskterminal/` 贴图 模型 语言 音效

`src/main/resources/data/dawnduskterminal/` 维度 群系 地形 战利品 配方 进度

`tools/` 移植与校验用的 Python 脚本

`docs/` 开发文档

## 构建与运行

`./gradlew build`，产物在 `build/libs/`

`./gradlew runClient`

`./gradlew runServer`

`./gradlew runData`，输出到 `src/generated/resources`

`./gradlew runGameTestServer`

`gradlew` 必须保持可执行位，CI 在 Linux 上跑，权限位丢了会直接失败

## 暮色森林

运行必须装暮色森林 1.21.1

构建期把 `libs/twilightforest-1.21.1.jar` 放好

代码里只用字符串 ID 引用暮色森林，没有 `import twilightforest.*`

所以缺 jar 仍可编译，运行会缺注册项

`gradle.properties` 的 `twilight_dependency` 控制依赖类型，默认 `required`

## 代码约定

每个注册类别一个 `registry/ModXxx`，用 `DeferredRegister`

在 `DawnDuskTerminal` 构造函数里调用对应的 `register(modBus)`

资源 ID 用 `DawnDuskTerminal.id(path)`

包名固定 `com.dawnduskterminal`

## 世界生成约定

维度 `dimension/chrono.json` 用 `dawnduskterminal:palette` 群系源

`PaletteBiomeSource` 把原版按气候选出的群系换成同名副本，副本在 `worldgen/biome/`

`data/dawnduskterminal/tags/worldgen/biome/palette_biomes.json` 列出全部副本

副本里的特征引用必须指向 `dawnduskterminal:` 命名空间

数据包够不到的硬编码由 `mixin/` 里的补丁接管，见 `docs/worldgen.md`

## 硬约束与坑

`noise_settings/chrono.json` 的 `default_fluid` 必须是 `dawnduskterminal:portal_fluid`，多个 mixin 靠它识别维度

`AquiferTests` 与 `WorldgenTests` 断言地形 流体 矿脉都走模组调色板

feature 引用与 `neoforge:remove_features` 的 ID 必须真实存在，否则注册表加载失败，整个存档起不来

1.21.1 密度函数 JSON 字段是 `argument`，不是 `input`

`runGameTestServer` 偶发卡死（区块等不到实体刻）由 `mixin/GameTestChunkWaitMixin` 兜底，见 `docs/development.md` 的「已知的失败与卡死」

天光烘焙进区块，改天光后老存档要重算

改世界生成后建议删掉 `run/<端>/world` 重建

## 测试

`./gradlew runGameTestServer`

共 34 个测试，覆盖世界生成 传送门形状 流体反应 霜层 半透明面剔除

见 `docs/development.md`

## 提交

提交说明用中文

格式 `type(scope): 中文说明`，subject 一行，句末不加句号

正文只在携带 subject 装不下的信息时添加

## 文档

`docs/README.md` 文档索引

`docs/architecture.md` 架构总览

`docs/worldgen.md` 世界生成

`docs/portal.md` 传送门与流体

`docs/development.md` 开发与构建

`docs/configuration.md` 配置项
