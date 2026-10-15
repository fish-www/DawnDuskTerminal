#!/usr/bin/env python3
"""Port the vanilla Overworld worldgen into the DawnDuskTerminal "chrono" dimension.

What this script does
---------------------
1. Extracts ``data/minecraft/worldgen/{density_function,noise,noise_settings}`` from the
   NeoForge-decompiled Minecraft client jar found in the Gradle cache.
2. Copies the Overworld noise router (density functions) plus every *portable* noise
   parameter they transitively reference into the ``dawnduskterminal`` namespace.
3. Writes ``worldgen/noise_settings/chrono.json`` as a faithful port of
   ``minecraft:noise_settings/overworld.json``, repointed at the ported ids.
4. Validates that every reference inside the ported documents resolves, and prints a
   report of anything that stays vanilla and why.

Why moving the namespace *is* the seed derivation
-------------------------------------------------
``Noises.instantiate(registry, randomFactory, key)`` builds each noise field as
``NormalNoise.create(randomFactory.fromHashOf(key.location()), params)``, and

    RandomState.random = settings.getRandomSource().newInstance(worldSeed).forkPositional()

so a noise field is a pure function of ``(world seed, noise key ResourceLocation)``.
Re-registering the very same ``firstOctave``/``amplitudes`` under ``dawnduskterminal:*``
therefore produces terrain with Overworld character but a *different, seed-derived*
realisation, instead of a 1:1 clone of the Overworld at the same coordinates.

Boundaries that a datapack cannot cross (documented, not bugs)
--------------------------------------------------------------
* ``old_blended_noise`` (the base 3D noise) is seeded from the hardcoded
  ``minecraft:terrain`` location (``RandomState.wrapNew``), so that one component is
  identical to the Overworld's.
* The aquifer and ore positional randoms are hardcoded to ``minecraft:aquifer`` and
  ``minecraft:ore``.
* Code-registered singletons (``minecraft:zero``, ``minecraft:y``,
  ``minecraft:blend_density``, ``minecraft:blend_alpha``, ``minecraft:blend_offset``,
  ``minecraft:beardifier``, ``minecraft:end_islands``) stay vanilla; they carry no
  seed-dependent noise of their own.

Usage
-----
    python tools/port_overworld_worldgen.py            # port + validate
    python tools/port_overworld_worldgen.py --check     # validate only, write nothing
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

MOD_ID = "dawnduskterminal"
VANILLA_NS = "minecraft"

REPO_ROOT = Path(__file__).resolve().parent.parent
OUT_ROOT = REPO_ROOT / "src" / "main" / "resources" / "data" / MOD_ID / "worldgen"

# --------------------------------------------------------------------------------------
# What gets ported
# --------------------------------------------------------------------------------------

# The Overworld noise router, lifted from `minecraft:noise_settings/overworld.json`.
# `shift_x`/`shift_z` are included so the positional shift is re-derived as well.
PORT_DENSITY_FUNCTIONS = [
    "shift_x",
    "shift_z",
    "overworld/base_3d_noise",
    "overworld/continents",
    "overworld/erosion",
    "overworld/depth",
    "overworld/factor",
    "overworld/jaggedness",
    "overworld/offset",
    "overworld/ridges",
    "overworld/ridges_folded",
    "overworld/sloped_cheese",
    "overworld/caves/entrances",
    "overworld/caves/noodle",
    "overworld/caves/pillars",
    "overworld/caves/spaghetti_2d",
    "overworld/caves/spaghetti_2d_thickness_modulator",
    "overworld/caves/spaghetti_roughness_function",
]

# Keys whose string values are *not* worldgen references and must never be rewritten.
#   type        - a registered codec id
#   Name        - a block id inside a `result_state` / `default_block`
#   random_name - a random source name, not a registry id
#   biome_is    - biome ids
NEVER_REWRITE_KEYS = {"type", "Name", "random_name", "biome_is"}

# Density functions registered in code (NoiseRouterData.bootstrap) rather than shipped as
# datapack files. They resolve in any datapack and are deliberately left vanilla.
CODE_REGISTERED_DENSITY_FUNCTIONS = {
    "zero",
    "y",
    "blend_density",
    "blend_alpha",
    "blend_offset",
    "beardifier",
    "end_islands",
}

# --------------------------------------------------------------------------------------
# The chrono block palette
# --------------------------------------------------------------------------------------
# The chrono dimension generates its own blocks, not the vanilla ones the Overworld pipeline is
# written with. Everything the worldgen data can own is swapped right here:
#
#   * `default_block`   - the bulk terrain, plus what the surface rule defaults to
#   * `default_fluid`   - aquifers and oceans
#   * every `result_state` the surface rule can emit
#
# The swap is applied to any `"Name": ...` inside the settings document, which covers all three.
# Blocks that only ever arrive through *biome features* (granite / diorite / andesite / tuff ore
# blobs, lava lakes and springs, pile_snow, ice patches, disks) cannot be reached from here - a
# biome's feature list is global, so those need per-dimension biomes to move too.
PALETTE = {
    "minecraft:grass_block": "dawnduskterminal:rosy_grass",
    "minecraft:dirt": "dawnduskterminal:rosy_dirt",
    "minecraft:stone": "dawnduskterminal:canopy_stone",
    "minecraft:granite": "dawnduskterminal:ochre",
    "minecraft:diorite": "dawnduskterminal:greenschist",
    "minecraft:andesite": "dawnduskterminal:eclogite",
    "minecraft:deepslate": "dawnduskterminal:charnockite",
    "minecraft:tuff": "dawnduskterminal:twilight_stone",
    "minecraft:sand": "dawnduskterminal:road_sand",
    "minecraft:red_sand": "dawnduskterminal:unconscious_sand",
    "minecraft:sandstone": "dawnduskterminal:siltstone",
    "minecraft:red_sandstone": "dawnduskterminal:siltstone",
    "minecraft:ice": "dawnduskterminal:frozen_canopy",
    "minecraft:packed_ice": "dawnduskterminal:frozen_canopy",
    "minecraft:snow": "dawnduskterminal:canopy_frost",
    "minecraft:snow_block": "dawnduskterminal:canopy_frost_block",
    "minecraft:water": "dawnduskterminal:portal_fluid",
    "minecraft:lava": "dawnduskterminal:enthu_fluid",
}


# --------------------------------------------------------------------------------------
# Locating the vanilla datapack
# --------------------------------------------------------------------------------------


def gradle_cache_roots() -> list[Path]:
    roots = []
    env = os.environ.get("GRADLE_USER_HOME")
    if env:
        roots.append(Path(env))
    home = Path(os.path.expanduser("~"))
    roots.append(home / ".gradle")
    return [r for r in roots if r.is_dir()]


def find_minecraft_client_jars() -> list[Path]:
    found: list[Path] = []
    for root in gradle_cache_roots():
        for pattern in (
            "caches/neoformruntime/artifacts/minecraft_*_client.jar",
            "caches/neoformruntime/intermediate_results/*_resourcesOutput.jar",
            "caches/modules-2/files-2.1/net.neoforged/minecraft*/**/*.jar",
        ):
            found.extend(sorted(root.glob(pattern)))
    # Keep only archives that actually carry the vanilla worldgen datapack.
    usable = []
    for jar in found:
        try:
            with zipfile.ZipFile(jar) as zf:
                if any(n.startswith("data/minecraft/worldgen/noise_settings/") for n in zf.namelist()):
                    usable.append(jar)
        except zipfile.BadZipFile:
            continue
    return usable


def extract_vanilla_worldgen(dest: Path) -> Path:
    jars = find_minecraft_client_jars()
    if not jars:
        sys.exit(
            "error: could not find a Minecraft client jar containing data/minecraft/worldgen/.\n"
            "       Run `gradlew build` (or runClient) once so NeoForm populates the Gradle cache."
        )
    jar = jars[0]
    print(f"vanilla source : {jar}")
    worldgen_root = dest / "worldgen"
    tag_root = dest / "tags" / "biome"
    block_tag_root = dest / "tags" / "block"
    prefixes = (
        ("data/minecraft/worldgen/", worldgen_root),
        ("data/minecraft/tags/worldgen/biome/", tag_root),
        ("data/minecraft/tags/block/", block_tag_root),
    )
    counts = {root: 0 for _, root in prefixes}
    with zipfile.ZipFile(jar) as zf:
        for name in zf.namelist():
            if name.endswith("/"):
                continue
            for prefix, root in prefixes:
                if name.startswith(prefix):
                    target = root / name[len(prefix):]
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(zf.read(name))
                    counts[root] += 1
                    break
    print(f"extracted      : {counts[worldgen_root]} worldgen files, {counts[tag_root]} biome tags")
    return worldgen_root


# --------------------------------------------------------------------------------------
# Reference resolution
# --------------------------------------------------------------------------------------


def rel_id(resource_id: str) -> str:
    return resource_id.split(":", 1)[1] if ":" in resource_id else resource_id


class Vanilla:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.df = {p.relative_to(root / "density_function").with_suffix("").as_posix()
                   for p in (root / "density_function").rglob("*.json")}
        self.noise = {p.relative_to(root / "noise").with_suffix("").as_posix()
                      for p in (root / "noise").rglob("*.json")}

    def classify(self, value: str) -> tuple[str, str] | None:
        """Return ('df'|'noise', relative_path) when `value` is a portable vanilla id."""
        if not value.startswith(f"{VANILLA_NS}:"):
            return None
        path = rel_id(value)
        if path in self.df:
            return ("df", path)
        if path in self.noise:
            return ("noise", path)
        return None

    def read(self, kind: str, path: str):
        folder = "density_function" if kind == "df" else "noise"
        return json.loads((self.root / folder / f"{path}.json").read_text(encoding="utf-8"))


def iter_reference_strings(node, key: str | None = None):
    """Yield every string that sits in a position that can hold a worldgen reference."""
    if isinstance(node, dict):
        for k, v in node.items():
            yield from iter_reference_strings(v, k)
    elif isinstance(node, list):
        for v in node:
            yield from iter_reference_strings(v, key)
    elif isinstance(node, str) and key not in NEVER_REWRITE_KEYS:
        yield node


def iter_block_names(node):
    """Yield every block-state `Name` in a settings document."""
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "Name" and isinstance(v, str):
                yield v
            else:
                yield from iter_block_names(v)
    elif isinstance(node, list):
        for v in node:
            yield from iter_block_names(v)


def compute_closure(vanilla: Vanilla, settings_doc) -> tuple[set[str], set[str]]:
    """Transitively collect the density functions and noise params that must be ported."""
    df: set[str] = set()
    noise: set[str] = set()

    queue: list[object] = []
    for name in PORT_DENSITY_FUNCTIONS:
        if name not in vanilla.df:
            sys.exit(f"error: vanilla density function `{name}` not found in the extracted datapack")
        queue.append(vanilla.read("df", name))
    queue.append(settings_doc)

    while queue:
        doc = queue.pop()
        for value in iter_reference_strings(doc):
            hit = vanilla.classify(value)
            if hit is None:
                continue
            kind, path = hit
            if kind == "df":
                if path not in df:
                    df.add(path)
                    queue.append(("df", vanilla.read("df", path)))
            else:
                noise.add(path)

    for name in PORT_DENSITY_FUNCTIONS:
        df.add(name)
    return df, noise


def rewrite(node, df: set[str], noise: set[str], key: str | None = None):
    if isinstance(node, dict):
        return {k: rewrite(v, df, noise, k) for k, v in node.items()}
    if isinstance(node, list):
        return [rewrite(v, df, noise, key) for v in node]
    if isinstance(node, str) and key not in NEVER_REWRITE_KEYS:
        if node.startswith(f"{VANILLA_NS}:"):
            path = rel_id(node)
            if path in df or path in noise:
                return f"{MOD_ID}:{path}"
    return node


def apply_palette(node):
    """Swaps every block-state `Name` the worldgen settings can own onto the chrono palette."""
    if isinstance(node, dict):
        return {
            k: (PALETTE.get(v, v) if k == "Name" and isinstance(v, str) else apply_palette(v))
            for k, v in node.items()
        }
    if isinstance(node, list):
        return [apply_palette(v) for v in node]
    return node


# --------------------------------------------------------------------------------------
# The palette layer: biomes and the features they place
# --------------------------------------------------------------------------------------
# Anything that arrives through a *biome feature* cannot be reached from the noise settings,
# because a biome's feature list is part of the global biome registry. So the dimension owns
# faithful copies of the biomes and features that touch a palette block, wired to each other:
#
#   configured_feature  (block providers / predicates swapped)
#        ^
#   placed_feature      (points at our configured feature)
#        ^
#   biome               (feature list points at our placed feature)
#        ^
#   multi_noise parameter list  (points at our biome)
#
# Whatever is not affected keeps its vanilla id, and the copies keep vanilla's ids one level down.


def load_json_dir(root: Path) -> dict[str, object]:
    out: dict[str, object] = {}
    if not root.is_dir():
        return out
    for p in sorted(root.rglob("*.json")):
        out[p.relative_to(root).with_suffix("").as_posix()] = json.loads(p.read_text(encoding="utf-8"))
    return out


def write_json(path: Path, doc) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")


def references_palette(node) -> bool:
    """True when the document names one of the blocks/fluids the palette owns."""
    if isinstance(node, dict):
        return any(references_palette(v) for v in node.values())
    if isinstance(node, list):
        return any(references_palette(v) for v in node)
    return isinstance(node, str) and node in PALETTE


def references_any(node, mapping: dict[str, str]) -> bool:
    if isinstance(node, dict):
        return any(references_any(v, mapping) for v in node.values())
    if isinstance(node, list):
        return any(references_any(v, mapping) for v in node)
    return isinstance(node, str) and node in mapping


def swap_block_ids(node, key: str | None = None):
    """Swaps block/fluid id references, everywhere except under a codec `type` key."""
    if isinstance(node, dict):
        return {k: (v if k == "type" else swap_block_ids(v, k)) for k, v in node.items()}
    if isinstance(node, list):
        return [swap_block_ids(v, key) for v in node]
    if isinstance(node, str) and key != "type" and node in PALETTE:
        return PALETTE[node]
    return node


def retarget(node, mapping: dict[str, str]):
    """Rewrites worldgen id references, e.g. a placed feature's configured feature."""
    if isinstance(node, dict):
        return {k: retarget(v, mapping) for k, v in node.items()}
    if isinstance(node, list):
        return [retarget(v, mapping) for v in node]
    if isinstance(node, str) and node in mapping:
        return mapping[node]
    return node


