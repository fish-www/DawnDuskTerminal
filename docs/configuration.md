# 配置项

定义在 `config/DdtConfig`

类型是 NeoForge 的 COMMON

文件是游戏目录下 `config/dawnduskterminal-common.toml`

改动后需要重启

每个 getter 都有兜底值，配置未加载时返回兜底而不是抛错

## lighting

`sun_angle_max` 默认 20.0，范围 0 到 90

太阳最大高度角，单位度

getter 兜底值是 20.0

同时影响服务端的 `ChronoLine` 与客户端天空

客户端天空按 `0.25 - param * sun_angle_max / 360` 换算成原版的 `timeOfDay`，见 `mixin/ClientLevelTimeOfDayMixin`

`sky_light_by_distance` 默认 true

true 时天光按坐标在晨昏线两侧衰减

false 时维度内天光恒为 15

`sky_light_day` 默认 15，范围 0 到 15，昼侧最远处天光

`sky_light_center` 默认 14，范围 0 到 15，晨昏线上天光

`sky_light_night` 默认 12，范围 0 到 15，夜侧最远处天光

注释里提醒 8 及以下会让怪物生成

## chrono_line

`max_distance` 默认 5000.0，范围 16 到 1000000

`ChronoLine.param` 的除数，也决定转换到最大太阳角的距离

`normal_override` 默认空字符串

填 `x,z` 可固定晨昏线法向，非法输入回退到随机

空字符串表示每个世界创建时随机，基准朝东，抖动正负 30 度，再随机决定是否翻转朝西

## portal

`cooldown_ticks` 默认 100，范围 0 到 72000

`entity_debuff` 默认 true

说明：传送改即时后这两个键已无读取方，属于遗留

## compat

`tolerate_feature_cycles` 默认 true

用可容忍环的特征排序替换原版 `FeatureSorter`

同一维度混用原版与模组群系时需要开启

调色板维度正属于这种情况
