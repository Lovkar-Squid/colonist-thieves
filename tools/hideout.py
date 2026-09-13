"""The Hideout - the Colonist Thieves building, levels 1 to 5, as voxel structures.

Same conventions as the Voyager designs (tools/designs.py there): x east, z south, front is SOUTH
(+z), and the hut block is the building's anchor. What is different is that this building mostly
goes DOWN.

  y =  0   the ground course: the top of the terrain, and the floor of the shed. A citizen
           upstairs stands at y = 1.
  y = -1   the cellar's ceiling, y = -5 its floor. A citizen downstairs stands at y = -4, and the
           rooms are three cells high so a lantern can hang without standing in anyone's face.
  y =  4   the shed's ceiling, which is also the floor of the loft; the ridge is at y = 7.

THE FICTION. From the street it is an ordinary shabby back building - a cobble footing, patched
plank walls, a board roof, a worn path to the door, a lean-to with barrels under it. The Hideout
is what is under it: a room dug out below the floor with the bunks and the board of marks on the
wall, a second room behind that, a stair down from the yard that the town is allowed to see, a
bolt-hole that comes up behind the woodpile, and at the top level a vault and a lookout in the
loft.

THE LOT, 13 x 13 (the shared box below), looking down on the ground course:

      x  -6 -5 -4 -3 -2 -1  0  1  2  3  4  5  6
  z=-5      +-----------------+  .  .  .  .        the shed, x[-5..1] z[-5..0]
  z=-4      | rack HUT ldr  n |  . awning .        the lean-to, x[2..4] z[-5..-2] (level 2)
  z=-3      |                 |  .  .  .  .
  z=-2      |          T      |  .  .  .  .        T the trapdoor down (level 1)
  z=-1      | bench      rack |
  z= 0      +--------D--------+                    D the door, in the south wall
  z= 1               ,     [kerb]                  the sunken cellar stair (level 3) runs
  z= 2               ,     [   ]                   down the x=1 column, from z=4 to a
  z= 3            ,  ,     [ v ]                   landing at z=0
  z= 4               ,  =  [ v ]                   = the woodpile, and under it
  z= 5               ,  =  L                       L the bolt-hole lid (level 4)

And the same lot five blocks down, where the building actually is:

      x  -6 -5 -4 -3 -2 -1  0  1  2  3  4  5  6
  z=-5      | bed  bed  bed P |  | chest chest |    ROOM A x[-5..0] z[-6..-1] (level 1)
  z=-4      | bed  bed  bed l |  |        rack |    the bunks stand side by side, heads to
  z=-3      |                 B  |             |    the north wall, feet all in one row
  z=-2      | rack   n    ldr B  |        rack |    B the board of marks
  z=-1      +---------  ---+--+--+--- door ----+    the VAULT x[0..4] z[-6..-1] (level 5)
  z= 0      | rack         | door  | landing        ROOM B x[-5..0] z[-1..3] (level 2)
  z= 1      | bars    rack |       | step
  z= 2      | box  n  k  g |       | step
  z= 3      +------  ------+       | step           the bolt-hole (level 4) leaves here,
  z= 4            |                | step           runs south and east under the yard
  z= 5            +-- tunnel --+ ladder up          and comes up at the woodpile

DIGGING. An underground building needs one thing an above-ground one never does. Structurize
reads every cell of the blueprint that is not a block as "make this air", so a blueprint whose box
reaches five blocks below the hut block tells the builder to dig the whole lot out down to the
cellar floor. So every cell at or below the ground course that the design does not use is filled
with structurize:blocksubstitution ("leave whatever is there") by build_hideout.pad_ground, and
the cells that really must be hollow - the rooms, the shaft, the tunnel, the stairwell - are
marked with carve() as the design draws them. Everything ABOVE the ground course is left as air on
purpose: that is the builder clearing trees and hillside off the plot, the way Voyager does it.
"""
from voxel import Structure

HUT = "colonist_thieves:blockhuthideout[facing=north]"   # against the north wall, like the Observatory's desk
RACK = "minecolonies:blockminecoloniesrack"

