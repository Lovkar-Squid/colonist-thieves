package me.lovkar.thieves.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.workers.CitizenAI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.lovkar.thieves.colony.BuildingHideout;
import me.lovkar.thieves.colony.JobThief;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A thief works at night.
 *
 * <p><b>Why a mixin, when nothing else in this mod needs one.</b> MineColonies decides what a
 * citizen does next in {@code CitizenAI.calculateNextState}, and the decision to go to bed is
 * written into that method with exactly one exemption: a guard job. There is no hook on the job,
 * none on the building and none on the schedule. Without this, dusk falls and the one profession
 * in the game whose entire point is the dark walks off to bed - which is what the first live test
 * showed: the mark sat on the board until the sun came up.</p>
 *
 * <p>Two cases, and the first one is about correctness rather than flavour.</p>
 *
 * <p><b>A thief who is out stays out.</b> While he is away he is not in the world at all; the AI
 * still has to tick so that the clock runs down and he comes home. Being sent to bed mid-job would
 * strand him and the take with him. So an away thief is always working, and nothing interrupts
 * that - there is nothing here to interrupt.</p>
 *
 * <p><b>A thief with work on the board does not sleep through the night.</b> That is the whole
 * fiction, and it is also when a place is worth robbing. Everything MineColonies would rightly
 * stop work for still stops it - hunger, illness, a raid on his own colony. With an empty board he
 * sleeps like anybody else, which is the other half of the point.</p>
 */
@Mixin(CitizenAI.class)
public abstract class CitizenAINightShiftMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    /** The last reason printed, so a thief who has been idle all night says so once, not all night. */
    private String thieves$lastWhy = null;

    /**
     * Say why the night shift did not start - once per reason.
     *
     * <p>It is a log line rather than a comment because "my thief does nothing" has three or four
     * perfectly ordinary causes (he is sick, he is hungry, the board is empty, it is the wrong half
     * of the day) and guessing which one from the outside costs an evening. It cost one.</p>
     */
    private void thieves$why(final String reason) {
        if (reason.equals(thieves$lastWhy)) {
            return;
        }
        thieves$lastWhy = reason;
        me.lovkar.thieves.Thieves.LOGGER.info("[nightshift] {} stays put: {}",
                citizen.getCitizenData().getName(), reason);
    }

    @Inject(method = "calculateNextState", at = @At("HEAD"), cancellable = true)
    private void thieves$workTheNight(final CallbackInfoReturnable<IState> callback) {
        if (citizen == null || citizen.getCitizenData() == null) {
            return;
        }
        final IJob<?> job = citizen.getCitizenData().getJob();
        if (!(job instanceof JobThief thief)) {
            return;
        }

        // out on a job: he is not in the world, and the clock only runs while the AI ticks
        if (thief.isAway()) {
            wake();
            callback.setReturnValue(CitizenAIState.WORK);
            return;
        }

        if (!isNight(citizen.level())) {
            thieves$why("it is daytime");
            return;                                   // by day, the ordinary schedule
        }
        if (job.getWorkBuilding() == null) {
            thieves$why("no work building on the job");
            return;                                   // no Hideout to work out of
        }
        if (!(job.getWorkBuilding() instanceof BuildingHideout hideout)) {
            thieves$why("work building is " + job.getWorkBuilding().getClass().getSimpleName());
            return;
        }
        // Something pinned is an order. On auto, an empty board is not idleness - it is the whole
        // point: he goes looking. Without this second case an auto thief walks to the tavern at
        // dusk and never scouts, which is exactly what the first auto test showed.
        final boolean work = !hideout.board().isEmpty()
                || (hideout.auto() && hideout.rested(citizen.level().getGameTime()));
        if (!work) {
            thieves$why("board " + hideout.board().size() + ", mode " + hideout.mode()
                    + ", rested " + hideout.rested(citizen.level().getGameTime()));
            return;                                   // nothing to do: he sleeps like anybody else
        }
        // everything MineColonies would rightly stop work for still stops it
        if (citizen.getCitizenData().getCitizenDiseaseHandler() != null
                && citizen.getCitizenData().getCitizenDiseaseHandler().isSick()) {
            thieves$why("he is sick");
            return;
        }
        if (((CitizenAI) (Object) this).shouldEat()) {
            thieves$why("he wants to eat (saturation "
                    + citizen.getCitizenData().getSaturation() + ")");
            return;
        }
        final IColony colony = citizen.getCitizenColonyHandler() == null
                ? null : citizen.getCitizenColonyHandler().getColonyOrRegister();
        if (colony != null && colony.getRaiderManager() != null && colony.getRaiderManager().isRaided()) {
            thieves$why("the colony is being raided");
            return;                                   // a raid sends everyone indoors, thieves too
        }
        thieves$lastWhy = null;
        wake();
        callback.setReturnValue(CitizenAIState.WORK);
    }

    private void wake() {
        if (citizen.getCitizenSleepHandler() != null && citizen.getCitizenSleepHandler().isAsleep()) {
            citizen.getCitizenSleepHandler().onWakeUp();
        }
    }

    /** Dusk to dawn, by the world's own clock. */
    private static boolean isNight(final net.minecraft.world.level.Level level) {
        final long time = level.getDayTime() % 24000L;
        return time >= 12500L && time <= 23000L;
    }
}
