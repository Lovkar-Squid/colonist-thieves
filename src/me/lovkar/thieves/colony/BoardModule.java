package me.lovkar.thieves.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import me.lovkar.thieves.job.Heist;
import me.lovkar.thieves.target.Heat;
import me.lovkar.thieves.target.Target;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * The board on the wall, as the hut window sees it.
 *
 * <p>It stores nothing of its own. The marks and the crew already live on {@link BuildingHideout}
 * and on the jobs; this only decides what of that is worth sending to a client and in what order,
 * which is the one thing a module view cannot work out for itself.</p>
 *
 * <p>Heat travels with each mark because it is the number that decides whether a job is worth
 * doing, and a player cannot see it any other way. It is read here, on the server, where the
 * world's saved data lives.</p>
 */
public class BoardModule extends AbstractBuildingModule implements IPersistentModule {

    @Override
    public void serializeToView(final @NotNull RegistryFriendlyByteBuf buf) {
        final BuildingHideout hideout = building instanceof BuildingHideout h ? h : null;
        if (hideout == null) {
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            return;
        }

        // the crew first: a player opening this wants to know where his thief is before anything
        int crew = 0;
        for (final ICitizenData citizen : hideout.getAllAssignedCitizen()) {
            if (citizen.getJob() instanceof JobThief) {
                crew++;
            }
        }
        buf.writeVarInt(crew);
        for (final ICitizenData citizen : hideout.getAllAssignedCitizen()) {
            if (!(citizen.getJob() instanceof JobThief thief)) {
                continue;
            }
            buf.writeUtf(citizen.getName());
            buf.writeUtf(thief.getStatusLine());
            final Heist heist = thief.getHeist();
            buf.writeBoolean(heist != null);
            if (heist != null) {
                buf.writeUtf(heist.target().name() == null ? "" : heist.target().name());
                buf.writeUtf(heist.work().id());
                final long left = heist.dueAt() - hideout.getColony().getWorld().getGameTime();
                buf.writeVarInt((int) Math.max(0L, Math.min(Integer.MAX_VALUE, left)));
            }
            buf.writeBoolean(citizen.getCitizenDiseaseHandler() != null
                    && citizen.getCitizenDiseaseHandler().isSick());
        }

        final java.util.List<me.lovkar.thieves.target.Job> board = hideout.board();
        buf.writeVarInt(board.size());
        for (final me.lovkar.thieves.target.Job pinned : board) {
            final Target mark = pinned.target();
            buf.writeUtf(mark.name() == null ? "" : mark.name());
            buf.writeUtf(mark.kind().id());
            buf.writeBlockPos(mark.pos());
            buf.writeVarInt(heatOf(hideout, mark));
            buf.writeUtf(pinned.work().id());
        }
    }

    /** How nervous that place is about being robbed, 0-100, or 0 if the world will not say. */
    private static int heatOf(final BuildingHideout hideout, final Target mark) {
        try {
            final net.minecraft.server.level.ServerLevel level =
                    hideout.getColony().getWorld().getServer().getLevel(mark.dimension());
            return level == null ? 0 : Heat.of(level, mark.key());
        } catch (final Throwable t) {
            return 0;
        }
    }
}
