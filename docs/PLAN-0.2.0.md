# Colonist Thieves 0.2.0 — plan

The owner's answer of 16 Nov 2026: **convoy robbing is an interception.** The Hideout can send a thief at a
Colonies at War convoy passing within range; he steals part of the cargo; the escort can catch him (a bigger escort,
a bigger risk); standing falls only when he is caught or seen, as with every other theft.

## T1 — intercepting a convoy

- Colonies at War 0.3.0 adds four methods to its `me.lovkar.war.api.WarBridge` (the six older ones are unchanged):
  - `convoysNear(ServerLevel level, BlockPos at, int range)` -> `List<long[]>`, each `{id, fromColony, toColony
    (0 = a kingdom), escortSize, stacksCarried, x, y, z}` for convoys on the road out whose current position is within
    `range` of `at`;
  - `convoyCargo(ServerLevel, long id)` -> `List<ItemStack>` (copies);
  - `convoyEscort(ServerLevel, long id)` -> `int` (-1 unknown);
  - `robConvoy(ServerLevel, long id, int maxStacks)` -> `List<ItemStack>` taken (never more than half of what the
    convoy set out with, counted over every robbery), recorded so the receiver gets only the rest.
- `compat/War` reflects them like the existing lookups: each one optional and cached; an older Colonies at War
  without them means no convoys are offered, never an error. The existing lookups stay exactly as they are.
- A new `TargetKind` (or the closest equivalent in this codebase) for a convoy: a moving target with no fixed
  position. The Hideout offers the convoys of other colonies (never its own colony's) that pass within its range (a
  config value, default 160 blocks, growing with the Hideout level like the other ranges) wherever the player picks
  targets today (the board and its send flow), plus a `/thieves` subcommand that sends a thief at a convoy by id
  (for admins and the rig).
- The thief walks out like on any job, to where the convoy will be when he gets there. If the convoy has arrived or
  left the range by then, he comes home empty ("the road was empty").
- The outcome uses the existing heist roll and outcomes (clean, seen, caught) with the escort as the defence: each
  escort guard raises the risk (config weight); a convoy with no escort is the easiest target in the game.
  - clean: `robConvoy` takes part of the cargo (more for a better thief and Hideout level, never more than the bridge
    allows); the loot goes home like other loot; **no standing change**.
  - seen: he takes less and standing with the sending colony falls (`War.offend`, the same amounts as a theft from
    that colony).
  - caught: he takes nothing, the existing caught consequences apply, and standing falls more.
- Ledger/sightings/notifications name the convoy ("a convoy from Ironhold to Oakford") like other targets.
- Saves: a thief out on a convoy job survives a save/reload (the job keeps the convoy id); 0.1.1 saves load.

**Done when:** the build and the static checks pass; a rig probe `tools/convoygate` on the Windows rig with Colonies
at War 0.3.0 loaded builds two colonies (one with a Hideout and a thief, one with a War Room) and a convoy, sends the
thief at it through the same code path the board uses, and proves: the convoy is offered only within range and never
the thief's own; a clean run takes cargo through `robConvoy` and changes no standing; a caught or seen run lowers the
standing; a bigger escort raises the catch chance; a convoy that is gone gives "the road was empty"; without the four
new bridge methods (a 0.2.2-style bridge, simulated) no convoy is offered and nothing throws.

## Test rig (Windows)

- Build: `powershell -NoProfile -ExecutionPolicy Bypass -File "D:/Claude/addon-rig/build-addon.ps1" -Repo "D:/Claude/thieves-020/repo" [-Probe <name>]`
  (Colonist Thieves compiles without Colonies at War: it only reflects on it). A probe that calls Colonies at War
  classes directly to set up the convoy compiles with `-ExtraCp "D:/Claude/addon-rig/deps/colonies_at_war-0.3.0.jar"`.
- Run: `powershell -NoProfile -ExecutionPolicy Bypass -File "D:/Claude/addon-rig/run-rig.ps1" -Name <name> -Jars "<jar>,<jar>"`
  - a NeoForge 21.1.249 dedicated server, flat world, no players, MineColonies 1.1.1420 and its libraries always loaded;
  - the probe logs `[<name>] CHECK <what>: OK|FAIL ...` lines and last `[<name>] RESULT PASS|FAIL`, then
    `server.halt(false)`.
  - Colonies at War 0.3.0 for the rig: `D:/Claude/addon-rig/deps/colonies_at_war-0.3.0.jar`; its own rig probe
    `D:/Claude/caw-030/repo/tools/roadgate` shows how to set up two colonies and a convoy headlessly.
- Static checks: `bash "D:/Claude/thieves-020/check-all.sh"`.
