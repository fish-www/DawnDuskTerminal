# YSM → GeckoLib 动画转换

这个目录把 **Yes Steve Model** 的 Bedrock 动画文件转换成 GeckoLib 能编译的形式 之所以需要它，
是因为 YSM（2.3.0+）实现了几乎全部 Bedrock Molang <em>外加自己的一套标准库</em>，
而 GeckoLib 两样都不实现 把 YSM 动画文件直接拷进 GeckoLib 模组，得到的不是「动画坏掉」，
而是一只永远不动的东西 —— 后者难查得多

## 为什么直接拷会失败

GeckoLib 的 `MathParser`（4.9.3）强制的三条，YSM 文件全都违反：

| 约束 | GeckoLib 4.9.3 | YSM 文件 |
| --- | --- | --- |
| 表达式字符集 | `^[\w\s_+-/*%^&|<>=!?:;.,(){}]+$` | `ysm.second_order('头发角速度', …)` —— 单引号 |
| 函数库 | 只有 `math.*` | `ysm.*`、`ctrl.*`，以及它不支持的 `query.*` |
| 跨帧状态 | 无，每条表达式都无状态求值 | `first_order` / `second_order` / `bone_rot` 都是逐帧滤波器 |

GeckoLib <em>逐个</em>烘焙动画片段，遇到解析失败就记一条日志并跳过该片段
（`BakedAnimationsAdapter.deserialize`）被跳过的片段在运行时完全看不出来：骨骼只是保持上一个姿势
在 `高塔恶魂` 这个模型上，这吃掉了 **50 个片段里的 9 个** —— 包括 `walk`、`death`
以及两个 `parallel` 物理层

## 转换器做了什么

`ysm_to_geckolib.py` 把每条表达式改写成 GeckoLib 的文法，并输出一份审计报告 每处改动都有分类：

### 两个 head 不是同一个东西

`ysm.head_yaw` 是<strong>相对身体</strong>的头部转角，GeckoLib 的 `query.head_y_rotation` 是
`getViewYRot()`，<strong>世界坐标</strong>。俯仰那一条没这个问题，因为身体不俯仰

直接替换的后果不是「差一点」，是静默放大与整只模型滚转：

- `RightEyesBase.position[0]` 的 `head_yaw/180`，相对值最大约 0.25 格，绝对值能到 2 格 —— 眼睛被推出脸外
- `run` 片段里 `Root.rotation[2] = 0.3*head_yaw`，绝对值下能到 54 度 —— 跑动时<em>整只模型</em>随朝向滚转，
  被打一下开始逃跑就是「倒下再立起来」

`query.body_y_rotation` 是 `Mth.lerp(partialTick, yBodyRotO, yBodyRot)`，`math.min_angle` 是
`Mth.wrapDegrees`，所以上面那一格就是 YSM 语义的原样翻译

### 一级 —— 精确（本模型 94 处）

维基写明与某个 query 是<em>同一个值</em>的 YSM 名字，逐个改名，无语义漂移

| YSM | GeckoLib | YSM 维基 |
| --- | --- | --- |
| `ysm.head_pitch` | `query.head_x_rotation` | 俯仰角，身体不俯仰，两者同值 |
| `ysm.head_yaw` | `math.min_angle(query.head_y_rotation-query.body_y_rotation)` | 见下方「两个 head 不是同一个东西」 |
| `ysm.is_sneak` | `query.is_sneaking` | 同值 |
| `ysm.is_sleep` | `query.is_sleeping` | 同值 |
| `ysm.is_passenger` | `query.is_riding` | 同值 |
| `ysm.has_helmet` | `query.has_head_gear` | 同值 |
| `q.*` | `query.*` | 文档写明的别名 |

### 二级 —— 有状态滤波与结构性修补（378 处）

**有状态滤波器** `ysm.first_order(name, input, response)` 与
`ysm.second_order(name, input, frequency, coefficient, response)` 是<strong>按 `name` 记忆的递归滤波器</strong>，
每帧积分一次，而 `ysm.bone_rot(name)` 读的是<strong>上一帧</strong>的骨骼变换
GeckoLib 没有跨帧状态，所以任何表达式改写都复现不了它们

转换器改为代入滤波器的<strong>静态可求值输入信号</strong>