# The shared box: every level, aligned on its hut block, fits inside this and the build tool's
# outline never changes between upgrades. Modest on the ground (13 x 13, a back building, not a
# keep) and deep rather than tall.
BOX = ((-6, -5, -6), (6, 7, 6))

GROUND_Y = 0          # the ground course (terrain top / the shed's floor)
CELLAR_FLOOR = -5     # the cellar's floor course; citizens stand on y = -4
CELLAR_CEIL = -1      # the cellar's ceiling course
CELLAR_AIR = (-4, -3, -2)

SHED = (-5, -5, 1, 0)     # x0, z0, x1, z1 - outer walls inclusive
WALL_TOP = 3              # the shed's walls are y = 1..3
LOFT_Y = 4                # its ceiling, and the loft floor
RIDGE_X = -2              # the roof ridge runs north-south down the middle of the shed

ROOM_A = (-5, -6, 0, -1)  # the back room: bunks and the board. Reaches one row further north
ROOM_B = (-5, -1, 0, 3)   # than the shed, under the grass behind it
VAULT = (0, -6, 4, -1)    # the vault, level 5, east of the back room

HUT_POS = (-3, 1, -4)     # inside the shed, against the north wall
HATCH = (-1, -2)          # the trapdoor in the shed floor and the ladder under it
LOFT_LADDER_X = RIDGE_X   # the loft is only standable under the ridge, so the ladder goes there
STAIR_X = 1               # the sunken stair runs down this column, from z=4 to the landing at z=0
BOLT_EXIT = (0, 5)        # where the bolt-hole comes up, behind the woodpile

# The bunks stand side by side with their heads against the far wall and their feet all in one
# row, so the row in front of them stays clear and the third bunk never walls the first one in -
# a bed a citizen cannot walk to is a bed MineColonies will not use.
BUNKS = (-4, -3, -2)                     # the x of each bunk; head at z=-5, foot at z=-4
CREW = {1: 1, 2: 1, 3: 2, 4: 2, 5: 3}    # BuildingHideout.crewSize, and one bed each


# ---------------------------------------------------------------- block state helpers
# The same handful designs.py keeps in the Voyager tools; copied rather than imported because
# designs.py is the Voyager buildings and has no business in this repository.

def stairs(block_id, facing, half="bottom"):
    return f"{block_id}[facing={facing},half={half},shape=straight,waterlogged=false]"


def slab(block_id, kind="bottom"):
    return f"{block_id}[type={kind},waterlogged=false]"


def door(block_id, facing, half, hinge="left"):
    return f"{block_id}[facing={facing},half={half},hinge={hinge},open=false,powered=false]"


def trapdoor(block_id, facing="north", half="top", open_=False):
    return (f"{block_id}[facing={facing},half={half},open={'true' if open_ else 'false'},"
            f"powered=false,waterlogged=false]")


def ladder(facing):
    """A ladder is held by the block BEHIND it: facing=north hangs on the wall to its south."""
    return f"minecraft:ladder[facing={facing},waterlogged=false]"


def lantern(kind="minecraft:lantern", hanging=False):
    return f"{kind}[hanging={'true' if hanging else 'false'},waterlogged=false]"


def connected(block_id, *dirs):
    """Fences, panes and bars: a post with the sides that join something set true."""
    props = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
    for d in dirs:
        props[d] = "true"
    return block_id + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


def bed(block, facing, part):
    """One half of a bed. The head sits at foot + facing, and both halves must agree or Minecraft
    drops them."""
    return f"{block}[facing={facing},occupied=false,part={part}]"


def chest(facing="south"):
    return f"minecraft:chest[facing={facing},type=single,waterlogged=false]"


