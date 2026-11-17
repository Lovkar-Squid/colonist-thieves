package me.lovkar.thieves.ai;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.managers.interfaces.IRaiderManager;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.util.TeleportHelper;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.ThievesConfig;
import me.lovkar.thieves.assess.Assessment;
import me.lovkar.thieves.colony.BuildingHideout;
import me.lovkar.thieves.colony.ThievesResearch;
import me.lovkar.thieves.colony.JobThief;
import me.lovkar.thieves.compat.War;
import me.lovkar.thieves.job.Heist;
import me.lovkar.thieves.job.Interception;
import me.lovkar.thieves.job.Outcome;
import me.lovkar.thieves.loot.Take;
import me.lovkar.thieves.target.Heat;
import me.lovkar.thieves.intel.Intel;
import me.lovkar.thieves.target.Convoys;
import me.lovkar.thieves.target.Job;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.Work;
import me.lovkar.thieves.target.TargetKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A thief's night.
 *
 * <p>Take a mark off the board, slip out of the Hideout, be gone for a while, and come back clean,
 * seen or not at all. He does not walk to the target: the Nether Worker established the shape years
 * ago - the citizen leaves the world, a clock runs, and what happens is decided by a roll against
 * what the place is actually worth defending. That is honest, cheap, and it works at any distance.</p>
 *
 * <p>Everything that follows from the roll happens in one place, {@link #comeHome()}: the take goes
 * into the sack, the place gets more nervous, and - <b>only if he was caught or seen</b> - somebody
 * over there works out where he came from. Lovkar's first rule lives on that one condition.</p>
 */
public class EntityAIWorkThief extends AbstractEntityAIBasic<JobThief, BuildingHideout> {

    private static final int TICK_DELAY = 40;
    /**
     * Calls spent trying to reach the Hideout door before he gives up for now - three minutes.
     *
     * <p>It was one minute, and in a colony the size of a real one that was not enough: the first
     * live test has him taking the same job twice, a minute apart, because he could not cross his
     * own town in time, put the mark back and picked it up again. A thief walking a long way is not
     * a thief who is stuck.</p>
     */
    private static final int WALK_ATTEMPTS = 90;
    /** Interaction shown when he cannot get out of the building at all. */
    public static final String CHAT_BLOCKED = "com.colonist_thieves.chat.blocked";

    private int walkAttempts = 0;
    /** When he last went looking. Searching the world for structures is not free. */
    private long scoutedAt = Long.MIN_VALUE;
    /** How often he may go looking, in ticks. Once a minute is plenty for a decision this slow. */
    private static final long SCOUT_EVERY = 1200L;
    /** Debug only: when he last said out loud that he had nothing to do. */
    private long idleSaidAt = Long.MIN_VALUE;
    /**
     * Whether a convoy on the board waits for dark like every other job. It does not: a convoy is
     * on the road now and will have arrived long before nightfall, so an order to meet one is
     * taken whenever it is there.
     */
    private static final boolean CONVOYS_WAIT_FOR_DARK = false;

    public EntityAIWorkThief(final @NotNull JobThief job) {
        super(job);
        super.registerTargets(
                new AITarget<IAIState>(AIWorkerState.IDLE, () -> AIWorkerState.START_WORKING, 10),
                new AITarget<IAIState>(AIWorkerState.START_WORKING, this::decide, TICK_DELAY),
                new AITarget<IAIState>(AIWorkerState.NETHER_LEAVE, this::slipOut, TICK_DELAY),
                new AITarget<IAIState>(AIWorkerState.NETHER_AWAY, this::away, TICK_DELAY),
                new AITarget<IAIState>(AIWorkerState.NETHER_RETURN, this::comeHome, TICK_DELAY));
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingHideout> getExpectedBuildingClass() {
        return BuildingHideout.class;
    }

    @Override
    protected int getActionsDoneUntilDumping() {
        return 1;
    }

    // ------------------------------------------------------------------ deciding

    private IAIState decide() {
        if (job.getHeist() != null) {
            return worker.isInvisible() ? AIWorkerState.NETHER_AWAY : AIWorkerState.NETHER_LEAVE;
        }
        // A pinned mark is an order, but it is still a thief's order: he goes after dark. Without
        // this he takes the next job the moment he walks in from the last one, and the first live
        // test had him leaving on a second job at ten in the morning. A convoy is the exception:
        // it will not still be on the road at nightfall, so it is met when it passes.
        Job mark;
        if (!isNight()) {
            mark = CONVOYS_WAIT_FOR_DARK ? null
                    : building.takeJob(on -> on.target().kind() == TargetKind.CONVOY && willing(on));
            if (mark == null) {
                job.setStatusLine("waiting for dark");
                return AIWorkerState.IDLE;
            }
        } else {
            mark = building.takeJob(this::willing);
            if (mark == null) {
                mark = lookForWork();
            }
        }
        if (mark == null) {
            job.setStatusLine(building.auto() ? "nothing worth robbing tonight" : "no marks on the board");
            if (world.getGameTime() - idleSaidAt > 600L) {
                idleSaidAt = world.getGameTime();
                Thieves.LOGGER.info("[thief] {} finds nothing: board {}, mode {}, rested {}, caution {}",
                        name(), building.board().size(), building.mode(),
                        building.rested(world.getGameTime()), building.caution());
                for (final Job on : building.board()) {
                    Thieves.LOGGER.info("[thief]   on the board: {} ({}) to {} - willing {}",
                            on.target().name(), on.target().kind().id(), on.work().id(), willing(on));
                }
            }
            return AIWorkerState.IDLE;
        }
        // a convoy has moved on since it was pinned: he sets out for where it is now
        mark = Convoys.onTheRoad(world, mark, building.getPosition(), building.getBuildingLevel(), colony());
        final ThievesResearch.Bonuses learned = ThievesResearch.Bonuses.of(colony());
        final Heist heist = Heist.begin(mark, building.getPosition(), world.getGameTime(), skill(),
                building.getBuildingLevel(), learned.travelCut());
        job.setHeist(heist);
        building.wentOut(world.getGameTime());
        job.setStatusLine("taking " + mark.describe());
        Thieves.LOGGER.info("[thief] {} takes {} ({} at a {}), due back in {} ticks",
                name(), mark.describe(), mark.work().id(), mark.target().kind().id(),
                heist.dueAt() - heist.leftAt());
        walkAttempts = 0;
        return AIWorkerState.NETHER_LEAVE;
    }

    /**
     * With the Hideout on <b>auto</b> and the board empty, go and find something.
     *
     * <p>Only at night, and only when he has had his rest - otherwise a thief on auto would simply
     * never stop, and the heat system would be the only thing holding him back. A mark somebody
     * pinned is an order and ignores both of those; this is the part where nobody told him
     * anything.</p>
     */
    private Job lookForWork() {
        if (!building.auto()) {
            return null;
        }
        if (!building.rested(world.getGameTime())) {
            return null;
        }
        // asking the world where the nearest camp, village and hoard are is a real search; do it
        // at most once a minute rather than on every tick of an idle thief
        if (scoutedAt != Long.MIN_VALUE && world.getGameTime() - scoutedAt < SCOUT_EVERY) {
            return null;
        }
        scoutedAt = world.getGameTime();
        // a thief left to himself robs. Scouting and sabotage are orders - they serve a campaign
        // somebody is planning, and nobody plans a campaign by accident
        final Target found = me.lovkar.thieves.target.Scout.choose(world, building.getPosition(),
                place -> willing(new Job(place, Work.ROB)));
        return found == null ? null : new Job(found, Work.ROB);
    }

    /** Dusk to dawn, by the world's own clock - the same window the night shift mixin uses. */
    private boolean isNight() {
        final long time = world.getDayTime() % 24000L;
        return time >= 12500L && time <= 23000L;
    }

    /** What "careful" wants: better than one night in four comes off clean. */
    private static final float CAREFUL_ENOUGH = 0.25f;

    /** Whether this thief will take that job, given the Hideout's standing orders. */
    private boolean willing(final Job mark) {
        final Target place = mark.target();
        final String caution = building.caution();
        if (BuildingHideout.CAUTION_BOLD.equals(caution)) {
            return true;
        }
        if (BuildingHideout.CAUTION_QUIET.equals(caution) && place.kind().diplomatic()) {
            return false;
        }
        if (BuildingHideout.CAUTION_CAREFUL.equals(caution)) {
            final Assessment there = reading(place);
            // he goes if he has a real chance of walking back out with it - read off the same
            // three numbers the roll will use, so "careful" means what the outcome will mean
            final int learned = ThievesResearch.Bonuses.of(colony()).roll();
            return Heist.prospect(Heist.brings(skill(), building.getBuildingLevel(), learned),
                    Heist.demands(there, place.kind(), mark.work())) >= CAREFUL_ENOUGH;
        }
        return true;
    }

    /**
     * What the place looks like <b>to this colony</b> - the world's own reading, softened by what
     * a scout has brought back and by anything a saboteur has broken.
     *
     * <p>Every roll goes through here rather than through {@link Assessment#of} directly, so that
     * the work of one thief is worth something to the next one. It is the only place the two sides
     * of the mod meet: heat makes a place harder, intelligence makes it easier, and both fade.</p>
     */
    private Assessment reading(final Target place) {
        final Assessment raw = Assessment.of(world, place);
        return raw.softenedBy(Intel.known(world, place.key()), Intel.shaken(world, place.key()));
    }

    /** His craft, in one number: what he was hired for, plus what he has learned. */
    private int skill() {
        try {
            return worker.getCitizenData().getCitizenSkillHandler()
                    .getLevel(com.minecolonies.api.entity.citizen.Skill.Agility)
                    + worker.getCitizenData().getCitizenSkillHandler()
                    .getLevel(com.minecolonies.api.entity.citizen.Skill.Adaptability) / 2;
        } catch (final Throwable t) {
            return 5;
        }
    }

    // ------------------------------------------------------------------ going

    /**
     * Away, or back.
     *
     * <p>Invisible is only half of it. A thief on a job is <b>not in the world</b> - the whole
     * conceit of the Nether-Worker shape is that he has left - but the citizen is still standing
     * at the Hideout's door, and an invisible citizen is still a body a zombie can walk up to and
     * kill. That is exactly what happened the first time somebody tested at night: the thief died
     * on a job he was nowhere near.</p>
     *
     * <p>So going away makes him invulnerable and silent as well, and coming home takes all three
     * off again <b>before</b> {@code settle} runs - because being caught is supposed to hurt, and
     * a thief who stayed invulnerable through his own punishment would never learn anything.</p>
     */
    private void gone(final boolean away) {
        worker.setInvisible(away);
        worker.setInvulnerable(away);
        worker.setSilent(away);
    }

    private IAIState slipOut() {
        if (job.getHeist() == null) {
            return AIWorkerState.START_WORKING;
        }
        if (!walkToBuilding()) {
            if (++walkAttempts > WALK_ATTEMPTS) {
                // three minutes and still not at his own back door: put the mark back so somebody
                // else can take it, and only now say he is stuck - a slow walk is not a blockage
                building.returnJob(new Job(job.getHeist().target(), job.getHeist().work()));
                job.setHeist(null);
                job.setBlocked(true);
                job.setStatusLine("cannot reach the Hideout");
                Thieves.LOGGER.warn("[thief] {} could not reach the Hideout in {} tick(s) - job put back",
                        name(), WALK_ATTEMPTS * TICK_DELAY);
                walkAttempts = 0;
                return AIWorkerState.IDLE;
            }
            return getState();
        }
        walkAttempts = 0;
        job.setBlocked(false);
        gone(true);
        job.setStatusLine("out on " + new Job(job.getHeist().target(), job.getHeist().work()).describe());
        Thieves.LOGGER.info("[thief] {} slips out of the Hideout", name());
        return AIWorkerState.NETHER_AWAY;
    }

    private IAIState away() {
        if (job.getHeist() == null) {
            gone(false);
            return AIWorkerState.START_WORKING;
        }
        if (!worker.isInvisible()) {
            // a reload can bring him back visible - and vulnerable - in the middle of a job
            gone(true);
        }
        final Heist heist = job.getHeist();
        if (heist.target().kind() == TargetKind.CONVOY) {
            return onTheRoad(heist);
        }
        if (!heist.due(world.getGameTime())) {
            return getState();
        }
        final ThievesResearch.Bonuses learned = ThievesResearch.Bonuses.of(colony());
        final Assessment there = reading(heist.target());
        final Outcome outcome = heist.roll(there, worker.getRandom(), learned.roll());
        job.setOutcome(outcome);
        if (outcome.keepsLoot() && heist.work().loot()) {
            final List<ItemStack> take = Take.roll(world, heist.target(),
                    heist.quality(there, outcome), building.getBuildingLevel() + learned.loot(),
                    worker.getRandom());
            job.addTake(capped(take, heist.target()));
        }
        // what he was sent for, if he managed it. A man who was caught brings nothing back -
        // not loot, not a report, and certainly not a burnt granary
        if (outcome != Outcome.CAUGHT) {
            if (heist.work() == Work.SCOUT) {
                Intel.scouted(world, heist.target().key(), Assessment.of(world, heist.target()).line());
            } else if (heist.work() == Work.SABOTAGE) {
                Intel.setBack(world, heist.target().key());
            }
        }
        Thieves.LOGGER.info("[thief] {} finishes {} at {} - {} - {}", name(), heist.work().id(),
                heist.target().name(), outcome.id(), there.line());
        return AIWorkerState.NETHER_RETURN;
    }

    /**
     * A convoy job, which is decided when he reaches the road rather than when he is home.
     *
     * <p>Half way through the trip the war mod is asked whether that convoy is still on the road
     * within the Hideout's reach; {@link Interception} does the asking, the roll and - only after
     * the roll, and only if he kept his loot - the taking. The outcome is kept on the job, so a
     * save in the second half of the trip does not roll it again, and he walks home with it.</p>
     */
    private IAIState onTheRoad(final Heist heist) {
        final long now = world.getGameTime();
        if (job.getOutcome() == null) {
            if (!heist.there(now)) {
                return getState();
            }
            final ThievesResearch.Bonuses learned = ThievesResearch.Bonuses.of(colony());
            final Interception.Result met = Interception.intercept(world, heist, building.getPosition(),
                    building.getBuildingLevel(), colony(), learned.roll(), learned.loot(), worker.getRandom());
            job.setOutcome(met.outcome());
            job.addTake(met.take());
            Thieves.LOGGER.info("[thief] {} reaches the road for {} - {} - {} stack(s) - {}", name(),
                    heist.target().name(), met.outcome() == Outcome.EMPTY ? "the road was empty"
                            : met.outcome().id(), met.take().size(), met.there().line());
        }
        return heist.due(now) ? AIWorkerState.NETHER_RETURN : getState();
    }

    /** Another player's warehouse is never emptied in one night, whatever the roll said. */
    private List<ItemStack> capped(final List<ItemStack> take, final Target target) {
        if (target.kind() != TargetKind.COLONY) {
            return take;
        }
        final int cap = ThievesConfig.maxStacksFromPlayerColony();
        return take.size() <= cap ? take : take.subList(0, cap);
    }

    // ------------------------------------------------------------------ coming home

    private IAIState comeHome() {
        final Heist heist = job.getHeist();
        if (heist == null) {
            gone(false);
            return AIWorkerState.START_WORKING;
        }
        if (worker.isInvisible()) {
            TeleportHelper.teleportCitizen(worker, world, building.getPosition());
            // back in the world, and answerable again - settle() is about to hurt him if he was caught
            gone(false);
            return getState();
        }
        if (!walkToBuilding()) {
            return getState();
        }
        final Outcome outcome = job.getOutcome() == null ? Outcome.CLEAN : job.getOutcome();
        settle(heist, outcome);
        for (final ItemStack stack : job.getTake()) {
            InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), stack);
        }
        job.getTake().clear();
        job.setHeist(null);
        job.setOutcome(null);
        job.setStatusLine("back at the Hideout");
        incrementActionsDoneAndDecSaturation();
        return AIWorkerState.INVENTORY_FULL;
    }

    /**
     * What the job cost, once he is home.
     *
     * <p>Heat always. Standing <b>only</b> if he was caught or seen, and only against somebody who
     * can hold a grudge - that is the rule, and it is why a clean night against a neighbour is
     * genuinely free. A caught thief brings the raid.</p>
     */
    private void settle(final Heist heist, final Outcome outcome) {
        reckon(world, building, colony(), heist, outcome, countOf(job.getTake()),
                ThievesResearch.Bonuses.of(colony()));
        if (outcome == Outcome.CAUGHT) {
            worker.getCitizenData().setJobStatus(com.minecolonies.api.entity.ai.JobStatus.STUCK);
            worker.hurt(world.damageSources().generic(), 6.0f);
            if (ThievesConfig.retaliation()) {
                retaliate();
            }
        }
    }

    /**
     * The books, once he is home: heat on the place, a line in the log, the hut's count, and -
     * <b>only</b> if he was caught or seen, and only against a colony or one of its convoys -
     * standing, through the war mod.
     *
     * <p>Static, and given everything it needs, so that the rig can put a night through exactly
     * this and read the log and the standing afterwards. A convoy's goods are its colony's goods:
     * being seen on the road costs what being seen in that colony's warehouse costs, and being
     * caught twice that. An empty road is no job at all - nobody was there, so nothing gets hotter
     * and nobody can put a name to anything; it goes in the log so the player knows why he came
     * home with nothing.</p>
     */
    public static void reckon(final ServerLevel world, final BuildingHideout building, final IColony mine,
                              final Heist heist, final Outcome outcome, final int took,
                              final ThievesResearch.Bonuses learned) {
        final Target target = heist.target();
        // a cold trail never makes a job free - somebody always notices something
        if (outcome != Outcome.EMPTY) {
            Heat.raise(world, target.key(),
                    Math.max(1, Math.round(outcome.heat() * (1f - learned.heatCut()))));
        }
        // getGameTime, NOT getDayTime. getDayTime is the clock, and the clock can be SET - by a
        // command, by another mod - which resets the day number to nought and leaves the log
        // reading "day 1, day 0, day 2" for nights that happened in order. getGameTime only ever
        // counts up, so the number beside a night is the night it actually was.
        building.record(new BuildingHideout.Entry(target.name(), target.kind().id(),
                world.getGameTime() / 24000L, outcome.id(), took, heist.work().id()));
        tally(building, heist, outcome, took);

        if (outcome.discovered() && target.kind().diplomatic()) {
            if (target.kind() == TargetKind.COLONY || target.kind() == TargetKind.CONVOY) {
                // being caught breaking something is worse than being caught taking something,
                // and being caught merely looking is the cheapest of the three
                final int base = switch (heist.work()) {
                    case SCOUT -> 5;
                    case SABOTAGE -> 30;
                    default -> 10;
                };
                final int full = outcome == Outcome.CAUGHT ? base * 2 : base;
                final int cost = Math.max(1, Math.round(full * (1f - learned.standingCut())));
                // a convoy's target carries the colony that sent it, which is who minds
                War.offend(mine, target.colonyId(), cost,
                        target.kind() == TargetKind.CONVOY ? "convoy" : "theft");
                tellTheVictim(target, outcome, mine);
            }
        }
    }

    /**
     * What the hut's own Statistics tab counts.
     *
     * <p>MineColonies gives every building a statistics module and a tab for it, and ours has been
     * showing "No statistics found" since the day it was built - a panel that is always empty is
     * worse than no panel. These are the numbers a player actually wants out of a Hideout: how
     * many nights, split by what he was sent to do and by how it went, and what came home.</p>
     *
     * <p>The labels go in our own lang file under MineColonies' key - the same trick the settings
     * labels need - because the id is ours but the window that reads it is theirs.</p>
     */
    private static void tally(final BuildingHideout building, final Heist heist, final Outcome outcome,
                              final int took) {
        try {
            final com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule stats =
                    building.getModuleMatching(
                            com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule.class,
                            m -> true);
            if (stats == null) {
                return;
            }
            stats.increment("thieves_nights");
            stats.increment("thieves_" + heist.work().id());
            stats.increment("thieves_" + outcome.id());
            if (took > 0) {
                stats.incrementBy("thieves_take", took);
            }
        } catch (final Throwable t) {
            // a MineColonies without that module is not a reason to lose the job
            Thieves.LOGGER.debug("[thief] could not add to the statistics: {}", t.toString());
        }
    }

    /** The night after a thief is caught, somebody comes looking. */
    private void retaliate() {
        try {
            final IColony mine = worker.getCitizenData().getColony();
            final IRaiderManager raiders = mine.getRaiderManager();
            raiders.setRaidNextNight(IRaiderManager.RaidSettings.defaultRaidSettings());
            Thieves.LOGGER.info("[thief] {} was caught - {} can expect visitors tonight", name(), mine.getName());
        } catch (final Throwable t) {
            Thieves.LOGGER.warn("[thief] could not arrange the retaliation raid: {}", t.toString());
        }
    }

    /** On a server the robbed player hears about it - but only when there was something to hear. */
    private static void tellTheVictim(final Target target, final Outcome outcome, final IColony mine) {
        if (!ThievesConfig.tellTheVictim()) {
            return;
        }
        try {
            final IColony theirs = IColonyManager.getInstance()
                    .getColonyByDimension(target.colonyId(), target.dimension());
            if (theirs == null) {
                return;
            }
            // a convoy's sender hears it was on the road, and which convoy
            final Component text = target.kind() == TargetKind.CONVOY
                    ? Component.translatable(outcome == Outcome.CAUGHT
                            ? "com.colonist_thieves.victim.convoy_caught"
                            : "com.colonist_thieves.victim.convoy_seen", mine.getName(), target.name())
                    : Component.literal(outcome == Outcome.CAUGHT
                            ? "We caught a thief from " + mine.getName() + " in our stores."
                            : "Somebody from " + mine.getName() + " was seen in our warehouse.");
            for (final net.minecraft.world.entity.player.Player player : theirs.getMessagePlayerEntities()) {
                player.sendSystemMessage(text);
            }
        } catch (final Throwable t) {
            Thieves.LOGGER.warn("[thief] could not tell the victim: {}", t.toString());
        }
    }

    /** How much came home, counted in items rather than stacks - it is what a log should say. */
    private static int countOf(final java.util.Collection<ItemStack> take) {
        int items = 0;
        for (final ItemStack stack : take) {
            items += stack.getCount();
        }
        return items;
    }

    private String name() {
        return worker.getCitizenData().getName();
    }

    /** The colony this thief works for, or null if the world has lost track of him. */
    private com.minecolonies.api.colony.IColony colony() {
        try {
            return worker.getCitizenData().getColony();
        } catch (final Throwable t) {
            return null;
        }
    }
}