这个替换比看上去要好 用参考实现（归属见下文）实测，两个滤波器的<strong>稳态增益都是 1</strong>，
`physics_reference.py` 对本模型用到的每一组 `(frequency, coefficient, response)` 都验过：

```
 freq  coef  resp      gain   90% at
  1.0   0.8   0.0    1.0000 11 ticks
  2.0   0.4   0.0    1.0000  4 ticks
  2.5   0.3   0.0    1.0000  3 ticks
  3.0   0.3   1.0    1.0000  2 ticks

response   gain   90% at      （first_order）
     0.3 1.0000 13 ticks
     0.5 1.0000 22 ticks
```

也就是说滤波器收敛到的正是喂进去的那个值 于是把 `second_order(name, X, …)` 换成 `X` 是
<strong>稳态精确</strong>的，只丢了暂态：2 到 11 个 tick（0.1 到 0.55 秒）的滞后，以及过冲
真正丢掉的是<em>次级运动</em> —— `walk` / `jump` / `swim` / `fly` 里的布料 旗帜 头发链，
是把每根骨骼自己的上一帧旋转喂给下一根驱动的，那条级联没有无状态等价物

**结构性修补** 有三条 GeckoLib 文法规则只有在真实客户端跑过之后才暴露出来，
因为检查器的第一版只建模了字符集，没有建模解析器对嵌套表达式的处理：

| 规则 | 日志里的失败 | 修法 |
| --- | --- | --- |
| `VARIABLE_FORMAT = ^[a-z_]+\.[\w+_]+$` 只允许<strong>一个点</strong> | `Unable to parse compiled symbols from expression: [Left[v.roaming.expe]]` | YSM 三级的 `v.roaming.*` 变成 `v.roaming_*`（`v.roaming.expe` → `v.roaming_expe`） |
| 条件式必须在同一层有配对的 `:`（`MathParser.compileTernary` 找不到冒号就返回 null），即 GeckoLib 不认 YSM 的<strong>无冒号条件式</strong> `cond?value` | `Failed to parse expression '5.63304+(query.ground_speed>0?math.sin(query.anim_time*720)*2)'` | 补成 `cond?(value):0`：YSM 条件为假时求值是 `null`，数值上下文按 0，所以补 0 是精确的 |
| 函数<strong>实参列表</strong>里的三元运算符编译不过（经验规则，不是 jar 里的常量） | 同上那条日志曾被归到这条上 | 三元先求值进前一条语句的 `t.hoistN`，调用改读那个变量 |

提升必须冒到<strong>顶层</strong>：把三元从嵌套调用里拉出来会产生一条赋值，
而这条赋值仍待在上一层调用的实参列表里，带 `?` 的赋值和三元一样编译不过

上面第二条才是 `ride` 丢失的真实原因。原先它被归到第三条上，于是转换器改成提升三元（提升本身是精确的），
而 `ride` 依旧失败：`t.hoist0=(cond?X)` 里那个 `?` 还是没冒号。
日志里有现成证据：含冒号的三元即使待在括号里也能加载（`parallel0` 被提升出的 `t.hoist0=(…(0<0?(0<0?-180:180):0))` 就是），
缺冒号的 `ride` 不能 —— 致命的是缺冒号，不是括号。

`v.sleevefix`、`v.forward`、`v.hg`、`v.hv`、`v.bg`、`v.bv` 是被文件读取、
但<strong>文件从未赋值</strong>的变量 —— 它们原本由外部自定义函数脚本喂值，而这套模型包并不带那些脚本
YSM 文档写明未初始化的变量是 `null`，而 YSM 在数值上下文里把 `null` 当作 0
（"参与数值类计算时被视为 0 或 false"），所以这些变量在 YSM 里同样读作 0
转换器的输出与 YSM 自身的行为一致，没有凭空造值

### 三级 —— 丢弃（240 处）

没有等价物，于是该项变成 `0`，并记进报告：

| 构造 | 原因 |
| --- | --- |
| `ysm.bone_rot/pos/scale/pivot_abs('name')` | 上一帧的骨骼变换，需要状态 |
| `ysm.perlin_noise(seed,x,y,z)` | GeckoLib 没有噪声函数 |
| `ysm.particle` / `ysm.abs_particle` / `play_sound` / `stop_sound` | 副作用，不是值 |
| `ysm.keyboard` / `ysm.mouse` / `time_delta` / `fps` | 客户端输入与计时没有暴露给 GeckoLib |
| `q.position_delta`、`q.eye_target_y_rotation`、`q.position` | YSM 新增的 query，GeckoLib 没有实现 |
| `q.yaw_speed` | GeckoLib <em>有</em> `query.yaw_speed`，早先误判成没有；补上时还要乘 20 换成每秒 |
| `??` 空值合并 | GeckoLib 没有 null，直接使用本来就默认取 0 的变量 |
| `#` 开头的关键帧指令 | 音效 / 粒子 / 指令关键帧 |