# ---------------------------------------------------------------- the palette
# Vanilla, MineColonies and Domum Ornamentum only. The Domum Ornamentum shapes are the three
# Voyager already proves in game (a shingle roof, the vanilla slab shape, the decorative barrel);
# their materials are the ones MineColonies' own blueprints use, because a material outside Domum
# Ornamentum's tag for that slot renders as missing and nothing warns you.
P = {
    # above ground
    "sill":     "minecraft:cobblestone",              # the stone footing the shed stands on
    "sill2":    "minecraft:mossy_cobblestone",
    "wall":     "minecraft:spruce_planks",
    "patch":    "minecraft:oak_planks",               # patched with whatever was to hand
    "post":     "minecraft:spruce_log[axis=y]",       # corner posts
    "floor":    "minecraft:spruce_planks",
    "bare":     "minecraft:coarse_dirt",              # level 1: half the floor is still dirt
    "roof_old": "minecraft:spruce_stairs",            # levels 1-2: a roof of old boards
    "eave_old": "minecraft:spruce_slab",
    "roof":     "domum_ornamentum:shingle",           # level 3 and up: slate, laid properly
    "eave":     "domum_ornamentum:vanilla_slab_compat",
    "m_roof":   "minecraft:deepslate_tiles",          # the shingle's tiles
    "m_sup":    "minecraft:spruce_planks",            # and the boards under them
    "door":     "minecraft:spruce_door",
    "trap":     "minecraft:spruce_trapdoor",
    "glass":    "minecraft:glass_pane",
    "fence":    "minecraft:spruce_fence",
    "path":     "minecraft:dirt_path",
    "yard":     "minecraft:gravel",
    "logs":     "minecraft:stripped_spruce_log[axis=y]",
    "lamp":     "minecraft:lantern",
    "barrel":   "domum_ornamentum:blockbarreldeco_standing[facing=north]",
    "bench":    "minecraft:crafting_table",
    # the cellar, as first dug
    "rough":    "minecraft:cobblestone",
    "rough2":   "minecraft:mossy_cobblestone",
    "grit":     "minecraft:gravel",
    "boards":   "minecraft:oak_planks",               # the boards holding the roof of the dig up
    # the cellar, once it is finished (level 2)
    "brick":    "minecraft:stone_bricks",
    "brick2":   "minecraft:cracked_stone_bricks",
    "brick3":   "minecraft:chiseled_stone_bricks",
    "slabfl":   "minecraft:polished_andesite",
    "ceil":     "minecraft:spruce_planks",
    "beam":     "minecraft:spruce_log[axis=x]",
    # the fittings that make it a hideout
    "board":    "minecraft:dark_oak_planks",          # the board the marks are pinned to
    "bed":      "minecraft:brown_bed",
    "bars":     "minecraft:iron_bars",
    "plan":     "minecraft:cartography_table",
    "ledger":   "minecraft:lectern[facing=south,has_book=true,powered=false]",
    "picks":    "minecraft:smithing_table",           # where lockpicks get filed
    "stone":    "minecraft:grindstone[face=floor,facing=north]",
    "cellar_st": "minecraft:stone_brick_stairs",
}


def speck(x, y, z, n=5):
    """A repeatable scatter, so the cobble is mossy and the planks are patched in the same places
    every time this runs - a render Lovkar approved has to come back identical."""
    return (x * 7 + y * 13 + z * 11) % n == 0


# ---------------------------------------------------------------- digging

def blank(level):
    s = Structure(f"hideout{level}")
    s.carved = set()        # cells the builder must hollow out; see the module docstring
    return s


def carve(s, x, y, z):
    s.set(x, y, z, None)
    s.carved.add((x, y, z))


def carve_column(s, x, z, y0, y1):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        carve(s, x, y, z)


def dig_room(s, rect, walls, floor, ceiling):
    """A cellar room: a floor under it, a lining round it, a ceiling over it, and the earth in the
    middle taken out. Outer walls inclusive, so the interior is one block in from every side."""
    x0, z0, x1, z1 = rect
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, CELLAR_FLOOR, z, floor(x, z))
            s.set(x, CELLAR_CEIL, z, ceiling(x, z))
            edge = x in (x0, x1) or z in (z0, z1)
            for y in CELLAR_AIR:
                if edge:
                    s.set(x, y, z, walls(x, y, z))
                else:
                    carve(s, x, y, z)


