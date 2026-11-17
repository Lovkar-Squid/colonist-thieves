package me.lovkar.thieves.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.assess.Assessment;
import me.lovkar.thieves.compat.Kingdoms;
import me.lovkar.thieves.target.Heat;
import me.lovkar.thieves.target.Scout;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.TargetKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Hideout knows about the neighbourhood - and the reason you never have to walk there.
 *
 * <p>A Mark is a fine way to aim a thief at a place you happen to be standing in. It is a poor way
 * to aim him at the town across the valley, which is the thing a player actually wants to do, so
 * the hut keeps its own list: every colony it can see, every kingdom, and the nearest camp,
 * village and hoard the world will admit to. Pick a line, press the button, he goes.</p>
 *
 * <p>The list is <b>not</b> rebuilt on every sync. Asking the world where the nearest structures
 * are is the {@code /locate} search and costs what that costs, so it happens when somebody presses
 * "Look around" and no more often than once every half minute. Colonies and kingdoms are cheap
 * enough to re-read each time and are added live, which is also why a colony that grows shows its
 * new strength without anybody pressing anything.</p>
 *
 * <p>With Colonies at War 0.3.0 the list has a third live part: the convoys on the road within the
 * Hideout's reach that its own colony did not send. They are read every time too - a convoy is
 * somewhere else every second - and never kept in the saved list, because by the next look it has
 * arrived.</p>
 */
public class SightingsModule extends AbstractBuildingModule implements IPersistentModule {

    private static final String TAG_SEEN = "seen";
    private static final String TAG_TARGET = "target";
    private static final String TAG_STRENGTH = "strength";
    private static final String TAG_WHEN = "when";

    /** Nobody may set the world searching more often than this. */
    public static final long LOOK_EVERY = 600L;

    /** One place the hut has heard of, with what it was worth defending when we last looked. */
    public record Sighting(Target target, int strength) {
    }

    private final List<Sighting> seen = new ArrayList<>();
    private long lookedAt = Long.MIN_VALUE;

    // ------------------------------------------------------------------ looking

    /**
     * Send somebody up the hill to look around.
     *
     * @return false if the last look was too recent to bother the world again
     */
    public boolean lookAround(final ServerLevel level, final BlockPos from) {
        if (lookedAt != Long.MIN_VALUE && level.getGameTime() - lookedAt < LOOK_EVERY) {
            return false;
        }
        lookedAt = level.getGameTime();
        seen.clear();
        for (final TargetKind kind : new TargetKind[]{TargetKind.CAMP, TargetKind.VILLAGE, TargetKind.HOARD}) {
            final Target found = Scout.nearestOf(level, from, kind);
            if (found != null) {
                seen.add(new Sighting(found, strengthOf(level, found)));
            }
        }
        // colonies and kingdoms need no finding, but they do need reading - and reading a place is
        // the expensive half, so it happens here rather than on every sync of the building
        final List<Sighting> live = new ArrayList<>();
        addColonies(level, building == null ? null : building.getColony(), live);
        addKingdoms(level, live);
        for (final Sighting sighting : live) {
            seen.add(new Sighting(sighting.target(), strengthOf(level, sighting.target())));
        }
        markDirty();
        Thieves.LOGGER.info("[hideout] looked around from {} and found {} place(s)",
                from.toShortString(), seen.size());
        return true;
    }

