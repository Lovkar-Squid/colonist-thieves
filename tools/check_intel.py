"""Is scouting worth doing, and does sabotage pay for itself?

Both are easy to build and hard to balance, because neither one does anything you can see. A
scout comes home with nothing in his hands; what he changes is a number in a roll somebody else
makes three nights later. So this asks the questions the game cannot be played long enough to
answer by hand, with the real constants read straight out of the Java so they cannot drift:

  1  does the reading fade the way it says - full the night it happened, gone when it runs out,
     and a straight line in between, never negative and never more than one
  2  do the two softenings together stay under the cap they are clamped to, so that raising one
     of them actually raises it rather than silently hitting a ceiling
  3  is looking easier than taking, and is breaking harder - in the roll, in how long it takes,
     and in what it costs a colony's standing when it goes wrong
  4  is a scouted place MEANINGFULLY easier to rob, and
  5  is it still not free - a place that would catch him nine times in ten has to keep catching
     him most of the time, or scouting stops being intelligence and starts being a cheat code
  6  and the one that justifies the whole feature: is a robbery after a successful sabotage
     easier than the same robbery cold, by more than the sabotage run itself risked

Run it:  python3 tools/check_intel.py
"""
import pathlib
import random
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def source(rel):
    return (ROOT / rel).read_text()


def number(text, pattern, cast=float):
    hit = re.search(pattern, text)
    if hit is None:
        raise SystemExit(f"  could not read {pattern!r} out of the source - has it been renamed?")
    return cast(hit.group(1))


# ------------------------------------------------------------------ the constants, from the Java
INTEL = source("src/me/lovkar/thieves/intel/Intel.java")
HEIST = source("src/me/lovkar/thieves/job/Heist.java")
WORK = source("src/me/lovkar/thieves/target/Work.java")
KINDS = source("src/me/lovkar/thieves/target/TargetKind.java")
ASSESS = source("src/me/lovkar/thieves/assess/Assessment.java")
AI = source("src/me/lovkar/thieves/ai/EntityAIWorkThief.java")

DAY = number(INTEL, r"long DAY = (\d+)L", int)
SCOUT_DAYS = number(INTEL, r"SCOUT_DAYS = (\d+)", int)
SABOTAGE_DAYS = number(INTEL, r"SABOTAGE_DAYS = (\d+)", int)
KNOWN = number(INTEL, r"float KNOWN = ([0-9.]+)f")
SHAKEN = number(INTEL, r"float SHAKEN = ([0-9.]+)f")
CAP = number(ASSESS, r"Math\.min\(([0-9.]+)f, known \+ shaken\)")

DIFFICULTY = {m.group(1): float(m.group(2))
              for m in re.finditer(r'([A-Z]+)\("[a-z]+", ([0-9.]+)f', WORK)}
PACE = {m.group(1): float(m.group(2))
        for m in re.finditer(r"case ([A-Z]+) -> ([0-9.]+)f;", HEIST.split("final float pace")[1])}
PACE.setdefault("ROB", 1.0)
KIND_HARD = {m.group(1): float(m.group(2))
             for m in re.finditer(r'([A-Z]+)\("[a-z]+", (?:"[^"]*"|null), ([0-9.]+)f', KINDS)}
STANDING = {m.group(1): int(m.group(2))
            for m in re.finditer(r"case ([A-Z]+) -> (\d+);", AI)}
STANDING.setdefault("ROB", int(re.search(r"default -> (\d+);", AI).group(1)))

CLEAN_AT = number(HEIST, r"CLEAN_AT = ([0-9.]+)f")
CAUGHT_AT = number(HEIST, r"CAUGHT_AT = ([0-9.]+)f")
LUCK = number(HEIST, r"float LUCK = ([0-9.]+)f")
LEAST_LUCK = number(HEIST, r"LEAST_LUCK = (\d+)", int)
CAREFUL = number(AI, r"CAREFUL_ENOUGH = ([0-9.]+)f")


def fade(age_ticks, days):
    span = days * DAY
    if age_ticks < 0 or age_ticks >= span:
        return 0.0
    return 1.0 - age_ticks / float(span)


def soften(vigilance, known, shaken):
    """Assessment.softenedBy, on the one number the roll actually reads."""
    off = max(0.0, min(CAP, known + shaken))
    return round(vigilance * (1.0 - off))


def brings(skill, hut, learned=0):
    return skill * 2 + hut * 6 + learned


def demands(vigilance, kind, work):
    return round(vigilance * KIND_HARD[kind] * DIFFICULTY[work])


def swing(mine, theirs):
    return max(LEAST_LUCK, round((mine + theirs) * LUCK))


