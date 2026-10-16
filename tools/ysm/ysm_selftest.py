"""ysm_to_geckolib.py 的自测

这里每一条用例都是读 YSM 维基（https://ysm.cfpa.team/wiki/molang/ref/ 与 .../molang/index/）
定下来的规则，并与 GeckoLib 4.9.3 的 MathParser 对过 改完转换表就跑一遍：

    python ysm_selftest.py
"""
from __future__ import annotations

import sys

from validate_geckolib import validate
from ysm_to_geckolib import Report, Translator, _walk_and_translate

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
check("head_yaw 别名", tr("ysm.head_yaw/180")[0], "query.head_y_rotation/180")
check("head_pitch 别名", tr("ysm.head_pitch/360")[0], "query.head_x_rotation/360")
check("q. 规范化", tr("q.anim_time")[0], "query.anim_time")
check("has_helmet 别名", tr("ysm.has_helmet")[0], "query.has_head_gear")
# 更长的点串名字不能被部分改写
check("不误伤更长的前缀", tr("ysm.head_yaw_extra")[0], "0")

# -- 二级：有状态滤波器 ---------------------------------------------------------------
print("二级 - 有状态滤波器")
out, rep = tr("ysm.second_order('n', math.clamp(q.vertical_speed+v.bv,-30,30), 3, 0.3, 0)")
check("second_order -> 输入", out, "(math.clamp(query.vertical_speed+v.bv,-30,30))")
check("second_order 记为二级", [n.tier for n in rep.notes].count(2), 1)

out, _ = tr("-1.5*ysm.second_order('a', x, 2, 0.6, 0)-3*ysm.second_order('b', y, 2, 0.6, 0)")
check("一项里两个滤波器", out, "-1.5*(x)-3*(y)")

out, _ = tr("ysm.first_order('n', (query.eye_target_y_rotation+query.head_x_rotation), 0.3)")
check("first_order -> 输入", out, "((0+query.head_x_rotation))")

# -- 三级：被丢弃的构造 ---------------------------------------------------------------
print("三级 - 被丢弃的构造")
check("bone_rot 访问器", tr("ysm.bone_rot('FrontFragFlag').x")[0], "0")
out, _ = tr("ysm.second_order('n', ysm.bone_rot('B').x, 2, 0.4, 0)-ysm.bone_rot('B').x")
check("输入是上一帧状态时滤波器 -> 0", out, "(0)-0")
check("abs_particle", tr("ysm.abs_particle('dust 1 0 0.1 2',0,1,0,0.3,0.7,0.3,0,math.random(1,12),20);")[0], "0")
check("perlin_noise", tr("ysm.perlin_noise(1,2,3,4)")[0], "0")
check("未知 query 长写法", tr("query.yaw_speed")[0], "0")
check("未知 query 短写法", tr("q.yaw_speed")[0], "0")
check("表达式里的未知 query", tr("math.clamp(-0.02*q.yaw_speed,-60,60)")[0],
      "math.clamp(-0.02*0,-60,60)")
check("带参数的 query", tr("query.position_delta(0)")[0], "0")
check("关键帧指令", tr("#sound")[0], "0")
check("兜底 ysm 符号", tr("ysm.some_future_thing")[0], "0")

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
print()
print(f"  转换结果: {sample['animations']['clip']['bones']['B']['rotation']}")
print(f"  记录条数: {len(report.notes)}")

print()
print(f"通过 {PASS}，失败 {FAIL}")
sys.exit(1 if FAIL else 0)
