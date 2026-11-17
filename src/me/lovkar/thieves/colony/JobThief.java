package me.lovkar.thieves.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import me.lovkar.thieves.ai.EntityAIWorkThief;
import me.lovkar.thieves.job.Heist;
import me.lovkar.thieves.job.Outcome;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

/**
 * The Thief's job. Remembers the one thing that has to survive a save: whether he is out, where he
 * went, and what he is carrying home.
 */
public class JobThief extends AbstractJob<EntityAIWorkThief, JobThief> {

    private static final String TAG_HEIST = "heist";
    private static final String TAG_TAKE = "take";
    private static final String TAG_OUTCOME = "outcome";

    private Heist heist;
    private Outcome outcome;
    private final Queue<ItemStack> take = new LinkedList<>();
    /** Transient: what he is doing right now, in plain words, for the hut window and other mods. */
    private String statusLine = "";
    /** Transient: he could not get out of the hideout and says so as an interaction. */
    private boolean blocked = false;

    public JobThief(final ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public EntityAIWorkThief generateAI() {
        return new EntityAIWorkThief(this);
    }

    /**
     * The Nether Miner's clothes for now: dark, travelled, and already in every resource pack.
     * A model of the thief's own is phase two - it is a texture job, not a design one.
     */
    @Override
    public @NotNull ResourceLocation getModel() {
        return com.minecolonies.api.client.render.modeltype.ModModelTypes.NETHERWORKER_ID;
    }

    /** Nobody catches a cold in somebody else's warehouse. */
    @Override
    public double getDiseaseModifier() {
        return heist == null ? super.getDiseaseModifier() : 0.0;
    }

    /** Waiting for a mark to be handed in is not idleness worth nagging about. */
    @Override
    public int getIdleSeverity(final boolean isDemand) {
        return isDemand ? super.getIdleSeverity(true) : 4;
    }

    public boolean isAway() {
        return heist != null;
    }

    public Heist getHeist() {
        return heist;
    }

    /**
     * A thief who dies on the way out takes his orders with him - unless we put them back.
     *
     * <p>The board hands a job to exactly one thief and takes it off the list, so a death between
     * the board and the door would quietly lose that mark: the player pinned a place, nobody ever
     * went, and nothing anywhere said why. Both of MineColonies' removal hooks come through here,
     * so whichever one fires, the job goes back up for somebody else.</p>
     */
    private void putTheJobBack() {
        if (heist == null) {
            return;
        }
        if (heist.target().kind() == me.lovkar.thieves.target.TargetKind.CONVOY && outcome != null) {
            // he already met the convoy and took what he took: that robbery happened, and putting
            // the job back would send somebody to rob the same convoy a second time
            me.lovkar.thieves.Thieves.LOGGER.info("[thief] {} did not come back from {} - the road was already met",
                    getCitizen() == null ? "a thief" : getCitizen().getName(), heist.target().name());
            heist = null;
            return;
        }
        try {
            final com.minecolonies.api.colony.buildings.IBuilding home =
                    getCitizen() == null ? null : getCitizen().getWorkBuilding();
            if (home instanceof me.lovkar.thieves.colony.BuildingHideout hideout) {
                hideout.returnJob(new me.lovkar.thieves.target.Job(heist.target(), heist.work()));
                me.lovkar.thieves.Thieves.LOGGER.info(
                        "[thief] {} did not come back - {} goes back on the board",
                        getCitizen().getName(), heist.target().name());
            }
        } catch (final Throwable t) {
            me.lovkar.thieves.Thieves.LOGGER.warn("[thief] could not put the job back: {}", t.toString());
        }
        heist = null;
    }

    @Override
    public void triggerDeathAchievement(final net.minecraft.world.damagesource.DamageSource source,
                                        final com.minecolonies.api.entity.citizen.AbstractEntityCitizen citizen) {
        super.triggerDeathAchievement(source, citizen);
        putTheJobBack();
    }

    @Override
    public void onRemoval() {
        putTheJobBack();
        super.onRemoval();
    }

    public void setHeist(final Heist heist) {
        this.heist = heist;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(final Outcome outcome) {
        this.outcome = outcome;
    }

    public Queue<ItemStack> getTake() {
        return take;
    }

    public void addTake(final Collection<ItemStack> stacks) {
        take.addAll(stacks);
    }

    public String getStatusLine() {
        return statusLine;
    }

    public void setStatusLine(final String line) {
        this.statusLine = line == null ? "" : line;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(final boolean blocked) {
        this.blocked = blocked;
    }

    @Override
    public CompoundTag serializeNBT(final @NotNull HolderLookup.Provider provider) {
        final CompoundTag compound = super.serializeNBT(provider);
        if (heist != null) {
            compound.put(TAG_HEIST, heist.save());
        }
        if (outcome != null) {
            compound.putString(TAG_OUTCOME, outcome.id());
        }
        final ListTag list = new ListTag();
        for (final ItemStack stack : take) {
            list.add(stack.saveOptional(provider));
        }
        compound.put(TAG_TAKE, list);
        return compound;
    }

    @Override
    public void deserializeNBT(final @NotNull HolderLookup.Provider provider, final CompoundTag compound) {
        super.deserializeNBT(provider, compound);
        heist = compound.contains(TAG_HEIST) ? Heist.load(compound.getCompound(TAG_HEIST)) : null;
        outcome = compound.contains(TAG_OUTCOME) ? Outcome.byId(compound.getString(TAG_OUTCOME)) : null;
        take.clear();
        final ListTag list = compound.getList(TAG_TAKE, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            take.add(ItemStack.parseOptional(provider, list.getCompound(i)));
        }
    }
}