def prospect(mine, theirs):
    """Heist.prospect - how often this comes off clean, without rolling."""
    s = swing(mine, theirs)
    needed = round(s * CLEAN_AT) - (mine - theirs)
    best = s - s // 2
    lucky = best - max(needed, -(s // 2)) + 1
    return max(0.0, min(1.0, lucky / float(s + 1)))


def odds(skill, hut, vigilance, kind, work, known=0.0, shaken=0.0, runs=40000, seed=7, learned=0):
    rng = random.Random(seed)
    mine = brings(skill, hut, learned)
    theirs = demands(soften(vigilance, known, shaken), kind, work)
    s = swing(mine, theirs)
    win, lose = round(s * CLEAN_AT), -round(s * CAUGHT_AT)
    got = {"clean": 0, "spotted": 0, "caught": 0}
    for _ in range(runs):
        margin = mine - theirs + rng.randrange(s + 1) - s // 2
        got["clean" if margin >= win else ("spotted" if margin > lose else "caught")] += 1
    return {k: v / runs for k, v in got.items()}


def main():
    bad = []
    say = print
    say("intel:")
    say(f"  constants: scouting lasts {SCOUT_DAYS} day(s) and takes {KNOWN:.0%} off, "
        f"sabotage lasts {SABOTAGE_DAYS} and takes {SHAKEN:.0%}, clamped at {CAP:.0%}")
    say(f"  work: " + ", ".join(f"{w} x{DIFFICULTY[w]} at pace {PACE[w]}, standing {STANDING[w]}"
                                for w in ("SCOUT", "ROB", "SABOTAGE")))

    # 1 - the fade
    span = SCOUT_DAYS * DAY
    if abs(fade(0, SCOUT_DAYS) - 1.0) > 1e-6:
        bad.append("a reading is not worth its full value the night it is taken")
    if fade(span, SCOUT_DAYS) != 0.0 or fade(span + 1, SCOUT_DAYS) != 0.0:
        bad.append("a reading is still worth something after it has run out")
    if fade(-1, SCOUT_DAYS) != 0.0:
        bad.append("a reading from the future is worth something")
    last = 2.0
    for step in range(0, span + 1, max(1, span // 200)):
        now = fade(step, SCOUT_DAYS)
        if now > last + 1e-6:
            bad.append("the fade goes back up")
            break
        if now < -1e-9 or now > 1.0 + 1e-9:
            bad.append("the fade leaves 0..1")
            break
        last = now
    say(f"  fade: 1.00 at dusk, {fade(span // 2, SCOUT_DAYS):.2f} at half, "
        f"0.00 at {SCOUT_DAYS} days - monotone")

    # 2 - the clamp
    if KNOWN + SHAKEN > CAP:
        bad.append(f"scouting and sabotage together ({KNOWN + SHAKEN:.0%}) run into the "
                   f"{CAP:.0%} clamp - raising either one would do nothing")
    say(f"  clamp: the most anything can be softened is {KNOWN + SHAKEN:.0%}, under the {CAP:.0%} cap")

    # 3 - the orderings
    if not DIFFICULTY["SCOUT"] < DIFFICULTY["ROB"] < DIFFICULTY["SABOTAGE"]:
        bad.append("looking is not easier than taking, or breaking is not harder")
    if not PACE["SCOUT"] < PACE["ROB"] < PACE["SABOTAGE"]:
        bad.append("the three jobs do not take the time their risk says they should")
    if not STANDING["SCOUT"] < STANDING["ROB"] < STANDING["SABOTAGE"]:
        bad.append("being caught at the three does not cost what it should")
    say("  order: looking is cheapest and quickest, breaking dearest and slowest - in all three")

    # 4 - the curve itself. The grid is the point: every cell 0% or 100% means the mod is a
    # step, and a step is a mod where a hut level decides everything and a scout decides nothing
    watches = (15, 25, 35, 50, 70, 95, 130)
    crew = ((8, 1, "novice, hut 1"), (25, 3, "steady, hut 3"), (45, 5, "expert, hut 5"))
    say("  the curve - clean / spotted / caught, robbing:")
    say(f"     {'':<22}" + "".join(f"{w:>19}" for w in watches))
    flat = 0
    cells = 0
    for kind in ("VILLAGE", "COLONY"):
        for skill, hut, who in crew:
            line = ""
            for watch in watches:
                got = odds(skill, hut, watch, kind, "ROB")
                line += f"   {got['clean']:>4.0%}/{got['spotted']:>4.0%}/{got['caught']:>4.0%}"
                cells += 1
                if max(got.values()) > 0.995:
                    flat += 1
            say(f"     {kind.lower():>8} {who:<13}" + line)
    say(f"     {flat} of {cells} cells are settled before the dice - the rest are a night's work")

    # 5 - progression. More thief, or less watch, is never worse
    for kind in ("VILLAGE", "COLONY"):
        for watch in watches:
            ladder = [odds(s_, h_, watch, kind, "ROB")["caught"] for s_, h_, _ in crew]
            if any(ladder[i] < ladder[i + 1] - 0.01 for i in range(len(ladder) - 1)):
                bad.append(f"a better thief is caught more often at {kind.lower()} watch {watch}")
        for skill, hut, _ in crew:
            row = [odds(skill, hut, w, kind, "ROB")["clean"] for w in watches]
            if any(row[i] < row[i + 1] - 0.01 for i in range(len(row) - 1)):
                bad.append(f"a better-watched {kind.lower()} is easier to rob, for skill {skill}")
    say("  progression: a better thief is never caught more often, a jumpier place never easier")

    # 6 - what a scout is worth, where it can be worth anything
    fresh = KNOWN
    looked = []
    for watch in watches:
        for skill, hut, _ in crew:
            cold = odds(skill, hut, watch, "COLONY", "ROB")["clean"]
            warm = odds(skill, hut, watch, "COLONY", "ROB", known=fresh)["clean"]
            hurt = odds(skill, hut, watch, "COLONY", "ROB")["caught"]
            safer = odds(skill, hut, watch, "COLONY", "ROB", known=fresh)["caught"]
            looked.append((watch, skill, hut, cold, warm, hurt, safer))
    contested = [(c, w) for _, _, _, c, w, _, _ in looked if 0.02 <= c <= 0.90]
    lift = sum(w - c for c, w in contested) / max(1, len(contested))
    if lift < 0.05:
        bad.append(f"a fresh scouting is worth only {lift:.1%} more clean jobs - nobody would bother")
    if lift > 0.45:
        bad.append(f"a fresh scouting is worth {lift:.1%} more clean jobs - it decides the game")
    hopeless = [(c, w) for _, _, _, c, w, _, _ in looked if c < 0.01]
    if any(w > 0.55 for c, w in hopeless):
        bad.append("a place that would never let him in becomes easy on one night's scouting")
    say(f"  scouting: on a target that was in doubt it turns {lift:.1%} more nights clean, "
        f"over {len(contested)} of {len(looked)} cases")
    for watch, skill, hut, cold, warm, hurt, safer in looked:
        if 0.02 <= cold <= 0.90:
            say(f"     colony watch {watch:>3}  skill {skill:>2} hut {hut}   clean {cold:>5.1%} "
                f"-> {warm:>5.1%}   caught {hurt:>5.1%} -> {safer:>5.1%}")

    # 7 - and the one that justifies sabotage at all
    paid = []
    for watch in watches:
        for skill, hut, _ in crew:
            cold = odds(skill, hut, watch, "COLONY", "ROB")["clean"]
            after = odds(skill, hut, watch, "COLONY", "ROB", shaken=SHAKEN)["clean"]
            risk = odds(skill, hut, watch, "COLONY", "SABOTAGE")["caught"]
            paid.append((watch, skill, hut, cold, after, risk))
    room = [(c, a) for _, _, _, c, a, _ in paid if 0.01 <= c <= 0.90]
    better = sum(1 for c, a in room if a > c + 0.02)
    if better < len(room) * 0.75:
        bad.append(f"a sabotage makes the robbery after it easier in only {better} of "
                   f"{len(room)} cases where there was room for it to")
    settled = [(c, a) for _, _, _, c, a, _ in paid if c > 0.90 or c < 0.01]
    if any(a < c - 0.01 for c, a in settled):
        bad.append("a sabotage makes an already-easy robbery harder")
    say(f"  sabotage: the robbery after it is easier in {better} of the {len(room)} cases "
        f"with room to be, and never harder in the {len(settled)} without")
    for watch, skill, hut, cold, after, risk in paid:
        if 0.02 <= cold <= 0.90 or 0.02 <= after <= 0.98:
            say(f"     colony watch {watch:>3}  skill {skill:>2} hut {hut}   clean {cold:>5.1%} "
                f"-> {after:>5.1%}   (the sabotage run itself: caught {risk:>5.1%})")

    # 8 - and what "careful" refuses, which has to agree with all of the above
    say("  careful: the standing order refuses anything under "
        f"{CAREFUL:.0%} - which is")
    for kind in ("VILLAGE", "COLONY"):
        for skill, hut, who in crew:
            takes = [w for w in watches
                     if prospect(brings(skill, hut), demands(w, kind, "ROB")) >= CAREFUL]
            worst = max(takes) if takes else None
            risk = odds(skill, hut, worst, kind, "ROB")["caught"] if worst else 0.0
            say(f"     {kind.lower():>8} {who:<13} up to watch "
                f"{worst if worst else '-':>4}  (caught there {risk:.0%})")
            if worst is not None and risk > 0.35:
                bad.append(f"'careful' still sends a {who} into a {kind.lower()} "
                           f"that catches him {risk:.0%} of the time")

    say("")
    if bad:
        for line in bad:
            say(f"  !! {line}")
        say(f"  {len(bad)} problem(s)")
        return 1
    say("  scouting is worth doing, sabotage pays for itself, and neither makes anywhere free")
    return 0


if __name__ == "__main__":
    sys.exit(main())
