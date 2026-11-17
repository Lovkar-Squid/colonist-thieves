# Colonist Thieves 0.2.1 (17 Nov 2026)

Two rules for robbing a Colonies at War convoy, both from playing 0.2.0.

**A convoy coming to your own colony is not offered any more.** 0.2.0 listed every other colony's
convoy within reach, including one on its way to you. Robbing it only took goods that were already
coming to you and risked your standing with the sender for nothing, so the Hideout now leaves those
off its list, the same way it never offers your colony's own convoys.

**A server can switch convoy robbing off.** New server setting `allowConvoyTheft` (on by default).
It is a switch of its own and not `allowPlayerColonyTheft`: a convoy always belongs to another
colony, so tying it to that setting (off by default, because robbing another player's colony is PvP)
would have switched convoy robbing off on every server. With `allowConvoyTheft = false` no Hideout is
offered any convoy, and a convoy job already on the board finds the road empty.

Checked on the headless test server (`tools/convoygate`, with Colonies at War 0.3.0): a convoy from
Ironhold to Millbrook passes Oakford's Hideout and is offered there, but never to Ironhold's or
Millbrook's own Hideout; with the setting off nothing is offered, and back on it is again.
