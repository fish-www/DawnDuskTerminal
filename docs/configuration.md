# 配置项

定义在 `config/DdtConfig`

类型是 NeoForge 的 COMMON

文件是游戏目录下 `config/dawnduskterminal-common.toml`

改动后需要重启

每个 getter 都有兜底值，配置未加载时返回兜底而不是抛错

## lighting

`sun_angle_max` 默认 20.0，范围 0 到 90

太阳最大高度角，单位度

getter 兜底值是 30.0

同时影响服务端的 `ChronoLine` 与客户端天空

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

## client

`custom_sky` 默认 true

是否自绘晨昏线天空与假太阳假月亮

`shader_warning` 默认 true

检测到光影模组时是否在登录提示

`sun_texture_size` 默认 64，范围 8 到 512

太阳与月亮贴图大小百分比，64 等于原版大小

`sky_top_colors` 默认五个色值

格式是六位十六进制不带井号

顺序是从极夜到极昼的天顶渐变色标

默认 `06070F` `0C1430` `2A1E4A` `2F6FC8` `4FA0E8`

少于两个色值会回退到默认

`sky_horizon_colors` 默认五个色值

顺序是从极夜到极昼的地平线渐变色标

默认 `0A0E20` `1A2248` `C8703C` `9CC4EE` `FFE6BC`

少于两个色值会回退到默认
