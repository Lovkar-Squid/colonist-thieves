package me.lovkar.thieves.network;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.colony.BuildingHideout;
import me.lovkar.thieves.target.Target;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * "Take that mark off the board."
 *
 * <p>A pinned mark was, until now, only removable by a thief taking the job - which is awkward
 * when the mark you pinned turns out to be a fortress. The packet carries the hut's position and
 * the mark's own position; the server matches by position rather than by index, because the list
 * shifts under a client that is a tick behind and removing the wrong job would be worse than
 * removing none.</p>
 */
public record UnpinMessage(BlockPos building, BlockPos mark) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnpinMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "unpin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnpinMessage> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
                buf.writeBlockPos(msg.building());
                buf.writeBlockPos(msg.mark());
            }, buf -> new UnpinMessage(buf.readBlockPos(), buf.readBlockPos()));

    @Override
    public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final UnpinMessage message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            final IColony colony = IColonyManager.getInstance()
                    .getColonyByPosFromWorld(player.serverLevel(), message.building());
            if (colony == null || !colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS)) {
                return;
            }
            final IBuilding building = colony.getServerBuildingManager().getBuilding(message.building());
            if (!(building instanceof BuildingHideout hideout)) {
                return;
            }
            for (final me.lovkar.thieves.target.Job pinned : new java.util.ArrayList<>(hideout.board())) {
                if (pinned.target().pos().equals(message.mark())) {
                    hideout.board().remove(pinned);
                    hideout.markDirty();
                    return;
                }
            }
        });
    }
}
