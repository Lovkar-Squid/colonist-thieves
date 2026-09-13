package me.lovkar.thieves.colony;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * The book under the board: every night the hut remembers, newest first.
 *
 * <p>The entries are kept on the building itself so that they survive with it and are written from
 * one place in the AI. This only sends them, and it sends them whole - twenty short rows is a few
 * hundred bytes, and paging a log a player looks at twice a week is not worth a packet of its
 * own.</p>
 */
public class LedgerModule extends AbstractBuildingModule implements IPersistentModule {

    @Override
    public void serializeToView(final @NotNull RegistryFriendlyByteBuf buf) {
        final BuildingHideout hideout = building instanceof BuildingHideout h ? h : null;
        if (hideout == null) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(hideout.ledger().size());
        for (final BuildingHideout.Entry entry : hideout.ledger()) {
            buf.writeUtf(entry.place() == null ? "" : entry.place());
            buf.writeUtf(entry.kind() == null ? "" : entry.kind());
            buf.writeVarLong(entry.day());
            buf.writeUtf(entry.outcome() == null ? "" : entry.outcome());
            buf.writeVarInt(entry.items());
            buf.writeUtf(entry.work() == null ? "rob" : entry.work());
        }
    }
}