def interior(rect):
    x0, z0, x1, z1 = rect
    return x0 + 1, z0 + 1, x1 - 1, z1 - 1


def archway(s, x, z, hang=None, facing="south"):
    """A door-sized hole through a cellar wall, with or without a door in it."""
    carve(s, x, -4, z)
    carve(s, x, -3, z)
    if hang:
        s.set(x, -4, z, door(hang, facing, "lower"))
        s.set(x, -3, z, door(hang, facing, "upper"))


# ---------------------------------------------------------------- the marks on the board

def pinned(s, x, y, z, facing="south"):
    """One mark pinned to the board: a vanilla item frame with a scrap of paper in it.

    An entity, not a block - the same mechanism Voyager hangs its photographs with. It carries the
    block it hangs in as TileX/Y/Z as well as its position, because Minecraft refuses a hanging
    entity whose two disagree, and the builder asks the colony for the frame and the paper the way
    it does for MineColonies' own item frames.
    """
    import nbtlib.tag as T
    facing3d = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}
    yaw = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}[facing]
    s.entity(x, y, z, "minecraft:item_frame", yaw=yaw, extra={
        "Facing": T.Byte(facing3d[facing]),
        "Item": T.Compound({"id": T.String("minecraft:paper"), "count": T.Int(1)}),
        "ItemRotation": T.Byte((x * 3 + y) % 8),
        "ItemDropChance": T.Float(1.0),
        "Fixed": T.Byte(0),
        "Invisible": T.Byte(0),
        "__tile__": True,
    })


# ================================================================ above ground

def roof_y(x):
    """The roof: a plain gable with its ridge down the x = -2 column, and one block of eave past
    each wall. 45 degrees, so every column is one course lower than the one nearer the ridge."""
    return max(LOFT_Y, LOFT_Y + 3 - abs(x - RIDGE_X))


