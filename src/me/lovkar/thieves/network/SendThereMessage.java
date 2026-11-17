package me.lovkar.thieves.network;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.MessageUtils;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.colony.BuildingHideout;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.Targets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * "Send him there" - pin a place from the hut's list without walking to it with a Mark.
 *
 * <p>The packet carries a <b>position and nothing else</b>. The server looks the place up again
 * with the same {@link Targets#at} a Mark would use, so a client cannot invent a target, rename
 * one, or point at a kind the world does not agree with. If the world says there is nothing there,
 * nothing is pinned.</p>
 *
 * <p>A convoy has no position worth looking up - it is somewhere else by the time the packet
 * arrives - so a row for a convoy sends the war mod's id for it as well, and the server asks the war
 * mod whether that convoy is still on the road within this Hideout's reach and not its own colony's
 * before it pins anything. A convoy is only ever robbed, whatever the button said.</p>
 */
public record SendThereMessage(BlockPos building, BlockPos where, String work, long convoy)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendThereMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "send_there"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SendThereMessage> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
                buf.writeBlockPos(msg.building());
                buf.writeBlockPos(msg.where());
                buf.writeUtf(msg.work());
                buf.writeLong(msg.convoy());
            }, buf -> new SendThereMessage(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf(), buf.readLong()));

    @Override
    public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final SendThereMessage message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            final ServerLevel level = player.serverLevel();
            final IColony colony = IColonyManager.getInstance()
                    .getColonyByPosFromWorld(level, message.building());
            if (colony == null || !colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS)) {
                return;
            }
            final IBuilding building = colony.getServerBuildingManager().getBuilding(message.building());
            if (!(building instanceof BuildingHideout hideout)) {
                return;
            }
            final Sent sent = send(level, hideout, message.where(), message.convoy(), message.work());
            if (sent.message() != null) {
                MessageUtils.format(sent.message()).sendTo(player);
            }
        });
    }

    /**
     * What came of pressing Send: whether a job went up on the board, and what to tell the player
     * (null when there is nothing worth saying - his own colony, or a job that is already there).
     */
    public record Sent(boolean pinned, Component message) {
    }

    /**
     * The server half of the Send button, once the player's right to use it has been checked.
     *
     * <p>Also what {@code /thieves convoy} and the rig call, so that there is exactly one way a
     * place gets from the hut's list onto its board.</p>
     *
     * @param convoy the war mod's id when the row was a convoy, else 0
     */
    public static Sent send(final ServerLevel level, final BuildingHideout hideout, final BlockPos where,
                            final long convoy, final String verb) {
        final IColony colony = hideout.getColony();
        final Target target;
        if (convoy != 0L) {
            final me.lovkar.thieves.target.Convoys.Seen seen = me.lovkar.thieves.target.Convoys.find(
                    level, hideout.getPosition(), hideout.getBuildingLevel(), colony, convoy);
            target = seen == null ? null : seen.target();
        } else {
            target = Targets.at(level, where);
        }
        if (target == null) {
            return new Sent(false, Component.translatable("com.colonist_thieves.gui.sightings.gone"));
        }
        if (target.kind() == me.lovkar.thieves.target.TargetKind.COLONY
                && target.colonyId() == colony.getID()) {
            return new Sent(false, null);              // robbing yourself is not a strategy
        }
        for (final me.lovkar.thieves.target.Job already : hideout.board()) {
            if (already.target().samePlace(target)) {
                return new Sent(false, null);
            }
        }
        // the verb is re-checked here too: scouting and sabotage exist only where a War Room
        // stands, and a client that asks for one anyway gets a robbery. A convoy is only robbed:
        // there is nothing on a road to look at for later, and nothing to break but the cart
        me.lovkar.thieves.target.Work work = me.lovkar.thieves.target.Work.byId(verb);
        if (target.kind() == me.lovkar.thieves.target.TargetKind.CONVOY
                || work.needsWarRoom() && !me.lovkar.thieves.compat.War.hasWarRoom(colony)) {
            work = me.lovkar.thieves.target.Work.ROB;
        }
        hideout.pin(new me.lovkar.thieves.target.Job(target, work));
        return new Sent(true, Component.translatable(target.kind() == me.lovkar.thieves.target.TargetKind.CONVOY
                        ? "com.colonist_thieves.gui.sightings.sent.convoy"
                        : "com.colonist_thieves.gui.sightings.sent." + work.id(),
                Component.literal(target.name())));
    }
}
