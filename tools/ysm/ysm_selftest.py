"""ysm_to_geckolib.py 的自测

这里每一条用例都是读 YSM 维基（https://ysm.cfpa.team/wiki/molang/ref/ 与 .../molang/index/）
定下来的规则，并与 GeckoLib 4.9.3 的 MathParser 对过 改完转换表就跑一遍：

    python ysm_selftest.py
"""
from __future__ import annotations

import sys

from validate_geckolib import validate
from ysm_to_geckolib import Report, Translator, _relocate_timeline_constants, _walk_and_translate

PASS = 0
FAIL = 0


def check(label: str, got, want) -> None:
    global PASS, FAIL
    if got == want:
        PASS += 1
        print(f"  通过 {label}")
    else:
        FAIL += 1
        print(f"  失败 {label}\n         实际: {got}\n         期望: {want}")


def tr(expr: str) -> tuple[str, Report]:
    report = Report()
    translator = Translator(report)
    translator.clip = "test"
    translator.trail = "x"
    return translator.translate(expr), report


# -- 一级：精确别名 -------------------------------------------------------------------
print("一级 - 别名")
# head_yaw 是相对身体的转角，GeckoLib 那个 query 是世界坐标，所以要走最小夹角（见 README）
check("head_yaw 别名走最小夹角", tr("ysm.head_yaw/180")[0],
      "math.min_angle(query.head_y_rotation-query.body_y_rotation)/180")
check("head_pitch 别名", tr("ysm.head_pitch/360")[0], "query.head_x_rotation/360")
# YSM 的 head query 命名与 GeckoLib 相反：YSM 的 x 是偏航、y 是俯仰
check("YSM 的 head_y_rotation 是俯仰", tr("query.head_y_rotation")[0], "query.head_x_rotation")
check("YSM 的 head_x_rotation 是偏航", tr("query.head_x_rotation")[0],
      "math.min_angle(query.head_y_rotation-query.body_y_rotation)")
check("短名同样交叉", tr("q.head_x_rotation")[0],
      "math.min_angle(query.head_y_rotation-query.body_y_rotation)")
# 脖子：源文件是 [-0.5*pitch, -0.5*yaw, 0]，两条规则不能互相改写
check("脖子 X 轴取俯仰", tr("-0.5*query.head_y_rotation")[0], "-0.5*query.head_x_rotation")
check("脖子 Y 轴取相对偏航", tr("-0.5*query.head_x_rotation")[0],
      "-0.5*math.min_angle(query.head_y_rotation-query.body_y_rotation)")
check("q. 规范化", tr("q.anim_time")[0], "query.anim_time")
check("has_helmet 别名", tr("ysm.has_helmet")[0], "query.has_head_gear")
# 更长的点串名字不能被部分改写
check("不误伤更长的前缀", tr("ysm.head_yaw_extra")[0], "0")

# -- 二级：有状态滤波器 ---------------------------------------------------------------
print("二级 - 有状态滤波器")
out, rep = tr("ysm.second_order('n', math.clamp(q.vertical_speed+v.bv,-30,30), 3, 0.3, 0)")
# 速度类 query 在 YSM 里是每秒，GeckoLib 里是每 tick，所以要乘 20
check("second_order -> 输入", out, "(math.clamp(20*query.vertical_speed+v.bv,-30,30))")
check("second_order 记为二级", [n.tier for n in rep.notes].count(2), 1)

out, _ = tr("-1.5*ysm.second_order('a', x, 2, 0.6, 0)-3*ysm.second_order('b', y, 2, 0.6, 0)")
check("一项里两个滤波器", out, "-1.5*(x)-3*(y)")

out, _ = tr("ysm.first_order('n', (query.eye_target_y_rotation+query.head_x_rotation), 0.3)")
# 那个 query 是偏航（YSM 的 x 是偏航），所以先把 eye_target 丢掉，再换成相对身体的偏航
check("first_order -> 输入", out, "((0+math.min_angle(query.head_y_rotation-query.body_y_rotation)))")

# -- 三级：被丢弃的构造 ---------------------------------------------------------------
print("三级 - 被丢弃的构造")
check("bone_rot 访问器", tr("ysm.bone_rot('FrontFragFlag').x")[0], "0")
out, _ = tr("ysm.second_order('n', ysm.bone_rot('B').x, 2, 0.4, 0)-ysm.bone_rot('B').x")
check("输入是上一帧状态时滤波器 -> 0", out, "(0)-0")
check("abs_particle", tr("ysm.abs_particle('dust 1 0 0.1 2',0,1,0,0.3,0.7,0.3,0,math.random(1,12),20);")[0], "0")
check("perlin_noise", tr("ysm.perlin_noise(1,2,3,4)")[0], "0")
check("yaw_speed 长写法", tr("query.yaw_speed")[0], "query.yaw_speed")
check("yaw_speed 短写法补 20 倍", tr("q.yaw_speed")[0], "20*query.yaw_speed")
check("表达式里的 yaw_speed", tr("math.clamp(-0.02*q.yaw_speed,-60,60)")[0],
      "math.clamp(-0.02*20*query.yaw_speed,-60,60)")
check("真的没有实现的 query", tr("q.eye_target_y_rotation")[0], "0")
check("带参数的 query", tr("query.position_delta(0)")[0], "0")
check("关键帧指令", tr("#sound")[0], "0")
check("兜底 ysm 符号", tr("ysm.some_future_thing")[0], "0")

