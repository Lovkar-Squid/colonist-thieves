package me.lovkar.thieves.target;

import net.minecraft.nbt.CompoundTag;

/**
 * One line on the Hideout's board: a place, and what to do when he gets there.
 *
 * <p>Kept apart from {@link Target} on purpose. A target is a fact about the world - this village
 * is here, it is worth this much, it is this jumpy - and it is shared by everything that reads a
 * place. What a thief has been told to do there is an instruction, and it belongs to the board.</p>
 */
public record Job(Target target, Work work) {

    private static final String TAG_WORK = "work";

    public static Job rob(final Target target) {
        return new Job(target, Work.ROB);
    }

    public CompoundTag save() {
        final CompoundTag tag = target.save();
        tag.putString(TAG_WORK, work.id());
        return tag;
    }

    /**
     * Read one back.
     *
     * <p>A board written before there was anything but robbing has no {@code work} tag at all, and
     * loads as a robbery - which is what it was.</p>
     */
    public static Job load(final CompoundTag tag) {
        final Target target = Target.load(tag);
        if (target == null) {
            return null;
        }
        return new Job(target, tag.contains(TAG_WORK) ? Work.byId(tag.getString(TAG_WORK)) : Work.ROB);
    }

    /**
     * "the Village Forest job" / "a look at Village Forest" / "the job on Village Forest" - and a
     * convoy, whose name already reads as a job: "a convoy from Ironhold to Oakford".
     */
    public String describe() {
        if (target.kind() == TargetKind.CONVOY) {
            return target.name();
        }
        return switch (work) {
            case SCOUT -> "a look at " + target.name();
            case SABOTAGE -> "the job on " + target.name();
            default -> "the " + target.name() + " job";
        };
    }
}
