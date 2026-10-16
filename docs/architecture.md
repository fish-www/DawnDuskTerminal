# 架构总览

一个 NeoForge 模组，新增「晨昏线」维度

维度里没有昼夜更替，太阳高度由玩家坐标在晨昏线法向上的投影决定

地形与群系来自原版主世界世界生成的移植版本，方块换成模组自己的调色板

## 模块职责

`advancement/` 自定义进度触发器与判定

`block/` 单独的自定义方块

`client/` 客户端渲染 天空 染色 屏幕效果 光影检测

`command/` `/ddt` 指令

`compat/` 特征排序兼容

`entity/` 自定义生物

`config/` 配置定义

`crafting/` 自定义 Ingredient

`fluid/` 自定义流体

`gametest/` 游戏内测试

`item/` 自定义物品

`mixin/` 针对原版的补丁

`network/` 网络包

`portal/` 传送门建造 触发 传送 状态

`progression/` Boss 进度追踪

`registry/` 全部 `DeferredRegister`

`server/` 服务端事件与刷怪规则

`structure/` 结构白名单与摆放表

`world/` 群系源 晨昏线 密度包装 自定义 Feature 与方块 流体反应 状态

## 入口

主类 `com.dawnduskterminal.DawnDuskTerminal`

构造函数里按顺序注册各类 `DeferredRegister`

注册网络负载 `ModNetwork::registerPayloads`

在 `FMLCommonSetupEvent` 上注册流体反应 `FluidReactions::register`

挂服务端事件，见下方「事件」

客户端环境下调用 `DdtClient.init`

注册 COMMON 配置 `DdtConfig.SPEC`

静态方法 `id(path)` 生成 `dawnduskterminal:` 命名空间的 `ResourceLocation`

## 生物

`registry/ModEntities` 注册 `tower_ghast`（高塔恶魂），属性走 `EntityAttributeCreationEvent`

`entity/TowerGhastEntity` 由 GeckoLib 驱动，模型 动画 贴图从 Yes Steve Model 的 Bedrock 文件转换而来，转换与校验在 `tools/ysm/`

`client/TowerGhastRenderer` 把三条资源路径直接写出，不走 GeckoLib 的命名约定

## 装备与武器

`registry/ModArmorMaterials` 注册护甲材质，目前四份：绯色钼、苍穹、砷铅铁是钻石级，天门松是铁级

`registry/ModTiers` 注册工具分级，同样四份（绯色钼只有护甲，没有工具）

`registry/ModEquipment` 按套注册工具与护甲，三套共 27 件，按套存在 `Map<String, Set>` 里而不是逐件写字段

`item/ScarletMolybdArmorItem` 只有绯色钼一套用，四件穿齐时每件自己在 `inventoryTick` 里续生命恢复

`item/CarminiteStaffItem` 近战按单手武器算，右键发射一颗 `entity/CarminiteBallEntity`

`entity/CarminiteBallEntity` 继承 `AbstractArrow`，命中时直接在那一帧结算范围伤害。源 mod 把反应场做成了一只无 AI 的飞行生物，这里不需要

`registry/ModParticles` 注册三种粒子（`particle_carminite_ball` `_trace` `_effect`），客户端 provider 在 `client/CarminiteParticle`

`registry/ModDamageTypes` 的 `carminite_field` 是反应场伤害，条目在 `data/dawnduskterminal/damage_type/`

这些内容从 `twilightextra`（MCreator 生成）迁移而来，原代码里的过程式实现已换成上面这些

装备贴图目前是原版铁、钻石贴图加色阶的占位图，真美术进来后换掉 `textures/item/` 与 `textures/models/armor/` 里对应前缀的文件即可

## 维度与晨昏线

`registry/ModDimensions.CHRONO` 是维度的 `ResourceKey`

`world/ChronoLine` 是 record，存原点与单位法向

`ChronoLine.distance(x,z)` 是点到线的有符号距离

`ChronoLine.param(x,z)` 是距离除以最大距离后钳到 -1 到 1

`ChronoLine.create(random)` 每个世界随机一次法向，基准朝东，抖动正负 30 度，再随机决定是否转 180 度朝西

配置 `normal_override` 可固定法向

`world/ChronoLineState` 继承 `SavedData`，存在主世界，键 `dawnduskterminal_chrono_line`

服务端与客户端各有一份静态缓存 `serverLine` 与 `clientLine`

`network/ChronoLinePayload` 在玩家登录时把线下发到客户端

法向只按维度算一次，多个玩家共用同一条线

## 群系源

`world/PaletteBiomeSource` 是自写 `BiomeSource`，注册为 `dawnduskterminal:palette`

它要求一个 `delegate` 与一个 `palette` 群系标签

`delegate` 是原版 `multi_noise` 主世界预设

`getNoiseBiome` 先跑 delegate，再把 `minecraft:*` 群系换成同名副本

副本名与命名空间对应关系按路径匹配，例如 `minecraft:plains` 换成 `dawnduskterminal:plains`

`floatrees` 群系走另一条路，采样深度低于阈值时直接返回

替换表延迟构建，因为 biome source 在数据包解析期创建，那时标签里的 holder 还没有 key

细节见 `worldgen.md`

## 世界生成补丁

数据包改不到的原版硬编码由 `mixin/` 接管

`LavaBandPaletteMixin` 把 `createFluidPicker` 的 y 以下岩浆带换成热情的暮光