## 资源还必须放在 GeckoLib 会去找的位置

只有文法对还不够 GeckoLib 的 `DefaultedGeoModel` 把每条路径拼成
`<folder>/<subtype>/<path>.<ext>`，而 `DefaultedEntityGeoModel.subtype()` 返回字面量 `"entity"`：

```java
// DefaultedGeoModel 的字符串常量
geo/<path>/<subtype>.geo.json
animations/<path>/<subtype>.animation.json
textures/<path>/<subtype>.png
// DefaultedEntityGeoModel.subtype() -> ldc "entity"
```

所以符合约定的路径会是 `geo/entity/…`、`animations/entity/…`、`textures/entity/…`
这一点花掉了真实的排查时间：动画文件放在 `animations/tower_ghast.animation.json`，
而模型去找的是 `animations/entity/`，客户端表现就是一只渲染出来但永远不动的怪

因此 `TowerGhastRenderer` 把三条路径直接写出来，不依赖那套约定，
代码里的路径就是 jar 里的文件，没有留下任何需要推断的东西

## 直接拷贝实际发生了什么

在真实客户端里，把 YSM 文件原样拷进去会得到：

```
[GeckoLib/]: Unable to parse animation: ride / parallel0 / pre_parallel1 / pre_parallel2
[GeckoLib/]: Unable to find animation: idle for TowerGhastEntity      (x 42825)
```

第二行才是关键：片段缺失时 GeckoLib 不会报错退出，它逐帧打日志并保持姿势
症状是一只站着一动不动的怪，很容易被误读成模型或渲染器的问题
`validate_geckolib.py` 就是为了让这项检查在构建期跑，而不是靠人看日志

## 本模型上的实测结果

```
片段数              50
改动的表达式        715 条，分布在 27 个片段
按级别              163 / 381 / 171  （一级 / 二级 / 三级）
GeckoLib 校验       50 个片段全部通过 GeckoLib 的解析规则
```

逐片段保留了多少动态（query 或变量驱动的）值 —— 除两个物理层外，每个片段都<strong>全部</strong>保留，
损失集中在那两层：

```
片段          动态值入   动态值出   丢失
parallel0          138         82     56
pre_parallel0       41         24     17
其余 48 个片段    1202       1202      0
-----------------------------------------
全部 50 个片段    1381       1308     73
```

具体说，`parallel0` 的 93 根骨骼里有 <strong>87 根</strong>已经没有非平凡内容，
`pre_parallel0` 是 32 根里 21 根 这两个片段<em>就是</em>模型的次级运动

## 文件

| 文件 | 用途 |
| --- | --- |
| `ysm_to_geckolib.py` | 转换器；`--report` 写出逐子句的审计 |
| `validate_geckolib.py` | <strong>要跑的就是它</strong>：按 GeckoLib 真实的解析规则检查文件，列出会被静默跳过的片段 它同时是全仓库唯一的 GeckoLib 语法规则模块，另外两个脚本从这里导入 |
| `ysm_selftest.py` | 自测，钉住每一条转换规则 |
| `ysm_analyze.py` | 清点文件用到了哪些 YSM 构造，以及哪些片段会被 GeckoLib 跳过 |
| `ysm_dump.py` | 导出指定片段的原始表达式 |
| `physics_reference.py` | 滤波器公式，用来量度二级替换的误差 |

```bash
python ysm_to_geckolib.py <in.animation.json> <out.animation.json> --report report.json
python validate_geckolib.py <out.animation.json>   # 预期：全部片段通过
python ysm_selftest.py
```

回归办法：拿源模型包里的 `animations/main.animation.json` 重新转换一遍，
输出应该与 `assets/dawnduskterminal/animations/tower_ghast.animation.json` 逐字节相同：

```bash
python ysm_to_geckolib.py <源模型包>/animations/main.animation.json /tmp/regen.json
cmp /tmp/regen.json src/main/resources/assets/dawnduskterminal/animations/tower_ghast.animation.json
```

