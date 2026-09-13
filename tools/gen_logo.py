"""The project logo: a hooded thief on the wall at night, with the Hideout behind him.

Voyager's logo is an isometric render of its own buildings, and this uses the same renderer -
but a building alone was the wrong subject here. The Hideout is a timber hut, and at the size a
CurseForge logo is actually seen (a 64 px tile in a search result) a timber hut reads as a brown
smudge. A hooded figure reads instantly. So the hut stays, pushed back and darkened to a night
silhouette, and the thing in front is a cloaked man with a lantern.

The figure is built here as voxels and rendered through the same projection as the buildings, so
it sits in the same world rather than being pasted on from somewhere else.

    python3 gen_logo.py            -> ../resources/colonist_thieves_logo.png
"""
import pathlib

from PIL import Image, ImageChops, ImageDraw, ImageFilter

import hideout
import isorender
import paths
from voxel import Structure

SIDE = 400
FLAT = (24, 26, 32)              # isorender's own background, so the trim is exact

# the night the mod is about
SKY_TOP = (12, 14, 24)
HORIZON = (74, 78, 104)
LANTERN = (255, 186, 92)
WINDOW = (255, 170, 70)

# colours for the blocks the figure is made of; isorender matches these by substring
isorender.FAMILY["cloakdeep"] = (20, 19, 26)
isorender.FAMILY["cloakmid"] = (38, 36, 48)
isorender.FAMILY["cloaklit"] = (96, 92, 118)
isorender.FAMILY["cloaktrim"] = (150, 46, 48)
isorender.FAMILY["lanternglow"] = (255, 196, 110)

DEEP = "colonist_thieves:cloakdeep"
MID = "colonist_thieves:cloakmid"
LIT = "colonist_thieves:cloaklit"
TRIM = "colonist_thieves:cloaktrim"
GLOW = "colonist_thieves:lanternglow"


def thief():
    """A cloaked figure, built blocky so it belongs in the same picture as the hut.

    Proportions are the whole game here. The first attempt was six blocks wide and four deep and
    rendered as a stack of plates: at this cube size a human has to be about two wide, two deep
    and ten tall, with the hood WIDER than the neck under it - that overhang is what the eye reads
    as a hood rather than a head.
    """
    s = Structure("thief")

    def box(x0, x1, y0, y1, z0, z1, block):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    s.set(x, y, z, block)

    Z0, Z1 = 0, 1                       # two deep, never more

    # the cloak reaching the ground, flared at the hem so he is planted
    box(0, 3, 0, 1, Z0, Z1, DEEP)
    box(1, 2, 0, 2, Z0, Z1, MID)
    # body
    box(1, 2, 2, 4, Z0, Z1, MID)
    box(2, 2, 2, 4, Z0, Z1, LIT)        # lit flank, the side the lantern is on
    box(1, 1, 2, 4, Z0, Z1, DEEP)
    box(1, 2, 3, 3, Z0, Z1, TRIM)       # the sash: the only colour in the whole thing
    # shoulders, wider than the body
    box(0, 3, 5, 5, Z0, Z1, MID)
    box(3, 3, 5, 5, Z0, Z1, LIT)
    box(0, 0, 5, 5, Z0, Z1, DEEP)
    # neck - narrow, and dark, so the hood above it reads as an overhang
    box(1, 2, 6, 6, Z0, Z1, DEEP)
    # hood: wider than the neck, and its front face stays in shadow where the face would be
    box(0, 3, 7, 8, Z0, Z1, MID)
    box(3, 3, 7, 8, Z0, Z1, LIT)
    box(0, 0, 7, 8, Z0, Z1, DEEP)
    box(0, 3, 7, 8, Z1, Z1, DEEP)
    box(1, 2, 9, 9, Z0, Z1, MID)        # the peak

    # the arm reaching out on the lit side, and the lantern hanging off it
    box(4, 4, 4, 5, Z0, Z1, MID)
    box(4, 4, 3, 3, Z0, Z1, DEEP)
    box(4, 5, 2, 2, Z0, Z1, GLOW)
    return s