# -- 二级：结构性修补 ---------------------------------------------------------------
print("二级 - 结构性修补")
# YSM 允许省掉假分支，GeckoLib 只认 `?:`；真值分支按优先级吃到本层结束
check("无冒号条件式补 `:0`", tr("5.63304+(q.ground_speed>0?math.sin(query.anim_time*720)*2)")[0],
      "5.63304+(20*query.ground_speed>0?(math.sin(query.anim_time*720)*2):0)")
check("语句级的无冒号条件式", tr("v.bv=q.ground_speed>0?math.cos(2*query.anim_time*360/(24/24))*5")[0],
      "v.bv=20*query.ground_speed>0?(math.cos(2*query.anim_time*360/(24/24))*5):0")
check("嵌套的无冒号条件式按右结合", tr("a?b?c:d")[0], "a?(b?c:d):0")
check("配对齐的三元不动", tr("v.a=(x==1?1:0)")[0], "v.a=(x==1?1:0)")
check("空值合并不会被当成条件式", tr("v.jump ?? 1")[0], "v.jump")

out, rep = tr("5.63304+(q.ground_speed>0?math.sin(query.anim_time*720)*2)")
check("补冒号记为二级", [n.tier for n in rep.notes if n.kind == "bare conditional closed"], [2])

# -- 语句处理 -------------------------------------------------------------------------
print("语句处理")
check("顶层分号切分并保持顺序",
      tr("v.a=1;v.b=2;v.a+v.b")[0], "v.a=1;v.b=2;v.a+v.b")
# YSM 平行片段的加法混合运算符 ++ 变成语句分隔符：两项都留着，
# 这对单个累加器来说正是混合的意思
check("++ 变成分隔符",
      tr("ysm.second_order('a',x,1,0.8,0)++(v.expo==3?0.25:0)")[0],
      "(x);(v.expo==3?0.25:0)")
check("全部丢弃后合成一个 0",
      tr("ysm.abs_particle('a',1,2,3,4,5,6,7,8,9);ysm.perlin_noise(1,2,3,4)")[0], "0")
check("return 被拆掉", tr("return v.speed_x")[0], "v.speed_x")
check("空值合并取那个本来默认就为 0 的变量", tr("v.jump ?? 1")[0], "v.jump")
check("纯数字不动", tr("1.5")[0], "1.5")
check("纯 molang 不动", tr("1+math.sin(120*query.life_time)")[0], "1+math.sin(120*query.life_time)")

# -- timeline：GeckoLib 只把它当自定义指令串，不求值 ---------------------------------------
print("timeline")

def relocate(clip: dict) -> tuple[dict, Report]:
    report = Report()
    _relocate_timeline_constants(clip, "test", report)
    return clip, report

clip, rep = relocate({"timeline": {"0.0": "v.sleevefix=1;"},
                      "bones": {"molang": {"rotation": ["v.bv=1", 0, 0]}}})
check("常量赋值搬进 molang 通道", clip["bones"]["molang"]["rotation"][0],
      "v.sleevefix=1;v.bv=1")
check("timeline 键被删掉", "timeline" in clip, False)
check("搬动记为二级", [n.kind for n in rep.notes], ["timeline constant"])

clip, _ = relocate({"timeline": {"0.0": "v.sleevefix=0;"}, "bones": {}})
check("没有 molang 通道时补一个", clip["bones"]["molang"]["rotation"][0], "v.sleevefix=0;0")

clip, _ = relocate({"timeline": {"0.0": "v.jumpsteb=1-v.jumpsteb;"}, "bones": {}})
check("非幂等赋值留在 timeline 里", clip.get("timeline"), {"0.0": "v.jumpsteb=1-v.jumpsteb;"})
check("非幂等赋值不动骨骼", clip["bones"], {})

# -- 输出必须满足 GeckoLib 自己的解析规则 ---------------------------------------------
print("GeckoLib 文法一致性")
sample = {
    "animations": {
        "clip": {
            "bones": {
                "B": {
                    "rotation": [
                        "ysm.head_pitch/360",
                        "-1.5*ysm.second_order('a', q.ground_speed+v.hg, 2, 0.6, 0)-3*ysm.second_order('b', v.bv, 2, 0.6, 0)",
                        "ysm.abs_particle('dust 1 0 0.1 2',0,1,0,0.3,0.7,0.3,0,math.random(1,12),20);",
                        "ysm.bone_rot('LeftHeadFrag').x",
                        "v.sleevefix=0;",
                        "5.63304+(q.ground_speed>0?math.sin(query.anim_time*720)*2)",
                    ],
                    "position": {"0.0": "ysm.perlin_noise(1,2,3,4)"},
                }
            }
        }
    }
}
report = Report()
translator = Translator(report)
translator.clip = "clip"
translator.trail = "B"
_walk_and_translate(sample["animations"]["clip"], [], translator)
check("转换后的样例通过校验", validate(sample), [])
check("校验器认得出无冒号条件式",
      validate({"animations": {"clip": {"bones": {"B": {"rotation": ["1+(a>0?b)"]}}}}}),
      [{"clip": "clip", "reasons": ["条件式缺少 `:`"], "count": 1}])
print()
print(f"  转换结果: {sample['animations']['clip']['bones']['B']['rotation']}")
print(f"  记录条数: {len(report.notes)}")

print()
print(f"通过 {PASS}，失败 {FAIL}")
sys.exit(1 if FAIL else 0)