## 源模型包里哪些文件用得上

`ref/高塔恶魂/` 是完整的一套 YSM 模型包，本模组只用其中三个文件：

| 源文件 | 去处 |
| --- | --- |
| `models/main.json` | 逐字节复制成 `assets/dawnduskterminal/geo/tower_ghast.geo.json` |
| `animations/main.animation.json` | 过 `ysm_to_geckolib.py` 变成 `animations/tower_ghast.animation.json` |
| `textures/ghast.png` | 逐字节复制成 `textures/entity/tower_ghast.png` |

其余是给「玩家形态」用的，与一只生物无关：

- `models/arm.json` + `animations/arm.animation.json`：第一人称手臂模型与 52 个 `hold_mainhand:*` 手持动画
- `models/arrow.json` + `animations/arrow.animation.json` + `textures/arrow.png`：箭矢形态
- `animations/` 下另外 8 个文件是给别的模组用的兼容层：`tac`（TACZ 枪械）`tlm`（车万女仆）`parcool`（跑酷）`swem`（骑马）`carryon`（搬运）`slashblade`（拔刀剑）`extra`（玩家手动触发的 idle1-7 与 bad1-5）
- `sounds/*.ogg`：`bad1-5` 是受击语音、`id1-7` 是那套 `extra` 动画的配音（这里当待机语音用）、
  `attack` 是攻击音，而这只生物没有攻击行为，所以留空。前两组已接进 `ModSounds`，
  变体由 `sounds.json` 里同一个事件的多个文件交给原版随机挑
- `controller/parallel_controllers.json`：YSM 的并行动画控制器，分层已在 `TowerGhastEntity` 里用 GeckoLib 控制器重写，这份是分层关系的原始依据

`ysm.json` 是包元数据，`avatar/` 是作者头像，`sounds/desktop.ini` 是 Windows 垃圾文件

## 归属

