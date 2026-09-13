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
 */
public record SendThereMessage(BlockPos building, BlockPos where, String work) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendThereMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "send_there"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SendThereMessage> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
                buf.writeBlockPos(msg.building());
                buf.writeBlockPos(msg.where());
                buf.writeUtf(msg.work());
            }, buf -> new SendThereMessage(buf.readBlockPos(), buf.readBlockPos(), buf.readUtf()));

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
            final Target target = Targets.at(level, message.where());
            if (target == null) {
                MessageUtils.format(Component.translatable("com.colonist_thieves.gui.sightings.gone")).sendTo(player);
                return;
            }
            if (target.kind() == me.lovkar.thieves.target.TargetKind.COLONY
                    && target.colonyId() == colony.getID()) {
                return;                           // robbing yourself is not a strategy
            }
            for (final me.lovkar.thieves.target.Job already : hideout.board()) {
                if (already.target().pos().equals(target.pos())) {
                    return;
                }
            }
            // the verb is re-checked here too: scouting and sabotage exist only where a War Room
            // stands, and a client that asks for one anyway gets a robbery
            me.lovkar.thieves.target.Work work = me.lovkar.thieves.target.Work.byId(message.work());
            if (work.needsWarRoom() && !me.lovkar.thieves.compat.War.hasWarRoom(colony)) {
                work = me.lovkar.thieves.target.Work.ROB;
            }
            hideout.pin(new me.lovkar.thieves.target.Job(target, work));
            MessageUtils.format(Component.translatable(
                    "com.colonist_thieves.gui.sightings.sent." + work.id(),
                    Component.literal(target.name()))).sendTo(player);
        });
    }
}