def collect_refs(node, names: set[str]) -> set[str]:
    """Relative ids out of `names` that the document references, in either namespace."""
    found: set[str] = set()
    if isinstance(node, dict):
        for v in node.values():
            found |= collect_refs(v, names)
    elif isinstance(node, list):
        for v in node:
            found |= collect_refs(v, names)
    elif isinstance(node, str):
        for ns in (VANILLA_NS, MOD_ID):
            if node.startswith(f"{ns}:"):
                path = rel_id(node)
                if path in names:
                    found.add(path)
                break
    return found


def overworld_biome_names(vanilla_tag_root: Path) -> set[str]:
    """The biomes `minecraft:is_overworld` lists - the only ones this dimension can ever reach."""
    tag = vanilla_tag_root / "is_overworld.json"
    if not tag.exists():
        return set()
    names: set[str] = set()
    for entry in json.loads(tag.read_text(encoding="utf-8")).get("values", []):
        name = entry.get("id") if isinstance(entry, dict) else entry
        if isinstance(name, str) and name.startswith(f"{VANILLA_NS}:"):
            names.add(rel_id(name))
    return names


def id_map(names) -> dict[str, str]:
    return {f"{VANILLA_NS}:{k}": f"{MOD_ID}:{k}" for k in names}


# Block tags the copied features test through. Vanilla's own copies are shared with the Overworld, so
# the dimension gets its own and the copied features point at those instead of widening vanilla's -
# widening them also let every ore feature replace every palette stone, not just stone.
BLOCK_TAG_RETARGET = (
    "base_stone_overworld",
    "stone_ore_replaceables",
    "deepslate_ore_replaceables",
    "overworld_carver_replaceables",
    "lush_ground_replaceable",
    "dirt",
    "dirt_replaceable",
    "sand",
    "snow",
    "ice",
    "leaves",
    "logs",
)


