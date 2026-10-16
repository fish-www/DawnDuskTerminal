"""清点一个 YSM 动画文件用到了哪些 Molang 构造

用法：python ysm_analyze.py <animation.json>

逐个构造列出哪些片段用到它，于是 GeckoLib 转换表可以由数据支撑，而不是靠猜
构造只在每个片段的字符串叶子上匹配，绝不匹配骨骼名或关键帧时间
"""
import json
import re
import sys
from collections import defaultdict

# 规则模块是 validate_geckolib.py，这里导入，不再抄一遍字符集与函数表
from validate_geckolib import validate

# YSM 维基记作函数或变量的全部 ysm.* / ctrl.* / tlm.* 标识符
YSM_TOKENS = re.compile(r"\b(ysm|ctrl|tlm|tacz|swem|parcool|carryon|slashblade)\.[A-Za-z_0-9]+")
QUERY_TOKENS = re.compile(r"\b(query|q)\.[A-Za-z_0-9]+")
VAR_TOKENS = re.compile(r"\b(variable|v|temp|t)\.(roaming\.)?[A-Za-z_0-9]+")
BONE_ACCESSOR = re.compile(r"\bysm\.bone_(rot|pos|scale|pivot_abs)\s*\(\s*'[^']*'\s*\)\s*\.[xyz]")
COUNTED_CALL = re.compile(r"\bysm\.(first_order|second_order|perlin_noise|particle|abs_particle|play_sound|stop_sound|stop_all_sounds|sync|dump_\w+|mouse|keyboard|mod_version)\s*\(")


def walk_strings(node, out):
    if isinstance(node, str):
        out.append(node)
    elif isinstance(node, dict):
        for value in node.values():
            walk_strings(value, out)
    elif isinstance(node, list):
        for value in node:
            walk_strings(value, out)


def main(path):
    with open(path, encoding="utf-8") as handle:
        doc = json.load(handle)
    animations = doc.get("animations", {})

    per_clip = {}
    for name, clip in animations.items():
        strings = []
        walk_strings(clip, strings)
        per_clip[name] = [s for s in strings if re.search(r"[A-Za-z]", s) and not s.startswith("#")]

    def group(label, pattern):
        """收集某个 pattern 的互异匹配，并映射到用到它的那些片段"""
        hits = defaultdict(set)
        for clip, exprs in per_clip.items():
            for expr in exprs:
                for match in pattern.finditer(expr):
                    hits[match.group(0)].add(clip)
        return label, hits

    groups = [
        group("ysm/ctrl/tlm 标识符", YSM_TOKENS),
        group("query 标识符", QUERY_TOKENS),
        group("变量标识符", VAR_TOKENS),
        group("骨骼访问器（上一帧）", BONE_ACCESSOR),
        group("有状态 / 有副作用的调用", COUNTED_CALL),
    ]

    print(f"文件           : {path}")
    print(f"format_version : {doc.get('format_version')}")
    print(f"片段数         : {len(animations)}")
    print(f"表达式字符串数 : {sum(len(v) for v in per_clip.values())}")
    print()

    for label, hits in groups:
        print(f"=== {label}（{len(hits)} 个互异值）")
        for token in sorted(hits):
            clips = sorted(hits[token])
            shown = ", ".join(clips[:8]) + (" ..." if len(clips) > 8 else "")
            print(f"  {token:<34} {len(clips):>3} 个片段  [{shown}]")
        print()

    # 逐片段对 GeckoLib 的解析规则下判断，规则实现只有 validate_geckolib.py 那一份
    print("=== 逐片段的 GeckoLib 兼容性")
    failures = {failure["clip"]: failure["reasons"] for failure in validate(doc)}
    for clip in sorted(animations):
        reasons = failures.get(clip)
        if reasons:
            print(f"  跳过 {clip:<26} {'; '.join(reasons)[:150]}")
    print()
    print(f"GeckoLib 会跳过的片段：{len(failures)} 个 -> {', '.join(sorted(failures))}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1])
