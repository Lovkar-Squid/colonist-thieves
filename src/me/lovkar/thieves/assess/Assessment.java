package me.lovkar.thieves.assess;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import me.lovkar.thieves.compat.Kingdoms;
import me.lovkar.thieves.compat.War;
import me.lovkar.thieves.target.Heat;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.TargetKind;
import net.minecraft.server.level.ServerLevel;

/**
 * What a place is worth robbing, and what it costs to try - one honest reading of somewhere,
 * shared by both mods.
 *
 * <p>This is the class Lovkar's third rule is about: <i>measure a colony by what is actually
 * built</i>. Nothing here is a difficulty slider. Every number comes from the world - guards that
 * are really hired, buildings that really stand at the level they really are, wall that is really
 * there, and MineColonies' own idea of how big a horde this colony deserves. The thief reads
 * {@link #vigilance()} and {@link #wealth()}; the war mod reads {@link #strength()}; a camp, a
 * village or a Waking World kingdom answers the same shape of question through
 * {@link Profiles}.</p>
 *
 * <p>Computing it walks every building in a colony, so it is never done in a loop: ask for it
 * once at the start of a job and hold on to it.</p>
 */
public record Assessment(String name, TargetKind kind, int garrisonHome, int garrisonAway, int military,
                         int walls, int raidLevel, int wealth, int watchfulness, int softness) {

    /** Nothing is there, or nothing can be read. Everything zero, and every roll against it succeeds. */
    public static final Assessment NOTHING = new Assessment("nowhere", TargetKind.CAMP, 0, 0, 0, 0, 0, 0, 0, 0);

    /**
     * What an attacker has to beat. Guards first, because a guard is the only thing that actually
     * stops anybody; walls and engines buy the guards time; the raid level is MineColonies' own
     * measure and keeps the whole thing anchored to what the game already believes.
     */
    public int strength() {
        return garrisonHome * 4 + garrisonAway * 2 + military * 2 + walls + raidLevel / 10;
    }

    /**
     * What a thief has to get past. A wall counts for half of what it counts for in a battle - it
     * is an obstacle at night rather than a line to hold - and a garrison that has marched off to
     * somebody else's war is not watching this gate at all.
     *
     * <p><b>The garrison is counted once.</b> It used to be counted twice - every guard as a body
     * here, and then every guard <i>post</i> again through {@link #watchfulness}, which is why a
     * real colony read 167 and no thief who ever lived could reach it. Watchfulness is now how
     * <i>awake</i> the posts are rather than how many there are (see {@link #of(IColony)}), so the
     * two terms finally mean different things: bodies, and whether those bodies are looking.</p>
     */
    public int vigilance() {
        return garrisonHome * 3 + walls / 2 + watchfulness + Math.max(0, 10 - softness);
    }

    /**
     * The same reading, as it looks to somebody who has had the place scouted and, perhaps, has
     * had something of theirs broken.
     *
     * <p>This is where the two mods pay each other. A thief who has walked the rounds knows when
     * the gate is unwatched, and a colony whose granary burned last night is not at its best.
     * Neither ever makes a place free: both are a fraction off the guards and the watch, and the
     * walls stay where they are because nobody sabotages a wall by sneaking past it.</p>
     *
     * @param known   0..1 of the vigilance that scouting takes off
     * @param shaken  0..1 that sabotage takes off on top
     */
    public Assessment softenedBy(final float known, final float shaken) {
        final float off = Math.max(0f, Math.min(0.6f, known + shaken));
        if (off <= 0f) {
            return this;
        }
        return new Assessment(name, kind,
                Math.round(garrisonHome * (1f - off)), garrisonAway, military,
                walls, raidLevel, wealth,
                Math.round(watchfulness * (1f - off)), softness);
    }

    /** Everything the place has that a thief could carry: 0 for a hovel, 100 for a treasury. */
    public int wealth() {
        return wealth;
    }

    /** One line for a log or a job board. */
    public String line() {
        return name + ": " + garrisonHome + " guard(s) home" + (garrisonAway > 0 ? " (" + garrisonAway + " away)" : "")
                + ", military " + military + ", walls " + walls + ", raid level " + raidLevel
                + ", wealth " + wealth + ", watchfulness " + watchfulness;
    }

    /** Read a real colony. */
    public static Assessment of(final IColony colony) {
        if (colony == null) {
            return NOTHING;
        }
        int garrisonHome = 0;
        int garrisonAway = 0;
        int military = 0;
        int wealth = 0;
        int softness = 0;
        int watchfulness = 0;
        int posts = 0;
        try {
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
                final int level = building.getBuildingLevel();
                if (level <= 0) {
                    continue;
                }
                // one odd building must never zero the whole reading: a Gatehouse has no patrol
                // setting, a modded guard building may have none of what we ask for, and a colony
                // that silently assessed as empty is worse than one that skipped a hut
                try {
                    if (building instanceof AbstractBuildingGuards guards) {
                        military += level;
                        for (final ICitizenData citizen : guards.getAllAssignedCitizen()) {
                            if (War.isAway(citizen)) {
                                garrisonAway++;
                            } else {
                                garrisonHome++;
                            }
                        }
                        watchfulness += watchfulnessOf(guards);
                        posts++;
                    } else if (building instanceof IWareHouse) {
                        wealth += level * 6;
                    } else {
                        wealth += level;
                        softness += 1;
                    }
                } catch (final Throwable t) {
                    if (!complained) {
                        complained = true;
                        me.lovkar.thieves.Thieves.LOGGER.warn(
                                "[assess] skipping {} at {}: {}", building.getBuildingType().getRegistryName(),
                                building.getPosition().toShortString(), t.toString());
                    }
                }
            }
            wealth = Math.min(100, wealth + colony.getCitizenManager().getCurrentCitizenCount());
            softness = Math.min(100, softness);
        } catch (final Throwable t) {
            // a MineColonies this build does not fit: say so loudly rather than quietly reading zero
            me.lovkar.thieves.Thieves.LOGGER.warn("[assess] cannot read {}: {}", colony.getName(), t.toString());
            return new Assessment(colony.getName(), TargetKind.COLONY, 0, 0, 0, 0, 0, 20, 0, 0);
        }
        final int raidLevel = safeRaidLevel(colony);
        final int walls = Walls.of(colony);
        // how awake the posts are, not how many of them there are: the guards themselves are
        // already counted as bodies, and counting the post again made a well-run colony
        // arithmetically unrobbable. Heat sits on top, because a jumpy town really is jumpier.
        watchfulness = posts == 0 ? 0 : Math.round(watchfulness / (float) posts);
        watchfulness = Math.min(100, watchfulness + Heat.of(colony.getWorld(), "colony/" + colony.getID()));
        return new Assessment(colony.getName(), TargetKind.COLONY, garrisonHome, garrisonAway, military,
                walls, raidLevel, wealth, watchfulness, softness);
    }

    /** Said once per run, not once per building, so a bad hut does not fill the log. */
    private static volatile boolean complained = false;

    /**
     * How awake a guard post is.
     *
     * <p>Each question separately, because they are not all answerable by every guard building:
     * a Gatehouse has no patrol setting at all, and asking it for one is how the whole assessment
     * used to come back as zeros on a real colony.</p>
     */
    private static int watchfulnessOf(final AbstractBuildingGuards guards) {
        int watch = 2;
        try {
            // a tower whose men actually walk is worth more than one whose men stand in the doorway
            watch = guards.shallPatrolManually() ? 3 : 2;
        } catch (final Throwable ignored) {
            // no patrol setting on this kind of guard building
        }
        try {
            watch += Math.min(4, guards.getBonusVision());
        } catch (final Throwable ignored) {
            // no vision bonus on this kind either
        }
        return watch;
    }

    private static int safeRaidLevel(final IColony colony) {
        try {
            return colony.getRaiderManager().getColonyRaidLevel();
        } catch (final Throwable t) {
            return 0;
        }
    }

    /** Read anything a mark can point at. */
    public static Assessment of(final ServerLevel level, final Target target) {
        if (target == null) {
            return NOTHING;
        }
        if (target.kind() == TargetKind.COLONY) {
            final com.minecolonies.api.colony.IColony colony =
                    com.minecolonies.api.colony.IColonyManager.getInstance()
                            .getColonyByDimension(target.colonyId(), target.dimension());
            return colony == null ? Profiles.unreadableColony(target) : of(colony);
        }
        if (target.kind() == TargetKind.KINGDOM) {
            return Profiles.kingdom(level, target, Kingdoms.at(level, target.pos()));
        }
        return Profiles.structure(level, target);
    }
}
