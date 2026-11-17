package me.lovkar.thieves.client;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.colony.LedgerModuleView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The hut's log: every night the Hideout remembers, newest first.
 *
 * <p>What a player wants from a log like this is a pattern - which places pay, which places have
 * started expecting him - so each row says where, what came home, and how it went, and the header
 * counts how many of the remembered nights ended badly.</p>
 */
public class LedgerWindow extends AbstractModuleWindow<LedgerModuleView> {

    private static final ResourceLocation LAYOUT =
            ResourceLocation.fromNamespaceAndPath(Thieves.MODID, "gui/ledger.xml");

    public LedgerWindow(final LedgerModuleView view) {
        super(view, LAYOUT);

        final ScrollingList nights = findPaneOfTypeByID("nights", ScrollingList.class);
        if (nights != null) {
            nights.setDataProvider(new ScrollingList.DataProvider() {
                @Override
                public int getElementCount() {
                    return moduleView.rows().size();
                }

                @Override
                public void updateElement(final int index, final Pane row) {
                    fillRow(moduleView.rows().get(index), row);
                }
            });
        }
        summarise();
    }

    private void summarise() {
        final Text header = findPaneOfTypeByID("summary", Text.class);
        if (header == null) {
            return;
        }
        int items = 0;
        int caught = 0;
        for (final LedgerModuleView.Row row : moduleView.rows()) {
            items += row.items();
            if ("caught".equals(row.outcome())) {
                caught++;
            }
        }
        header.setText(Component.translatable("com.colonist_thieves.gui.ledger.summary",
                moduleView.rows().size(), items, caught));
    }

    private void fillRow(final LedgerModuleView.Row night, final Pane row) {
        final Text place = row.findPaneOfTypeByID("place", Text.class);
        if (place != null) {
            place.setText(Component.translatable("com.colonist_thieves.gui.ledger.place",
                    night.day(), night.place().isEmpty()
                            ? Component.translatable("com.colonist_thieves.kind." + night.kind())
                            : Component.literal(night.place())));
        }
        final Text result = row.findPaneOfTypeByID("result", Text.class);
        if (result != null) {
            // a night's takings are worth printing when there were meant to be any; a scout who
            // came home with nothing has not failed, and the log should not read as though he had
            final boolean robbery = night.work().isEmpty() || "rob".equals(night.work());
            result.setText((robbery
                    ? Component.translatable("com.colonist_thieves.gui.ledger.result",
                            Component.translatable("com.colonist_thieves.outcome." + night.outcome()),
                            night.items())
                    : Component.translatable("com.colonist_thieves.gui.ledger.result_work",
                            Component.translatable("com.colonist_thieves.work." + night.work()),
                            Component.translatable("com.colonist_thieves.outcome." + night.outcome())))
                    .withStyle(colourOf(night.outcome())));
        }
    }

    private static ChatFormatting colourOf(final String outcome) {
        return switch (outcome) {
            case "caught" -> ChatFormatting.DARK_RED;
            case "spotted" -> ChatFormatting.GOLD;
            case "empty" -> ChatFormatting.DARK_GRAY;
            default -> ChatFormatting.DARK_GREEN;
        };
    }
}
