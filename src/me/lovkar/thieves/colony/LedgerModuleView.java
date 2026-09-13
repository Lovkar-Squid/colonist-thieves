package me.lovkar.thieves.colony;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.client.LedgerWindow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** The client's copy of the hut's log. */
public class LedgerModuleView extends AbstractBuildingModuleView {

    /** One night, as the window shows it. */
    public record Row(String place, String kind, long day, String outcome, int items, String work) {
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public void deserialize(final @NotNull RegistryFriendlyByteBuf buf) {
        rows.clear();
        final int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            rows.add(new Row(buf.readUtf(), buf.readUtf(), buf.readVarLong(),
                    buf.readUtf(), buf.readVarInt(), buf.readUtf()));
        }
    }

    public List<Row> rows() {
        return rows;
    }

    @Override
    public BOWindow getWindow() {
        return new LedgerWindow(this);
    }

    @Override
    public ResourceLocation getIconResourceLocation() {
        return ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "textures/gui/ledger.png");
    }

    @Override
    public Component getDesc() {
        return Component.translatable("com.colonist_thieves.gui.ledger.tab");
    }
}
