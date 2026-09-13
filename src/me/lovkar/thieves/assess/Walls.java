package me.lovkar.thieves.assess;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import me.lovkar.thieves.compat.War;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * How walled a colony is, from 0 (open town) to 100 (a closed stone ring).
 *
 * <p>Two answers, in this order. If Colonies at War is installed, its <b>wall register</b> knows
 * exactly - every wall block the mod ships tells the register where it stands and what tier it is,
 * so the number costs nothing and is not a guess. Without it, or for the stretches a player built
 * by hand out of their own blocks, the ring is <b>sampled</b>: bearings out from the town hall,
 * each looking for a column of built, non-natural blocks at least two high, scored for how much of
 * the circle is closed and how tall it stands, and counted at a discount because a hand-laid wall
 * is not a mason's wall.</p>
 *
 * <p>"Built" is a datapack question, not a hardcoded list: anything in
 * {@code #colonist_thieves:not_a_wall} is countryside, however solid it looks. That tag ships with
 * the natural world in it and is somebody else's to extend.</p>
 *
 * <p>Sampling touches a few hundred blocks, so an answer is kept for ten minutes per colony.</p>
 */
public final class Walls {
    private Walls() {
    }

    /** Blocks that are scenery even when they are solid: stone, dirt, sand, logs, leaves, water. */
    public static final TagKey<Block> NOT_A_WALL = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("colonist_thieves", "not_a_wall"));

    private static final int BEARINGS = 48;
    private static final int KEEP_TICKS = 12000;
    /** A wall a player laid by hand is worth this much of the same length of the mod's own wall. */
    private static final float HAND_BUILT = 0.6f;

    private record Cached(int score, long until) {
    }

    private static final Map<Integer, Cached> CACHE = new HashMap<>();

    /** Forget every cached answer - a wall was built or knocked down, or a test wants the truth now. */
    public static void forget() {
        CACHE.clear();
    }

    public static int of(final IColony colony) {
        if (colony == null || !(colony.getWorld() instanceof ServerLevel level)) {
            return 0;
        }
        final int registered = War.walls(colony);
        if (registered >= 0) {
            return registered;
        }
        final long now = level.getGameTime();
        final Cached hit = CACHE.get(colony.getID());
        if (hit != null && hit.until() > now) {
            return hit.score();
        }
        final int score = sample(level, colony);
        if (CACHE.size() > 256) {
            CACHE.clear();
        }
        CACHE.put(colony.getID(), new Cached(score, now + KEEP_TICKS));
        return score;
    }

    /** The ring: how much of the circle round the town is closed, and how tall it stands. */
    private static int sample(final ServerLevel level, final IColony colony) {
        final BlockPos centre = colony.getCenter();
        final int radius = reach(colony, centre);
        if (radius < 12) {
            return 0;
        }
        int closed = 0;
        int height = 0;
        for (int b = 0; b < BEARINGS; b++) {
            final double angle = (Math.PI * 2 * b) / BEARINGS;
            final double dx = Math.cos(angle);
            final double dz = Math.sin(angle);
            int best = 0;
            // look in a band round the built edge - a wall is at the edge, not in the middle of town
            for (int r = Math.max(8, radius - 12); r <= radius + 10; r++) {
                final int x = centre.getX() + (int) Math.round(dx * r);
                final int z = centre.getZ() + (int) Math.round(dz * r);
                if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight() + 1, z))) {
                    continue;
                }
                final int ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        new BlockPos(x, 0, z)).getY();
                int tall = 0;
                for (int y = ground - 1; y >= ground - 8 && tall < 8; y--) {
                    final BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (built(state)) {
                        tall++;
                    } else {
                        break;
                    }
                }
                // count upward from the ground too: a wall stands proud of it
                int up = 0;
                for (int y = ground - 1; y <= ground + 6; y++) {
                    final BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (built(state)) {
                        up++;
                    }
                }
                best = Math.max(best, Math.max(tall, up));
            }
            if (best >= 2) {
                closed++;
                height += Math.min(6, best);
            }
        }
        if (closed == 0) {
            return 0;
        }
        final float coverage = closed / (float) BEARINGS;
        final float tall = (height / (float) closed) / 6f;
        // a closed ring is worth much more than the same length of wall with a hole in it
        final float sealed = coverage > 0.95f ? 1.25f : 1.0f;
        return Math.round(Math.min(100f, 100f * coverage * (0.5f + 0.5f * tall) * sealed * HAND_BUILT));
    }

    /** How far out the town's own buildings reach - the circle a wall would sit on. */
    private static int reach(final IColony colony, final BlockPos centre) {
        int far = 0;
        try {
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
                final BlockPos at = building.getPosition();
                final int d = (int) Math.sqrt(centre.distSqr(at));
                if (d > far) {
                    far = d;
                }
            }
        } catch (final Throwable t) {
            return 0;
        }
        return Math.min(96, far + 6);
    }

    /** Somebody put this here on purpose. */
    private static boolean built(final BlockState state) {
        if (state.isAir() || !state.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return state.is(BlockTags.WALLS) || state.is(BlockTags.FENCES);
        }
        return !state.is(NOT_A_WALL);
    }
}