def retarget_biome_ids(node, biome_names: set[str], key: str | None = None):
    """Points biome conditions at our copies.

    Vanilla's overworld surface rule names biomes directly (`biome_is: ["minecraft:desert", ...]`)
    rather than through tags. In this dimension the real biome is the copy, so an unrewritten
    `minecraft:desert` never matches, every branch misses, and the whole world falls through to the
    default grass/dirt branch.
    """
    if isinstance(node, dict):
        return {k: retarget_biome_ids(v, biome_names, k) for k, v in node.items()}
    if isinstance(node, list):
        return [retarget_biome_ids(v, biome_names, key) for v in node]
    if isinstance(node, str) and key in ("biome_is", "biomes"):
        ns, sep, path = node.partition(":")
        if sep and ns == VANILLA_NS and path in biome_names:
            return f"{MOD_ID}:{path}"
    return node


def retarget_block_tags(node, key: str | None = None):
    """Points block tag predicates at our own tags, only where a tag is really being referenced."""
    if isinstance(node, dict):
        return {k: retarget_block_tags(v, k) for k, v in node.items()}
    if isinstance(node, list):
        return [retarget_block_tags(v, key) for v in node]
    if isinstance(node, str) and key in ("tag", "matching_block_tag", "replaceable"):
        prefix = "#" if node.startswith("#") else ""
        ns, sep, path = node[len(prefix):].partition(":")
        if sep and ns == VANILLA_NS and path in BLOCK_TAG_RETARGET:
            return f"{prefix}{MOD_ID}:{path}"
    return node


