"""导出指定片段的原始表达式字符串，一行一条，供设计转换表时查看

用法：python ysm_dump.py <animation.json> [片段名,逗号分隔]
"""
import json
import sys


def walk(node, trail, out):
    if isinstance(node, str):
        if any(c.isalpha() for c in node):
            out.append((".".join(trail), node))
    elif isinstance(node, dict):
        for key, value in node.items():
            walk(value, trail + [str(key)], out)
    elif isinstance(node, list):
        for i, value in enumerate(node):
            walk(value, trail + [str(i)], out)


def main(path, wanted):
    with open(path, encoding="utf-8") as handle:
        doc = json.load(handle)
    for name, clip in doc["animations"].items():
        if wanted and name not in wanted:
            continue
        out = []
        walk(clip, [], out)
        print(f"########## {name}   （{len(out)} 条表达式）")
        for trail, expr in out:
            print(f"  [{trail}] {expr}")
        print()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    main(sys.argv[1], set(sys.argv[2].split(",")) if len(sys.argv) > 2 else None)