`LavaAquiferPaletteMixin` 把深部岩浆含水层换成热情的暮光

`PillarsFluidBlockMixin` 让石柱洞穴保持干燥

`NoiseChunkVeinPaletteMixin` 用 `world/ChronoVeinPalette` 把大矿脉的花岗岩与凝灰岩换成赭石与暮光石

`NoiseChunkTemperatureMixin` 用 `world/ChronoTemperature` 给温度噪声加晨昏线偏移

`MineshaftHeightMixin` 把废弃矿井压到 y -64 到 -10

`MineshaftChestLootMixin` 把矿井箱车战利品换成 `dawnduskterminal:chests/chrono_mineshaft`

识别维度统一靠噪声设置的 `default_fluid` 是不是 `portal_fluid`

## 传送门

`portal/PortalShape` 做水池识别与判定

`portal/PortalBuilder` 把水池方块替换成传送门流体

`portal/PortalTrigger` 判定实体是否处在可用传送门里

`portal/PortalBlock` 是传送门流体的方块，处理方块内实体与自愈

`portal/PortalTeleporter` 负责即时传送 落点搜索 到达门

`portal/PortalState` 是玩家附件，存回程维度与坐标

细节见 `portal.md`

## 流体

`registry/ModFluids` 注册 `portal_fluid`，流动参数照抄水

`registry/ModScorchingTwilight` 注册 `enthu_fluid`（热情的暮光）的类型 流体 方块 桶

`world/FluidReactions` 在 `FMLCommonSetupEvent` 上把接触反应注册进 NeoForge `FluidInteractionRegistry`

`block/ScorchingTwilightBlock` 继承岩浆的方块属性，但不点火不清植物，接触造成 6 点 `enthu_fluid` 伤害

`item/CanocanItem` 是唯一能舀这两种流体的容器，原版桶会被毁灭

## 进度与效果

`progression/BossKind` 枚举三个暮色森林 Boss 与对应战利品

`progression/BossProgress` 是 record，存三个击败标记

`progression/BossProgressTracker` 在击杀事件里写入附件

附件定义在 `registry/ModAttachments`，`boss_progress` 序列化并 `copyOnDeath`

`registry/ModEffects` 注册 `stability` 与 `portal_sickness`

`world/StabilityEffect` 是纯标记效果，方向由 `PortalBlock` 决定

`advancement/LineParamTrigger` 是自定义判定，按晨昏线投影参数给进度

## 客户端

`client/DdtClient` 是客户端入口，注册模型 颜色 渲染层 事件

`client/ChronoDimensionEffects` 是维度特效，处理雾色 云 天空

`client/ChronoSkyRenderer` 自绘天空与假太阳假月亮

`client/ChronoFoliageColors` 复刻暮色森林的树叶染色算法

`client/model/MountedTowerWoodModel` 做镶边塔木与封装塔木梁的连接模型

`client/ShaderGuard` 检测光影模组并在登录时提示

`client/PortalFluidExtensions` 与 `ScorchingTwilightFluidExtensions` 是流体的客户端扩展

`DdtClient` 注册方块与物品颜色，草与树叶随群系染色

天空贴图在 `assets/dawnduskterminal/textures/environment/`

## Mixin

配置 `dawnduskterminal.mixins.json`，分公共与客户端两份

`FluidStatusAccessor` 暴露 `Aquifer.FluidStatus` 的流体类型给其他 mixin

`LavaBandPaletteMixin` `LavaAquiferPaletteMixin` `PillarsFluidBlockMixin` 见「世界生成补丁」

`NoiseChunkVeinPaletteMixin` `NoiseChunkTemperatureMixin` 见「世界生成补丁」

`MineshaftHeightMixin` `MineshaftChestLootMixin` 见「世界生成补丁」

`FeatureSorterMixin` 替换原版特征排序为可容忍环的实现，受配置 `tolerate_feature_cycles` 控制

`LightEngineSkyLightMixin` 在晨昏线维度把天光上限压到 `ChronoSkyLight.maxSkyLight`

`StructureExclusionMixin` 在晨昏线维度只放行结构白名单里的结构

`BiomeColorsFoliageMixin` 是客户端 mixin，把树叶染色接进 `ChronoFoliageColors`

## 事件

`server/DdtServerEvents` 挂到 `NeoForge.EVENT_BUS`

`onServerStarted` 读取并记录晨昏线，校验结构与摆放表

`onPlayerLoggedIn` 下发晨昏线包

`onPlayerTick` 每 10 tick 扫玩家附近的投料物，进维度后触发进度判定

`onLivingDeath` 记录 Boss 击杀

`onSpawnPlacement` 走 `SpawnRules`

`onRightClickItem` 拦截原版桶，禁止舀两种模组流体

## 结构

`structure/StructureExclusions` 是唯一白名单，`StructureExclusionMixin` 排除不在其中的结构

`structure/StructureExclusions` 里的 `UNMAPPED` 与 `LANDMARKS` 记录未放行项与地标，只服务日志

服务端启动时校验这几张表，缺失只记日志

## 已知遗留

`registry/ModEffects` 仍注册 `portal_sickness`，但已无人施加，方向改由 `stability` 决定

`config/DdtConfig` 的 `portal.cooldown_ticks` 与 `portal.entity_debuff` 已无读取方

语言文件里 `death.attack.dawnduskterminal.portal` 与 `message.dawnduskterminal.searching` 已不再使用