用来量度二级近似误差的 `first_order` / `second_order` 公式，取自
**[OpenYSM](https://github.com/OpenYSM/OpenYSM)**
（`client/animation/molang/functions/physics/`，MIT 许可）的参考实现，OpenYSM 是 Yes Steve Model
的开放重实现 那里的 `SecondOrder` 把算法归功于
[*Giving Personality to Procedural Animations using Math*](https://www.youtube.com/watch?v=KPoeNZZ6H4s)

Yes Steve Model 本身是<strong>保留所有权利</strong>的；这里没有从它取任何东西
本文档记录的行为来自公开维基与 OpenYSM

## 恢复次级运动

不能烘焙，只能在运行时积分。这一节记的是为什么，以及现在做到哪一步

### 为什么不是烘焙

这里原先写的是「按 20 fps 走片段的关键帧，逐帧求值有状态表达式，把结果写成关键帧」。查过数据之后这条路不成立：

- 滤波器的输入是活的运行时量。实际调用点长这样：

  ```
  ysm.second_order('头发垂直', math.clamp(-3*q.vertical_speed+v.hv,-10,120), 2, 0.6, 0)
  ysm.second_order('前2x',     ysm.bone_rot('FrontFragFlag'), ...)
  ```

  输入是 `ysm.head_yaw`、`q.vertical_speed`、`q.ground_speed`、`q.yaw_speed`，以及上一帧的骨骼旋转。
  离线烘焙要把它们定成常数，烘出来的头发是死的，比现在「直接用滤波器的输入」还差
- `parallel0` 与 `pre_parallel0` 这两层<strong>连 `animation_length` 都没有</strong>（`loop: true`，没有长度）。
  它们不是时间轴，是逐帧函数，没有可以烘焙的关键帧轴

### 现在的做法

状态放在客户端，逐 tick 积分，结果在渲染层写回骨骼：

| 文件 | 职责 |
| --- | --- |
| `entity/TowerGhastPhysics` | 两支滤波器的公式，与 `physics_reference.py` 逐字对应 |
| `client/TowerGhastBoneFilters` | 每个实体一份的跨帧状态；输入取自实体，单位与 GeckoLib 的 `MolangQueries` 一致 |
| `client/TowerGhastBoneLayer` | `FastBoneFilterGeoLayer`，动画求值之后逐骨骼写回 |

`gametest/TowerGhastPhysicsTests` 把阶跃响应表钉住：表里写 `1.0/0.8/0.0` 在 11 tick 到 90%，
Java 版就必须也是 11，所以这不是「差不多」，是同一个滤波器

单位与符号照 GeckoLib 对表达式的处理来：常数以外的旋转值先转弧度，再把 X 与 Y 取反，Z 不取反
（`AnimationController.getAnimationPointAtTick`）。旋转是「初始姿势加偏移」，位置与缩放是绝对值

### 三处必须和 GeckoLib 内置 query 区别对待的地方

实机跑出来两类症状（眼睛被推出脸外、头发看不到动）之后才查出来的，都写进
`client/TowerGhastBoneFilters` 的类注释了：

| 项 | 源动画的语义 | GeckoLib 的 `query.*` | 处理 |
| --- | --- | --- | --- |
| 头部偏航 | 相对身体（Bedrock） | `getViewYRot()`，世界坐标 | 取 `wrapDegrees(yHeadRot - yBodyRot)` |
| `ground_speed` `vertical_speed` `yaw_speed` | 每秒 | 每 tick | 乘 20 |
| `head_x_rotation` 取值时刻 | — | `getViewXRot(0)` 给的是上一刻 | 用当前值 |

第一条最要命：眼睛那条是 `head_yaw/180`，相对转角最大约 0.25 格，世界偏航能到 2 格，眼睛直接被推出脸外

### `ysm.perlin_noise` 的真实实现

不是「仿一个噪声」：OpenYSM（MIT）里那一句是

```java
STBPerlin.stb_perlin_noise3_seed(x, y, z, 0, 0, 0, seed)
```

所以照抄即可，波形与 YSM 一致。发丝的式子是 `±10*perlin(1492, life_time) ± 头发角速度`，
38 根只有轴与符号不同，表在 `client/TowerGhastBoneLayer` 里

### 模型里没有的骨骼

这套动画用到 5 个模型里不存在的名字：`Breast` `FLongHair` `MLongHair` `ElytraLocator` `LongHair`。
它们由 YSM 自己往模型里补，GeckoLib 没有这一步，所以这些通道在移植后必然是空的

`TowerGhastBoneLayer` 在第一次渲染时会把这些名字报一次警告 —— 缺骨骼时
`FastBoneFilterGeoLayer` 是静默跳过的，不报就会白查一遍

### 做到哪一步

- 已完成：输入不含 `ysm.bone_rot` 的那批 —— 头发（`MLongHair` `FLongHair` `LeftHeadFrag` `RightHeadFrag`
  `ElytraLocator`）、胸口（`Breast` `Carminite` `LeftGuard` `Bung`）、眼睛（`RightEyesBase` `LeftEyesBase`）、
  布条转身滞后（`FragFlag` 一系），共 19 根骨骼、12 支滤波器
- 已完成：发丝的 38 根通道（`perlin` 加 `头发角速度`），以及上面三处量纲与语义修正
- 已完成：主状态机的死亡与受击两档。YSM 的宿主状态机是 `isDeadOrDying()` 播 `death`（Priority.HIGHEST）、
  `hurtTime > 0` 播 `attacked`（Priority 2），条件与优先级都照抄进 `TowerGhastEntity.mainAnimation`。
  同时把渲染器的 `getDeathMaxRotation` 覆写成 0，否则 GeckoLib 那套通用倒地会与模型自带的死亡演出重叠
- 未做：`ysm.bone_rot` 的递次滞后（`FragFlag2-4`、`LeftHeadFrag2-3` 等，读上一帧快照即可）；
  以及只存在于 YSM 补充模型里的那 5 根骨骼（做不了，除非自己补几何体）

源模型里 `v.bv` `v.bg` `v.hv` `v.hg` `v.sleevefix` 由外部脚本喂值，而脚本不在这套模型包里，
YSM 对未初始化变量取 0，所以这几项也按 0 处理。因此 `RightSlave*` `LeftSlave*` 一系整条通道恒为 0

资料：[YSM molang 参考](https://ysm.cfpa.team/wiki/molang/ref/)、
[YSM molang 简介](https://ysm.cfpa.team/wiki/molang/index/)、
[YSM 变量](https://ysm.cfpa.team/wiki/molang/var/)、
[YSM 并行动画](https://ysm.cfpa.team/wiki/animation/parallel/)、
[YSM 主动画](https://ysm.cfpa.team/wiki/animation/main/)。
