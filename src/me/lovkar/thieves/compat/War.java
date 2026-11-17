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
     * Tell the war mod a thief was caught or seen. <b>This is the whole of Lovkar's first rule:</b>
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

    // ------------------------------------------------------------------ convoys on the road (0.3.0)

    /*
     * Colonies at War 0.3.0 put its convoys on a real road and gave the bridge four ways to look at
     * one. They are looked up the way hasWarRoom is: each by name, each on its own, each cached,
     * and a missing one is remembered as missing rather than tried again on every call. An older
     * war mod without them is a world with no convoys in it - never a broken bridge, so none of
     * these four ever sets {@code broken}, and walls, standing and the rest carry on as before.
     */

    private static final int NEAR = 0;
    private static final int CARGO = 1;
    private static final int ESCORT = 2;
    private static final int ROB = 3;
    private static final String[] CONVOY_NAMES = {"convoysNear", "convoyCargo", "convoyEscort", "robConvoy"};
    private static final Class<?>[][] CONVOY_TYPES = {
            {net.minecraft.server.level.ServerLevel.class, net.minecraft.core.BlockPos.class, int.class},
            {net.minecraft.server.level.ServerLevel.class, long.class},
            {net.minecraft.server.level.ServerLevel.class, long.class},
            {net.minecraft.server.level.ServerLevel.class, long.class, int.class}};
    private static final java.lang.reflect.Method[] convoyMethods = new java.lang.reflect.Method[4];
    private static final boolean[] convoyMissing = new boolean[4];
    /** Test hook: the class the four convoy lookups are made on instead of the real bridge. */
    private static Class<?> convoyBridge;

    /**
     * <b>For the rig only.</b> Make the four convoy lookups on {@code stand} instead of the real
     * bridge - a class with the six older methods and none of the new four is a 0.2.2 war mod, as
     * far as this file can tell. {@code null} goes back to the real thing. Only the convoy lookups
     * are touched; the six older ones keep the method they already found.
     */
    public static synchronized void convoyBridgeForTests(final Class<?> stand) {
        convoyBridge = stand;
        java.util.Arrays.fill(convoyMethods, null);
        java.util.Arrays.fill(convoyMissing, false);
    }

    private static synchronized java.lang.reflect.Method convoyMethod(final int which) {
        if (!present() || convoyMissing[which]) {
            return null;
        }
        if (convoyMethods[which] == null) {
            try {
                final Class<?> on = convoyBridge != null ? convoyBridge : bridge();
                convoyMethods[which] = on.getMethod(CONVOY_NAMES[which], CONVOY_TYPES[which]);
            } catch (final NoSuchMethodException old) {
                convoyMissing[which] = true;           // a war mod from before the road
                return null;
            } catch (final Throwable t) {
                convoyMissing[which] = true;
                return null;
            }
        }
        return convoyMethods[which];
    }

    /** True if the war mod has all four convoy lookups, so the Hideout may offer convoys at all. */
    public static boolean convoys() {
        for (int which = 0; which < CONVOY_NAMES.length; which++) {
            if (convoyMethod(which) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * The convoys on the road out within {@code range} blocks of {@code at}: one {@code long[]}
     * each, {@code {id, fromColony, toColony (0 = a kingdom), escortSize, stacksCarried, x, y, z}}.
     * Empty without the war mod, without the lookup, or on any error.
     */
    @SuppressWarnings("unchecked")
    public static java.util.List<long[]> convoysNear(final net.minecraft.server.level.ServerLevel level,
                                                     final net.minecraft.core.BlockPos at, final int range) {
        final java.lang.reflect.Method m = convoyMethod(NEAR);
        if (m == null || level == null || at == null) {
            return new java.util.ArrayList<>();
        }
        try {
            final Object rows = m.invoke(null, level, at, range);
            return rows instanceof java.util.List<?> list ? (java.util.List<long[]>) list : new java.util.ArrayList<>();
        } catch (final Throwable t) {
            return new java.util.ArrayList<>();
        }
    }

    /** A copy of what that convoy carries now; empty when it is not on the road or cannot be asked. */
    @SuppressWarnings("unchecked")
    public static java.util.List<net.minecraft.world.item.ItemStack> convoyCargo(
            final net.minecraft.server.level.ServerLevel level, final long id) {
        final java.lang.reflect.Method m = convoyMethod(CARGO);
        if (m == null || level == null) {
            return new java.util.ArrayList<>();
        }
        try {
            final Object goods = m.invoke(null, level, id);
            return goods instanceof java.util.List<?> list
                    ? (java.util.List<net.minecraft.world.item.ItemStack>) list : new java.util.ArrayList<>();
        } catch (final Throwable t) {
            return new java.util.ArrayList<>();
        }
    }

    /**
     * How many guards ride with that convoy, or -1 when nobody can say. <b>-1 is not "no escort":</b>
     * it is a convoy that is gone, unknown or unreachable, and nothing is ever rolled against it.
     */
    public static int convoyEscort(final net.minecraft.server.level.ServerLevel level, final long id) {
        final java.lang.reflect.Method m = convoyMethod(ESCORT);
        if (m == null || level == null) {
            return -1;
        }
        try {
            return (Integer) m.invoke(null, level, id);
        } catch (final Throwable t) {
            return -1;
        }
    }

    /**
     * Take at most {@code maxStacks} stacks off that convoy. The war mod never lets more than half
     * of what it set out with go, over every robbery of it, and it does not touch standing: that
     * is still {@link #offend}, and still only when the thief was caught or seen.
     */
    @SuppressWarnings("unchecked")
    public static java.util.List<net.minecraft.world.item.ItemStack> robConvoy(
            final net.minecraft.server.level.ServerLevel level, final long id, final int maxStacks) {
        final java.lang.reflect.Method m = convoyMethod(ROB);
        if (m == null || level == null || maxStacks <= 0) {
            return new java.util.ArrayList<>();
        }
        try {
            final Object taken = m.invoke(null, level, id, maxStacks);
            return taken instanceof java.util.List<?> list
                    ? (java.util.List<net.minecraft.world.item.ItemStack>) list : new java.util.ArrayList<>();
        } catch (final Throwable t) {
            return new java.util.ArrayList<>();
        }
    }
}
