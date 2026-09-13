package me.lovkar.thieves.client;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.colony.SightingsModuleView;
import me.lovkar.thieves.network.LookAroundMessage;
import me.lovkar.thieves.network.SendThereMessage;
import me.lovkar.thieves.target.Work;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Somewhere to rob - the answer to "do I really have to walk there".
 *
 * <p>Every colony and kingdom the hut can see, plus the nearest camp, village and hoard, sorted
 * near to far, each with what it is worth defending and how jumpy it is. One button sends the
 * thief; the server looks the place up again before it believes a word of it.</p>
 *
 * <p>What he does when he gets there is the button at the top. It says <b>Rob</b> and does nothing
 * else until a War Room stands in the colony, at which point it starts to cycle - rob, scout,
 * sabotage - because those two are the seam between this mod and the war one and have no meaning
 * without it. The server checks that again anyway; this button is a convenience, not a
 * permission.</p>
 */
public class SightingsWindow extends AbstractModuleWindow<SightingsModuleView> {

    private static final ResourceLocation LAYOUT =
            ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "gui/sightings.xml");

    private final List<SightingsModuleView.Place> shown = new ArrayList<>();

    /** What the next Send button pressed will ask for. Robbery until somebody says otherwise. */
    private Work work = Work.ROB;

    public SightingsWindow(final SightingsModuleView view) {
        super(view, LAYOUT);
        reorder();

        final ScrollingList list = findPaneOfTypeByID("places", ScrollingList.class);
        if (list != null) {
            list.setDataProvider(new ScrollingList.DataProvider() {
                @Override
                public int getElementCount() {
                    reorder();
                    return shown.size();
                }

                @Override
                public void updateElement(final int index, final Pane row) {
                    fillRow(shown.get(index), row);
                }
            });
        }

        final ButtonImage look = findPaneOfTypeByID("look", ButtonImage.class);
        if (look != null) {
            look.setEnabled(!moduleView.resting());
            look.setHandler(clicked -> {
                PacketDistributor.sendToServer(new LookAroundMessage(buildingView.getID()));
                clicked.setEnabled(false);
            });
        }

        final ButtonImage mode = findPaneOfTypeByID("mode", ButtonImage.class);
        if (mode != null) {
            mode.setEnabled(moduleView.warRoom());
            showMode(mode);
            mode.setHandler(clicked -> {
                work = next(work);
                showMode(mode);
            });
        }
    }

    /**
     * Rob, then scout, then sabotage, then round again.
     *
     * <p>With no War Room the button is dead and this is never reached, but it wraps back to
     * robbery anyway: a hut that loses its War Room while the window is open should not be left
     * holding a verb it can no longer ask for.</p>
     */
    private Work next(final Work from) {
        if (!moduleView.warRoom()) {
            return Work.ROB;
        }
        return switch (from) {
            case ROB -> Work.SCOUT;
            case SCOUT -> Work.SABOTAGE;
            default -> Work.ROB;
        };
    }

    private void showMode(final ButtonImage mode) {
        if (!moduleView.warRoom()) {
            work = Work.ROB;
        }
        mode.setText(Component.translatable("com.colonist_thieves.gui.sightings.mode",
                Component.translatable(work.nameKey())));
    }

    /** Near to far. A thief picks by the walk before he picks by the purse. */
    private void reorder() {
        shown.clear();
        shown.addAll(moduleView.places());
        final BlockPos here = buildingView.getID();
        shown.sort(Comparator.comparingDouble(p -> p.pos().distSqr(here)));
    }

    private void fillRow(final SightingsModuleView.Place place, final Pane row) {
        final Text name = row.findPaneOfTypeByID("place", Text.class);
        if (name != null) {
            name.setText(place.name().isEmpty()
                    ? Component.translatable("com.colonist_thieves.kind." + place.kind())
                    : Component.literal(place.name()));
        }
        final Text where = row.findPaneOfTypeByID("where", Text.class);
        if (where != null) {
            final BlockPos here = buildingView.getID();
            where.setText(Component.translatable("com.colonist_thieves.gui.sightings.where",
                            Component.translatable("com.colonist_thieves.kind." + place.kind()),
                            (int) Math.round(Math.sqrt(place.pos().distSqr(here))),
                            bearing(here, place.pos()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        final Text risk = row.findPaneOfTypeByID("risk", Text.class);
        if (risk != null) {
            risk.setText(describe(place));
        }
        final Text known = row.findPaneOfTypeByID("known", Text.class);
        if (known != null) {
            known.setText(place.known() <= 0
                    ? Component.empty()
                    : Component.translatable("com.colonist_thieves.gui.sightings.known", place.known())
                            .withStyle(ChatFormatting.DARK_GREEN));
        }
        final ButtonImage send = row.findPaneOfTypeByID("send", ButtonImage.class);
        if (send != null) {
            send.setText(Component.translatable(place.pinned()
                    ? "com.colonist_thieves.gui.sightings.pinned"
                    : "com.colonist_thieves.gui.sightings.send"));
            send.setEnabled(!place.pinned());
            send.setHandler(clicked -> {
                PacketDistributor.sendToServer(
                        new SendThereMessage(buildingView.getID(), place.pos(), work.id()));
                clicked.setEnabled(false);
                clicked.setText(Component.translatable("com.colonist_thieves.gui.sightings.pinned"));
            });
        }
    }

    /**
     * What it is worth defending, and how jumpy it is.
     *
     * <p>A place nobody has been to look at yet says so rather than inventing a number: the
     * reading costs a walk round the walls, and "Look around" is the walk.</p>
     */
    private MutableComponent describe(final SightingsModuleView.Place place) {
        if (place.strength() < 0) {
            return Component.translatable("com.colonist_thieves.gui.sightings.unread")
                    .withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.translatable("com.colonist_thieves.gui.sightings.risk",
                        place.strength(), place.heat())
                .withStyle(place.heat() >= 40 ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY);
    }

    /** Eight points is as much as anybody reads off a line of text. */
    private static String bearing(final BlockPos from, final BlockPos to) {
        final int dx = to.getX() - from.getX();
        final int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) {
            return "";
        }
        final double angle = Math.toDegrees(Math.atan2(dx, -dz));
        final String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return points[(int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8];
    }
}
