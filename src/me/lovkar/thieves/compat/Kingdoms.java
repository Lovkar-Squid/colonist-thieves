package me.lovkar.thieves.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/**
 * The Waking World bridge - the only class here that knows that mod exists.
 *
 * <p>Reflection rather than a compile-time link, because Colonist Thieves must build and run in a
 * modpack that has never heard of The Waking World; without it a thief simply never sees a
 * kingdom. Everything read here is public in Waking World's own {@code KingdomData}: a kingdom's
 * centre, its standing with the player, its tier, how much wall it has raised, how many catapults
 * it keeps and how many houses stand outside the moat. That is the whole assessment of a kingdom -
 * no scanning, because the kingdom already knows.</p>
 *
 * <p>If any lookup fails once, this class goes quiet for the rest of the run rather than throwing
 * on every mark.</p>
 */
public final class Kingdoms {
    private Kingdoms() {
    }

    private static final boolean PRESENT = lookFor();
    private static boolean broken = false;
    private static java.lang.reflect.Method dataGet;
    private static java.lang.reflect.Method allKingdoms;
    private static java.lang.reflect.Method kingdomAt;
    private static java.lang.reflect.Method kingdomName;
    private static Class<?> kingdomClass;

    private static boolean lookFor() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("wakingworld");
        } catch (final Throwable t) {
            return false;
        }
    }

    public static boolean present() {
        return PRESENT && !broken;
    }

    private static void link() throws Exception {
        if (dataGet != null) {
            return;
        }
        final Class<?> data = Class.forName("me.lovkar.wakingworld.kingdom.KingdomData");
        kingdomClass = Class.forName("me.lovkar.wakingworld.kingdom.KingdomData$Kingdom");
        dataGet = data.getMethod("get", ServerLevel.class);
        kingdomAt = data.getMethod("kingdomAt", BlockPos.class);
        kingdomName = Class.forName("me.lovkar.wakingworld.kingdom.Kingdoms").getMethod("name", BlockPos.class);
    }

    /**
     * Every kingdom this world has raised, or an empty list.
     *
     * <p>Needed by the Hideout's list of somewhere-to-rob: a kingdom four thousand blocks away is
     * exactly the kind of target nobody is going to walk to with a Mark in hand.</p>
     */
    @SuppressWarnings("unchecked")
    public static java.util.Collection<Object> all(final ServerLevel level) {
        if (!present()) {
            return java.util.List.of();
        }
        try {
            link();
            final Object data = dataGet.invoke(null, level);
            if (allKingdoms == null) {
                allKingdoms = data.getClass().getMethod("all");
            }
            final Object list = allKingdoms.invoke(data);
            return list instanceof java.util.Collection<?> c
                    ? new java.util.ArrayList<>((java.util.Collection<Object>) c)
                    : java.util.List.of();
        } catch (final Throwable t) {
            return java.util.List.of();
        }
    }

    /** The kingdom object whose land this is, or null. Opaque on purpose - read it with the getters below. */
    public static Object at(final ServerLevel level, final BlockPos pos) {
        if (!present()) {
            return null;
        }
        try {
            link();
            return kingdomAt.invoke(dataGet.invoke(null, level), pos);
        } catch (final Throwable t) {
            broken = true;
            return null;
        }
    }

    /** The name of the kingdom whose land this is, or null. */
    public static String nameAt(final ServerLevel level, final BlockPos pos) {
        final Object k = at(level, pos);
        return k == null ? null : name(k);
    }

    public static String name(final Object kingdom) {
        try {
            return (String) kingdomName.invoke(null, centre(kingdom));
        } catch (final Throwable t) {
            return "a kingdom";
        }
    }

    public static BlockPos centre(final Object kingdom) {
        return (BlockPos) field(kingdom, "center", BlockPos.ZERO);
    }

    /** How the kingdom feels about the player who owns the colony doing the robbing: -100..100. */
    public static int standing(final Object kingdom) {
        return (Integer) field(kingdom, "standing", 0);
    }

    /** 1 hamlet .. 4 city. Everything about a kingdom's strength scales off it. */
    public static int tier(final Object kingdom) {
        return (Integer) field(kingdom, "tier", 1);
    }

    /** How many arcs of wall the town has actually raised - a wall score that needed no measuring. */
    public static int wallArcs(final Object kingdom) {
        return (Integer) field(kingdom, "wallArcs", 0);
    }

    /** Engines on the walls. A kingdom with catapults answers a theft with them. */
    public static int catapults(final Object kingdom) {
        final Object set = field(kingdom, "catapults", null);
        return set instanceof java.util.Collection<?> c ? c.size() : 0;
    }

    /** Houses raised outside the moat: the closest thing a kingdom has to a population. */
    public static int houses(final Object kingdom) {
        final Object set = field(kingdom, "houses", null);
        return set instanceof java.util.Collection<?> c ? c.size() : 0;
    }

    private static Object field(final Object kingdom, final String name, final Object fallback) {
        if (kingdom == null || kingdomClass == null) {
            return fallback;
        }
        try {
            return kingdomClass.getField(name).get(kingdom);
        } catch (final Throwable t) {
            return fallback;
        }
    }
}
