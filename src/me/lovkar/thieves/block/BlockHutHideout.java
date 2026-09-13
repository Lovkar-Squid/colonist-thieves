package me.lovkar.thieves.block;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.item.MarkItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Hideout's hut block.
 *
 * <p>Two overrides beyond the name, for the same reasons every MineColonies addon has them:
 * AbstractColonyBlock hardcodes the "minecolonies" namespace in getRegistryName(), and
 * MineColonies' own block-entity type only accepts MineColonies hut blocks.</p>
 *
 * <p>And a third, which a headless test could never have found: <b>a hut block eats the right
 * click.</b> {@code AbstractColonyBlock.useItemOn} opens the hut window and reports the click
 * consumed, so {@code MarkItem.useOn} was never reached and handing a mark in - the step the
 * README, the item tooltip and the mod page all describe - silently opened the window instead.
 * Only a sneak-click got through, and nothing told anyone that.</p>
 */
public class BlockHutHideout extends AbstractBlockHut<BlockHutHideout> {

    @Override
    public String getHutName() {
        return Thieves.HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return Thieves.BUILDING.get();
    }

    @Override
    public @NotNull ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath(Thieves.MODID, Thieves.HUT_NAME);
    }

    /**
     * A mark goes to the mark, everything else opens the hut.
     *
     * <p>PASS_TO_DEFAULT_BLOCK_INTERACTION sends the click on to {@code useWithoutItem}, which
     * nothing in the hut hierarchy overrides, and from there vanilla hands it to the item. So a
     * plain right-click with a mark pins it, a blank mark still gets told it is blank, and a hand
     * holding anything else - or nothing - opens the window exactly as before.</p>
     */
    @Override
    public @NotNull ItemInteractionResult useItemOn(final @NotNull ItemStack stack,
                                                    final @NotNull BlockState state,
                                                    final @NotNull Level level,
                                                    final @NotNull BlockPos pos,
                                                    final @NotNull Player player,
                                                    final @NotNull InteractionHand hand,
                                                    final @NotNull BlockHitResult hit) {
        if (stack.getItem() instanceof MarkItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        final TileEntityColonyBuilding te = Thieves.BUILDING_BE.get().create(pos, state);
        if (te != null) {
            te.registryName = getBuildingEntry().getRegistryName();
        }
        return te;
    }
}
