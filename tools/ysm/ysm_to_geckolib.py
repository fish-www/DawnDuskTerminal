"""把 Yes Steve Model 的 Molang 动画文件翻译成 GeckoLib 能编译的文件

为什么要有这个脚本
------------------
YSM（2.3.0+）实现了几乎全部 Bedrock Molang，外加它自己的一套标准库，而它的行为与 GeckoLib
的表达式求值器有若干差别，直接把 YSM 动画文件拷进来会静默失效：

* GeckoLib 的解析器只接受 `^[\\w\\s_+-/*%^&|<>=!?:;.,(){}]+$`，所以任何单引号字符串参数
  （如 `ysm.second_order('x', ...)`）都是硬性解析失败
* GeckoLib 没有 `ysm.*` 函数库，也不实现 `q.position_delta`、`q.eye_target_y_rotation`
  （`q.yaw_speed` 是有的，早先误判成没有；速度类 query 还差 20 倍的换算，见别名表）
* YSM 的 `??` 空值合并运算符和 `loop` / `break` / `continue` / `return` 都不是 GeckoLib
  认得的 Molang
* YSM 允许省掉条件式的假分支（`cond?value`），GeckoLib 只认 `cond?value:other`，
  缺冒号会让整条表达式编译失败并让整个片段被跳过（模型上的 `ride` 就是这样没的）
* GeckoLib 逐个烘焙动画片段，失败时记一条日志并<em>跳过</em>该片段 被跳过的片段玩家根本看不到，
  骨骼只是僵在原地 这个脚本存在的意义就是消掉这种失败方式

什么能翻译 什么不能
-------------------
一级 —— 精确（别名与等值 query）逐个改名，无语义漂移
二级 —— 近似（跨帧单向滤波器）它们<em>按定义</em>就是有状态的：
    `ysm.first_order(name, input, response)` 与
    `ysm.second_order(name, input, frequency, coefficient, response)` 是按 `name` 记忆的
    递归滤波器，每帧积分一次，而 `ysm.bone_rot(name).x` 读的是上一帧的骨骼旋转
    GeckoLib 的表达式是无状态的，变量不跨帧保留，所以任何表达式改写都复现不了它们
    本脚本在能取到滤波器的<em>静态可求值输入</em>时就代入该输入（于是滤波器以零状态运行，
    没有滞后也没有过冲），取不到就代入 0 每一次代入都记进报告
三级 —— 丢弃（没有 GeckoLib 等价物的副作用）粒子与音效发射、柏林噪声、键鼠轮询、帧间隔时间、
    上一帧骨骼访问器 记进报告，不静默丢弃

报告才是重点：它列出每一条被改动的子句，于是二级 三级的损失是可审计的，而不是看不见的
配套的离线烘焙器可以吃同一份报告，把二级滤波器换成采样出来的关键帧 —— 这是拿回次级运动的唯一办法，
因为它需要 Molang 承载不了的状态

用法
----
    python ysm_to_geckolib.py <in.animation.json> <out.animation.json> [--report report.json]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field

# GeckoLib 自己的解析规则集中在 validate_geckolib.py，这里只导入，不再抄一遍
from validate_geckolib import FUNCTIONS, validate

# --------------------------------------------------------------------------------------
# GeckoLib 解析器的约束，摘自 software.bernie.geckolib.loading.math.MathParser
# --------------------------------------------------------------------------------------

# MathParser.VARIABLE_FORMAT - "^[a-z_]+\.[\w+_]+$"，只能有一个点，所以裸的 `v` 不算变量
GECKOLIB_VARIABLE = re.compile(r"^[a-z_]+\.[\w+_]+$")

# YSM 的 roaming 变量有三层（`v.roaming.expe`，文档里写作 `variable.roaming.`，
# 见 https://ysm.cfpa.team/wiki/molang/var/），GeckoLib 直接拒绝 -
# 客户端日志会写 "Unable to parse compiled symbols from expression: [Left[v.roaming.expe]]"
MULTI_DOT_VARIABLE = re.compile(r"\b((?:v|variable|t|temp)\.[A-Za-z_][\w.]*?)(?![A-Za-z_0-9.])")


def _variable_is_geckolib_valid(name: str) -> bool:
    """点串名字是否符合 MathParser.VARIABLE_FORMAT（恰好一个点）"""
    return bool(GECKOLIB_VARIABLE.match(name))

# --------------------------------------------------------------------------------------
# 一级 —— 精确别名表 每一条都是与某个 GeckoLib query 等值的 YSM 名字
# YSM 一侧见 https://ysm.cfpa.team/wiki/molang/ref/
# --------------------------------------------------------------------------------------

ALIASES = {
    # ysm.head_pitch 与 query.head_x_rotation 确实是同一个值（都是俯仰角，身体不俯仰）
    "ysm.head_pitch": "query.head_x_rotation",
    # ysm.head_yaw 不是 query.head_y_rotation：前者是<strong>相对身体</strong>的头部转角，
    # 后者是 getViewYRot()，世界坐标。直接用会把「转头 45 度」从 0.25 格放大到 2 格，
    # 而且整只模型会随朝向滚转 —— 例如 run 片段里 Root.rotation[2]=0.3*head_yaw
    # 在绝对坐标下能到 54 度，看上去就是模型自己倒下
    # query.body_y_rotation 是 yBodyRot，math.min_angle 是 Mth.wrapDegrees
    "ysm.head_yaw": "math.min_angle(query.head_y_rotation-query.body_y_rotation)",
    "ysm.is_passenger": "query.is_riding",
    "ysm.is_sleep": "query.is_sleeping",
    "ysm.is_sneak": "query.is_sneaking",
    "ysm.has_helmet": "query.has_head_gear",
    "ysm.eye_in_water": "query.is_in_water",
    "ysm.on_ladder": "query.is_wall_climbing",
    "ysm.armor_value": "query.equipment_count",
    "ysm.swinging": "query.is_using_item",
    # YSM 也接受 query 的短名，GeckoLib 两个都认，这里统一成完整写法，好让报告读起来一致
    "q.anim_time": "query.anim_time",
    # 速度类 query 的单位是<strong>每秒</strong>，GeckoLib 给的是<strong>每 tick</strong>，差 20 倍。
    # 证据是 OpenYSM（YSM 的开放重实现）里 QueryBinding 的三个实现：
    #     yaw_speed      = 20.0f * (getYRot() - yRotO)
    #     ground_speed   = 20.0f * sqrt(dx*dx + dz*dz)
    #     vertical_speed = 20.0f * dy / deltaTime
    # 差 20 倍的症状是「看着不动」：源动画把 ground_speed 夹在 0..20、把 -3*vertical_speed 夹在
    # -10..120、把 -0.02*yaw_speed 夹在 -60..60，这些夹位只有在秒的量级上才够得着
    "q.ground_speed": "20*query.ground_speed",
    "q.vertical_speed": "20*query.vertical_speed",
    "q.yaw_speed": "20*query.yaw_speed",
}

# GeckoLib 完全没有实现的 YSM query，对照 GeckoLib 4.9.3 里 MolangQueries 的字段表确认过
UNIMPLEMENTED_QUERIES = {
    "query.position_delta": "GeckoLib 没有位移增量这个 query",
    "query.position": "GeckoLib 没有绝对坐标这个 query",
    "query.eye_target_y_rotation": "GeckoLib 没有视线目标朝向这个 query",
    "query.eye_target_x_rotation": "GeckoLib 没有视线目标朝向这个 query",
    "query.is_eating": "GeckoLib 没有 is_eating 这个 query",
    "query.is_playing_dead": "GeckoLib 没有 is_playing_dead 这个 query",
}

# 三级 —— 只为副作用而存在、或需要 GeckoLib 存不下的状态的调用 替换为 0（或连同所在项一起删掉）并记录
SIDE_EFFECT_CALLS = {
    "ysm.particle": "粒子发射在 GeckoLib 表达式里没有等价物",
    "ysm.abs_particle": "粒子发射在 GeckoLib 表达式里没有等价物",
    "ysm.play_sound": "音效播放在 GeckoLib 表达式里没有等价物",
    "ysm.stop_sound": "音效控制在 GeckoLib 表达式里没有等价物",
    "ysm.stop_all_sounds": "音效控制在 GeckoLib 表达式里没有等价物",
    "ysm.sync": "服务端同步在 GeckoLib 表达式里没有等价物",
    "query.debug_output": "聊天栏输出是 YSM 的调试设施",
    # 程序化噪声源 GeckoLib 有 math.random 但没有噪声函数，而噪声是逐帧变化的偏移，
    # 找不到忠实的无状态替代
    "ysm.perlin_noise": "GeckoLib 没有柏林噪声函数",
    "ysm.keyboard": "键盘轮询是 YSM 的客户端设施",
    "ysm.mouse": "鼠标轮询是 YSM 的客户端设施",
    "ysm.time_delta": "帧间隔时间没有暴露给 GeckoLib 表达式",
    "ysm.bone_pivot_abs": "上一帧的骨骼轴心，而 GeckoLib 表达式是无状态的",
}

# 兜底：任何没被显式处理过的 ysm.*/ctrl.*/tlm.* 名字都会被丢掉 它的作用是让将来新增函数的
# YSM 版本无法再悄悄弄坏文件：输出永远落在 GeckoLib 的文法内，报告永远写清丢掉了什么
YSM_NAMESPACE = re.compile(r"\b(?:ysm|ctrl|tlm|tacz|swem|parcool|carryon|slashblade)\.[A-Za-z_0-9]+")

# 二级 —— 有状态滤波器 `shape` 决定调用被换成什么：
#   "input"  -> 代入滤波器的静态可求值输入信号
#   "zero"   -> 输入本身就是上一帧的状态，静态上什么都留不下
STATEFUL_FILTERS = {
    "ysm.second_order": {"input": 1, "shape": "input"},
    "ysm.first_order": {"input": 1, "shape": "input"},
}

# 上一帧骨骼访问器：`ysm.bone_rot('x').y` 之类
BONE_ACCESSOR = re.compile(
    r"ysm\.bone_(?:rot|pos|scale|pivot_abs)\s*\(\s*'([^']*)'\s*\)\s*(?:\.\s*[xyz])?"
)


@dataclass
class Note:
    """对某一条表达式所做的一次可审计改动"""
    clip: str
    trail: str
    tier: int
    kind: str
    detail: str
    before: str
    after: str


@dataclass
class Report:
    notes: list[Note] = field(default_factory=list)
    clips_changed: set[str] = field(default_factory=set)

    def add(self, note: Note) -> None:
        self.notes.append(note)
        self.clips_changed.add(note.clip)

    def to_json(self) -> dict:
        by_tier: dict[str, int] = {}
        for note in self.notes:
            by_tier[f"tier{note.tier}"] = by_tier.get(f"tier{note.tier}", 0) + 1
        return {
            "summary": {
                "expressions_changed": len(self.notes),
                "clips_changed": len(self.clips_changed),
                "by_tier": by_tier,
                "tiers": {
                    "1": "精确别名/query 改名，无语义漂移",
                    "2": "有状态滤波器近似为其无状态输入（无滞后 无过冲）",
                    "3": "丢弃：没有 GeckoLib 等价物（副作用或上一帧状态）",
                },
            },
            "notes": [note.__dict__ for note in self.notes],
        }


class Tokenizer:
    """扫这些文件用到的那部分 Molang/表达式语法的字符扫描器

    字符串字面量整体成一个 token，这样引号参数里的括号与逗号绝不会影响调用边界的判断
    """

    def __init__(self, text: str):
        self.text = text
        self.i = 0

    def tokens(self) -> list[tuple[str, str]]:
        out: list[tuple[str, str]] = []
        text = self.text
        n = len(text)
        while self.i < n:
            ch = text[self.i]
            if ch in "'\"":
                quote = ch
                start = self.i
                self.i += 1
                while self.i < n and text[self.i] != quote:
                    self.i += 2 if text[self.i] == "\\" else 1
                self.i += 1
                out.append(("string", text[start:self.i]))
            elif ch.isalpha() or ch == "_":
                start = self.i
                while self.i < n and (text[self.i].isalnum() or text[self.i] == "_"):
                    self.i += 1
                # 带点的名字（ysm.foo、query.bar）算一个标识符
                while self.i < n and text[self.i] == "." and self.i + 1 < n and (
                    text[self.i + 1].isalnum() or text[self.i + 1] == "_"
                ):
                    self.i += 1
                    while self.i < n and (text[self.i].isalnum() or text[self.i] == "_"):
                        self.i += 1
                out.append(("ident", text[start:self.i]))
            elif ch.isdigit() or (ch == "." and self.i + 1 < n and text[self.i + 1].isdigit()):
                start = self.i
                while self.i < n and (text[self.i].isdigit() or text[self.i] == "."):
                    self.i += 1
                out.append(("number", text[start:self.i]))
            elif text.startswith("??", self.i) or text.startswith("++", self.i):
                out.append(("op", text[self.i:self.i + 2]))
                self.i += 2
            else:
                out.append(("op", ch))
                self.i += 1
        return out


def find_calls(text: str, names: set[str]) -> list[tuple[str, int, int, list[str]]]:
    """定位 `name(...)` 调用，返回（名字，起点，终点，实参）实参是原文切片

    嵌套调用也会报，而不只是最外层：三元提升必须看见嵌在另一个调用实参里的调用，
    那正是 GeckoLib 卡住的位置
    """
    found = []
    tokens = Tokenizer(text).tokens()
    # 重建偏移量，这样才能切原文
    offsets = []
    pos = 0
    for kind, value in tokens:
        idx = text.find(value, pos)
        offsets.append((kind, value, idx, idx + len(value)))
        pos = idx + len(value)

    i = 0
    while i < len(offsets):
        kind, value, start, end = offsets[i]
        if kind == "ident" and value in names and i + 1 < len(offsets) and offsets[i + 1][1] == "(":
            depth = 0
            args = []
            arg_start = offsets[i + 1][3]
            j = i + 1
            while j < len(offsets):
                _, tok, _, tok_end = offsets[j]
                if tok == "(":
                    depth += 1
                elif tok == ")":
                    depth -= 1
                    if depth == 0:
                        args.append(text[arg_start:offsets[j][2]])
                        break
                elif tok == "," and depth == 1:
                    args.append(text[arg_start:offsets[j][2]])
                    arg_start = tok_end
                j += 1
            close = offsets[j][3] if j < len(offsets) else end
            found.append((value, start, close, args))
            # 继续扫括号内部，这样嵌套调用也会被报出来
            i += 1
            continue
        i += 1
    return found


def split_statements(text: str) -> list[str]:
    """按顶层 `;` 与 YSM 的加法混合运算符 `++` 切分

    YSM 的平行片段用 `++` 表示「把这一项加到下层已经产出的结果上」，
    而 YSM 的求值器按顺序执行 `;` 分隔的子句 GeckoLib 求的是单条表达式，
    同时把顶层 `;` 当语句分隔符，所以两者都映射到 `;`
    """
    parts = []
    current = []
    depth = 0
    tokens = Tokenizer(text).tokens()
    pos = 0
    for kind, value in tokens:
        idx = text.find(value, pos)
        pos = idx + len(value)
        if value in "([{":
            depth += 1
        elif value in ")]}":
            depth -= 1
        if depth == 0 and (value == ";" or (kind == "op" and value == "++")):
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(text[idx:pos])
    parts.append("".join(current).strip())
    return [p for p in parts if p]


def strip_control_flow(text: str) -> tuple[str, list[str]]:
    """删掉 GeckoLib 解析不了的 YSM/Bedrock 控制流

    `return X` 变成 `X`，结尾光秃秃的 `return` 直接删掉 `loop`/`break`/`continue` 一般无法
    正确展平，删掉后由调用方记进报告
    """
    notes = []
    if re.search(r"\b(loop|break|continue)\b", text):
        notes.append("loop/break/continue")
        text = re.sub(r"\b(loop|break|continue)\b", "", text)
    text = re.sub(r"\breturn\b", "", text)
    return text, notes


class Translator:
    def __init__(self, report: Report):
        self.report = report
        self.clip = ""
        self.trail = ""

    def note(self, tier: int, kind: str, detail: str, before: str, after: str) -> None:
        self.report.add(Note(self.clip, self.trail, tier, kind, detail, before, after))

    # -- 三级 ---------------------------------------------------------------------
    def _replace_side_effect_calls(self, expr: str) -> str:
        for name, why in SIDE_EFFECT_CALLS.items():
            while True:
                calls = find_calls(expr, {name})
                if not calls:
                    break
                _, start, end, _ = calls[0]
                self.note(3, "side-effect call", f"{name}：{why}", expr[start:end], "0")
                expr = expr[:start] + "0" + expr[end:]
        return expr

    def _replace_bone_accessors(self, expr: str) -> str:
        while True:
            match = BONE_ACCESSOR.search(expr)
            if not match:
                return expr
            self.note(
                3, "previous-frame bone accessor",
                "ysm 读的是上一帧的骨骼变换，而 GeckoLib 表达式是无状态的",
                match.group(0), "0",
            )
            expr = expr[:match.start()] + "0" + expr[match.end():]

    # -- 二级 ---------------------------------------------------------------------
    def _replace_stateful_filters(self, expr: str) -> str:
        for name, spec in STATEFUL_FILTERS.items():
            while True:
                calls = find_calls(expr, {name})
                if not calls:
                    break
                _, start, end, args = calls[0]
                index = spec["input"]
                original = expr[start:end]
                if spec["shape"] == "input" and len(args) > index:
                    replacement = f"({args[index].strip()})"
                    detail = (
                        f"{name}：按名字记忆的递归滤波器，YSM 每帧积分一次，"
                        "GeckoLib 存不下这个状态，所以改用原始输入信号（无滞后 无过冲）"
                    )
                else:
                    replacement = "0"
                    detail = f"{name}：输入本身就是上一帧状态，静态上什么都留不下"
                self.note(2, "stateful filter", detail, original, replacement)
                # 滤波器自己的 `name` 实参随调用一起丢掉
                expr = expr[:start] + replacement + expr[end:]
        return expr

    # -- 一级 ---------------------------------------------------------------------
    def _replace_aliases(self, expr: str) -> str:
        for old, new in sorted(ALIASES.items(), key=lambda kv: -len(kv[0])):
            if old in expr:
                # 防止匹配到更长的点串名字（ysm.head_yaw 与 ysm.head_yaw_x）
                pattern = re.compile(re.escape(old) + r"(?![\w.])")
                if pattern.search(expr):
                    self.note(1, "alias", f"{old} -> {new}（等价改写）", old, new)
                    expr = pattern.sub(new, expr)

        for old, why in UNIMPLEMENTED_QUERIES.items():
            short = old.replace("query.", "q.", 1)
            # 带参数的 query 要连调用一起换掉：`query.position_delta(0)` 得变成 `0`，不能是 `0(0)`
            for form in (old, short):
                calls = find_calls(expr, {form})
                while calls:
                    _, start, end, _ = calls[0]
                    self.note(3, "unimplemented query", f"{form}：{why}", expr[start:end], "0")
                    expr = expr[:start] + "0" + expr[end:]
                    calls = find_calls(expr, {form})
                pattern = re.compile(re.escape(form) + r"(?![\w.])")
                if pattern.search(expr):
                    self.note(3, "unimplemented query", f"{form}：{why}", form, "0")
                    expr = pattern.sub("0", expr)
        return expr

    def _replace_variable_forms(self, expr: str) -> str:
        # YSM：`v.x ?? default` 在变量从未被赋值时取默认值（YSM 未初始化的变量是 null）
        # GeckoLib 没有 null，从未赋值的变量读出来就是 0 —— 实践中默认值给的就是 0，
        # 而且这两个运算符根本不在 GeckoLib 的文法里
        pattern = re.compile(r"([A-Za-z_][\w.]*)\s*\?\?\s*([^?;]+?)(?=[;,)]|$)")
        while True:
            match = pattern.search(expr)
            if not match:
                break
            self.note(3, "null-coalescing operator",
                      "GeckoLib 没有 null，直接使用本来就默认取 0 的变量",
                      match.group(0), match.group(1))
            expr = expr[:match.start()] + match.group(1) + expr[match.end():]
        return expr

    # -- GeckoLib 解析器逼出来的结构性修补 ----------------------------------------
    def _flatten_multidot_variables(self, expr: str) -> str:
        """`v.roaming.expe` -> `v.roaming_expe`

        GeckoLib 的 VARIABLE_FORMAT 是 `^[a-z_]+\\.[\\w+_]+$` —— 恰好一个点 YSM 的 roaming
        变量（https://ysm.cfpa.team/wiki/molang/var/）有三层，GeckoLib 在编译期就拒绝：

            Unable to parse compiled symbols from expression: [Left[v.roaming.expe]]
        """
        while True:
            match = MULTI_DOT_VARIABLE.search(expr)
            if not match:
                return expr
            name = match.group(1)
            if _variable_is_geckolib_valid(name):
                return expr
            flattened = name.replace(".", "_", name.count(".") - 1)
            self.note(2, "variable name flattened",
                      f"{name} -> {flattened}：GeckoLib 的变量名只允许一个点",
                      name, flattened)
            expr = expr[:match.start(1)] + flattened + expr[match.end(1):]

    # YSM 允许省掉条件式的假分支（`cond?value`，条件为假时求值是 null，数值上下文按 0 处理），
    # GeckoLib 的 compileTernary 只在同一层找到配对 `:` 时才认这个三元，缺冒号会让整条表达式
    # 编译失败（"Unable to parse compiled symbols"）并让整个片段被跳过。
    # 真值分支的范围按 YSM 的优先级算：吃到本层的 `,` `;` 或优先级低于三元的运算符为止。
    # 优先级取自 OpenYSM 的 BinaryExpression.Op（CONDITIONAL=1400），比它低的只有
    # NULL_COALESCE(1200) 与 ASSIGN(1)，所以 `&&` `||` `==` 这些会被吃进真值分支
    _BARE_CONDITIONAL_ORDER_SEPARATORS = (",", ";")

    @staticmethod
    def _level_parts(expr: str, start: int, end: int) -> tuple[list[tuple[str, int]], list[tuple[int, int]]]:
        """把 `expr[start:end]` 拆成（本层字符及其下标, 每个括号组的内容区间）

        括号自身的字符不算本层，组的内容由递归处理
        """
        chars: list[tuple[str, int]] = []
        children: list[tuple[int, int]] = []
        depth = 0
        child_start = start
        for i in range(start, end):
            ch = expr[i]
            if ch == "(":
                depth += 1
                if depth == 1:
                    child_start = i + 1
                else:
                    chars.append((ch, i))
            elif ch == ")":
                depth -= 1
                if depth <= 0:
                    depth = 0
                    children.append((child_start, i))
                else:
                    chars.append((ch, i))
            elif depth == 0:
                chars.append((ch, i))
        if depth > 0:
            # 括号不配对，剩下的当子层，让 GeckoLib 自己去报这个错
            children.append((child_start, end))
        return chars, children

    @staticmethod
    def _is_bare_question(expr: str, index: int) -> bool:
        """`??` 是空值合并，不是条件式"""
        return expr[index:index + 2] != "??" and expr[index - 1:index + 1] != "??"

    @staticmethod
    def _is_branch_end(expr: str, index: int) -> bool:
        """真值分支是否在这里结束：`=` 是赋值，`==`/`!=`/`<=`/`>=` 不是"""
        return expr[index] == "=" and expr[index:index + 2] != "==" and expr[index - 1] not in "=!<>"

    def _find_bare_conditional(self, expr: str, start: int, end: int) -> tuple[int, int, int] | None:
        """找最内层最右边那个没有配对 `:` 的 `?`

        返回（`?` 下标, 真值分支起点, 真值分支终点）
        """
        chars, children = self._level_parts(expr, start, end)
        for child_start, child_end in reversed(children):
            found = self._find_bare_conditional(expr, child_start, child_end)
            if found:
                return found

        pending: list[int] = []
        for ch, index in chars:
            if ch == "?" and self._is_bare_question(expr, index):
                pending.append(index)
            elif ch == ":" and pending:
                pending.pop()

        for question in reversed(pending):
            branch_end = end
            for ch, index in chars:
                if index <= question:
                    continue
                if ch in self._BARE_CONDITIONAL_ORDER_SEPARATORS or self._is_branch_end(expr, index):
                    branch_end = index
                    break
            if branch_end > question + 1:
                return question, question + 1, branch_end
        return None

    def _close_bare_conditionals(self, expr: str) -> str:
        """把 YSM 的无冒号条件式 `cond?value` 补成 `cond?(value):0`

        补 0 是精确的：YSM 在条件为假时给 null，而 null 在数值上下文里被当作 0，
        与本模型里那些「未赋值变量读作 0」是同一条依据
        """
        original = expr
        # 每次至少给一个 `?` 补上冒号，所以一定会结束
        for _ in range(256):
            found = self._find_bare_conditional(expr, 0, len(expr))
            if found is None:
                break
            question, branch_start, branch_end = found
            expr = f"{expr[:question]}?({expr[branch_start:branch_end]}):0{expr[branch_end:]}"
        if expr != original:
            self.note(2, "bare conditional closed",
                      "GeckoLib 只认 `?:`，YSM 的 `cond?value` 条件为假时是 null（数值上下文按 0），"
                      "所以补成 `cond?(value):0`",
                      original, expr)
        return expr

    def _hoist_ternaries_out_of_arguments(self, statement: str, counter: list) -> list[str]:
        """把每处 `?:` 从每个函数实参列表里挪到语句最前面

        GeckoLib 的 MathParser 编译不了实参列表里的三元运算符，例如

            Failed to parse expression '5.63304+(query.ground_speed>0?math.sin(query.anim_time*720)*2)'
              -> Unable to parse compiled symbols from expression: [Left[0], Left[?], Left[math.sin], ...]

        提升是精确的：先把三元求值写进临时变量，调用再读那个变量 GeckoLib 按顺序求值 `;` 分隔的语句，
        所以 `t.hoist0=(...);用到 t.hoist0 的表达式` 与原表达式等值

        提升必须一路冒到顶层：把三元从嵌套调用里拉出来会产生一条赋值，
        而这条赋值本身还待在上一层调用的实参列表里，带 `?` 的赋值和三元一样编译不过
        """
        assignments: list[str] = []
        original_statement = statement
        # 每一轮至少把一个三元从实参列表里挪走，所以一定会结束
        for _ in range(256):
            calls = find_calls(statement, set(FUNCTIONS))
            victim = None
            # 先处理跨度最小的：内层调用待在外层调用的实参里，
            # 先改写外层会让内层调用的偏移量失效
            for name, start, end, args in sorted(calls, key=lambda c: c[2] - c[1]):
                if any("?" in argument for argument in args):
                    victim = (name, start, end, args)
                    break
            if victim is None:
                break

            name, start, end, args = victim
            new_args = []
            for argument in args:
                if "?" not in argument:
                    new_args.append(argument)
                    continue
                temp = f"t.hoist{counter[0]}"
                counter[0] += 1
                assignments.append(f"{temp}=({self._strip_outer_parens(argument.strip())})")
                new_args.append(temp)
            rebuilt = name + "(" + ",".join(new_args) + ")"
            statement = statement[:start] + rebuilt + statement[end:]

        if assignments:
            self.note(2, "ternary hoisted out of arguments",
                      "GeckoLib 编译不了函数实参里的 ?:，所以先把值算进后一条语句的临时变量",
                      original_statement, ";".join(assignments) + ";" + statement)
        return assignments + [statement]

    @staticmethod
    def _strip_outer_parens(text: str) -> str:
        while text.startswith("(") and text.endswith(")"):
            depth = 0
            balanced = True
            for i, ch in enumerate(text):
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0 and i != len(text) - 1:
                        balanced = False
                        break
            if not balanced:
                break
            text = text[1:-1].strip()
        return text

    def _drop_unknown_ysm(self, expr: str) -> str:
        """兜底：丢掉任何闯过上面各轮显式处理的 ysm.*/ctrl.*/tlm.* 名字"""
        while True:
            match = YSM_NAMESPACE.search(expr)
            if not match:
                return expr
            name = match.group(0)
            calls = find_calls(expr, {name})
            if calls:
                _, start, end, _ = calls[0]
                replaced = expr[start:end]
            else:
                start, end = match.span()
                replaced = name
            self.note(
                3, "unmapped YSM symbol",
                f"{name}：不在对照表里，丢掉以保证片段仍能烘焙",
                replaced, "0",
            )
            expr = expr[:start] + "0" + expr[end:]

    def translate(self, expr: str) -> str:
        if not any(c.isalpha() for c in expr):
            return expr
        if expr.startswith("#"):
            # 关键帧上的音效/粒子/指令指令行是 YSM/Bedrock 的关键帧元数据
            self.note(3, "keyframe directive",
                      "YSM 关键帧指令（音效 粒子 指令）在 GeckoLib 里没有等价物",
                      expr, "0")
            return "0"

        original = expr
        statements = split_statements(expr)

        hoist_counter = [0]
        processed: list[str] = []
        for statement in statements:
            text, stripped = strip_control_flow(statement)
            if stripped:
                self.note(3, "control flow",
                          f"{' '.join(stripped)} 已删除（GeckoLib 没有控制流）",
                          statement, text)
            text = self._replace_bone_accessors(text)
            text = self._replace_side_effect_calls(text)
            text = self._replace_stateful_filters(text)
            text = self._replace_aliases(text)
            text = self._replace_variable_forms(text)
            text = self._drop_unknown_ysm(text)
            text = self._flatten_multidot_variables(text)
            text = re.sub(r"\s+", " ", text).strip()
            text = self._close_bare_conditionals(text)
            # 结构性修补可以把一条语句变成好几条（提升出去的三元）
            for piece in self._hoist_ternaries_out_of_arguments(text, hoist_counter):
                piece = re.sub(r"\s+", " ", piece).strip()
                processed.append(piece if piece else "0")

        # 整条式子全部退化成中性占位符时合成一个 0，输出更可读也更好做游程压缩；
        # 否则按顺序保留每一条语句（它们可能带着后面语句要读的赋值）
        if all(part == "0" for part in processed):
            merged = "0"
        else:
            merged = ";".join(processed)

        if merged != original and not any(
            note.clip == self.clip and note.trail == self.trail for note in self.report.notes
        ):
            self.note(1, "normalised", "只做了空白/规范化", original, merged)
        return merged


def convert(doc: dict, report: Report, *, only_clips: set[str] | None = None) -> dict:
    """转换整个文件；`only_clips` 给定时只转换其中一部分片段"""
    translator = Translator(report)
    out = dict(doc)
    animations = {}
    for clip_name, clip in doc.get("animations", {}).items():
        if only_clips is not None and clip_name not in only_clips:
            animations[clip_name] = clip
            continue
        translator.clip = clip_name
        new_clip = json.loads(json.dumps(clip))  # 深拷贝
        _walk_and_translate(new_clip, [], translator)
        animations[clip_name] = new_clip
    out["animations"] = animations
    return out


def _walk_and_translate(node, trail: list[str], translator: Translator):
    """就地改写每一条表达式

    Bedrock 关键帧的值有两种形状，两种都要处理：
      * 分量数组，例如 "rotation": [5, "expr", 0]      -> 列表里的字符串<em>元素</em>
      * 按时间戳的映射，例如 "rotation": {"0.0": "expr", "0.2": "expr2"}
    第一种更常见，所以只处理字典的值会静默漏掉文件里大部分内容
    """
    if isinstance(node, dict):
        for key, value in node.items():
            if _is_expression(value):
                translator.trail = ".".join(trail + [str(key)])
                node[key] = translator.translate(value)
            else:
                _walk_and_translate(value, trail + [str(key)], translator)
    elif isinstance(node, list):
        for i, value in enumerate(node):
            if _is_expression(value):
                translator.trail = ".".join(trail + [str(i)])
                node[i] = translator.translate(value)
            else:
                _walk_and_translate(value, trail + [str(i)], translator)


def _is_expression(value) -> bool:
    """判断字符串是 Molang 还是骨骼名之类的字面内容"""
    return (
        isinstance(value, str)
        and any(c.isalpha() for c in value)
        and not value.startswith("$")
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("target")
    parser.add_argument("--report")
    parser.add_argument("--clips", help="只转换这些片段，逗号分隔")
    args = parser.parse_args()

    with open(args.source, encoding="utf-8") as handle:
        doc = json.load(handle)

    report = Report()
    only = set(args.clips.split(",")) if args.clips else None
    converted = convert(doc, report, only_clips=only)

    with open(args.target, "w", encoding="utf-8") as handle:
        json.dump(converted, handle, ensure_ascii=False, indent=1)

    # 拿 GeckoLib 的解析规则复核结果，而不是假定成功
    problems = validate(converted)
    report_json = report.to_json()
    report_json["geckolib_validation"] = problems

    if args.report:
        with open(args.report, "w", encoding="utf-8") as handle:
            json.dump(report_json, handle, ensure_ascii=False, indent=1)

    summary = report_json["summary"]
    print(f"片段数            : {len(converted.get('animations', {}))}")
    print(f"改动的表达式      : {summary['expressions_changed']} 条，分布在 {summary['clips_changed']} 个片段")
    print(f"按级别            : {summary['by_tier']}")
    print(f"校验              : {'通过' if not problems else f'{len(problems)} 个片段仍未通过'}")
    for problem in problems:
        print(f"  - {problem['clip']}: {'; '.join(problem['reasons'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