def build_palette_layer(vanilla_root: Path, vanilla_tag_root: Path, out_root: Path) -> dict:
    cf_all = load_json_dir(vanilla_root / "configured_feature")
    pf_all = load_json_dir(vanilla_root / "placed_feature")
    biome_all = load_json_dir(vanilla_root / "biome")
    carver_all = load_json_dir(vanilla_root / "configured_carver")
    overworld = overworld_biome_names(vanilla_tag_root)

    # 1. configured features that place or test a palette block, closed upward: a vanilla parent
    #    feature would otherwise keep pointing at the vanilla child that places palette blocks
    cf_keep = {k for k, v in cf_all.items() if references_palette(v)}
    while True:
        grown = cf_keep | {k for k, v in cf_all.items() if collect_refs(v, cf_keep)}
        if grown == cf_keep:
            break
        cf_keep = grown

    # 2. placed features that reach a kept configured feature, or touch a palette block themselves
    pf_keep = {k for k, v in pf_all.items() if references_palette(v) or collect_refs(v, cf_keep)}

    # 3. the overworld biomes whose feature lists reference a kept placed feature
    biome_keep = {k for k, v in biome_all.items() if k in overworld and collect_refs(v, pf_keep)}

    # 4. carvers are configured features reached from the biome's `carvers` map rather than from its
    #    feature list, and they carve through a block tag too - miss them and there are no caves
    carvers = {rel_id(ref) for k in biome_keep for refs in (biome_all[k].get("carvers") or {}).values()
               for ref in refs}

    # 5. prune the feature sets to what the kept biomes actually reach (drops the nether features)
    biome_docs = {k: retarget(swap_block_ids(biome_all[k]),
                              id_map(pf_keep) | id_map(carvers)) for k in biome_keep}
    pf_keep = collect_refs(biome_docs, pf_all)
    cf_keep = collect_refs({k: pf_all[k] for k in pf_keep}, cf_keep)
    while True:
        grown = cf_keep | collect_refs({k: cf_all[k] for k in cf_keep}, cf_all)
        if grown == cf_keep:
            break
        cf_keep = grown
    # carvers live in their own registry (worldgen/carver), not in configured_feature, and they carve
    # through `replaceable` - an uncopied carver would find no matching block and carve nothing
    for k in sorted(carvers & set(carver_all)):
        write_json(out_root / "configured_carver" / f"{k}.json",
                   retarget_block_tags(swap_block_ids(carver_all[k])))

    # the biome references are rewritten only once, and only after both sets are final: step 3 built
    # them from a provisional pf_keep that a biome-only feature like freeze_top_layer is not in yet
    biome_docs = {k: retarget(swap_block_ids(biome_all[k]),
                              id_map(pf_keep) | id_map(carvers)) for k in biome_keep}

    for k in sorted(cf_keep):
        write_json(out_root / "configured_feature" / f"{k}.json",
                   retarget_block_tags(swap_block_ids(cf_all[k])))
    # `minecraft:freeze_top_layer` writes ICE and SNOW from code, so no JSON swap can reach it - the
    # dimension points the feature list at its own feature that places the palette's snow and ice
    write_json(out_root / "configured_feature" / "freeze_top_layer.json",
               {"type": f"{MOD_ID}:freeze_top_layer", "config": {}})
    for k in sorted(pf_keep):
        doc = retarget_block_tags(retarget(swap_block_ids(pf_all[k]), id_map(cf_keep)))
        if k == "freeze_top_layer":
            doc["feature"] = f"{MOD_ID}:freeze_top_layer"
        write_json(out_root / "placed_feature" / f"{k}.json", doc)
    for k in sorted(biome_docs):
        write_json(out_root / "biome" / f"{k}.json", biome_docs[k])

    biome_map = id_map(biome_keep)
    param_lists = 0
    for name, doc in sorted(load_json_dir(vanilla_root / "multi_noise_biome_source_parameter_list").items()):
        write_json(out_root / "multi_noise_biome_source_parameter_list" / f"{name}.json",
                   retarget(doc, biome_map))
        param_lists += 1

    return {
        "configured_feature": len(cf_keep),
        "configured_feature_total": len(cf_all),
        "placed_feature": len(pf_keep),
        "placed_feature_total": len(pf_all),
        "biome": len(biome_keep),
        "biome_total": len(biome_all),
        "parameter_list": param_lists,
        "biome_map": biome_map,
    }


