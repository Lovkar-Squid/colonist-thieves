"""The Structurize pack icon: resources/blueprints/colonist_thieves/colonist_thieves/colonist_thieves.png

What the pack is, in one 64 x 64 picture the build tool shows in a list: a cellar hatch standing
open in the ground at night, a ladder going down into the lamplight, and nothing else - which is
the whole building. Drawn block by block rather than painted, because at this size a drawing of a
Minecraft thing should look like Minecraft.
"""
import sys

from PIL import Image, ImageDraw

import paths

SIZE = 64
SCALE = 4                      # drawn at 16 x 16 "blocks", then blown up with no smoothing

NIGHT = (22, 24, 32)
NIGHT2 = (30, 33, 43)
GRASS = (74, 96, 52)
GRASS2 = (62, 82, 44)
EARTH = (86, 66, 48)
EARTH2 = (70, 53, 38)
HOLE = (14, 13, 16)
LAMP = (232, 176, 84)
LAMP2 = (168, 118, 52)
WOOD = (122, 84, 50)
WOOD2 = (92, 62, 36)
RUNG = (176, 132, 82)


def px(d, x, y, colour, w=1, h=1):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=colour)


def draw():
    im = Image.new("RGB", (16, 16), NIGHT)
    d = ImageDraw.Draw(im)
    # night sky, a shade lighter towards the horizon
    px(d, 0, 4, NIGHT2, 16, 3)
    # the ground: grass over earth, the near half cut away so the hole is visible
    px(d, 0, 7, GRASS, 16, 1)
    for x in range(0, 16, 3):
        px(d, x, 7, GRASS2)
    px(d, 0, 8, EARTH, 16, 8)
    for x in range(1, 16, 4):
        px(d, x, 10, EARTH2, 2, 2)
        px(d, x + 2, 13, EARTH2, 1, 2)
    # the hole under the hatch
    px(d, 4, 8, HOLE, 8, 8)
    px(d, 4, 8, LAMP2, 8, 1)             # lamplight catching the lip of it
    px(d, 5, 12, LAMP, 6, 4)             # the lantern's light at the bottom
    px(d, 6, 13, LAMP, 4, 3)
    # the ladder going down into it
    for y in range(8, 16):
        px(d, 6, y, WOOD2)
        px(d, 9, y, WOOD2)
    for y in range(9, 16, 2):
        px(d, 6, y, RUNG, 4, 1)
    # the hatch itself, thrown back on its hinge
    px(d, 2, 4, WOOD, 4, 1)
    px(d, 2, 5, WOOD2, 4, 1)
    px(d, 2, 6, WOOD, 4, 1)
    px(d, 3, 4, WOOD2, 1, 3)
    px(d, 2, 7, WOOD2, 4, 1)
    return im.resize((SIZE, SIZE), Image.NEAREST)


def main():
    import build_pack as bp
    import os
    os.makedirs(bp.PACK_DIR, exist_ok=True)
    path = os.path.join(bp.PACK_DIR, bp.PACK_ICON)
    draw().save(path)
    print("wrote", path, draw().size)
    return 0


if __name__ == "__main__":
    sys.exit(main())
