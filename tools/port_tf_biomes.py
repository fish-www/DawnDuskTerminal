#!/usr/bin/env python3
"""Port Twilight Forest biomes into the dawn-dusk terminal dimension.

For each (tf_biome, vanilla_biome) pair this replaces the content of the mod's
`dawnduskterminal:<vanilla_biome>` copy with the TF biome, while keeping:
  * the existing mod copy's `carvers` (so caves still carve in this dimension)
  * every `minecraft:*` placed feature remapped to `dawnduskterminal:*` (palette)
  * every `twilightforest:*` placed feature referenced directly (TF is a hard dep)

Run from the repo root with the bundled Python. The source jar is the user's
actual TF jar (twilightforest-1.21.1-4.8.3345-universal.jar).
"""
import json
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
TF_JAR = REPO_ROOT / "run" / "client" / "mods" / "twilightforest-1.21.1-4.8.3345-universal.jar"
BIOME_OUT = REPO_ROOT / "src" / "main" / "resources" / "data" / "dawnduskterminal" / "worldgen" / "biome"

# (tf_biome, vanilla_biome) — the TF biome id replaces the vanilla biome copy of the same name.
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