    /** How well defended a place is, as one number - the same reading the thief rolls against. */
    private static int strengthOf(final ServerLevel level, final Target target) {
        try {
            return Math.round(Assessment.of(level, target).vigilance());
        } catch (final Throwable t) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ the whole list

    /**
     * Everything worth showing: what was found by looking, plus the colonies, kingdoms and
     * convoys that need no looking for.
     */
    public List<Sighting> everything(final ServerLevel level, final IColony mine) {
        final List<Sighting> out = new ArrayList<>(seen);
        final List<Sighting> live = new ArrayList<>();
        addColonies(level, mine, live);
        addKingdoms(level, live);
        addConvoys(level, mine, live);
        // anything the last look already read keeps its reading; anything new since shows up as
        // it came - unread for a colony or a kingdom, which is the honest answer and costs
        // nothing, and already read for a convoy, whose escort came with it
        for (final Sighting sighting : live) {
            if (!known(out, sighting.target())) {
                out.add(sighting);
            }
        }
        return out;
    }

    /** A place nobody has been to look at yet. */
    public static final int UNREAD = -1;

    private static boolean known(final List<Sighting> out, final Target target) {
        for (final Sighting sighting : out) {
            if (sighting.target().samePlace(target)) {
                return true;
            }
        }
        return false;
    }

    private static void addColonies(final ServerLevel level, final IColony mine, final List<Sighting> out) {
        try {
            for (final IColony other : IColonyManager.getInstance().getColonies(level)) {
                if (mine != null && other.getID() == mine.getID()) {
                    continue;                     // robbing yourself is not a strategy
                }
                out.add(new Sighting(
                        Target.ofColony(level.dimension(), other.getCenter(), other.getName(), other.getID()),
                        UNREAD));
            }
        } catch (final Throwable t) {
            Thieves.LOGGER.warn("[hideout] could not list the neighbouring colonies: {}", t.toString());
        }
    }

    /**
     * The convoys passing within this Hideout's reach, other than its own colony's. Reading one
     * costs nothing - the escort is in the war mod's answer already - so a convoy shows what it is
     * worth defending straight away rather than waiting for somebody to look around.
     */
    private void addConvoys(final ServerLevel level, final IColony mine, final List<Sighting> out) {
        if (building == null) {
            return;
        }
        try {
            for (final me.lovkar.thieves.target.Convoys.Seen convoy : me.lovkar.thieves.target.Convoys.offered(
                    level, building.getPosition(), building.getBuildingLevel(), mine)) {
                out.add(new Sighting(convoy.target(), Math.round(me.lovkar.thieves.assess.Profiles.convoy(
                        convoy.target(), convoy.escort(), convoy.stacks(),
                        Heat.of(level, convoy.target().key())).vigilance())));
            }
        } catch (final Throwable t) {
            Thieves.LOGGER.warn("[hideout] could not look at the road: {}", t.toString());
        }
    }

    private static void addKingdoms(final ServerLevel level, final List<Sighting> out) {
        if (!Kingdoms.present()) {
            return;
        }
        try {
            for (final Object kingdom : Kingdoms.all(level)) {
                final BlockPos centre = Kingdoms.centre(kingdom);
                if (centre == null) {
                    continue;
                }
                out.add(new Sighting(
                        Target.ofKingdom(level.dimension(), centre, Kingdoms.name(kingdom)), UNREAD));
            }
        } catch (final Throwable t) {
            Thieves.LOGGER.warn("[hideout] could not list the kingdoms: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ to the client

    @Override
    public void serializeToView(final @NotNull RegistryFriendlyByteBuf buf) {
        final BuildingHideout hideout = building instanceof BuildingHideout h ? h : null;
        if (hideout == null || !(hideout.getColony().getWorld() instanceof ServerLevel level)) {
            buf.writeVarInt(0);
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            return;
        }
        final List<Sighting> all = everything(level, hideout.getColony());
        buf.writeVarInt(all.size());
        for (final Sighting sighting : all) {
            final Target target = sighting.target();
            buf.writeUtf(target.name() == null ? "" : target.name());
            buf.writeUtf(target.kind().id());
            buf.writeBlockPos(target.pos());
            buf.writeInt(sighting.strength());
            buf.writeVarInt(Heat.of(level, target.key()));
            buf.writeBoolean(alreadyPinned(hideout, target));
            // 0..100 of how well this colony knows the place, so a player can see his scouting
            buf.writeVarInt(Math.round(100f
                    * (me.lovkar.thieves.intel.Intel.known(level, target.key())
                    / me.lovkar.thieves.intel.Intel.KNOWN)));
            // the war mod's id for a convoy, which is what the Send button has to name; 0 otherwise
            buf.writeLong(target.convoy());
        }
        buf.writeBoolean(lookedAt != Long.MIN_VALUE && level.getGameTime() - lookedAt < LOOK_EVERY);
        // whether this colony can send a thief to do anything other than rob
        buf.writeBoolean(me.lovkar.thieves.compat.War.hasWarRoom(hideout.getColony()));
    }

    private static boolean alreadyPinned(final BuildingHideout hideout, final Target target) {
        for (final me.lovkar.thieves.target.Job pinned : hideout.board()) {
            if (pinned.target().samePlace(target)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag tag) {
        seen.clear();
        final ListTag list = tag.getList(TAG_SEEN, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag one = list.getCompound(i);
            final Target target = Target.load(one.getCompound(TAG_TARGET));
            if (target != null) {
                seen.add(new Sighting(target, one.getInt(TAG_STRENGTH)));
            }
        }
        lookedAt = tag.contains(TAG_WHEN) ? tag.getLong(TAG_WHEN) : Long.MIN_VALUE;
    }

    @Override
    public void serializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag tag) {
        final ListTag list = new ListTag();
        for (final Sighting sighting : seen) {
            final CompoundTag one = new CompoundTag();
            one.put(TAG_TARGET, sighting.target().save());
            one.putInt(TAG_STRENGTH, sighting.strength());
            list.add(one);
        }
        tag.put(TAG_SEEN, list);
        if (lookedAt != Long.MIN_VALUE) {
            tag.putLong(TAG_WHEN, lookedAt);
        }
    }
}
