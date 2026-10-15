#!/usr/bin/env python3
"""把暮色森林群系移植进晨昏线维度。

对每一组 (tf_biome, vanilla_biome)，用 TF 群系替换模组那份
`dawnduskterminal:<vanilla_biome>` 副本的内容，同时保留：
  * 模组副本原有的 `carvers`（这样洞穴仍会在本维度雕刻）
  * 每个 `minecraft:*` 放置特征都重映射到 `dawnduskterminal:*`（调色板）
  * 每个 `twilightforest:*` 放置特征都直接引用（TF 是硬依赖）

在仓库根目录用自带的 Python 运行。源 jar 是用户实际安装的 TF jar
（twilightforest-1.21.1-4.8.3345-universal.jar）。
"""
import json
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
TF_JAR = REPO_ROOT / "run" / "client" / "mods" / "twilightforest-1.21.1-4.8.3345-universal.jar"
BIOME_OUT = REPO_ROOT / "src" / "main" / "resources" / "data" / "dawnduskterminal" / "worldgen" / "biome"

# (tf_biome, vanilla_biome) —— TF 群系 id 替换同名的原版群系副本。
PAIRS = [
    ("dark_forest_center", "dark_forest"),
    ("firefly_forest", "birch_forest"),
    ("dense_forest", "flower_forest"),
    ("enchanted_forest", "jungle"),
    ("fire_swamp", "swamp"),
    ("clearing", "plains"),
    ("oak_savannah", "savanna"),
    ("glacier", "snowy_plains"),
    ("highlands", "windswept_hills"),
]


def remap_feature(feature_id: str) -> str:
    if isinstance(feature_id, str) and feature_id.startswith("minecraft:"):
        return "dawnduskterminal:" + feature_id[len("minecraft:"):]
    return feature_id


def transform(tf_json: dict, vanilla_json: dict) -> dict:
    out = dict(tf_json)
    out["carvers"] = vanilla_json.get("carvers", {"air": []})
    out["features"] = [
        [remap_feature(f) for f in step] for step in tf_json.get("features", [])
    ]
    return out


def main() -> int:
    if not TF_JAR.exists():
        print(f"TF jar not found: {TF_JAR}", file=sys.stderr)
        return 1

    with zipfile.ZipFile(TF_JAR) as zf:
        for tf_name, vanilla_name in PAIRS:
            tf_entry = f"data/twilightforest/worldgen/biome/{tf_name}.json"
            tf_json = json.loads(zf.read(tf_entry).decode("utf-8"))

            vanilla_path = BIOME_OUT / f"{vanilla_name}.json"
            with open(vanilla_path, "r", encoding="utf-8") as f:
                vanilla_json = json.load(f)

            out = transform(tf_json, vanilla_json)
            with open(vanilla_path, "w", encoding="utf-8") as f:
                json.dump(out, f, indent=2, ensure_ascii=False)
                f.write("\n")

            effects = tf_json.get("effects", {})
            music = effects.get("music")
            print(
                f"{tf_name} -> {vanilla_name}: "
                f"temp={tf_json.get('temperature')} "
                f"grass_mod={effects.get('grass_color_modifier')!r} "
                f"music_type={'list' if isinstance(music, list) else 'object'} "
                f"feature_steps={len(out['features'])}"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
