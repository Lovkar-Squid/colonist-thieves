package me.lovkar.thieves.item;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.ThievesConfig;
import me.lovkar.thieves.block.BlockHutHideout;
import me.lovkar.thieves.colony.BuildingHideout;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.TargetKind;
import me.lovkar.thieves.target.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The Mark: a scrap of paper with a place on it.
 *
 * <p>Blank, it is worth nothing. Use it on somewhere worth robbing - a barbarian camp, a village,
 * a temple, a Waking World kingdom, a neighbour's colony where the server allows it - and it
 * remembers what and where. Use the marked paper on a <b>Hideout</b> and it goes up on the board;
 * the next thief on shift takes it down.</p>
 *
 * <p>A mark is deliberately a physical thing that can be crafted, carried, stolen and lost. It is
 * how a player says "that one" without a map screen, and later it is what a scout brings home.</p>
 */
public class MarkItem extends Item {
    private static final String TAG_TARGET = "thieves_target";

    public MarkItem(final Properties properties) {
        super(properties);
    }

    /** The place this mark points at, or null while it is still blank. */
    public static Target targetOf(final ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        final CompoundTag tag = data.copyTag();
        return tag.contains(TAG_TARGET) ? Target.load(tag.getCompound(TAG_TARGET)) : null;
    }

    private static void bind(final ItemStack stack, final Target target) {
        final CompoundTag tag = new CompoundTag();
        tag.put(TAG_TARGET, target.save());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public @NotNull InteractionResult useOn(final UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        final Player player = context.getPlayer();
        final ItemStack stack = context.getItemInHand();
        final BlockPos clicked = context.getClickedPos();
        final Target held = targetOf(stack);

        // handing a mark in at a Hideout
        if (level.getBlockState(clicked).getBlock() instanceof BlockHutHideout) {
            if (held == null) {
                say(player, "This mark is blank. Use it on somewhere worth robbing first.");
                return InteractionResult.FAIL;
            }
            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, clicked);
            final BuildingHideout hideout = colony == null ? null
                    : colony.getServerBuildingManager().getBuilding(clicked, BuildingHideout.class);
            if (hideout == null) {
                say(player, "That Hideout is not built yet.");
                return InteractionResult.FAIL;
            }
            if (held.kind() == TargetKind.COLONY && held.colonyId() == colony.getID()) {
                say(player, "Your own colony. Find somebody else's.");
                return InteractionResult.FAIL;
            }
            hideout.pin(held);
            stack.shrink(1);
            say(player, "Pinned to the board: " + held.name() + ".");
            return InteractionResult.CONSUME;
        }

        if (held != null) {
            say(player, "This mark already points at " + held.name() + ".");
            return InteractionResult.FAIL;
        }

        final Target found = Targets.at(level, clicked);
        if (found == null) {
            say(player, "Nothing here worth robbing.");
            return InteractionResult.FAIL;
        }
        if (found.kind() == TargetKind.COLONY) {
            final IColony mine = IColonyManager.getInstance().getIColonyByOwner(level, player);
            if (mine != null && mine.getID() == found.colonyId()) {
                say(player, "Your own colony. Find somebody else's.");
                return InteractionResult.FAIL;
            }
            if (!ThievesConfig.allowPlayerColonyTheft()) {
                say(player, "This server does not allow robbing other colonies.");
                return InteractionResult.FAIL;
            }
        }
        bind(stack, found);
        say(player, "Marked: " + found.name() + ".");
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(final @NotNull ItemStack stack, final @NotNull TooltipContext context,
                                final @NotNull List<Component> lines, final @NotNull TooltipFlag flag) {
        final Target target = targetOf(stack);
        if (target == null) {
            lines.add(Component.literal("Blank - use it on somewhere worth robbing")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.add(Component.literal(target.name()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal(target.kind().id() + "  " + target.pos().getX() + ", " + target.pos().getZ())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void say(final Player player, final String text) {
        player.displayClientMessage(Component.literal(text), false);
    }
}
