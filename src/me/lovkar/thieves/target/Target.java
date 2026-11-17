package me.lovkar.thieves.target;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Somewhere worth robbing: what it is, where it is, and what to call it.
 *
 * <p>A mark carries one of these; so does a job on the Hideout's board and an expedition in
 * flight. {@link #structure} is the worldgen structure the place was recognised by, kept so a
 * datapack can give one structure its own loot table without inventing a new kind.</p>
 *
 * <p>A {@link TargetKind#CONVOY convoy} is the one target that moves. Its {@link #convoy} is the
 * war mod's id for it, which is what everything asks about later; {@link #colonyId} is the colony
 * that sent it, which is who holds the grudge; and {@link #pos} is only where it was when somebody
 * last looked.</p>
 */
public record Target(TargetKind kind, ResourceKey<Level> dimension, BlockPos pos, String name,
                     ResourceLocation structure, int colonyId, long convoy) {

    private static final String TAG_KIND = "kind";
    private static final String TAG_DIM = "dim";
    private static final String TAG_POS = "pos";
    private static final String TAG_NAME = "name";
    private static final String TAG_STRUCTURE = "structure";
    private static final String TAG_COLONY = "colony";
    private static final String TAG_CONVOY = "convoy";

    /** A place found in the world by its structure. */
    public static Target ofStructure(final TargetKind kind, final ResourceKey<Level> dim, final BlockPos pos,
                                     final String name, final ResourceLocation structure) {
        return new Target(kind, dim, pos, name, structure, 0, 0L);
    }

    /** Somebody's colony. The id is what we ask MineColonies about later; the position may move. */
    public static Target ofColony(final ResourceKey<Level> dim, final BlockPos pos, final String name, final int colonyId) {
        return new Target(TargetKind.COLONY, dim, pos, name, null, colonyId, 0L);
    }

    /** A Waking World kingdom, named by its own town. */
    public static Target ofKingdom(final ResourceKey<Level> dim, final BlockPos pos, final String name) {
        return new Target(TargetKind.KINGDOM, dim, pos, name, null, 0, 0L);
    }

    /**
     * A convoy on the road, by the war mod's id for it.
     *
     * @param pos         where it is now - it will not be there for long
     * @param fromColony  the colony that sent it: the one that minds if he is caught or seen
     */
    public static Target ofConvoy(final ResourceKey<Level> dim, final BlockPos pos, final String name,
                                  final long convoyId, final int fromColony) {
        return new Target(TargetKind.CONVOY, dim, pos, name, null, fromColony, convoyId);
    }

    /** The same convoy somewhere else along its road. */
    public Target movedTo(final BlockPos now) {
        return new Target(kind, dimension, now, name, structure, colonyId, convoy);
    }

    /**
     * True if this and that are the same place - by position, as always, except for a convoy,
     * which is the same convoy wherever it has got to and never the same as anything else.
     */
    public boolean samePlace(final Target other) {
        if (other == null) {
            return false;
        }
        if (convoy != 0L || other.convoy != 0L) {
            return convoy == other.convoy;
        }
        return pos.equals(other.pos);
    }

    /** A stable key for heat, standing and the job board - one per place, not one per mark. */
    public String key() {
        if (convoy != 0L) {
            // before the colony: a convoy carries its sender's id, and its heat is not the town's
            return "convoy/" + convoy;
        }
        if (colonyId != 0) {
            return "colony/" + colonyId;
        }
        return kind.id() + "/" + dimension.location() + "/" + (pos.getX() >> 5) + "/" + (pos.getZ() >> 5);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString(TAG_KIND, kind.id());
        tag.putString(TAG_DIM, dimension.location().toString());
        tag.putLong(TAG_POS, pos.asLong());
        tag.putString(TAG_NAME, name == null ? "" : name);
        if (structure != null) {
            tag.putString(TAG_STRUCTURE, structure.toString());
        }
        if (colonyId != 0) {
            tag.putInt(TAG_COLONY, colonyId);
        }
        if (convoy != 0L) {
            tag.putLong(TAG_CONVOY, convoy);
        }
        return tag;
    }

    public static Target load(final CompoundTag tag) {
        if (tag == null || !tag.contains(TAG_KIND)) {
            return null;
        }
        final ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(tag.getString(TAG_DIM)));
        return new Target(TargetKind.byId(tag.getString(TAG_KIND)), dim, BlockPos.of(tag.getLong(TAG_POS)),
                tag.getString(TAG_NAME),
                tag.contains(TAG_STRUCTURE) ? ResourceLocation.parse(tag.getString(TAG_STRUCTURE)) : null,
                tag.getInt(TAG_COLONY),
                // a 0.1.1 save has no convoys, and a missing tag reads as nought: not a convoy
                tag.getLong(TAG_CONVOY));
    }
}
