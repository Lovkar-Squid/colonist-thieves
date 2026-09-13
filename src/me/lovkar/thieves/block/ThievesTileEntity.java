package me.lovkar.thieves.block;

import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import me.lovkar.thieves.Thieves;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of the Hideout. Behaves exactly like a MineColonies hut block entity; it exists
 * only so our hut block is a valid block for a block-entity type we own.
 */
public class ThievesTileEntity extends TileEntityColonyBuilding {
    public ThievesTileEntity(final BlockPos pos, final BlockState state) {
        super(Thieves.BUILDING_BE.get(), pos, state);
    }
}
