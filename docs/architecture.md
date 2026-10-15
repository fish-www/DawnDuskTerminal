# 架构总览

一个 NeoForge 模组，新增「晨昏线」维度

世界被一条晨昏线切成昼侧与夜侧

太阳高度由玩家坐标在晨昏线法向上的投影决定，不随时间变化

## 模块职责

`advancement/` 自定义进度触发器与判定

`block/` 单独的自定义方块

`client/` 客户端渲染 天空 屏幕效果 光影检测

`command/` `/ddt` 指令

`compat/` 兼容层，目前只有特征排序兼容

`config/` 配置定义

`fluid/` 自定义流体类型

`gametest/` 游戏内测试

`mixin/` 四个 Mixin

`network/` 网络包

`portal/` 传送门建造 触发 传送 状态

`progression/` Boss 进度追踪

`registry/` 全部 `DeferredRegister`

`server/` 服务端事件与刷怪规则

`structure/` 结构摆放与排除表

`world/` 群系源 密度相关 自定义 Feature 流体反应 状态

## 入口

主类 `com.dawnduskterminal.DawnDuskTerminal`

构造函数里按顺序注册各类 `DeferredRegister`

注册网络负载 `ModNetwork::registerPayloads`

挂服务端事件，见下方「事件」

客户端环境下调用 `DdtClient.init`

注册 COMMON 配置 `DdtConfig.SPEC`

静态方法 `id(path)` 生成 `dawnduskterminal:` 命名空间的 `ResourceLocation`

## 维度与晨昏线状态

`registry/ModDimensions.CHRONO` 是维度的 `ResourceKey`

`world/ChronoLine` 是 record，存原点与单位法向

`ChronoLine.distance(x,z)` 是点到线的有符号距离

`ChronoLine.param(x,z)` 是距离除以最大距离后钳到 -1 到 1

`ChronoLine.sunAngleDegrees(x,z)` 是 param 乘最大太阳角

`ChronoLine.create(random)` 生成世界时随机法向，基准朝东，抖动正负 30 度

配置 `normal_override` 可固定法向

`world/ChronoLineState` 继承 `SavedData`，存在主世界

存档键 `dawnduskterminal_chrono_line`

服务端静态缓存 `serverLine`，客户端静态缓存 `clientLine`

`setClientLine` 由网络包写入

法向只按维度算一次，多个玩家共用同一条线

## 网络

`network/ModNetwork` 注册两个 playToClient 包，版本号 `1`

`ChronoLinePayload` 把 `ChronoLine` 发给客户端，登录时下发

`PortalSearchPayload` 通知客户端开始或结束落点搜索，驱动屏幕红屏效果

## 群系源

`world/ChronoBiomeSource` 是自写 `BiomeSource`

字段来自维度 JSON 的 `biome_source` 块

`bands` 少于 4 个时退回 `delegate`

判定顺序是先看 `y_bands`，再看线上群系，再看内外分区

边界加 60 格抖动，抖动用 128 格栅格的值噪声

内外分区按距离取 `bands` 里的四个群系

`side_shift` 按昼侧或夜侧把 `delegate` 群系换成自定义群系

`replacements` 与 `fallback` 做兜底替换

细节见 `worldgen.md`

## 传送门

`portal/PortalShape` 做水池识别与判定

`portal/PortalBuilder` 把水池方块替换成传送门流体

`portal/PortalTrigger` 判定实体是否处在可用传送门里

`portal/PortalBlock` 是传送门流体的方块，处理方块内实体与自愈

`portal/PortalTeleporter` 负责落点搜索 传送 回程 冷却

`portal/PortalState` 是玩家附件，存冷却与回程坐标

细节见 `portal.md`

## 进度

`progression/BossKind` 枚举三个暮色森林 Boss 与对应战利品

`progression/BossProgress` 是 record，存三个击败标记

`progression/BossProgressTracker` 在击杀事件里写入附件

附件定义在 `registry/ModAttachments`

`boss_progress` 同时序列化并 `copyOnDeath`

`portal_state` 只序列化

## 客户端

`client/DdtClient` 是客户端入口，注册事件

`client/ChronoDimensionEffects` 是维度特效，处理雾色 云 天空

`client/ChronoSkyRenderer` 自绘天空与假太阳假月亮

`client/ChronoLineStateHolder` 持有客户端线状态

`client/PortalDistortion` 传送时的红屏与视角扭曲

`client/ShaderGuard` 检测光影模组并在登录时提示

`client/PortalFluidExtensions` 与 `ScorchingTwilightFluidExtensions` 是流体客户端扩展

`DdtClient` 还注册方块颜色，草色与树叶随坐标渐变

天空贴图在 `assets/dawnduskterminal/textures/environment/`

## Mixin

配置文件 `dawnduskterminal.mixins.json`，四个都是 common

`FeatureSorterMixin` 替换原版特征排序为可容忍环的实现，受配置 `tolerate_feature_cycles` 控制

`LightEngineSkyLightMixin` 在晨昏线维度把天光上限压到 `ChronoSkyLight.maxSkyLight`

`StructureExclusionMixin` 在晨昏线维度抑制排除表里的结构

`NoLavaLevelMixin` 在 `default_fluid` 为空气时把流体选择器改成全空气

## 事件

`server/DdtServerEvents` 挂到 `NeoForge.EVENT_BUS`

`onServerStarted` 读取并记录晨昏线，校验结构排除表

`onPlayerLoggedIn` 下发晨昏线包

`onPlayerTick` 每 tick 走冷却，每 10 tick 扫玩家附近的投料物，进维度后触发进度判定，最后跑传送逻辑

`onLivingDeath` 记录 Boss 击杀

`onSpawnPlacement` 走 `SpawnRules`

## 流体反应

`world/FluidReactions` 维护一张反应表

传送门流体与水 岩浆 热情的暮光各有产物

热情的暮光与水 岩浆各有产物

极光方块按字符串 ID 取暮色森林的 `aurora_block`

## 结构

`structure/StructureExclusions` 列出排除的结构与白名单

`structure/StructurePlacements` 列出复用的结构

两张表都在服务端启动时校验，缺失只记日志