# Vanilla biome tags are load-bearing: structure placement, spawn gating and a pile of mob/scenery
# behaviours are keyed on them. Our biome copies carry new ids, so every vanilla tag that mentioned
# one of the originals has to mention the copy too. Datapack tags merge by default, so each generated
# file only needs to list our ids.
VANILLA_BIOME_TAG_OUT = REPO_ROOT / "src/main/resources/data/minecraft/tags/worldgen/biome"
PALETTE_BIOME_TAG_OUT = REPO_ROOT / "src/main/resources/data" / MOD_ID / "tags/worldgen/biome"


VANILLA_BLOCK_TAG_OUT = REPO_ROOT / "src/main/resources/data/minecraft/tags/block"
PALETTE_BLOCK_TAG_OUT = REPO_ROOT / "src/main/resources/data" / MOD_ID / "tags/block"


# Tags that *vanilla code* reads, not just data: SnowyDirtBlock tests `#minecraft:snow` to decide
# whether a dirt block shows its snowy texture, BushBlock/StemBlock/SugarCaneBlock test
# `#minecraft:dirt`, CactusBlock tests `#minecraft:sand`, Feature.isDirt/isStone and the dripstone
# features test dirt and base stones, and OceanRuinPieces tests `#minecraft:ice`. No datapack can
# point code at another tag, so these keep a vanilla merge beside our own tag. `leaves` and `logs` are
# deliberately absent: the palette maps neither, so there is nothing of ours to add.
CODE_READ_TAGS = (
    "snow",
    "ice",
    "dirt",
    "sand",
    "base_stone_overworld",
    "stone_ore_replaceables",
    "deepslate_ore_replaceables",
)


