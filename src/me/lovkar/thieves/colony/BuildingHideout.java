package me.lovkar.thieves.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.target.Job;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.Work;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hideout: a back room, a board with marks pinned to it, and one to three people who are out
 * most nights.
 *
 * <p>The building holds the <b>board</b> - the marks that have been handed in and not yet worked.
 * A thief coming on shift takes the top one; a mark is spent when the job is over, however it
 * went. The board is the building's, not the citizen's, so a thief who is caught does not take the
 * colony's plans with him.</p>
 */
public class BuildingHideout extends AbstractBuilding {
    private static final String HUT_NAME = "hideout";
    private static final int MAX_LEVEL = 5;

    private static final String TAG_BOARD = "board";
    private static final String TAG_LEDGER = "ledger";
    private static final String TAG_LAST_JOB = "lastjob";

    /** How many past jobs the hut remembers. Enough to see a pattern, not enough to become a novel. */
    private static final int LEDGER_LENGTH = 20;

    /** How the colony wants its thieves to work. */
    public static final ISettingKey<StringSetting> CAUTION =
            new SettingKey<>(StringSetting.class, net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath(Thieves.MODID, "caution"));

    /** Take every job on the board. */
    public static final String CAUTION_BOLD = "com.colonist_thieves.setting.caution.bold";
    /** Skip a mark whose place is too hot or too strong for the thief on shift. */
    public static final String CAUTION_CAREFUL = "com.colonist_thieves.setting.caution.careful";
    /** Only rob places nobody can put a name to: camps, hoards, ruins - never a neighbour. */
    public static final String CAUTION_QUIET = "com.colonist_thieves.setting.caution.quiet";

    /**
     * Whether the thief waits to be told, or goes looking.
     *
     * <p>The same shape as a guard tower's patrol mode, and for the same reason: some players want
     * to aim every job themselves, and some want to hire a thief and forget about him.</p>
     */
    public static final ISettingKey<StringSetting> WORK_MODE =
            new SettingKey<>(StringSetting.class, net.minecraft.resources.ResourceLocation
                    .fromNamespaceAndPath(Thieves.MODID, "workmode"));

    /** Only the marks you pin. Nothing happens unless you say so. */
    public static final String MODE_MANUAL = "com.colonist_thieves.setting.mode.manual";
    /** Pinned marks first, and when the board is empty he finds his own work. */
    public static final String MODE_AUTO = "com.colonist_thieves.setting.mode.auto";

    /** One in-game day of rest between jobs he chose himself. An order on the board ignores it. */
    public static final long REST_TICKS = 24000L;

    /** One night's work, as the hut remembers it. */
    public record Entry(String place, String kind, long day, String outcome, int items, String work) {

        /** A night that was written before there was anything to do but rob. */
        public static Entry robbery(final String place, final String kind, final long day,
                                    final String outcome, final int items) {
            return new Entry(place, kind, day, outcome, items,
                    me.lovkar.thieves.target.Work.ROB.id());
        }

        CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("place", place);
            tag.putString("kind", kind);
            tag.putLong("day", day);
            tag.putString("outcome", outcome);
            tag.putInt("items", items);
            tag.putString("work", work);
            return tag;
        }

        static Entry load(final CompoundTag tag) {
            return new Entry(tag.getString("place"), tag.getString("kind"),
                    tag.getLong("day"), tag.getString("outcome"), tag.getInt("items"),
                    tag.contains("work") ? tag.getString("work")
                            : me.lovkar.thieves.target.Work.ROB.id());
        }

        /** One line for the log: "day 14  Village Forest (village)  rob  clean  13 item(s)". */
        public String line() {
            return "day " + day + "  " + place + " (" + kind + ")  " + work + "  " + outcome
                    + "  " + items + " item(s)";
        }
    }

    private final List<Job> board = new ArrayList<>();
    private final List<Entry> ledger = new ArrayList<>();
    private long lastJobAt = Long.MIN_VALUE;

    public BuildingHideout(final IColony colony, final BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public @NotNull String getSchematicName() {
        return HUT_NAME;
    }

    @Override
    public int getMaxBuildingLevel() {
        return MAX_LEVEL;
    }

    /** One thief at first, two once the back room is dug out, three in a proper hideout. */
    public static int crewSize(final IBuilding building) {
        final int level = building.getBuildingLevel();
        if (level >= 5) {
            return 3;
        }
        if (level >= 3) {
            return 2;
        }
        return level >= 1 ? 1 : 0;
    }

    /** The jobs pinned up and waiting, oldest first. */
    public List<Job> board() {
        return board;
    }

    public void pin(final Target target) {
        pin(new Job(target, Work.ROB));
    }

    public void pin(final Job job) {
        board.add(job);
        markDirty();
    }

    /** Take the next job a thief of this standing is willing to do, or null. */
    public Job takeJob(final java.util.function.Predicate<Job> willing) {
        for (int i = 0; i < board.size(); i++) {
            final Job job = board.get(i);
            if (willing.test(job)) {
                board.remove(i);
                markDirty();
                return job;
            }
        }
        return null;
    }

    /** Put a job back - the thief never left, so it was never spent. */
    public void returnJob(final Job job) {
        if (job != null) {
            board.add(0, job);
            markDirty();
        }
    }

    /** The hut's own record of what its thieves have done, newest first. */
    public List<Entry> ledger() {
        return ledger;
    }

    /** Write one night into the log. */
    public void record(final Entry entry) {
        ledger.add(0, entry);
        while (ledger.size() > LEDGER_LENGTH) {
            ledger.remove(ledger.size() - 1);
        }
        markDirty();
    }

    /** Remember that somebody went out, for the rest between self-chosen jobs. */
    public void wentOut(final long gameTime) {
        lastJobAt = gameTime;
        markDirty();
    }

    /** True if enough time has passed for a thief to go looking for work on his own. */
    public boolean rested(final long gameTime) {
        return lastJobAt == Long.MIN_VALUE || gameTime - lastJobAt >= REST_TICKS;
    }

    public String mode() {
        try {
            return getSetting(WORK_MODE).getValue();
        } catch (final Throwable t) {
            return MODE_MANUAL;
        }
    }

    public boolean auto() {
        return MODE_AUTO.equals(mode());
    }

    public String caution() {
        try {
            return getSetting(CAUTION).getValue();
        } catch (final Throwable t) {
            return CAUTION_CAREFUL;
        }
    }

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag compound) {
        super.deserializeNBT(provider, compound);
        board.clear();
        final ListTag list = compound.getList(TAG_BOARD, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            // a board written before there were jobs other than robbing reads back as robberies,
            // which is what those lines meant
            final Job job = Job.load(list.getCompound(i));
            if (job != null) {
                board.add(job);
            }
        }
        ledger.clear();
        final ListTag past = compound.getList(TAG_LEDGER, Tag.TAG_COMPOUND);
        for (int i = 0; i < past.size(); i++) {
            ledger.add(Entry.load(past.getCompound(i)));
        }
        lastJobAt = compound.contains(TAG_LAST_JOB) ? compound.getLong(TAG_LAST_JOB) : Long.MIN_VALUE;
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag compound = super.serializeNBT(provider);
        final ListTag list = new ListTag();
        for (final Job job : board) {
            list.add(job.save());
        }
        compound.put(TAG_BOARD, list);
        final ListTag past = new ListTag();
        for (final Entry e : ledger) {
            past.add(e.save());
        }
        compound.put(TAG_LEDGER, past);
        if (lastJobAt != Long.MIN_VALUE) {
            compound.putLong(TAG_LAST_JOB, lastJobAt);
        }
        return compound;
    }
}
