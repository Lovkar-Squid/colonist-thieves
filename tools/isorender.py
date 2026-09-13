"""A quick isometric preview of a voxel Structure, for eyeballing shapes without Blender.

The Voyager tool, with a driver for this repository's one building. Not textures - flat colours
by block family, three shades for the three visible faces - but enough to see that the roof sits
on the walls, the stair goes where it should and the bunks are in the room.

    python3 isorender.py 1 3 5           the whole of levels 1, 3 and 5
    python3 isorender.py --above 5       only what stands above the ground course
    python3 isorender.py --cellar 5      only what is dug in under it

-> tools/out/iso_hideout5.png. The cut is what makes this useful here: seen whole, an
underground building is a shed with a lid on a hole.
"""
import sys
from PIL import Image, ImageDraw

from voxel import parse_state

FAMILY = {
    "copper": (120, 170, 130), "oxidized": (90, 160, 140), "weathered": (110, 165, 135),
    "deepslate": (70, 72, 80), "blackstone": (40, 38, 42), "black_brick": (45, 45, 50),
    "beige": (215, 200, 170), "cream": (235, 225, 200), "brown": (150, 120, 90),
    "stone_brick": (150, 150, 150), "cobblestone": (125, 125, 125), "chiseled": (170, 170, 175),
    "sandstone": (225, 205, 150), "sand_stone": (225, 205, 150), "terracotta": (90, 160, 170),
    "quartz": (240, 238, 232), "calcite": (235, 235, 230), "purpur": (170, 120, 175),
    "amethyst": (150, 100, 200), "glass": (200, 230, 240), "tinted": (40, 40, 50),
    "lantern": (255, 220, 120), "sea_lantern": (190, 240, 230), "glowstone": (250, 220, 130),
    "bulb": (250, 200, 100), "end_rod": (250, 250, 250), "door": (110, 80, 50),
    "ladder": (160, 120, 70), "bed": (200, 60, 80), "lectern": (150, 110, 70),
    "bookshelf": (160, 120, 80), "rack": (120, 90, 60), "lightroom": (200, 60, 60),
    "analyzer": (80, 200, 220), "blockhut": (255, 120, 40), "cutter": (170, 150, 120),
    "barrel": (130, 100, 60), "cartography": (150, 120, 90), "enchanting": (90, 60, 120),
    "lodestone": (90, 90, 100), "blue_brick": (60, 80, 160), "blue_": (60, 80, 160),
    "gray_brick": (120, 120, 125), "light_blue": (120, 170, 220), "cyan": (60, 160, 170),
    "gravel": (140, 135, 130), "coarse_dirt": (120, 90, 62), "dirt_path": (150, 125, 85),
    "andesite": (150, 150, 148), "oak_planks": (185, 150, 95), "smithing": (70, 70, 80),
    "grindstone": (140, 140, 145), "crafting": (160, 120, 75), "trapdoor": (125, 92, 55),
    "chest": (170, 130, 70), "fence": (110, 82, 55), "stairs": (150, 120, 85),
    "shingle": (150, 80, 70), "bricks": (160, 80, 70), "smooth_stone": (170, 170, 170),
    "concrete": (200, 200, 200), "iron_bars": (140, 140, 150), "spruce": (100, 75, 50),
    "mangrove": (110, 50, 50), "warped": (60, 140, 130), "acacia": (190, 100, 50),
    "dark_oak": (70, 50, 30), "cut_": (225, 205, 150), "solidsubstitution": (90, 130, 80),
    "substitution": (90, 130, 80), "squarepillar": (200, 200, 195), "vanilla": (200, 200, 195),
}


def colour(block):
    name = parse_state(block)[0].split(":")[1]
    for key, rgb in FAMILY.items():
        if key in name:
            return rgb
    return (180, 180, 180)


def render(s, path, cell=10, top=False):
    (x0, y0, z0), (x1, y1, z1) = s.bounds()
    ents = {(x, y, z): eid for (x, y, z), eid, *_ in s.entities}
    # iso: screen u = (x - z), v = (x + z)/2 - y
    def proj(x, y, z):
        u = (x - x0) - (z - z0)
        v = ((x - x0) + (z - z0)) * 0.5 - (y - y0)
        return u, v
    us, vs = [], []
    for c in ((x0, y0, z0), (x1, y0, z0), (x0, y0, z1), (x1, y0, z1), (x0, y1, z0), (x1, y1, z1), (x0, y1, z1), (x1, y1, z0)):
        u, v = proj(*c)
        us.append(u); vs.append(v)
    w = int((max(us) - min(us) + 3) * cell)
    h = int((max(vs) - min(vs) + 3) * cell)
    ou, ov = -min(us) + 1.5, -min(vs) + 1.5
    img = Image.new("RGB", (w, h), (24, 26, 32))
    d = ImageDraw.Draw(img)
    order = sorted(s.blocks.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1]))
    for (x, y, z), b in order:
        rgb = colour(b)
        u, v = proj(x, y, z)
        px, py = (u + ou) * cell, (v + ov) * cell
        hx, hy = cell, cell * 0.5
        # top face
        d.polygon([(px, py - cell), (px + hx, py - cell + hy), (px, py - cell + 2 * hy), (px - hx, py - cell + hy)], fill=rgb)
        dark = tuple(int(c * 0.72) for c in rgb)
        darker = tuple(int(c * 0.5) for c in rgb)
        # left (south-west... x-) face and right (z+) face
        d.polygon([(px - hx, py - cell + hy), (px, py - cell + 2 * hy), (px, py + hy), (px - hx, py)], fill=dark)
        d.polygon([(px + hx, py - cell + hy), (px, py - cell + 2 * hy), (px, py + hy), (px + hx, py)], fill=darker)
    for (x, y, z), eid in ents.items():
        u, v = proj(x, y, z)
        px, py = (u + ou) * cell, (v + ov) * cell
        col = (255, 255, 0) if "camera" in eid else (0, 255, 255)
        d.ellipse([px - 4, py - cell - 4, px + 4, py - cell + 4], fill=col)
    img.save(path)
    return path


def slice_y(s, lo=None, hi=None):
    """A copy of the structure with only the courses between lo and hi - a cutaway."""
    from voxel import Structure
    t = Structure(s.name)
    for (x, y, z), b in s.blocks.items():
        if (lo is None or y >= lo) and (hi is None or y <= hi):
            t.set(x, y, z, b)
    t.entities = [e for e in s.entities
                  if (lo is None or e[0][1] >= lo) and (hi is None or e[0][1] <= hi)]
    t.anchor = s.anchor
    return t


if __name__ == "__main__":
    import hideout
    import paths
    args = sys.argv[1:]
    cut = ""
    if args and args[0] in ("--above", "--cellar"):
        cut, args = args[0], args[1:]
    for level in [int(a) for a in args] or [1, 2, 3, 4, 5]:
        st = hideout.build(level)
        if cut == "--above":
            st = slice_y(st, lo=hideout.GROUND_Y)
        elif cut == "--cellar":
            st = slice_y(st, hi=hideout.GROUND_Y)
        out = paths.out(f"iso_hideout{level}{cut.replace('--', '_')}.png")
        render(st, out)
        print(out)
