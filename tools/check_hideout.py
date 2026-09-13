"""Validate the Hideout designs before anything is written.

The Voyager tools' check_observatory.py, with the two questions an underground building adds.
For every level:

  1  does every block id exist in this Minecraft, and in one of the three mods this pack may use
  2  does anything float, or hang off nothing (a ladder with no wall behind it, a lantern on air)
  3  does every Domum Ornamentum block carry its material
  4  does the level fit the shared box, and do all five levels share one anchor
  5  is there somewhere to stand next to the hut block, can a citizen reach every bunk from it
     at all (hatch and ladder count), and - from level 3, where the stair goes in - can he WALK
     to every one of them
  6  is every cell that must be hollow actually hollow, and is every other cell under the ground
     accounted for, so the builder does not dig out the whole lot

A block id that does not exist becomes AIR in Structurize without a word of warning, and a Domum
Ornamentum block with no material comes out wearing the wrong stone, so this runs first.
"""
import sys

import build_hideout as bh
import build_pack as bp
import floatcheck
import hideout
import voxel
from walkcheck import climb_reachable, reachable, standable

(BX0, BY0, BZ0), (BX1, BY1, BZ1) = hideout.BOX

known = bp.known_blocks()
ok = True
anchors = set()

for level in range(1, 6):
    s = bp.aligned(hideout.build, level)
    anchors.add(s.anchor)

    missing = bp.unknown_blocks(s, known)
    if missing:
        print(f"!! {s.name}: blocks that do not exist in vanilla, MineColonies or Domum "
              f"Ornamentum:", missing)
        ok = False

    loose = floatcheck.floating(s)
    if loose:
        comps = floatcheck.components(s, loose)
        print(f"!! {s.name}: {len(loose)} floating block(s) in {len(comps)} group(s), e.g.", loose[:4])
        ok = False

    # A block can be connected and still pop off on placement: a ladder with no wall behind it,
    # a lantern hung from air, a lectern on nothing.
    unattached = floatcheck.attachment(s)
    if unattached:
        print(f"!! {s.name}: {len(unattached)} block(s) with nothing to attach to, e.g.", unattached[:4])
        ok = False

    bare = sorted(pos for pos, b in s.blocks.items()
                  if voxel.parse_state(b)[0] in voxel.DO_SLOTS and pos not in s.materials)
    if bare:
        print(f"!! {s.name}: {len(bare)} Domum Ornamentum block(s) with no material, e.g.", bare[:4])
        ok = False

    (x0, y0, z0), (x1, y1, z1) = s.bounds()
    fits = BX0 <= x0 and x1 <= BX1 and BY0 <= y0 and y1 <= BY1 and BZ0 <= z0 and z1 <= BZ1
    if not fits:
        print(f"!! {s.name}: x[{x0},{x1}] y[{y0},{y1}] z[{z0},{z1}] is outside the box {hideout.BOX}")
        ok = False

    dug = getattr(s, "carved", set())
    stray = sorted(q for q in dug if not (BX0 <= q[0] <= BX1 and BY0 <= q[1] <= BY1
                                          and BZ0 <= q[2] <= BZ1))
    if stray:
        print(f"!! {s.name}: {len(stray)} dug cell(s) outside the box, e.g.", stray[:4])
        ok = False
    design_size = s.size()

    # and the walking, on the structure as the builder will leave it
    bh.pad_ground(s)
    # nothing the design dug out may come back as earth: a cell that does is a room with its own
    # floor filled in, and the only sign of it in game is a builder that never finishes
    refilled = sorted(q for q in dug if s.blocks.get(q) == bp.KEEP)
    if refilled:
        print(f"!! {s.name}: {len(refilled)} dug cell(s) filled back in by the padding, e.g.",
              refilled[:4])
        ok = False
    if s.size() != (BX1 - BX0 + 1, BY1 - BY0 + 1, BZ1 - BZ0 + 1):
        print(f"!! {s.name}: padded to {s.size()}, not the shared box")
        ok = False
    ax, ay, az = s.anchor
    stands = [(ax + dx, ay, az + dz) for dx, dz in ((0, 1), (0, -1), (1, 0), (-1, 0))
              if standable(s, ax + dx, ay, az + dz)]
    if not stands:
        print(f"!! {s.name}: nowhere to stand next to the hut block {s.anchor}")
        ok = False
    beds = sorted(q for q, tags in s.tags.items() if "bed" in tags)
    walked = sum(1 for b in beds if stands and reachable(s, stands[0], b)[0]) if beds else 0
    # Every bunk has to be reachable at every level - by the stair from level 3, and before that
    # down the hatch and the ladder, which is MineColonies' own way into a cellar.
    climbed = sum(1 for b in beds if stands and climb_reachable(s, stands[0], b)[0]) if beds else 0
    if climbed < len(beds):
        print(f"!! {s.name}: only {climbed} of {len(beds)} bunks can be reached at all from {stands[:1]}")
        ok = False
    if level >= 3 and walked < len(beds):
        print(f"!! {s.name}: only {walked} of {len(beds)} bunks can be WALKED to from {stands[:1]}")
        ok = False
    if len(beds) != hideout.CREW[level]:
        print(f"!! {s.name}: {len(beds)} bed(s) for a crew of {hideout.CREW[level]}")
        ok = False

    sx, sy, sz = design_size
    print(f"{s.name:10s} built {sx:2d}x{sy:2d}x{sz:2d}  {len(s.blocks):5d} blocks  {len(dug):4d} dug  "
          f"{len(s.materials):3d} DO  {len(s.entities):2d} marks  {len(beds)} bunks "
          f"({walked} walkable, {climbed} reachable)  anchor {s.anchor}  x[{x0},{x1}] y[{y0},{y1}] z[{z0},{z1}]  "
          f"{'fits' if fits else 'OUTSIDE THE BOX'}")

if len(anchors) != 1:
    print("!! the five levels do not share one anchor:", sorted(anchors))
    ok = False

print("OK" if ok else "PROBLEMS ABOVE")
sys.exit(0 if ok else 1)