def read_tag_entries(path: Path) -> list[str]:
    if not path.exists():
        return []
    entries: list[str] = []
    for entry in json.loads(path.read_text(encoding="utf-8")).get("values", []):
        name = entry.get("id") if isinstance(entry, dict) else entry
        if isinstance(name, str):
            entries.append(name)
    return entries


def build_block_tags(vanilla_tag_root: Path) -> int:
    """Our own block tags, holding the palette equivalents of vanilla's entries.

    These used to be `data/minecraft/tags/block/*` merges instead, which is a global edit for a
    dimension-local need: it reached the Overworld, and because `base_stone_overworld` ended up
    containing every stone in the palette, each ore feature could replace any of the others.
    """
    written = 0
    for name in BLOCK_TAG_RETARGET:
        # nested vanilla tags are flattened, so nothing ends up pointing back at a vanilla tag
        pending = [f"#{VANILLA_NS}:{name}"]
        pending += read_tag_entries(vanilla_tag_root / f"{name}.json")
        pending += read_tag_entries(VANILLA_BLOCK_TAG_OUT / f"{name}.json")
        seen: set[str] = set()
        values: list[str] = []
        while pending:
            entry = pending.pop()
            if entry.startswith("#"):
                ref = rel_id(entry)
                if ref in seen:
                    continue
                seen.add(ref)
                pending += read_tag_entries(vanilla_tag_root / f"{ref}.json")
                continue
            values.append(PALETTE.get(entry, entry))
        mine = sorted(v for v in dict.fromkeys(values) if v.startswith(f"{MOD_ID}:"))
        if name in CODE_READ_TAGS:
            write_json(VANILLA_BLOCK_TAG_OUT / f"{name}.json", {"replace": False, "values": mine})
        write_json(PALETTE_BLOCK_TAG_OUT / f"{name}.json",
                   {"replace": False, "values": sorted(dict.fromkeys(values))})
        written += 1
    return written


