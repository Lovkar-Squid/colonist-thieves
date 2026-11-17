# Colonist Thieves 0.2.0 (unreleased)

## Robbing a convoy on the road

With Colonies at War 0.3.0 installed, colonies send their goods to each other and to Waking World
kingdoms by convoy, and a convoy is on the road for a few minutes between one War Room and the far
end. A Hideout can now send a thief out to meet one.

**The Hideout watches the road.** Its list of places worth robbing now also shows every convoy
passing within reach, named for where it is going: *a convoy from Ironhold to Oakford*. Reach is
160 blocks from a level 1 Hideout, and every level above that adds another 40, so a level 5
Hideout watches 320 blocks of road. A convoy your own colony sent is never on the list; one coming
to your colony is somebody else's goods until it arrives, and is. Press Send and the convoy goes on
the board like any other job. A convoy is only ever robbed — there is nothing on a road to scout and
nothing to break but the cart — so the verb button does not apply to it.

**He goes when it passes, not after dark.** Every other job still waits for nightfall. A convoy will
have arrived long before then, so a convoy on the board is taken as soon as there is a thief to take
it. The walk out to the road is short — a minute for every two hundred blocks, with none of the
three-minute minimum a camp or a village has — and he heads for where the convoy is when he sets
out.

**The road may be empty.** When he reaches it, the war mod is asked again whether that convoy is
still on its way and still within reach. If it has arrived, or gone on out of reach, he comes home
with nothing, and the log says *found the road empty*. Nothing got hotter and nobody saw anything,
so it costs nothing either.

**The escort is the defence.** If the convoy is there, it is the same roll as every other job —
clean, seen or caught — with the guards riding with it as the whole of what he has to beat. Each
guard adds the same amount to the watch, and a convoy that left with no escort is the easiest
target in the game.

- **Clean:** he takes part of the cargo off the convoy — more for a better thief and a bigger
  Hideout — and brings it home like any other loot. Nobody can put a name to it, so the standing
  with the colony that sent the convoy does not move.
- **Seen:** he takes less, and the sending colony's standing with yours falls by what being seen
  robbing its warehouse would cost.
- **Caught:** he takes nothing and the convoy loses nothing. Being caught costs twice that standing,
  and everything else a caught thief brings with him still happens: a hurt thief, the victim told who
  it was, and a raid on your own colony the next night.

Whatever happens, the war mod never lets more than half of what a convoy set out with go, however
many thieves are waiting on its road, and the receiving colony gets only what is left.

The sending colony's owner hears about it when the thief was caught or seen, and only then.

A thief who meets the convoy and then dies or loses his job before he is home loses what he took with
him, and the job does not go back on the board: that convoy has been robbed once already.

**Two settings and a command.** `convoyRange` (160) is how far a level 1 Hideout watches the road,
and `convoyEscortWeight` (8) is what each escort guard adds to the watch. `/thieves convoy` lists
the convoys your Hideout can see, with their ids, and `/thieves convoy <id>` sends a thief at one —
the same as pressing Send.

**Older versions.** A thief out on a convoy job keeps it through a save. Worlds from 0.1.1 load
as they were. With an older Colonies at War, or without it, there are simply no convoys on the
list. A 0.2.0 server needs 0.2.0 on the client, because the Send button now names a convoy by its
id.
