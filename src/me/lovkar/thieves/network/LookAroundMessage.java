package me.lovkar.thieves.network;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.colony.BuildingHideout;
import me.lovkar.thieves.colony.HideoutModules;
import me.lovkar.thieves.colony.SightingsModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * "Go and see what is out there."
 *
 * <p>This is the only thing in the mod that makes the world do real work on a button press - the
 * {@code /locate} search, three times - so the module refuses to run it more than once every half
 * minute and the packet carries no parameters that could make it bigger.</p>
 */
public record LookAroundMessage(BlockPos building) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LookAroundMessage> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "look_around"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LookAroundMessage> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeBlockPos(msg.building()),
                    buf -> new LookAroundMessage(buf.readBlockPos()));

    @Override
    public CustomPacketPayload.@NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final LookAroundMessage message, final IPayloadContext context) {
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
            final SightingsModule module = hideout.getModule(HideoutModules.SIGHTINGS);
            if (module != null) {
                module.lookAround(level, hideout.getPosition());
            }
        });
    }
}
