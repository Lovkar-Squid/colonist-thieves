package me.lovkar.thieves.job;

import me.lovkar.thieves.assess.Assessment;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.TargetKind;
import me.lovkar.thieves.target.Work;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * One job: where the thief went, when he is due back, and how well he was set up for it.
 *
 * <p>He does not walk there. The Nether Worker showed the way years ago and everybody who plays
 * MineColonies already understands it: the citizen leaves the world, a clock runs, and he comes
 * back with something or he does not. Pathing a colonist five hundred blocks across loaded chunks
 * to a barbarian camp would be slower, less reliable and much less fun to watch.</p>
 */
public record Heist(Target target, Work work, long leftAt, long dueAt,
                    int skill, int hideoutLevel) {

    private static final String TAG_TARGET = "target";
    private static final String TAG_WORK = "work";
    private static final String TAG_LEFT = "left";
    private static final String TAG_DUE = "due";
    private static final String TAG_SKILL = "skill";
    private static final String TAG_LEVEL = "level";

    /** A minute away for every two hundred blocks, never under three minutes, never over twenty. */
    public static long travelTicks(final BlockPos from, final BlockPos to) {
        final double blocks = Math.sqrt(from.distSqr(to));
        final long minutes = Math.round(blocks / 200.0);
        return Math.max(3, Math.min(20, minutes)) * 1200L;
    }

    /**
     * Out to the road, one way: a minute for every two hundred blocks, the pace of any job, but
     * with no three-minute floor - a convoy is met near home or not at all, and it will not wait.
     * Never under ten seconds; {@link #begin} still makes the whole trip at least a minute.
     */
    public static long roadTicks(final BlockPos from, final BlockPos to) {
        final double blocks = Math.sqrt(from.distSqr(to));
        return Math.max(ROAD_LEAST, Math.round(blocks / 200.0 * 1200.0));
    }

    /** The shortest walk out to a road there is, in ticks. */
    private static final long ROAD_LEAST = 200L;

    public static Heist begin(final Target target, final BlockPos from, final long now, final int skill, final int level) {
        return begin(new me.lovkar.thieves.target.Job(target, me.lovkar.thieves.target.Work.ROB),
                from, now, skill, level, 0f);
    }

    /**
     * @param travelCut 0..1 of the travel time the colony's research saves him
     */
    public static Heist begin(final me.lovkar.thieves.target.Job job, final BlockPos from, final long now,
                              final int skill, final int level, final float travelCut) {
        // a convoy is met on the road where it is as he sets out (see Convoys.onTheRoad); anything
        // else is a place, and places keep the old minutes
        final long there = Math.round((job.target().kind() == TargetKind.CONVOY
                ? roadTicks(from, job.target().pos())
                : travelTicks(from, job.target().pos())) * (1f - travelCut));
        // a look is a quicker errand than a robbery and a sabotage is a slower one: he has to
        // wait for the right moment, and then he has to get away from what he has just done
        final float pace = switch (job.work()) {
            case SCOUT -> 0.75f;
            case SABOTAGE -> 1.2f;
            default -> 1.0f;
        };
        final long away = Math.max(1200L, Math.round(there * 2 * pace));
        return new Heist(job.target(), job.work(), now, now + away, skill, level);
    }

    public boolean due(final long now) {
        return now >= dueAt;
    }

    /**
     * When he gets there - half way through the trip. A convoy job is decided at this moment, not
     * when he is home again: whether the convoy is still on the road within reach is a question
     * about when he reaches the road.
     */
    public long reachesAt() {
        return leftAt + (dueAt - leftAt) / 2;
    }

    /** True once he has reached the road (or the place). */
    public boolean there(final long now) {
        return now >= reachesAt();
    }

    /** 0..1 - how much of the trip is behind him, for the Hideout's window. */
    public float progress(final long now) {
        final long span = Math.max(1L, dueAt - leftAt);
        return Math.max(0f, Math.min(1f, (now - leftAt) / (float) span));
    }

    /**
     * The roll, and the whole of the mod's difficulty in one line.
     *
     * <p>What the thief brings: his own skill, the Hideout he was trained in, and luck. What the
     * place brings: {@link Assessment#vigilance()}, scaled by how hard a place of that kind is to
     * begin with. A comfortable margin is a clean job; a thin one means he was seen; a bad one
     * means they have him.</p>
     */
    public Outcome roll(final Assessment against, final RandomSource random) {
        return roll(against, random, 0);
    }

    /**
     * @param learned what the colony's research adds to the thief's side
     */
    public Outcome roll(final Assessment against, final RandomSource random, final int learned) {
        final int mine = brings(skill, hideoutLevel, learned);
        final int theirs = demands(against, target.kind(), work);
        final int swing = swing(mine, theirs);
        final int margin = mine - theirs + random.nextInt(swing + 1) - swing / 2;
        if (margin >= Math.round(swing * CLEAN_AT)) {
            return Outcome.CLEAN;
        }
        if (margin > -Math.round(swing * CAUGHT_AT)) {
            return Outcome.SPOTTED;
        }
        return Outcome.CAUGHT;
    }

    // ------------------------------------------------------------------ the two sides, and luck

    /** How far above the night's own swing a clean job sits. */
    private static final float CLEAN_AT = 0.25f;
    /** How far below it they have him. */
    private static final float CAUGHT_AT = 0.6f;
    /** How much of a night is luck - as a share of what both sides brought to it. */
    private static final float LUCK = 0.35f;
    /** The smallest a night's luck is ever worth, so that a trivial job is still a job. */
    private static final int LEAST_LUCK = 24;

    /** What the thief brings, before luck: himself, the hut that trained him, what it has learned. */
    public static int brings(final int skill, final int hideoutLevel, final int learned) {
        return skill * 2 + hideoutLevel * 6 + learned;
    }

    /**
     * What the place brings, before luck.
     *
     * <p>Its watchfulness, scaled by how hard a place of that kind is to begin with and then by
     * what he was sent there to do: looking is easier than taking, and breaking something has to
     * be done where somebody would notice.</p>
     */
    public static int demands(final Assessment against, final TargetKind kind, final Work work) {
        return Math.round(against.vigilance() * kind.difficulty() * work.difficulty());
    }

    /**
     * How much of the night is luck.
     *
     * <p>It grows with the size of the job, and that is the whole of the difficulty curve. With a
     * fixed swing the mod is a step: a thief who can do a place can always do it and one who
     * cannot never can, every hut level flips a row of targets from impossible to free, and
     * nothing a scout brings back could ever change an outcome. Scaled, a big operation has more
     * that can go either way on both sides, so the best thief in the world is still gambling when
     * he walks into a fortress - and a quarter off its watch is what turns that gamble.</p>
     */
    public static int swing(final int mine, final int theirs) {
        return Math.max(LEAST_LUCK, Math.round((mine + theirs) * LUCK));
    }

    /**
     * Roughly how often this job comes off clean, 0..1, without rolling for it.
     *
     * <p>For the Hideout's standing orders, which have to decide whether a thief is willing before
     * anybody rolls anything. It reads the same three numbers the roll does, so "careful" means
     * the same thing the outcome will.</p>
     */
    public static float prospect(final int mine, final int theirs) {
        final int swing = swing(mine, theirs);
        final int needed = Math.round(swing * CLEAN_AT) - (mine - theirs);
        final int best = swing - swing / 2;             // the luckiest draw there is
        final int lucky = best - Math.max(needed, -swing / 2) + 1;
        return Math.max(0f, Math.min(1f, lucky / (float) (swing + 1)));
    }

    /**
     * The odds of each outcome, 0..1 - clean, seen, caught - by counting every draw of luck the
     * roll can make. {@link #prospect} is the first of the three; the third is what a player means
     * by "how likely is he to be caught", and it is what a bigger escort makes worse.
     */
    public static float[] chances(final int mine, final int theirs) {
        final int swing = swing(mine, theirs);
        int clean = 0;
        int caught = 0;
        for (int luck = 0; luck <= swing; luck++) {
            final int margin = mine - theirs + luck - swing / 2;
            if (margin >= Math.round(swing * CLEAN_AT)) {
                clean++;
            } else if (margin <= -Math.round(swing * CAUGHT_AT)) {
                caught++;
            }
        }
        final float draws = swing + 1;
        return new float[]{clean / draws, (draws - clean - caught) / draws, caught / draws};
    }

    /** How good the take is, 0..1, from the same margin the outcome came from. */
    public float quality(final Assessment against, final Outcome outcome) {
        if (!outcome.keepsLoot() || !work.loot()) {
            return 0f;
        }
        final float base = outcome == Outcome.CLEAN ? 0.75f : 0.45f;
        return Math.max(0.1f, Math.min(1f, base * (0.4f + against.wealth() / 100f)));
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.put(TAG_TARGET, target.save());
        tag.putString(TAG_WORK, work.id());
        tag.putLong(TAG_LEFT, leftAt);
        tag.putLong(TAG_DUE, dueAt);
        tag.putInt(TAG_SKILL, skill);
        tag.putInt(TAG_LEVEL, hideoutLevel);
        return tag;
    }

    public static Heist load(final CompoundTag tag) {
        final Target target = Target.load(tag.getCompound(TAG_TARGET));
        if (target == null) {
            return null;
        }
        // a heist saved before there was anything but robbing was a robbery
        final me.lovkar.thieves.target.Work work = tag.contains(TAG_WORK)
                ? me.lovkar.thieves.target.Work.byId(tag.getString(TAG_WORK))
                : me.lovkar.thieves.target.Work.ROB;
        return new Heist(target, work, tag.getLong(TAG_LEFT), tag.getLong(TAG_DUE),
                tag.getInt(TAG_SKILL), tag.getInt(TAG_LEVEL));
    }
}
