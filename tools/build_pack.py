"""The Structurize pack shipped inside the Colonist Thieves jar, and the helpers every
generator here shares.

    resources/blueprints/colonist_thieves/colonist_thieves/pack.json
    resources/blueprints/colonist_thieves/colonist_thieves/colonist_thieves.png   (pack icon)
    resources/blueprints/colonist_thieves/colonist_thieves/hideout/hideout1-5.blueprint

This is the Voyager tools' build_pack.py with the Voyager buildings taken out: what is left is
pack.json plus the four things every build/check script needs - `aligned` (line a level up on its
hut block), the substitution blocks, and the "does this block id actually exist" lookup. The
blueprints themselves are written by build_hideout.py.
"""
import json
import os
import re
import sys
import zipfile

import paths
import voxel

PACK_NAME = "Colonist Thieves"
PACK_DIR = paths.res("blueprints", "colonist_thieves", "colonist_thieves")
PACK_ICON = "colonist_thieves.png"

GROUND = "structurize:blocksolidsubstitution"     # "a solid block of this terrain" (fills, costs materials)
KEEP = "structurize:blocksubstitution"            # "leave whatever is there" (no work, no materials)


def aligned(fn, level):
    """Level `level` of a building, moved so its anchor coincides with level 5's anchor.

    The hut block does not move when a colony upgrades, so every level has to be drawn around the
    same anchor. The Hideout is drawn that way from the start (one plan, five levels of it), so
    this is a no-op here - it is kept because it is what makes the shared box mean anything, and
    the first level that wanders will be caught by it rather than by a player.
    """
    s = fn(level)
    a5 = fn(5).anchor
    dx, dy, dz = a5[0] - s.anchor[0], 0, a5[2] - s.anchor[2]
    return s.translated(dx, dy, dz) if (dx or dz) else s


# ---------------------------------------------------------------- block ids that exist

MC_ASSETS = paths.lib(os.environ.get("MC_ASSETS_JAR", "mc-extra.jar"))   # client assets jar (blockstates)

# Mod blocks with no blockstate file to look up: MineColonies' rack is registered in code and its
# model is built at runtime, and our own hut block lives in this repository, not in libs/.
KNOWN_MOD_BLOCKS = {
    "minecolonies:blockminecoloniesrack",
    "colonist_thieves:blockhuthideout",
}


def _blockstate_ids(jar_path, namespace):
    """Every block id in `namespace` that has a blockstate file in this jar."""
    ids = set()
    with zipfile.ZipFile(jar_path) as jar:
        for name in jar.namelist():
            m = re.match(rf"assets/{namespace}/blockstates/([a-z0-9_]+)\.json$", name)
            if m:
                ids.add(f"{namespace}:{m.group(1)}")
    return ids


def vanilla_blocks():
    """Every vanilla block id that has a blockstate file in the client assets - a block that is
    not in here (polished_andesite_wall, say) silently becomes air when Structurize loads the
    blueprint, with nothing in the log to say so."""
    return _blockstate_ids(MC_ASSETS, "minecraft")


def known_blocks():
    """Vanilla plus the three mods this pack is allowed to use, read out of the jars in libs/
    rather than typed by hand, so a renamed block is caught the next time the jars are updated."""
    ids = vanilla_blocks()
    ids |= _blockstate_ids(paths.lib("structurize-*.jar"), "structurize")
    ids |= _blockstate_ids(paths.lib("minecolonies-*.jar"), "minecolonies")
    ids |= _blockstate_ids(paths.lib("domum-ornamentum-*.jar"), "domum_ornamentum")
    return ids | KNOWN_MOD_BLOCKS


def unknown_blocks(s, known):
    return sorted({voxel.parse_state(b)[0] for b in s.blocks.values() if b is not None
                   and voxel.parse_state(b)[0] not in known})


# ---------------------------------------------------------------- pack.json

DESC = ("The Hideout: an ordinary shabby town building with a cellar under it, and the cellar is "
        "where the colony's thieves sleep, keep the board of marks and come back to after dark. "
        "One building, five levels, dug deeper each time. Works with every colony style.")


def write_pack_json():
    os.makedirs(PACK_DIR, exist_ok=True)
    path = os.path.join(PACK_DIR, "pack.json")
    with open(path, "w") as f:
        json.dump({
            "icon": PACK_ICON,
            "name": PACK_NAME,
            "authors": ["Lovkar", "Claude"],
            "desc": DESC,
            "mods": ["structurize", "minecolonies", "colonist_thieves"],
            "version": "1",
            "pack-format": "1",
        }, f, indent=2)
    return path


def main():
    print("wrote", write_pack_json())
    print("now run build_hideout.py for the blueprints themselves")
    return 0


if __name__ == "__main__":
    sys.exit(main())
