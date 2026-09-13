package me.lovkar.thieves.client;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.colony.BoardModuleView;
import me.lovkar.thieves.network.UnpinMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The board on the Hideout wall: where the crew is tonight, and what is still waiting to be done.
 *
 * <p>The one thing this window can change is the board - a mark pinned in error can be taken down
 * again. Everything else is a reading, including the countdown on a thief who is out, which is why
 * it is rebuilt from the module view on every frame rather than ticked here.</p>
 */
public class BoardWindow extends AbstractModuleWindow<BoardModuleView> {

    private static final ResourceLocation LAYOUT =
            ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "gui/board.xml");

    public BoardWindow(final BoardModuleView view) {
        super(view, LAYOUT);

        final ScrollingList crew = findPaneOfTypeByID("crew", ScrollingList.class);
        if (crew != null) {
            crew.setDataProvider(new ScrollingList.DataProvider() {
                @Override
                public int getElementCount() {
                    return moduleView.crew().size();
                }

                @Override
                public void updateElement(final int index, final Pane row) {
                    fillCrew(moduleView.crew().get(index), row);
                }
            });
        }

        final ScrollingList marks = findPaneOfTypeByID("marks", ScrollingList.class);
        if (marks != null) {
            marks.setDataProvider(new ScrollingList.DataProvider() {
                @Override
                public int getElementCount() {
                    return moduleView.marks().size();
                }

                @Override
                public void updateElement(final int index, final Pane row) {
                    fillMark(moduleView.marks().get(index), row);
                }
            });
        }
    }

    private void fillCrew(final BoardModuleView.Crew thief, final Pane row) {
        final Text who = row.findPaneOfTypeByID("who", Text.class);
        if (who != null) {
            who.setText(Component.literal(thief.name()));
        }
        final Text doing = row.findPaneOfTypeByID("doing", Text.class);
        if (doing != null) {
            doing.setText(whatHeIsDoing(thief));
        }
    }

    /** One line: out on a job and how long, laid up sick, or whatever the AI last said. */
    private static MutableComponent whatHeIsDoing(final BoardModuleView.Crew thief) {
        if (thief.away()) {
            return Component.translatable("com.colonist_thieves.gui.board.out",
                    thief.where().isEmpty()
                            ? Component.translatable("com.colonist_thieves.kind." + thief.kind())
                            : Component.literal(thief.where()),
                    minutes(thief.ticksLeft())).withStyle(ChatFormatting.DARK_AQUA);
        }
        if (thief.sick()) {
            return Component.translatable("com.colonist_thieves.gui.board.sick")
                    .withStyle(ChatFormatting.DARK_RED);
        }
        if (!thief.status().isEmpty()) {
            return Component.literal(thief.status()).withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.translatable("com.colonist_thieves.gui.board.waiting")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Ticks, in the only unit a player thinks in. */
    private static Component minutes(final int ticks) {
        final int seconds = ticks / 20;
        if (seconds < 60) {
            return Component.translatable("com.colonist_thieves.gui.board.seconds", seconds);
        }
        return Component.translatable("com.colonist_thieves.gui.board.minutes", seconds / 60);
    }

    private void fillMark(final BoardModuleView.Mark mark, final Pane row) {
        final Text place = row.findPaneOfTypeByID("place", Text.class);
        if (place != null) {
            place.setText(mark.name().isEmpty()
                    ? Component.translatable("com.colonist_thieves.kind." + mark.kind())
                    : Component.literal(mark.name()));
        }
        final Text detail = row.findPaneOfTypeByID("detail", Text.class);
        if (detail != null) {
            // the verb, not the kind: the line above already says what sort of place it is when it
            // has no name of its own, and what he was told to do there is the thing worth reading
            detail.setText(Component.translatable("com.colonist_thieves.gui.board.mark_detail",
                    Component.translatable("com.colonist_thieves.work." + mark.work()),
                    mark.pos().getX(), mark.pos().getZ(), mark.heat()).withStyle(heatColour(mark.heat())));
        }
        final ButtonImage unpin = row.findPaneOfTypeByID("unpin", ButtonImage.class);
        if (unpin != null) {
            unpin.setHandler(clicked -> {
                PacketDistributor.sendToServer(new UnpinMessage(buildingView.getID(), mark.pos()));
                moduleView.marks().remove(mark);
            });
        }
    }

    /** Heat is the number that decides whether a job is worth doing; it should look like one. */
    private static ChatFormatting heatColour(final int heat) {
        if (heat >= 60) {
            return ChatFormatting.DARK_RED;
        }
        if (heat >= 30) {
            return ChatFormatting.GOLD;
        }
        return ChatFormatting.DARK_GRAY;
    }
}
