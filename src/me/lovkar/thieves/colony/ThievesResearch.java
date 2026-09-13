package me.lovkar.thieves.colony;

import com.minecolonies.api.colony.IColony;
import me.lovkar.thieves.Thieves;
import net.minecraft.resources.ResourceLocation;

/**
 * The thief's corner of the University's Technology tree (data/colonist_thieves/researches).
 *
 * <p>Same shape as Voyager's: every constant here is a research effect id, the JSON under
 * {@code researches/effects} declares its levels, and the code asks the colony for the strength.
 * MineColonies locks the hut for free - it derives the effect id from the block's registry name,
 * so {@code effects/blockhuthideout} is all it takes to make the Hideout unbuildable until the
 * colony has learned to pick a lock.</p>
 *
 * <p>Every effect below is <b>read once per job</b> and folded into {@link Bonuses}, rather than
 * being asked for in the middle of a roll. A heist is one decision; it should be made against one
 * snapshot of what the colony knows.</p>
 */
public final class ThievesResearch {

    /** Unlocks the Hideout, and its strength is the highest level it may be built to. */
    public static final ResourceLocation HIDEOUT = effect("blockhuthideout");
    /** Added straight to the thief's side of the roll. */
    public static final ResourceLocation LOCKPICKS = effect("lockpicks");
    /** Counts as extra Hideout levels when the take is rolled - a fence who knows what sells. */
    public static final ResourceLocation FENCE = effect("fence");
    /** Fraction of the heat a job would raise that never gets raised. */
    public static final ResourceLocation COLD_TRAIL = effect("cold_trail");
    /** Fraction of the standing a discovered job would cost that it does not cost. */
    public static final ResourceLocation ALIBI = effect("alibi");
    /** Fraction of the travel time saved - he knows the back roads. */
    public static final ResourceLocation BACK_ROADS = effect("back_roads");

    /**
     * Everything the colony has learned, as it applies to one night's work.
     *
     * @param roll     added to the thief's side of the roll
     * @param loot     extra Hideout levels for the loot roll
     * @param heatCut  0..1 of the heat that is never raised
     * @param standingCut 0..1 of the standing cost that is never paid
     * @param travelCut   0..1 of the travel time that is saved
     */
    public record Bonuses(int roll, int loot, float heatCut, float standingCut, float travelCut) {

        public static final Bonuses NONE = new Bonuses(0, 0, 0f, 0f, 0f);

        /** What this colony's University has bought the thieves, right now. */
        public static Bonuses of(final IColony colony) {
            if (colony == null) {
                return NONE;
            }
            try {
                return new Bonuses(
                        (int) Math.round(strength(colony, LOCKPICKS)),
                        (int) Math.round(strength(colony, FENCE)),
                        capped(strength(colony, COLD_TRAIL)),
                        capped(strength(colony, ALIBI)),
                        capped(strength(colony, BACK_ROADS)));
            } catch (final Throwable t) {
                return NONE;                      // no University, no research manager, no problem
            }
        }

        /** A fraction is a fraction: nothing here may make a job free. */
        private static float capped(final double raw) {
            return (float) Math.max(0.0, Math.min(0.75, raw));
        }
    }

    private ThievesResearch() {
    }

    private static ResourceLocation effect(final String name) {
        return ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "effects/" + name);
    }

    public static double strength(final IColony colony, final ResourceLocation effect) {
        if (colony == null) {
            return 0.0;
        }
        return colony.getResearchManager().getResearchEffects().getEffectStrength(effect);
    }

    public static boolean has(final IColony colony, final ResourceLocation effect) {
        return strength(colony, effect) > 0.0;
    }
}
