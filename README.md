# Colonist Thieves

A MineColonies profession that robs other people's settlements.

Build a **Hideout**, hire a **Thief**, craft a **Mark** and use it on somewhere worth robbing — a
barbarian camp, a village, a temple, a Waking World kingdom, or, if the server allows it, a
neighbour's colony. Hand the mark in at the Hideout and your thief slips out for the night. He
comes home clean, seen, or not at all.

## What it actually does

**Every place is judged by what is really there.** Not a difficulty slider: guards actually hired,
military buildings at the level they actually are, wall that is actually standing, what the
warehouse actually holds, and MineColonies' own raid level as the honest baseline. A camp's
garrison is the barbarians standing in it; a Waking World kingdom answers with its own tier, wall
arcs and catapults, because the kingdom already knows.

**Every item stolen comes out of a datapack loot table.** `colonist_thieves:theft/<kind>`, with
`colonist_thieves:theft/structure/<namespace>/<path>` overriding it for one exact structure. Which
structures are robbable is a tag — `#colonist_thieves:camp`, `#colonist_thieves:village`,
`#colonist_thieves:hoard` — so a modpack can make a new kind of settlement worth robbing without a
line of Java.

**A clean job costs nothing.** Standing with another colony falls only when the thief is *caught or
seen*; a clean night is genuinely free, which makes stealth the whole game. What always rises is
**heat** on the place itself, so no camp can be farmed: it cools eight points a day, and there is
always somewhere else to go.

**Getting caught has consequences.** No loot, a wounded thief, the victim told who it was, and a
raid on your own colony the following night.

## Settings

`allowPlayerColonyTheft` is **off** by default. On a shared server this mod is flavour until
somebody turns that on, and then it is PvP — that has to be a decision, not a surprise.
`maxStacksFromPlayerColony`, `retaliation` and `tellTheVictim` are the other three.

`/thieves here` and `/thieves at <x> <z>` print what the mod thinks of a place: its garrison, its
walls, its wealth, what a thief has to roll against and what an army would.

## With Colonies at War

The two are built to fit and neither depends on the other. With the war mod installed it owns
standing, it knows exactly how much wall a colony has, and a thief can be sent scouting or
sabotaging ahead of a campaign. Without it the thief keeps his own lighter versions of all of that.

## Building

See [BUILDING.md](BUILDING.md). Plain `javac`, no Gradle; the blueprints are generated from Python.

Made by Lovkar & Claude.
