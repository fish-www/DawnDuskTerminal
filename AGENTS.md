# AGENTS.md

本文件面向在本仓库工作的 AI 与协作者，给出最短上手路径与硬约束

玩家说明在 `README.md`

其他开发者的历史临时笔记在 `DEVNOTES.md`，注意当前开发者可能不同，要先检查

开发细节在 `docs/`

## 项目

Minecraft 1.21.1 NeoForge 模组

新增一个「晨昏线」维度，维度内没有昼夜更替，太阳高度由玩家坐标决定

`mod_id` 为 `dawnduskterminal`

主类 `com.dawnduskterminal.DawnDuskTerminal`

Java 21

构建用 Gradle 与 ModDevGradle 2.0.95

版本号与依赖范围写在 `gradle.properties`

## 目录结构

`src/main/java/com/dawnduskterminal/` 代码

`src/main/resources/assets/dawnduskterminal/` 贴图 模型 语言

`src/main/resources/data/dawnduskterminal/` 维度 群系 地形 战利品 结构

`src/generated/resources/` `runData` 任务产物

`docs/` 开发文档

## 构建与运行

构建 `./gradlew build`，产物在 `build/libs/`

运行客户端 `./gradlew runClient`

运行服务端 `./gradlew runServer`

生成数据 `./gradlew runData`，输出到 `src/generated/resources`

跑游戏内测试 `./gradlew runGameTestServer`

## 硬依赖暮色森林

运行必须同时安装暮色森林的 1.21.1 版本

构建期把 `libs/twilightforest-1.21.1.jar` 放好

`src` 里没有 `import twilightforest.*`，只按字符串 ID 引用

所以缺 jar 仍可编译，但运行会缺注册项

`gradle.properties` 的 `twilight_dependency` 控制依赖类型，默认 `required`

## 代码约定

每个注册类别一个 `registry/ModXxx`，用 `DeferredRegister`

在 `DawnDuskTerminal` 构造函数里调用对应的 `register(modBus)`

资源 ID 用 `DawnDuskTerminal.id(path)`

新增方块放 `block/` 或 `registry/`

新增世界生成放 `world/`

配置项集中在 `config/DdtConfig`

包名固定 `com.dawnduskterminal`

## 世界生成

维度定义在 `data/dawnduskterminal/dimension/chrono.json`

群系源是自写的 `ChronoBiomeSource`

`bands` 少于 4 个会退回 `delegate`

自定义 Feature 在 `world/`，注册在 `registry/ModFeatures`

细节见 `docs/worldgen.md`

## 硬约束与坑

`noise_settings/chrono.json` 保持 `default_fluid=water`、`aquifers_enabled=true`、`sea_level=62`，`AquiferTests` 会断言这三项

地下不灌水靠 `fluid_level_floodedness` 在 y 小于等于 8 时钳到 -1

`lava` 噪声必须恒为 0

`ore_veins_enabled` 必须是 `false`

`neoforge:remove_features` 与 biome_modifier 引用的 ID 必须真实存在，否则注册表加载失败，整个存档起不来

1.21.1 密度函数 JSON 字段是 `argument`，不是 `input`

天光烘焙进区块，改天光后老存档要重算

客户端自绘天空会被光影模组盖掉，`ShaderGuard` 检测 iris oculus optifine

改纯资源后 `gradle build` 可能报 UP-TO-DATE，要验证 `build/libs/` 里的 jar

## 测试

游戏内测试在 `gametest/`

命令 `./gradlew runGameTestServer`

共 20 个测试，覆盖地形密度 传送门形状 流体反应 群系注入 含水层

见 `docs/development.md`

## 提交

用 Conventional Commits，格式 `type(scope): subject`

subject 一行，祈使句，句末不加句号

正文只在携带 subject 装不下的信息时添加

## 文档

`docs/README.md` 文档索引

`docs/architecture.md` 架构总览

`docs/worldgen.md` 世界生成

`docs/portal.md` 传送门

`docs/development.md` 开发与构建

`docs/configuration.md` 配置项
