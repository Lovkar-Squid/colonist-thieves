"""Generate the Hideout blueprints into the Colonist Thieves Structurize pack.

    resources/blueprints/colonist_thieves/colonist_thieves/hideout/hideout1-5.blueprint

Same shape as the Voyager tools' build_observatory.py: check the design, fill the untouched
ground, write the file, read it back and compare. Run check_hideout.py first - this writes the
files, it does not judge them, and a blueprint that is wrong is wrong in a world, quietly.

The hut block is the anchor: colonist_thieves:blockhuthideout, block entity
colonist_thieves:colonybuilding, building type colonist_thieves:hideout - our own block-entity
type, because MineColonies' own only accepts MineColonies hut blocks (see Thieves.java).
"""
import os
import sys

import build_pack as bp
import hideout
import paths
import voxel
from floatcheck import attachment, floating
from walkcheck import reachable, standable

PACK_NAME = bp.PACK_NAME
PACK_DIR = bp.PACK_DIR
FOLDER = "hideout"                                  # BuildingHideout.getSchematicName()
BUILDING_TYPE = "colonist_thieves:hideout"          # Thieves.BUILDING_ID
BE_TYPE = "colonist_thieves:colonybuilding"         # Thieves.BUILDING_BE
BOX = hideout.BOX
REQUIRED_MODS = ("structurize", "minecolonies", "domum_ornamentum", "colonist_thieves")


def pad_ground(s):
    """Fill in the earth the design does not use.

    Everything from the ground course down that is neither a block of the design nor a cell it
    dug out on purpose becomes structurize:blocksubstitution - "leave whatever is there". Without
    this the builder reads the blueprint's empty cells as air and digs the whole 13 x 13 lot out
    to the cellar floor before it lays a single plank.

    Above the ground course nothing is added, so those cells stay air and the builder clears the
    trees and the hillside off the plot - the same rule Voyager's pad_ground uses, only that one
    never had to think about what is underneath.
    """
    (bx0, by0, bz0), (bx1, _by1, bz1) = BOX
    dug = getattr(s, "carved", set())
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            for y in range(by0, hideout.GROUND_Y + 1):
                if (x, y, z) not in s.blocks and (x, y, z) not in dug:
                    s.set(x, y, z, bp.KEEP)


def walkable_hut(s):
    """A citizen has to be able to stand next to the hut block: that is where MineColonies puts
    the thief when it wants him at his building."""
    x, y, z = s.anchor
    stands = [(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
              if standable(s, x + dx, y, z + dz)]
    if not stands:
        return False, f"nowhere to stand next to the hut block {s.anchor}"
    return True, f"stand {stands[0]}"


def walkable_beds(s, start):
    """How many of the bunks a citizen can WALK to from the hut block, over the padded ground.

    Levels 1 and 2 reach the cellar by ladder alone, and a ladder is not something this check can
    climb - so this is reported, and only required from level 3, where the stair goes in.
    """
    beds = sorted(p for p, tags in s.tags.items() if "bed" in tags)
    if start is None:
        return 0, beds
    return sum(1 for b in beds if reachable(s, start, b)[0]), beds


def one(level, known):
    s = bp.aligned(hideout.build, level)
    ok = True

    # --- the design itself, before any padding: padding would make everything look connected
    missing = bp.unknown_blocks(s, known)
    if missing:
        print("!! blocks that do not exist:", s.name, missing)
        ok = False
    loose = floating(s)
    if loose:
        print("!! floating:", s.name, loose[:4])
        ok = False
    unattached = attachment(s)
    if unattached:
        print("!! nothing to attach to:", s.name, unattached[:3])
        ok = False
    bare = sorted(pos for pos, b in s.blocks.items()
                  if voxel.parse_state(b)[0] in voxel.DO_SLOTS and pos not in s.materials)
    if bare:
        print("!! Domum Ornamentum blocks with no material:", s.name, bare[:4])
        ok = False
    mats, dug = len(s.materials), len(getattr(s, "carved", ()))

    # --- then the ground around it, and the walking
    pad_ground(s)
    walk_ok, why = walkable_hut(s)
    if not walk_ok:
        print("!! walk:", s.name, why)
        ok = False
    start = None
    x, y, z = s.anchor
    for dx, dz in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        if standable(s, x + dx, y, z + dz):
            start = (x + dx, y, z + dz)
            break
    got, beds = walkable_beds(s, start)
    if level >= 3 and got < len(beds):
        print(f"!! only {got} of {len(beds)} bunks can be walked to on {s.name}")
        ok = False

    file_name = f"{s.name}.blueprint"
    path = os.path.join(PACK_DIR, FOLDER, file_name)
    s.to_blueprint(path, file_name, PACK_NAME, f"{FOLDER}/{file_name}", BUILDING_TYPE,
                   required_mods=REQUIRED_MODS, be_type=BE_TYPE, box=BOX)

    # read it back: the palette packing is the one place a silent mistake would survive everything
    back = voxel.load_blueprint(path)
    (x0, y0, z0), _ = BOX
    norm = {(px - x0, py - y0, pz - z0): voxel.parse_state(b) for (px, py, pz), b in s.blocks.items()}
    same = norm == {q: voxel.parse_state(b) for q, b in back.blocks.items()}
    anchor_ok = back.anchor == (s.anchor[0] - x0, s.anchor[1] - y0, s.anchor[2] - z0)
    print(f"{file_name:20s} anchor {s.anchor!s:13s} {len(s.blocks):5d} blocks {dug:4d} dug "
          f"{mats:3d} DO {len(s.entities):2d} marks {len(beds)} beds({got} walkable) "
          f"{os.path.getsize(path):6d} B roundtrip={'ok' if same else 'MISMATCH'} "
          f"anchor={'ok' if anchor_ok else 'BAD'}  {why}")
    return ok and same and anchor_ok


def main():
    os.makedirs(os.path.join(PACK_DIR, FOLDER), exist_ok=True)
    print("wrote", bp.write_pack_json())
    known = bp.known_blocks()
    ok = True
    for level in range(1, 6):
        ok = one(level, known) and ok
    print("ALL OK" if ok else "PROBLEMS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
