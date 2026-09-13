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
 */
public record Target(TargetKind kind, ResourceKey<Level> dimension, BlockPos pos, String name,
                     ResourceLocation structure, int colonyId) {

    private static final String TAG_KIND = "kind";
    private static final String TAG_DIM = "dim";
    private static final String TAG_POS = "pos";
    private static final String TAG_NAME = "name";
    private static final String TAG_STRUCTURE = "structure";
    private static final String TAG_COLONY = "colony";

    /** A place found in the world by its structure. */
    public static Target ofStructure(final TargetKind kind, final ResourceKey<Level> dim, final BlockPos pos,
                                     final String name, final ResourceLocation structure) {
        return new Target(kind, dim, pos, name, structure, 0);
    }

    /** Somebody's colony. The id is what we ask MineColonies about later; the position may move. */
    public static Target ofColony(final ResourceKey<Level> dim, final BlockPos pos, final String name, final int colonyId) {
        return new Target(TargetKind.COLONY, dim, pos, name, null, colonyId);
    }

    /** A Waking World kingdom, named by its own town. */
    public static Target ofKingdom(final ResourceKey<Level> dim, final BlockPos pos, final String name) {
        return new Target(TargetKind.KINGDOM, dim, pos, name, null, 0);
    }

    /** A stable key for heat, standing and the job board - one per place, not one per mark. */
    public String key() {
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
                tag.getInt(TAG_COLONY));
    }
}
