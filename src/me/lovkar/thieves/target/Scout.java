package me.lovkar.thieves.target;

import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.assess.Assessment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Finding somewhere to rob without being told.
 *
 * <p>This is the whole of the Hideout's <b>auto</b> mode. A thief on his own asks the world where
 * the nearest camp, village and hoard are - the same question the {@code /locate} command asks, so
 * it costs what that costs and no more - reads each one, and picks the one worth the walk.</p>
 *
 * <p>The score is deliberately simple and deliberately includes {@link Heat}: a place he has just
 * robbed is jumpy and scores worse than a poorer place he has left alone, so a thief left to
 * himself <b>rotates round the neighbourhood</b> instead of wearing one village out. That is the
 * whole reason heat exists, and here it finally does something on its own.</p>
 */
public final class Scout {
    private Scout() {
    }

    /** Nothing further than this is worth the walk, in blocks. */
    public static final int RANGE = 1200;
    /** Chunks the world is allowed to search per kind. 1200 blocks is 75 chunks. */
    private static final int CHUNK_RANGE = RANGE / 16;

    /**
     * The best target within range, or null if nothing is worth it.
     *
     * @param willing what the Hideout's standing orders will allow - the same test the AI uses
     */
    public static Target choose(final ServerLevel level, final BlockPos from,
                                final java.util.function.Predicate<Target> willing) {
        Target best = null;
        int bestScore = Integer.MIN_VALUE;
        for (final TargetKind kind : new TargetKind[]{TargetKind.HOARD, TargetKind.CAMP, TargetKind.VILLAGE}) {
            final Target found = nearestOf(level, from, kind);
            if (found == null || !willing.test(found)) {
                continue;
            }
            final int score = worth(level, from, found);
            if (score > bestScore) {
                bestScore = score;
                best = found;
            }
        }
        if (best != null) {
            Thieves.LOGGER.info("[scout] picked {} ({}) at {}, score {}",
                    best.name(), best.kind().id(), best.pos().toShortString(), bestScore);
        }
        return best;
    }

    /** The nearest place of this kind, read and named, or null. Also what the hut's list uses. */
    public static Target nearestOf(final ServerLevel level, final BlockPos from, final TargetKind kind) {
        final net.minecraft.resources.ResourceLocation tagId = kind.tag();
        if (tagId == null) {
            return null;
        }
        try {
            final BlockPos at = level.findNearestMapStructure(
                    TagKey.create(Registries.STRUCTURE, tagId), from, CHUNK_RANGE, false);
            if (at == null) {
                return null;
            }
            // ask the world what it actually is, so the name and the loot table are the real ones
            final Target read = Targets.at(level, at);
            return read != null && read.kind() == kind ? read : null;
        } catch (final Throwable t) {
            return null;
        }
    }

    /**
     * What a place is worth going to.
     *
     * <p>Wealth pulls him in, heat and a hard place push him away, and distance costs a little -
     * enough that a thief prefers the village down the road to the temple across the world, but not
     * so much that he never leaves the parish.</p>
     */
    private static int worth(final ServerLevel level, final BlockPos from, final Target target) {
        final Assessment there = Assessment.of(level, target);
        final int heat = Heat.of(level, target.key());
        final int away = (int) Math.sqrt(from.distSqr(target.pos()));
        return there.wealth() * 2
                - heat * 3
                - Math.round(there.vigilance() * target.kind().difficulty())
                - away / 100;
    }
}
