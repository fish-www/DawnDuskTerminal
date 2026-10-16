"""按 GeckoLib 4.9.3 真实的 MathParser 规则校验动画文件

这里是全仓库唯一一份 GeckoLib 语法规则：`ysm_to_geckolib.py` 与 `ysm_analyze.py` 都从这里导入

比 `ysm_to_geckolib.py` 早期版本的检查更严：那版只查字符集，客户端日志随后报出了一堆它没建模的规则：

  Unable to parse animation: pre_parallel2
    -> Failed to parse expression 'v.roaming.hat'
      -> Unable to parse compiled symbols from expression: [Left[v.roaming.hat]]

  Unable to parse animation: ride
    -> Failed to parse expression '5.63304+(query.ground_speed>0?math.sin(query.anim_time*720)*2)'
      -> Unable to parse compiled symbols from expression: [Left[0], Left[?], Left[math.sin], ...]

这里强制的规则全部读自发布 jar 里的 MathParser：

  1. EXPRESSION_FORMAT = ^[\\w\\s_+-/*%^&|<>=!?:;.,(){}]+$   （字符集）
  2. VARIABLE_FORMAT   = ^[a-z_]+\\.[\\w+_]+$                （只能有一个点）
  3. 被调用的函数必须在 FUNCTION_FACTORIES 里，或者是变量访问
  4. 函数实参列表里嵌套三元运算符（`?`）会被解析器拒绝
     （第 4 条是从 `ride` 那次失败反推出来的经验规则，不是 jar 里的常量）

用法：
    python validate_geckolib.py <animation.json>
"""
from __future__ import annotations

import json
import re
import sys

# MathParser.EXPRESSION_FORMAT：表达式里不允许出现这个字符集之外的字符
CHARSET = re.compile(r"^[\w\s_+\-/*%^&|<>=!?:;.,(){}]+$")
# MathParser.VARIABLE_FORMAT：只能有一个点，所以裸的 `v` 不算变量
VARIABLE = re.compile(r"^[a-z_]+\.[\w+_]+$")

# MathParser.FUNCTION_FACTORIES，照抄 GeckoLib 4.9.3 发布的内容
FUNCTIONS = {
    "math.abs", "math.acos", "math.asin", "math.atan", "math.atan2", "math.ceil",
    "math.clamp", "math.cos", "math.die_roll", "math.die_roll_integer", "math.exp",
    "math.floor", "math.hermite_blend", "math.lerp", "math.lerprotate", "math.ln",
    "math.max", "math.min", "math.min_angle", "math.mod", "math.pi", "math.pow",
    "math.random", "math.random_integer", "math.round", "math.sin", "math.sqrt",
    "math.to_deg", "math.to_rad", "math.trunc",
}

# GeckoLib 认得的变量命名空间。`v_roaming` 是 YSM 三级命名 `v.roaming.*` 展平后的形态
NAMESPACES = {"query", "q", "v", "variable", "t", "temp", "v_roaming", "variable_roaming"}

CALL = re.compile(r"([A-Za-z_][A-Za-z_0-9]*\.[A-Za-z_0-9]+)\s*\(")
IDENT = re.compile(r"[A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_0-9]+)+")


def statements(expr: str) -> list[str]:
    """只按顶层 `;` 切分（GeckoLib 的语句分隔符）"""
    parts, depth, current = [], 0, []
    for ch in expr:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == ";" and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    parts.append("".join(current))
    return [p for p in parts if p.strip()]


def function_arg_spans(expr: str) -> list[tuple[str, str]]:
    """返回每个函数调用的（函数名，实参原文）"""
    out = []
    for match in CALL.finditer(expr):
        name = match.group(1)
        start = match.end() - 1
        depth = 0
        for i in range(start, len(expr)):
            if expr[i] == "(":
                depth += 1
            elif expr[i] == ")":
                depth -= 1
                if depth == 0:
                    out.append((name, expr[start + 1:i]))
                    break
    return out


def check_expression(expr: str) -> list[str]:
    """把一条表达式按上面四条规则过一遍，返回问题清单"""
    problems = []
    for statement in statements(expr):
        statement = statement.strip()
        if not statement or statement in ("0", "1") or statement.startswith("#"):
            continue
        if not CHARSET.match(statement):
            bad = "".join(sorted({c for c in statement if not re.match(r"[\w\s_+\-/*%^&|<>=!?:;.,(){}]", c)}))
            problems.append(f"字符集外字符 {bad!r}")

        for ident in IDENT.findall(statement):
            base = ident.split(".")[0]
            # math.* 是函数命名空间，函数名本身在下面按调用逐个查
            if base == "math" or CALL.match(ident + "("):
                continue
            if not VARIABLE.match(ident):
                problems.append(f"变量名不合规 {ident!r}（GeckoLib 只允许一个点）")
            elif base not in NAMESPACES:
                problems.append(f"未知命名空间 {ident!r}")

        for name, args in function_arg_spans(statement):
            if name not in FUNCTIONS:
                problems.append(f"未知函数 {name!r}")
            # 第 4 条规则：实参里的三元运算符编译不过
            if "?" in args:
                problems.append(f"{name}(...) 的实参里有三元运算符")

    return sorted(set(problems))


def walk_strings(node, out):
    if isinstance(node, str):
        if any(c.isalpha() for c in node):
            out.append(node)
    elif isinstance(node, dict):
        for value in node.values():
            walk_strings(value, out)
    elif isinstance(node, list):
        for value in node:
            walk_strings(value, out)


def validate(doc: dict) -> list[dict]:
    """逐片段校验，返回会被 GeckoLib 静默跳过的片段

    只走 `bones`：GeckoLib 也只解析这一段，走整段会把定位器名 音效名之类的东西当表达式误报
    """
    failures = []
    for clip, body in doc.get("animations", {}).items():
        exprs = []
        walk_strings(body.get("bones", {}), exprs)
        reasons = []
        for expr in exprs:
            reasons.extend(check_expression(expr))
        if reasons:
            failures.append({"clip": clip, "reasons": sorted(set(reasons)), "count": len(exprs)})
    return failures


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    with open(sys.argv[1], encoding="utf-8") as handle:
        doc = json.load(handle)
    failures = validate(doc)
    total = len(doc.get("animations", {}))
    print(f"片段数：{total}")
    if not failures:
        print("全部片段都通过 GeckoLib 的解析规则")
        return 0
    print(f"GeckoLib 会跳过的片段：{len(failures)}")
    for failure in failures:
        print(f"  {failure['clip']}")
        for reason in failure["reasons"][:6]:
            print(f"      {reason}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
