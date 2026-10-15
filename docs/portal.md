# 传送门

传送门由水池加水池边植物加投料物构成

判定与传送代码在 `portal/` 包

## 三个标签

`tags/block/portal/fluid` 定义可用作水池的方块，当前只有 `minecraft:water`

`tags/block/portal/edge` 定义可种植的池边方块

`tags/block/portal/decoration` 定义可算作植物的方块，含空气类

标签文件在 `data/dawnduskterminal/tags/block/portal/`

## 建造判定

判定在 `portal/PortalShape.find`

种子方块必须在水池标签里

从种子水平洪泛，只走同一 y 的水方块

水池方块数下限 `MIN_WATER` 4

上限 `MAX_WATER` 64，超过即判失败

每列水向下最多找 16 格到达的地板，地板必须能站

所有水方块的水平邻居必须属于 `portal/edge` 标签

环上每个位置上方若属于 `portal/decoration` 且不是空气，记为一株植物

植物数须大于等于 `max(2, 环长 / 4)`

满足则返回一个 `PortalShape`，带水池 边框 植物三组坐标

## 生成传送门

`portal/PortalBuilder.build`

对判定出的水池方块逐个替换成 `dawnduskterminal:portal_fluid`

已经是传送门流体的跳过

`probe` 只做判定不改方块

## 触发

投料物是 `dawnduskterminal:chrono_core`

`PortalBlock.isCatalyst` 判定

两条触发路径

物品实体落在水里时由 `PortalBlock.entityInside` 调 `tryIgnite`

玩家附近 12 格内落在水里的投料物由 `DdtServerEvents.scanFuelItems` 每 10 tick 扫描并调 `tryIgnite`

`tryIgnite` 会先调 `build`，成功才消耗一个物品

成功后触发 `build_gate` 进度，召雷，放粒子，放 `portal_open` 音效

## 传送门方块

`portal/PortalBlock` 继承 `LiquidBlock`

`randomTick` 里若四周没有相邻传送门方块，就变回空气，实现自愈

否则调用 `FluidReactions.handle`

`isCompletePortalPart` 要求相邻传送门方块至少 2 个

## 传送

`portal/PortalTrigger.shouldTeleport` 判定实体是否处在完整传送门里

玩家进入方块时由 `PortalBlock.entityInside` 调 `PortalTeleporter.tick`

落点搜索是分帧的

进入时先蓄力 100 tick

每 tick 预算 2 个区块加载

从 16 格环开始搜，进入目标最大半径 320，回程最大半径 48

环步长取 16 与 环半径除以 6 的较大值

`scanColumn` 判定单列

用 `MOTION_BLOCKING_NO_LEAVES` 取表面

表面必须大于世界最低高度且不超过 128

下方方块必须能站

脚部方块无碰撞体积且无流体

搜不到时在原点铺一块半径 6 的兜底平台

## 回程落点

回程坐标存在玩家附件 `portal_state`

字段 `return_dim` 与 `x y z`

进维度时写入当前维度与坐标

回程时 `returnSpot` 在正负 3 格内按距离排序找安全站位

`isSafeStanding` 排除岩浆块 仙人掌 火 灵魂火 营火 甜浆果 细雪

传入回程坐标只有 1 格的下探余量

## 到达传送门

进入维度时的落点会建一座到达门

`buildArrivalPortal` 先在落点附近 24 格环内找平缓地面

铺半径 4 的 `sky_stone` 台面

在台面上开 2 乘 2 的传送门流体池

池边铺 `sky_soil` 与 `ember_soil`

清掉台上方的方块

落点返回在池子西侧一格半

回程不建新门，直接传送到记住的坐标

## 冷却

冷却 tick 存在 `portal_state`

进入或回程后写入，取配置 `portal.cooldown_ticks` 与 40 的较大值

冷却期间在传送门里会显示剩余秒数

## 减益

效果 `dawnduskterminal:portal_sickness`，在 `registry/ModEffects`

类 `world/PortalSicknessEffect`

判定周期是每 40 tick

只在晨昏线维度生效，造成 1 加等级点魔法伤害

非玩家生物碰到传送门会获得 200 tick 该效果，受配置 `portal.entity_debuff` 控制

玩家到达时会获得 300 tick 中毒

## 传输到客户端

`network/PortalSearchPayload` 在开始与结束搜索时下发

客户端 `client/PortalDistortion` 据此做红屏与视角扭曲

## 音效

`portal_open` 开门

`portal_enter` 进入

`portal_exit` 离开

`chrono_ambient` 维度环境音

## 相关命令

`/ddt tp <param>` 按投影参数传送到维度内指定位置

`/ddt void tp` 传送到最近的空置域中心

其余见 `development.md`
