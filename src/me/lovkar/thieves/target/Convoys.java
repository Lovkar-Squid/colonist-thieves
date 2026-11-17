package me.lovkar.thieves.target;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import me.lovkar.thieves.ThievesConfig;
import me.lovkar.thieves.compat.War;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The road past the Hideout: the Colonies at War convoys a thief could go out and meet.
 *
 * <p>A convoy is the one target that cannot be found by standing somewhere. It is on its way from
 * one War Room to another colony or a kingdom, it is somewhere different every second, and in a few
 * minutes it will have arrived. So it is never looked up by position the way {@link Targets#at}
 * looks up a camp: it is asked for by the war mod's own id, within reach of one Hideout, every time
 * anybody needs to know where it is.</p>
 *
 * <p>Two rules live here and nowhere else. A Hideout only watches the road so far - the configured
 * range, a quarter more for every level the hut has above the first - and it never offers a convoy
 * its own colony sent: robbing yourself is not a strategy. A convoy <i>coming to</i> this colony is
 * somebody else's goods until it arrives, and is offered like any other.</p>
 */
public final class Convoys {
    private Convoys() {
    }

    /** What each Hideout level above the first adds to the range, as a share of the base. */
    public static final float RANGE_PER_LEVEL = 0.25f;

    /** One convoy the Hideout can see, with how many ride with it and how much it carries. */
    public record Seen(Target target, int escort, int stacks) {
    }

    /** How far a Hideout of this level watches the road, in blocks. */
    public static int range(final int hideoutLevel) {
        final int base = ThievesConfig.convoyRange();
        return Math.round(base * (1f + RANGE_PER_LEVEL * Math.max(0, hideoutLevel - 1)));
    }

    /**
     * Every convoy on the road within this Hideout's reach that is not its own colony's.
     *
     * <p>Empty, and quietly so, when the war mod is missing or is one from before convoys had a
     * road: the Hideout simply has nothing on the road to offer.</p>
     */
    public static List<Seen> offered(final ServerLevel level, final BlockPos hideout, final int hideoutLevel,
                                     final IColony mine) {
        final List<Seen> out = new ArrayList<>();
        if (level == null || hideout == null || !ThievesConfig.allowConvoyTheft() || !War.convoys()) {
            return out;
        }
        for (final long[] row : War.convoysNear(level, hideout, range(hideoutLevel))) {
            if (row == null || row.length < 8 || row[0] == 0L) {
                continue;
            }
            if (mine != null && row[1] == mine.getID()) {
                continue;                          // never its own colony's convoy
            }
            if (mine != null && row[2] == mine.getID()) {
                continue;                          // nor one bringing goods to it: they are on their way anyway
            }
            final Target target = Target.ofConvoy(level.dimension(),
                    new BlockPos((int) row[5], (int) row[6], (int) row[7]),
                    name(level, row[1], row[2]), row[0], (int) row[1]);
            out.add(new Seen(target, (int) row[3], (int) row[4]));
        }
        return out;
    }

    /** That one convoy, if it is on the road within reach and not this colony's own; else null. */
    public static Seen find(final ServerLevel level, final BlockPos hideout, final int hideoutLevel,
                            final IColony mine, final long id) {
        if (id == 0L) {
            return null;
        }
        for (final Seen seen : offered(level, hideout, hideoutLevel, mine)) {
            if (seen.target().convoy() == id) {
                return seen;
            }
        }
        return null;
    }

    /**
     * The same job, aimed at where the convoy is as he sets out.
     *
     * <p>The board keeps the place a convoy was when it was pinned; by the time a thief takes the
     * job it has moved on. His trip is reckoned from where it is now. If it is no longer within
     * reach the job is left as it was - he goes anyway, and finds the road empty.</p>
     */
    public static Job onTheRoad(final ServerLevel level, final Job job, final BlockPos hideout,
                                final int hideoutLevel, final IColony mine) {
        if (job == null || job.target().kind() != TargetKind.CONVOY) {
            return job;
        }
        final Seen now = find(level, hideout, hideoutLevel, mine, job.target().convoy());
        return now == null ? job : new Job(job.target().movedTo(now.target().pos()), job.work());
    }

    /**
     * "a convoy from Ironhold to Oakford" - the sending colony first, then where it is going. A
     * convoy bound for a Waking World kingdom has no colony at the far end, and the war mod does
     * not say which kingdom, so it is going to "a kingdom".
     */
    public static String name(final ServerLevel level, final long from, final long to) {
        return "a convoy from " + colonyName(level, from) + " to "
                + (to == 0L ? "a kingdom" : colonyName(level, to));
    }

    private static String colonyName(final ServerLevel level, final long id) {
        try {
            final IColony colony = IColonyManager.getInstance().getColonyByDimension((int) id, level.dimension());
            if (colony != null && colony.getName() != null && !colony.getName().isEmpty()) {
                return colony.getName();
            }
        } catch (final Throwable ignored) {
            // a colony the manager cannot find is still somebody's
        }
        return "colony " + id;
    }
}