def shed(s, level):
    """The building the town sees: a cobble footing, plank walls patched with whatever was to
    hand, a board roof, a door onto the path, and the hut block against the back wall.

    The hut block is the Hideout's anchor and the only thing in here that says what this is. It
    stands on the north wall with a free square in front of it, which is where MineColonies puts
    the thief when it wants him at his building.
    """
    x0, z0, x1, z1 = SHED
    p = P
    tidy = level >= 3

    # the ground course: the floor, on a cobble footing under the walls
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                s.set(x, GROUND_Y, z, p["sill2"] if speck(x, 0, z, 4) else p["sill"])
            elif level == 1 and speck(x, 1, z, 3):
                s.set(x, GROUND_Y, z, p["bare"])      # level 1: half of it is still bare earth
            else:
                s.set(x, GROUND_Y, z, p["floor"])

    # the walls: a course of cobble, then planks, with a log at each corner
    for y in range(1, WALL_TOP + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    continue
                if x in (x0, x1) and z in (z0, z1):
                    s.set(x, y, z, p["post"])
                elif y == 1:
                    s.set(x, y, z, p["sill2"] if speck(x, y, z, 6) else p["sill"])
                elif not tidy and speck(x, y, z, 7):
                    s.set(x, y, z, p["patch"])
                else:
                    s.set(x, y, z, p["wall"])

    # the door, in the middle of the south wall, and the windows
    cx = (x0 + x1) // 2
    s.set(cx, 1, z1, door(p["door"], "south", "lower"))
    s.set(cx, 2, z1, door(p["door"], "south", "upper"))
    for wx, wz in ((x0, -3), (x1, -3), (0, z1)):
        if level >= 2:
            s.set(wx, 2, wz, connected(p["glass"], *(("north", "south") if wx in (x0, x1) else ("east", "west"))))
        else:
            # level 1 they are shuttered: a trapdoor held open stands upright in the opening,
            # which is what a boarded window is
            out = "west" if wx == x0 else ("east" if wx == x1 else "south")
            s.set(wx, 2, wz, trapdoor(p["trap"], facing=out, half="bottom", open_=True))

    # the ceiling, which is the floor of the loft
    ix0, iz0, ix1, iz1 = interior(SHED)
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            s.set(x, LOFT_Y, z, p["ceil"])
    for z in (iz0, iz1):
        s.set(RIDGE_X, LOFT_Y, z, p["beam"])          # two beams across it, because it is a shed

    # the gable ends fill the triangle between the wall top and the roof
    for z in (z0, z1):
        for x in range(x0, x1 + 1):
            for y in range(WALL_TOP + 1, roof_y(x)):
                s.set(x, y, z, p["wall"])

    roof(s, level)
    fittings(s, level)


def roof(s, level):
    """Old boards at first, slate shingle laid on them from level 3 - the one thing a passer-by
    would notice getting better."""
    x0, z0, x1, z1 = SHED
    p = P
    for x in range(x0 - 1, x1 + 2):
        y = roof_y(x)
        for z in range(z0 - 1, z1 + 2):
            if x == RIDGE_X:
                if level >= 3:
                    s.mixed(x, y, z, slab(p["eave"]), p["m_roof"])
                else:
                    s.set(x, y, z, slab(p["eave_old"]))
            elif x in (x0 - 1, x1 + 1):
                if level >= 3:
                    s.mixed(x, y, z, slab(p["eave"]), p["m_sup"])
                else:
                    s.set(x, y, z, slab(p["eave_old"]))
            else:
                facing = "east" if x < RIDGE_X else "west"   # stairs face uphill, toward the ridge
                if level >= 3:
                    s.mixed(x, y, z, stairs(p["roof"], facing), p["m_roof"], p["m_sup"])
                else:
                    s.set(x, y, z, stairs(p["roof_old"], facing))


def fittings(s, level):
    """What is in the shed: the hut block, a rack or two, a barrel, a bench. Nothing that says
    thief - that is all downstairs."""
    p = P
    ix0, iz0, ix1, iz1 = interior(SHED)
    s.set_anchor(*HUT_POS, HUT)
    s.set(ix0, 1, iz0, RACK)                       # the north-west corner
    s.set(ix1, 1, iz0, p["barrel"])
    if level >= 2:
        s.set(ix1, 1, iz1, RACK)
    if level >= 3:
        s.set(ix0, 1, iz1, p["bench"])
    s.set(RIDGE_X, WALL_TOP, (iz0 + iz1) // 2, lantern(p["lamp"], hanging=True))


def loft(s):
    """Level 5: the lookout. The loft is only a catwalk - the roof comes down on either side of
    the ridge - but under the ridge there is room to stand, and the gable end gets a window over
    the street instead of the boards that were there.
    """
    p = P
    x0, z0, x1, z1 = SHED
    ix0, iz0, ix1, iz1 = interior(SHED)
    s.set(LOFT_LADDER_X, LOFT_Y, iz0, None)                  # the way up through the ceiling
    for y in range(1, LOFT_Y + 1):
        s.set(LOFT_LADDER_X, y, iz0, ladder("south"))        # held by the north wall behind it
    for y in (5, 6):
        s.set(RIDGE_X, y, z1, connected(p["glass"], "east", "west"))
    s.set(RIDGE_X, 6, iz1, lantern(p["lamp"], hanging=True))  # hung off the ridge, seen from the street
    s.set(RIDGE_X + 1, 5, iz1 - 1, p["barrel"])


def awning(s, level):
    """The lean-to against the east wall: a gravel apron, two posts and a roof of slabs. Level 2,
    when the colony has anything worth keeping dry."""
    p = P
    for x in range(2, 5):
        for z in range(-5, -1):
            s.set(x, GROUND_Y, z, p["yard"])
            if level >= 3:
                s.mixed(x, WALL_TOP, z, slab(p["eave"]), p["m_sup"])
            else:
                s.set(x, WALL_TOP, z, slab(p["eave_old"]))
    for z in (-5, -2):
        for y in (1, 2):
            s.set(4, y, z, connected(p["fence"]))
    s.set(4, 1, -4, p["barrel"])
    s.set(2, 1, -5, RACK)
    for y in (1, 2):
        s.set(3, y, -2, p["logs"])                  # a little wood, stacked


def yard(s, level):
    """The path to the door, worn into the ground. The rest of the lot is left alone."""
    p = P
    for z in range(1, 6):
        s.set(RIDGE_X, GROUND_Y, z, p["path"])
    s.set(RIDGE_X - 1, GROUND_Y, 3, p["path"])
    s.set(RIDGE_X + 1, GROUND_Y, 1, p["path"])
    if level >= 2:
        awning(s, level)


# ================================================================ below ground

def rough_walls(x, y, z):
    return P["rough2"] if speck(x, y, z, 4) else P["rough"]


def brick_walls(x, y, z):
    return P["brick2"] if speck(x, y, z, 6) else P["brick"]


def back_room(s, level):
    """The back room, dug out under the shed floor: the bunks, and the board with the marks on it.

    Level 1 it is a hole with cobble shoring and a gravel floor. From level 2 it is lined and
    floored properly and the roof of the dig becomes a boarded ceiling on beams - the same room,
    finished, which is what an upgrade should look like from inside.

    The plan: three bunks side by side against the north wall, the row in front of their feet kept
    clear so every one of them can be walked to, the board and the planning table down the east
    side, and the ladder from the shed floor in the south-east corner.
    """
    p = P
    if level == 1:
        dig_room(s, ROOM_A, rough_walls,
                 lambda x, z: p["grit"] if speck(x, 5, z, 3) else p["rough"],
                 lambda x, z: p["boards"])
    else:
        dig_room(s, ROOM_A, brick_walls,
                 lambda x, z: p["brick"] if speck(x, 5, z, 5) else p["slabfl"],
                 lambda x, z: p["beam"] if z in (-5, -3) else p["ceil"])

    ix0, iz0, ix1, iz1 = interior(ROOM_A)          # x -4..-1, z -5..-2
    # the bunks: one thief, then two, then three, exactly as BuildingHideout.crewSize counts them
    for bx in BUNKS[:CREW[level]]:
        s.set(bx, -4, iz0 + 1, bed(p["bed"], "north", "foot"))
        s.set(bx, -4, iz0, bed(p["bed"], "north", "head"))
        s.tag(bx, -4, iz0 + 1, "bed")
    board(s, level)
    s.set(ix1, -4, iz0, p["plan"])                 # the table the jobs get planned on,
    s.set(ix1, -4, iz0 + 1, p["ledger"])           # and the ledger of what came back
    s.set(ix0, -4, iz1, RACK)
    s.set(RIDGE_X, -2, iz1 - 1, lantern(p["lamp"], hanging=True))
    if level >= 3:
        s.set(ix0 + 1, -4, iz1, p["barrel"])


def board(s, level):
    """The board: a panel of dark boards down the east wall with the marks pinned to it.

    This is the building - BuildingHideout keeps the board in NBT and a thief coming on shift
    takes the top mark off it. One mark pinned at level 1 and one more at every level after, so
    the wall fills up as the colony gets ambitious. The marks are item frames with a scrap of
    paper in them: entities, the way Voyager hangs its photographs.
    """
    p = P
    ix0, iz0, ix1, iz1 = interior(ROOM_A)
    wall_x = ROOM_A[2]                             # the east wall, shared with the vault at level 5
    for z in range(iz0, iz0 + 3):
        for y in (-3, -2):
            s.set(wall_x, y, z, p["board"])
    slots = [(-3, iz0 + 1), (-3, iz0), (-3, iz0 + 2), (-2, iz0 + 1), (-2, iz0), (-2, iz0 + 2)]
    for y, z in slots[:min(level, len(slots))]:
        pinned(s, ix1, y, z, "west")


def second_room(s, level):
    """Level 2: a second room behind the first, for what the first one has no space for - stores,
    then the strongbox, then the bench where the lockpicks get filed.

    The north row is kept clear: the arch into the back room and the door up to the stair both
    open into it, and a citizen has to be able to cross from one to the other.
    """
    p = P
    dig_room(s, ROOM_B, brick_walls,
             lambda x, z: p["brick"] if speck(x, 5, z, 5) else p["slabfl"],
             lambda x, z: p["beam"] if z in (0, 2) else p["ceil"])
    archway(s, RIDGE_X, ROOM_A[3])                   # the arch between the two rooms
    ix0, iz0, ix1, iz1 = interior(ROOM_B)            # x -4..-1, z 0..2
    s.set(ix0, -4, iz0, RACK)
    s.set(ix0 + 1, -4, iz1, p["barrel"])
    s.set(RIDGE_X, -2, iz0 + 1, lantern(p["lamp"], hanging=True))
    if level >= 3:
        s.set(ix1, -4, iz0 + 1, RACK)
        strongbox(s)
    if level >= 4:
        s.set(ix1 - 1, -4, iz1, p["picks"])          # the bench, and the stone to finish them on
        s.set(ix1, -4, iz1, p["stone"])


def strongbox(s):
    """Level 3: what the thieves bring home, in a chest in the corner with bars across it."""
    p = P
    ix0, iz0, ix1, iz1 = interior(ROOM_B)
    s.set(ix0, -4, iz1, chest("east"))
    s.set(ix0, -3, iz1, p["brick3"])
    s.set(ix0, -4, iz1 - 1, connected(p["bars"], "south"))


def cellar_stair(s):
    """Level 3: the way in the town is allowed to see.

    A sunken stair off the yard, the first two steps open to the sky behind a stone kerb and the
    rest running in under the ground to a landing and a door into the second room. Four steps, one
    block each: a citizen WALKS this, which is the point of it - the ladder from level 1 is the
    thieves' way and the pathfinder's problem, this is how a barrel gets down there.
    """
    p = P
    x = STAIR_X
    # the kerb and the lining, from the cellar floor up to the ground course on both sides
    for lx in (x - 1, x + 1):
        for z in range(1, 5):
            for y in range(-4, GROUND_Y + 1):
                s.set(lx, y, z, brick_walls(lx, y, z))
    # only the LAST step is buried; the rest of the flight is open to the sky, so that from the
    # yard this reads as a stairway rather than a hole with a lid on it
    s.set(x, GROUND_Y, 1, p["brick"])                # the roof over the buried step
    s.set(x, -1, 1, p["brick"])                      # and the course under it - see below
    s.set(x, CELLAR_FLOOR, 0, p["slabfl"])           # the landing
    s.set(x, CELLAR_FLOOR, 1, p["brick"])

    # Four steps down, and then the landing at -4. Each step needs the two cells above it hollow,
    # and the three open ones are cut all the way to two courses ABOVE the ground as well. That
    # last part is not decoration: the blueprint only claims the cells it names, and the builder
    # fills every unclaimed cell at or below the ground course with earth. On flat ground nobody
    # notices. On a slope - and this hut is made to be dug into one - the unclaimed cells over the
    # stair are hillside, so the mouth of the flight comes out packed with dirt and the cellar is
    # unreachable. Claiming them is what makes the stair work on ground that is not flat.
    OPEN = (2, 3, 4)
    for i, z in enumerate((4, 3, 2, 1)):
        step = -1 - i
        for y in (step + 1, step + 2):               # the two cells a citizen occupies
            carve(s, x, y, z)
        if z in OPEN:
            for y in range(step + 3, GROUND_Y + 3):  # and open to the sky above that
                carve(s, x, y, z)
        s.set(x, step, z, stairs(p["cellar_st"], "south"))
    carve_column(s, x, 0, -4, CELLAR_CEIL)           # the landing itself
    archway(s, ROOM_B[2], 0, hang=p["door"], facing="east")   # and the door into the second room
    for z in (3, 4):                                 # a rail, so nobody walks into the stairwell
        for lx in (x - 1, x + 1):
            s.set(lx, 1, z, connected(p["fence"], "north" if z == 4 else "south"))


def bolthole(s):
    """Level 4: the way out nobody is watching.

    A crawl out of the back of the second room, along under the yard and up a ladder into a hole
    in the ground beside the woodpile, five blocks from the door. Two cells high and shored with
    old boards - it is not meant to be comfortable, it is meant to be there.
    """
    p = P
    bx, bz = BOLT_EXIT
    run = [(-3, 4), (-3, 5), (-2, 5), (-1, 5), (bx, bz)]
    archway(s, -3, ROOM_B[3])                        # out through the back wall
    for x, z in run:
        s.set(x, CELLAR_FLOOR, z, p["rough"])
        s.set(x, -2, z, p["boards"])                 # the boards holding the roof of the dig up
        carve(s, x, -4, z)
        carve(s, x, -3, z)
    # the shaft, and the ladder up it: held by the cobble lining on its east side
    carve_column(s, bx, bz, -2, CELLAR_CEIL)
    for y in range(-4, GROUND_Y + 1):
        s.set(bx + 1, y, bz, p["rough2"] if speck(bx, y, bz, 3) else p["rough"])
    for y in range(-4, GROUND_Y):
        s.set(bx, y, bz, ladder("west"))
    # the lid, and the woodpile that is the reason nobody looks at it
    for x in range(bx - 1, bx + 1):
        for z in range(bz - 1, bz + 2):
            s.set(x, GROUND_Y, z, p["yard"])
    s.set(bx, GROUND_Y, bz, trapdoor(p["trap"], facing="west", half="top"))
    for y in (1, 2):
        for z in (bz - 1, bz):
            s.set(bx - 1, y, z, p["logs"])


def vault(s):
    """Level 5: a small room off the stair landing with one door and no other way in.

    Drawn before the back room, so that the board of marks - painted onto the same wall from the
    back room's side - is the last thing written there and survives.
    """
    p = P
    dig_room(s, VAULT, lambda x, y, z: p["brick3"] if speck(x, y, z, 7) else brick_walls(x, y, z),
             lambda x, z: p["slabfl"],
             lambda x, z: p["ceil"])
    archway(s, STAIR_X, VAULT[3], hang=p["door"], facing="south")
    ix0, iz0, ix1, iz1 = interior(VAULT)             # x 1..3, z -5..-2
    for x in range(ix0, ix1 + 1):
        s.set(x, -4, iz0, chest("south"))
    s.set(ix1, -4, iz0 + 1, RACK)
    s.set(ix1, -4, iz1, RACK)
    s.set(ix0 + 1, -2, iz0 + 1, lantern(p["lamp"], hanging=True))


def hatch(s):
    """The thieves' own way down, from level 1 on: a trapdoor in the shed floor and a ladder in
    the corner of the back room under it. It stays at every level - the stair at level 3 is for
    barrels, this is for people who would rather not be seen using a stair."""
    p = P
    x, z = HATCH
    carve(s, x, CELLAR_CEIL, z)
    s.set(x, GROUND_Y, z, trapdoor(p["trap"], facing="north", half="top"))
    for y in range(-4, GROUND_Y):
        s.set(x, y, z, ladder("north"))              # held by the room's south wall behind it


# ================================================================ the five levels

def build(level):
    """One level of the Hideout, drawn around the same anchor as every other level."""
    s = blank(level)
    if level >= 5:
        vault(s)                 # before the back room: they share a wall, and the board wins it
    back_room(s, level)
    if level >= 2:
        second_room(s, level)
    shed(s, level)
    yard(s, level)
    hatch(s)
    if level >= 3:
        cellar_stair(s)
    if level >= 4:
        bolthole(s)
    if level >= 5:
        loft(s)
    s.tag(*s.anchor, "name=Hideout")
    return s


if __name__ == "__main__":
    for lv in range(1, 6):
        st = build(lv)
        print(f"{st.name:10s} {str(st.size()):16s} {len(st.blocks):5d} blocks  "
              f"{len(st.carved):4d} dug  {len(st.entities):2d} marks  anchor {st.anchor}")
