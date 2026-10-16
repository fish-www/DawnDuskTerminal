#!/usr/bin/env python3
"""检查世界生成里「有副本却指向原版」的特征引用

`configured_feature` 内部的引用不在移植脚本的改写范围内。漏改不会报错，只会静默：
模组副本把土换成了 `dawnduskterminal:rosy_dirt`，原版那份不会，于是同一片林子里
一半的树长在霞土上，另一半长在原版泥土上。

用法：

    python3 tools/check_worldgen_refs.py              # 有问题时退出码 1
    python3 tools/check_worldgen_refs.py --list-dead  # 同时列出没人引用的副本

一条引用指的是哪个注册表，取决于它长什么样：

| 形态 | 指向 |
|---|---|
| `{"feature": "X", "placement": [...]}` 里的 `X` | 配置特征 |
| `"default": "X"` 或不在内联对象里的 `"feature": "X"` | 放置特征 |

所以内联对象里的 `X` 要在 `configured_feature/` 里找同名副本，裸字符串要在
`placed_feature/` 里找同名副本。`minecraft:` 与 `dawnduskterminal:` 两个命名空间里
都有同一个名字时，用原版那个就是漏改。
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main")
WORLDGEN = os.path.join(SRC, "resources", "data", "dawnduskterminal", "worldgen")
CF_DIR = os.path.join(WORLDGEN, "configured_feature")
PF_DIR = os.path.join(WORLDGEN, "placed_feature")


def load(path):
    with open(path, "rb") as handle:
        return json.loads(handle.read().decode("utf-8-sig"))


def names(directory):
    out = set()
    for base, _dirs, files in os.walk(directory):
        for f in files:
            if f.endswith(".json"):
                rel = os.path.relpath(os.path.join(base, f), directory)
                out.add(rel[:-len(".json")].replace(os.sep, "/"))
    return out


def collect(node, key, inside_placed, out_configured, out_placed):
    if isinstance(node, dict):
        is_placed = "feature" in node and "placement" in node and isinstance(node["feature"], str)
        for k, v in node.items():
            if k == "feature" and is_placed and isinstance(v, str):
                out_configured.append(v)
                continue
            collect(v, k, is_placed, out_configured, out_placed)
    elif isinstance(node, list):
        for v in node:
            collect(v, key, inside_placed, out_configured, out_placed)
    elif isinstance(node, str) and key in ("default", "feature") and not inside_placed:
        out_placed.append(node)


def source_blob():
    """src/main 下所有文本文件拼起来，用来判断某个副本有没有被人提到"""
    parts = []
    for base, _dirs, files in os.walk(SRC):
        for name in files:
            try:
                parts.append(open(os.path.join(base, name), encoding="utf-8-sig", errors="ignore").read())
            except OSError:
                continue
    return "\n".join(parts)


def main():
    configured = names(CF_DIR)
    placed = names(PF_DIR)
    missed = []
    referenced_configured = set()
    referenced_placed = set()

    for base, _dirs, files in os.walk(CF_DIR):
        for name in sorted(files):
            if not name.endswith(".json"):
                continue
            rel = os.path.relpath(os.path.join(base, name), CF_DIR).replace(os.sep, "/")
            refs_configured, refs_placed = [], []
            collect(load(os.path.join(base, name)), None, False, refs_configured, refs_placed)
            for ref in refs_configured:
                namespace, _, path = ref.partition(":")
                if namespace == "minecraft" and path in configured:
                    missed.append((rel, path, "configured_feature"))
                elif namespace == "dawnduskterminal":
                    referenced_configured.add(path)
            for ref in refs_placed:
                namespace, _, path = ref.partition(":")
                if namespace == "minecraft" and path in placed:
                    missed.append((rel, path, "placed_feature"))
                elif namespace == "dawnduskterminal":
                    referenced_placed.add(path)

    status = 0
    if missed:
        status = 1
        print(f"有副本却指向原版：{len(missed)} 处")
        for rel, path, registry in sorted(missed):
            print(f"  {rel}: minecraft:{path} -> dawnduskterminal:{path}  ({registry})")
    else:
        print("特征引用检查通过")

    # 没人引用的副本。放这特征由 placed_feature/tree/... 这种多层路径，所以直接搜整棵 src/main
    blob = source_blob()
    dead_configured = [n for n in sorted(configured) if f"dawnduskterminal:{n}" not in blob]
    dead_placed = [n for n in sorted(placed) if f"dawnduskterminal:{n}" not in blob]
    if "--list-dead" in sys.argv:
        print(f"\n没有任何引用的配置特征副本：{len(dead_configured)}")
        for name in dead_configured:
            print("  " + name)
        print(f"\n没有任何引用的放置特征副本：{len(dead_placed)}")
        for name in dead_placed:
            print("  " + name)
    else:
        print(f"没有任何引用的副本：配置特征 {len(dead_configured)} 放置特征 {len(dead_placed)}"
              "（用 --list-dead 看名字）")

    return status


if __name__ == "__main__":
    sys.exit(main())
