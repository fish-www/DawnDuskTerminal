#!/usr/bin/env python3
"""校验移植后的暮色森林群系：JSON 合法、没有残留的 minecraft:* 特征，且每个
twilightforest:* 放置特征都能在真正的 TF jar 里解析到。"""
import json
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
TF_JAR = REPO_ROOT / "run" / "client" / "mods" / "twilightforest-1.21.1-4.8.3345-universal.jar"
BIOME_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "dawnduskterminal" / "worldgen" / "biome"

VANILLA_NAMES = [
    "dark_forest", "birch_forest", "flower_forest", "jungle", "swamp",
    "plains", "savanna", "snowy_plains", "windswept_hills",
]


def main() -> int:
    with zipfile.ZipFile(TF_JAR) as zf:
        tf_pf = {
            e.filename[len("data/twilightforest/worldgen/placed_feature/"):-len(".json")]
            for e in zf.infolist()
            if e.filename.startswith("data/twilightforest/worldgen/placed_feature/")
            and e.filename.endswith(".json")
        }

    failures = 0
    for name in VANILLA_NAMES:
        path = BIOME_DIR / f"{name}.json"
        try:
            doc = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            print(f"{name}: INVALID JSON {e}")
            failures += 1
            continue

        leftover = [f for step in doc["features"] for f in step if f.startswith("minecraft:")]
        tf_refs = [f for step in doc["features"] for f in step if f.startswith("twilightforest:")]
        missing_tf = [f for f in tf_refs if f[len("twilightforest:"):] not in tf_pf]

        carvers = doc.get("carvers", {}).get("air", [])
        print(
            f"{name}: leftover_minecraft={leftover} missing_tf={missing_tf} "
            f"carvers={carvers}"
        )
        if leftover or missing_tf:
            failures += 1

    print(f"\n{'ALL OK' if failures == 0 else f'{failures} PROBLEM(S)'}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
