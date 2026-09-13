package me.lovkar.thieves.colony;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.client.BoardWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** The client's copy of the board: who is out, and what is still pinned up. */
public class BoardModuleView extends AbstractBuildingModuleView {

    /** One thief, as the window shows him. */
    public record Crew(String name, String status, boolean away, String where, String kind,
                       int ticksLeft, boolean sick) {
    }

    /** One mark on the board, and what the thief who takes it has been told to do there. */
    public record Mark(String name, String kind, BlockPos pos, int heat, String work) {
    }

    private final List<Crew> crew = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();

    @Override
    public void deserialize(final @NotNull RegistryFriendlyByteBuf buf) {
        crew.clear();
        final int people = buf.readVarInt();
        for (int i = 0; i < people; i++) {
            final String name = buf.readUtf();
            final String status = buf.readUtf();
            final boolean away = buf.readBoolean();
            String where = "";
            String kind = "";
            int left = 0;
            if (away) {
                where = buf.readUtf();
                kind = buf.readUtf();
                left = buf.readVarInt();
            }
            crew.add(new Crew(name, status, away, where, kind, left, buf.readBoolean()));
        }
        marks.clear();
        final int pinned = buf.readVarInt();
        for (int i = 0; i < pinned; i++) {
            marks.add(new Mark(buf.readUtf(), buf.readUtf(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readUtf()));
        }
    }

    public List<Crew> crew() {
        return crew;
    }

    public List<Mark> marks() {
        return marks;
    }

    @Override
    public BOWindow getWindow() {
        return new BoardWindow(this);
    }

    @Override
    public ResourceLocation getIconResourceLocation() {
        return ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "textures/gui/board.png");
    }

    @Override
    public Component getDesc() {
        return Component.translatable("com.colonist_thieves.gui.board.tab");
    }
}
