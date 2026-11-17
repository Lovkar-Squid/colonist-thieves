package me.lovkar.thieves.job;

import com.minecolonies.api.colony.IColony;
import me.lovkar.thieves.assess.Assessment;
import me.lovkar.thieves.compat.War;
import me.lovkar.thieves.intel.Intel;
import me.lovkar.thieves.target.Convoys;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.TargetKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Meeting a convoy on the road - the one job that is decided out there rather than at home.
 *
 * <p>A camp is still where it was when he comes back; a convoy is not. So the moment that matters
 * is when he reaches the road ({@link Heist#reachesAt}): the war mod is asked whether that convoy is
 * still on its way and still within the Hideout's reach. If it has arrived, or gone on past, the
 * road was empty and nothing happened. If it is there, it is the ordinary roll - the same
 * {@link Heist#roll}, clean, seen or caught - with the escort as the whole of the defence.</p>
 *
 * <p>The goods come off the convoy only <b>after</b> the roll and only if he kept his loot, through
 * the war mod's own {@code robConvoy}, which never lets more than half of what a convoy set out
 * with go. A caught thief takes nothing, and the convoy loses nothing. Nothing in here touches
 * standing: that happens when he is home, in the same place as every other job, and only if he was
 * caught or seen.</p>
 */
public final class Interception {
    private Interception() {
    }

    /** What happened on the road: the outcome, what came off the convoy, and what he was up against. */
    public record Result(Outcome outcome, List<ItemStack> take, Assessment there) {
        static Result empty() {
            return new Result(Outcome.EMPTY, new ArrayList<>(), Assessment.NOTHING);
        }
    }

    /**
     * He has reached the road. Is the convoy there, and how does it go?
     *
     * @param hideout       where the Hideout stands - the road is watched from there
     * @param hideoutLevel  its level now, for how far it watches
     * @param mine          the thief's own colony; its own convoys are never his to rob
     * @param learnedRoll   what the colony's research adds to his side of the roll
     * @param learnedLoot   what it adds to how much he carries off
     */
    public static Result intercept(final ServerLevel level, final Heist heist, final BlockPos hideout,
                                   final int hideoutLevel, final IColony mine, final int learnedRoll,
                                   final int learnedLoot, final RandomSource random) {
        final Target target = heist.target();
        if (target.kind() != TargetKind.CONVOY || target.convoy() == 0L) {
            return Result.empty();
        }
        // still on the road, still within reach, still not ours: otherwise the road was empty
        final Convoys.Seen there = Convoys.find(level, hideout, hideoutLevel, mine, target.convoy());
        if (there == null || War.convoyEscort(level, target.convoy()) < 0) {
            return Result.empty();
        }
        final Assessment reading = Assessment.of(level, target)
                .softenedBy(Intel.known(level, target.key()), Intel.shaken(level, target.key()));
        final Outcome outcome = heist.roll(reading, random, learnedRoll);
        final List<ItemStack> take = new ArrayList<>();
        if (outcome.keepsLoot()) {
            take.addAll(War.robConvoy(level, target.convoy(),
                    stacksFor(outcome, heist.skill(), heist.hideoutLevel() + learnedLoot)));
        }
        return new Result(outcome, take, reading);
    }

    /**
     * How many stacks he may try to carry off: two, plus one for every two Hideout levels, plus one
     * for every eight points of skill - half that, never none, if he was seen doing it, which is
     * always less than a clean job would have taken. The war mod caps it again at half of what the
     * convoy set out with.
     */
    public static int stacksFor(final Outcome outcome, final int skill, final int hideoutLevel) {
        final int clean = 2 + Math.max(0, hideoutLevel) / 2 + Math.max(0, skill) / 8;
        if (outcome == Outcome.CLEAN) {
            return clean;
        }
        if (outcome == Outcome.SPOTTED) {
            return Math.max(1, clean / 2);
        }
        return 0;
    }
}