def trimmed(img, bg=FLAT):
    mask = Image.new("L", img.size, 0)
    px, mk = img.load(), mask.load()
    for y in range(img.height):
        for x in range(img.width):
            if px[x, y] != bg:
                mk[x, y] = 255
    box = mask.getbbox()
    if box is None:
        raise SystemExit("the render came out empty")
    return img.crop(box), mask.crop(box)


def darkened(img, mask, k):
    out = img.point(lambda v: int(v * k))
    return out, mask


def sky():
    """Dark overhead, lighter at the horizon.

    A black figure on a black sky is not a silhouette, it is a hole. The band of moonlight low in
    the frame is what the thief and the hut are read against.
    """
    img = Image.new("RGB", (SIDE, SIDE))
    d = ImageDraw.Draw(img)
    for y in range(SIDE):
        t = y / (SIDE - 1)
        e = t ** 1.6                       # keep the top dark, open up low down
        img_row = tuple(round(a + (b - a) * e) for a, b in zip(SKY_TOP, HORIZON))
        d.line([(0, y), (SIDE, y)], fill=img_row)
    for x, y in ((41, 38), (96, 21), (147, 55), (212, 29), (268, 62), (321, 34), (364, 71),
                 (72, 88), (188, 96), (295, 104)):
        img.putpixel((x, y), (170, 178, 205))
    return img


def glow(img, at, radius, colour, strength):
    """A soft pool of light, ADDED to the picture.

    The first version blended towards a layer that was black everywhere except the disc, which
    darkened the whole frame instead of lighting part of it - the logo came out almost solid
    black. Light adds; it does not average.
    """
    layer = Image.new("RGB", img.size, (0, 0, 0))
    ImageDraw.Draw(layer).ellipse(
        [at[0] - radius, at[1] - radius, at[0] + radius, at[1] + radius], fill=colour)
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.6))
    if strength != 1.0:
        layer = layer.point(lambda v: int(v * strength))
    return ImageChops.add(img, layer)


def main():
    out = sky()

    # the hut, behind and to the right, darkened to a night silhouette
    hut_src = isorender.slice_y(hideout.build(5), lo=hideout.GROUND_Y)
    scratch = pathlib.Path(paths.out("logo_hut.png"))
    isorender.render(hut_src, str(scratch), cell=7)
    hut, hut_mask = trimmed(Image.open(scratch).convert("RGB"))
    hut, hut_mask = darkened(hut, hut_mask, 0.40)   # a silhouette, but not a void
    hw = int(SIDE * 0.72)
    hut = hut.resize((hw, max(1, round(hut.height * hw / hut.width))), Image.LANCZOS)
    hut_mask = hut_mask.resize(hut.size, Image.LANCZOS)
    hut_at = (SIDE - hw - 4, 44)
    out.paste(hut, hut_at, hut_mask)

    # one lit window in the hut, so the dark mass has a reason to be dark
    out = glow(out, (hut_at[0] + int(hw * 0.52), hut_at[1] + int(hut.height * 0.56)),
               34, WINDOW, 0.55)

    # the thief, in front and to the left, big enough to carry the picture
    scratch2 = pathlib.Path(paths.out("logo_thief.png"))
    isorender.render(thief(), str(scratch2), cell=22)
    fig, fig_mask = trimmed(Image.open(scratch2).convert("RGB"))
    fh = int(SIDE * 0.74)
    fig = fig.resize((max(1, round(fig.width * fh / fig.height)), fh), Image.LANCZOS)
    fig_mask = fig_mask.resize(fig.size, Image.LANCZOS)
    fig_at = (10, SIDE - fh - 8)

    # the lantern throws light before the figure is laid down, so the glow is behind him
    out = glow(out, (fig_at[0] + int(fig.width * 0.86), fig_at[1] + int(fh * 0.30)),
               46, LANTERN, 0.62)
    out.paste(fig, fig_at, fig_mask)
    # and a small tight core on the lantern itself, over him
    out = glow(out, (fig_at[0] + int(fig.width * 0.86), fig_at[1] + int(fh * 0.30)),
               13, LANTERN, 0.85)

    dest = pathlib.Path(__file__).resolve().parent.parent / "resources" / "colonist_thieves_logo.png"
    out.save(dest)
    scratch.unlink(missing_ok=True)
    scratch2.unlink(missing_ok=True)
    print(dest, out.size)


if __name__ == "__main__":
    main()