def build_biome_tags(vanilla_tag_root: Path, biome_map: dict[str, str]) -> int:
    if VANILLA_BIOME_TAG_OUT.exists():
        shutil.rmtree(VANILLA_BIOME_TAG_OUT)
    written = 0
    for p in sorted(vanilla_tag_root.rglob("*.json")):
        tag = json.loads(p.read_text(encoding="utf-8"))
        ours = []
        for entry in tag.get("values", []):
            name = entry.get("id") if isinstance(entry, dict) else entry
            if not isinstance(name, str) or name.startswith("#"):
                continue
            if name in biome_map:
                ours.append(biome_map[name])
        if not ours:
            continue
        write_json(VANILLA_BIOME_TAG_OUT / p.relative_to(vanilla_tag_root),
                   {"replace": False, "values": sorted(set(ours))})
        written += 1

    # one tag naming every copied biome, so the dimension can point its biome source at the set
    write_json(PALETTE_BIOME_TAG_OUT / "palette_biomes.json",
               {"replace": False, "values": sorted(biome_map.values())})
    return written


# --------------------------------------------------------------------------------------
# Validation
# --------------------------------------------------------------------------------------


def validate(out_root: Path, vanilla: Vanilla) -> int:
    problems: list[str] = []
    leftover: dict[str, set[str]] = {}

    def check_doc(label: str, doc) -> None:
        for value in iter_reference_strings(doc):
            if value.startswith(f"{MOD_ID}:"):
                path = rel_id(value)
                if not (out_root / "density_function" / f"{path}.json").exists() and not (
                    out_root / "noise" / f"{path}.json"
                ).exists():
                    problems.append(f"{label}: unresolved reference {value}")
            elif value.startswith(f"{VANILLA_NS}:"):
                path = rel_id(value)
                is_df = path in vanilla.df
                is_noise = path in vanilla.noise
                if is_df or is_noise:
                    leftover.setdefault(path, set()).add(label)

    for p in sorted((out_root / "density_function").rglob("*.json")):
        check_doc(p.relative_to(out_root).as_posix(), json.loads(p.read_text(encoding="utf-8")))
    for p in sorted((out_root / "noise_settings").rglob("*.json")):
        check_doc(p.relative_to(out_root).as_posix(), json.loads(p.read_text(encoding="utf-8")))

    print()
    print("validation")
    print("----------")
    if problems:
        for p in problems:
            print(f"  FAIL  {p}")
    else:
        print("  ok    every dawnduskterminal: reference resolves to a generated file")

    if leftover:
        print("  note  these portable vanilla ids are still referenced on purpose:")
        for path, labels in sorted(leftover.items()):
            kind = "density_function" if path in vanilla.df else "noise"
            print(f"          {VANILLA_NS}:{path}  ({kind}) <- {', '.join(sorted(labels))}")

    # palette coverage: the settings must not still emit a block the palette owns
    settings_path = out_root / "noise_settings" / "chrono.json"
    if settings_path.exists():
        names: dict[str, int] = {}
        for value in iter_block_names(json.loads(settings_path.read_text(encoding="utf-8"))):
            names[value] = names.get(value, 0) + 1
        survived = sorted(name for name in names if name in PALETTE)
        for name in survived:
            problems.append(f"noise_settings/chrono.json still emits the palette source {name}")
        print(f"  palette: the settings emit {len(names)} distinct block states")
        for name, count in sorted(names.items(), key=lambda kv: (-kv[1], kv[0])):
            if name in PALETTE:
                mark = "PALETTE SOURCE - should be gone"
            elif name.startswith(f"{MOD_ID}:"):
                mark = "chrono palette"
            else:
                mark = "left vanilla by design"
            print(f"          {name:<38} x{count:<4} {mark}")
    return 1 if problems else 0


