package me.lovkar.thieves.compat;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import net.neoforged.fml.ModList;

/**
 * The Colonies at War bridge.
 *
 * <p>The rule the two mods are built on: <b>neither depends on the other, and each looks for the
 * other at runtime through one small class.</b> This is that class on the thief's side. With the
 * war mod installed it owns standing, it knows exactly how much wall a colony has because its own
 * wall blocks registered themselves, and it knows which guards have marched off on campaign.
 * Without it every method here answers "I don't know" and the thief falls back on his own
 * lightweight versions - sampled walls, his own standing, everybody counted as home.</p>
 */
public final class War {
    private War() {
    }

    private static final boolean PRESENT = lookFor();
    private static boolean broken = false;
    private static java.lang.reflect.Method wallsOf;
    private static java.lang.reflect.Method awayOf;
    private static java.lang.reflect.Method standingOf;
    private static java.lang.reflect.Method offendMethod;
    private static java.lang.reflect.Method atWarMethod;

    private static boolean lookFor() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("colonies_at_war");
        } catch (final Throwable t) {
            return false;
        }
    }

    public static boolean present() {
        return PRESENT && !broken;
    }

    private static Class<?> bridge() throws Exception {
        return Class.forName("me.lovkar.war.api.WarBridge");
    }

    /**
     * True if this colony has a War Room, so the Hideout may offer scouting and sabotage.
     *
     * <p>Looked up by name and forgiving of its absence: an older Colonies at War has no such
     * method, and the honest answer there is "no War Room", not a broken bridge. Failing this one
     * call does <b>not</b> set {@code broken} - the rest of the bridge is still good.</p>
     */
    public static boolean hasWarRoom(final IColony colony) {
        if (!present() || colony == null) {
            return false;
        }
        try {
            if (warRoomOf == null) {
                warRoomOf = bridge().getMethod("hasWarRoom", IColony.class);
            }
            return (Boolean) warRoomOf.invoke(null, colony);
        } catch (final NoSuchMethodException old) {
            return false;                          // a war mod from before the jobs existed
        } catch (final Throwable t) {
            return false;
        }
    }

    private static java.lang.reflect.Method warRoomOf;

    /** The colony's wall score from the war mod's register, or -1 when it cannot say. */
    public static int walls(final IColony colony) {
        if (!present()) {
            return -1;
        }
        try {
            if (wallsOf == null) {
                wallsOf = bridge().getMethod("walls", IColony.class);
            }
            return (Integer) wallsOf.invoke(null, colony);
        } catch (final Throwable t) {
            broken = true;
            return -1;
        }
    }

    /** True if this guard has marched out with a warband and is not defending anything at home. */
    public static boolean isAway(final ICitizenData citizen) {
        if (!present()) {
            return false;
        }
        try {
            if (awayOf == null) {
                awayOf = bridge().getMethod("isOnCampaign", ICitizenData.class);
            }
            return (Boolean) awayOf.invoke(null, citizen);
        } catch (final Throwable t) {
            broken = true;
            return false;
        }
    }

    /** How the victim feels about the robber's colony, -100..100, or 0 when the war mod is absent. */
    public static int standing(final IColony mine, final int theirs) {
        if (!present()) {
            return 0;
        }
        try {
            if (standingOf == null) {
                standingOf = bridge().getMethod("standing", IColony.class, int.class);
            }
            return (Integer) standingOf.invoke(null, mine, theirs);
        } catch (final Throwable t) {
            broken = true;
            return 0;
        }
    }

    /**
     * Tell the war mod a thief was caught or seen. <b>This is the whole of Marko's first rule:</b>
     * standing only ever moves when the job goes wrong, and only the war mod moves it.
     */
    public static void offend(final IColony mine, final int theirs, final int amount, final String why) {
        if (!present()) {
            return;
        }
        try {
            if (offendMethod == null) {
                offendMethod = bridge().getMethod("offend", IColony.class, int.class, int.class, String.class);
            }
            offendMethod.invoke(null, mine, theirs, amount, why);
        } catch (final Throwable t) {
            broken = true;
        }
    }

    /** Openly at war: robbing them costs nothing further, because there is nothing left to spend. */
    public static boolean atWar(final IColony mine, final int theirs) {
        if (!present()) {
            return false;
        }
        try {
            if (atWarMethod == null) {
                atWarMethod = bridge().getMethod("atWar", IColony.class, int.class);
            }
            return (Boolean) atWarMethod.invoke(null, mine, theirs);
        } catch (final Throwable t) {
            broken = true;
            return false;
        }
    }
}
