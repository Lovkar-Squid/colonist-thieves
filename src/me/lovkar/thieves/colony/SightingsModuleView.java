package me.lovkar.thieves.colony;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.client.SightingsWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** The client's copy of the neighbourhood: everywhere the hut has heard of, and how it looks. */
public class SightingsModuleView extends AbstractBuildingModuleView {

    /**
     * One place, as the window shows it.
     *
     * @param known 0-100, how much of what a scout could tell you about this place is still fresh
     */
    public record Place(String name, String kind, BlockPos pos, int strength, int heat,
                        boolean pinned, int known) {
    }

    private final List<Place> places = new ArrayList<>();
    /** True while the world was searched too recently to be searched again. */
    private boolean resting;
    /** True where a War Room stands, which is the only place a thief does more than rob. */
    private boolean warRoom;

    @Override
    public void deserialize(final @NotNull RegistryFriendlyByteBuf buf) {
        places.clear();
        final int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            places.add(new Place(buf.readUtf(), buf.readUtf(), buf.readBlockPos(),
                    buf.readInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
        }
        resting = buf.readBoolean();
        warRoom = buf.readBoolean();
    }

    public List<Place> places() {
        return places;
    }

    public boolean resting() {
        return resting;
    }

    /** Whether this colony may send a thief to do anything other than rob. */
    public boolean warRoom() {
        return warRoom;
    }

    @Override
    public BOWindow getWindow() {
        return new SightingsWindow(this);
    }

    @Override
    public ResourceLocation getIconResourceLocation() {
        return ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "textures/gui/sightings.png");
    }

    @Override
    public Component getDesc() {
        return Component.translatable("com.colonist_thieves.gui.sightings.tab");
    }
}