# --------------------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------------------


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="validate the existing output without writing")
    args = ap.parse_args()

    with tempfile.TemporaryDirectory(prefix="ddt-worldgen-port-") as tmp:
        vanilla_root = extract_vanilla_worldgen(Path(tmp))
        vanilla_tag_root = Path(tmp) / "tags" / "biome"
        vanilla_block_tag_root = Path(tmp) / "tags" / "block"
        vanilla = Vanilla(vanilla_root)
        overworld_settings = json.loads(
            (vanilla_root / "noise_settings" / "overworld.json").read_text(encoding="utf-8")
        )
        print(f"vanilla has    : {len(vanilla.df)} density functions, {len(vanilla.noise)} noise params")

        if args.check:
            return validate(OUT_ROOT, vanilla)

        df_set, noise_set = compute_closure(vanilla, overworld_settings)
        print(f"porting        : {len(df_set)} density functions, {len(noise_set)} noise params")

        # wipe everything this script owns, so nothing stale survives
        for sub in ("density_function", "noise", "noise_settings", "configured_feature",
                    "placed_feature", "biome", "configured_carver",
                    "multi_noise_biome_source_parameter_list"):
            if (OUT_ROOT / sub).exists():
                shutil.rmtree(OUT_ROOT / sub)

        written = 0
        for path in sorted(df_set):
            doc = rewrite(vanilla.read("df", path), df_set, noise_set)
            target = OUT_ROOT / "density_function" / f"{path}.json"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
            written += 1
        for path in sorted(noise_set):
            doc = vanilla.read("noise", path)
            target = OUT_ROOT / "noise" / f"{path}.json"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
            written += 1

        settings = rewrite(overworld_settings, df_set, noise_set)
        settings = apply_palette(settings)
        # the surface rule names biomes directly, so it has to name our copies
        settings = retarget_biome_ids(settings, overworld_biome_names(vanilla_tag_root))
        target = OUT_ROOT / "noise_settings" / "chrono.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(settings, indent=2) + "\n", encoding="utf-8")
        written += 1

        stats = build_palette_layer(vanilla_root, vanilla_tag_root, OUT_ROOT)
        tags = build_biome_tags(vanilla_tag_root, stats["biome_map"])
        block_tags = build_block_tags(vanilla_block_tag_root)
        written += (stats["configured_feature"] + stats["placed_feature"]
                    + stats["biome"] + stats["parameter_list"] + block_tags)
        print("biome layer    : "
              f"configured_feature {stats['configured_feature']}/{stats['configured_feature_total']}, "
              f"placed_feature {stats['placed_feature']}/{stats['placed_feature_total']}, "
              f"biome {stats['biome']}/{stats['biome_total']}, "
              f"parameter_list {stats['parameter_list']}, biome tags {tags}, block tags {block_tags}")

    print(f"wrote          : {written} files under {OUT_ROOT.relative_to(REPO_ROOT)}")
    return validate(OUT_ROOT, vanilla)


if __name__ == "__main__":
    raise SystemExit(main())
