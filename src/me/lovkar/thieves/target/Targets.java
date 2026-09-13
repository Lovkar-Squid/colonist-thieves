package me.lovkar.thieves.target;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import me.lovkar.thieves.compat.Kingdoms;
import me.lovkar.thieves.Thieves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Recognising a place worth robbing.
 *
 * <p>Order matters: a colony first (somebody lives there and can object), then a Waking World
 * kingdom, then whatever worldgen structure stands here. Structures are matched against our own
 * tags - {@code #colonist_thieves:hoard}, {@code #colonist_thieves:camp},
 * {@code #colonist_thieves:village} - so a datapack can make any structure in any mod robbable
 * without touching this class. The tags ship filled in with MineColonies' camps and the vanilla
 * set; everything else is somebody else's to add.</p>
 */
public final class Targets {
    private Targets() {
    }

    /** What is here, or null if this is just countryside. */
    public static Target at(final ServerLevel level, final BlockPos pos) {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony != null) {
            return Target.ofColony(level.dimension(), colony.getCenter(), colony.getName(), colony.getID());
        }
        final String kingdom = Kingdoms.nameAt(level, pos);
        if (kingdom != null) {
            return Target.ofKingdom(level.dimension(), pos, kingdom);
        }
        // the richest reading wins: a mansion inside a village is a mansion
        for (final TargetKind kind : new TargetKind[]{TargetKind.HOARD, TargetKind.CAMP, TargetKind.VILLAGE}) {
            final ResourceLocation found = structureOf(level, pos, kind);
            if (found != null) {
                return Target.ofStructure(kind, level.dimension(), pos, pretty(found), found);
            }
        }
        return null;
    }

    /**
     * The id of a structure of this kind at the position, or null.
     *
     * <p>Deliberately forgiving. The first version asked
     * {@code getStructureWithPieceAt}, which is true only when the exact block is inside an actual
     * building piece - and a village is mostly the gaps between its houses, so standing in the
     * square and using a Mark did nothing. A player who is <i>in</i> a village means the village.
     * So the question asked here is the chunk-level one: which structures does this chunk belong
     * to. A hit on a real piece still wins, for the case of a mansion inside a village.</p>
     */
    private static ResourceLocation structureOf(final ServerLevel level, final BlockPos pos, final TargetKind kind) {
        final ResourceLocation tagId = kind.tag();
        if (tagId == null) {
            return null;
        }
        try {
            final Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            final Optional<HolderSet.Named<Structure>> holders =
                    registry.getTag(TagKey.create(Registries.STRUCTURE, tagId));
            if (holders.isEmpty() || holders.get().size() == 0) {
                // a tag that is not there at all is a datapack problem, and silence is how it hid
                if (!warned) {
                    warned = true;
                    Thieves.LOGGER.warn("[targets] the structure tag {} is empty or missing - nothing of that"
                            + " kind will ever be recognised", tagId);
                }
                return null;
            }
            final Set<Structure> wanted = new HashSet<>();
            for (final Holder<Structure> holder : holders.get()) {
                wanted.add(holder.value());
            }
            // a piece under our feet is the best answer
            for (final Structure structure : wanted) {
                final StructureStart exact = level.structureManager().getStructureWithPieceAt(pos, structure);
                if (exact != null && exact.isValid()) {
                    return registry.getKey(structure);
                }
            }
            // otherwise: is this chunk part of one at all
            for (final Structure structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
                if (wanted.contains(structure)) {
                    return registry.getKey(structure);
                }
            }
            return null;
        } catch (final Throwable t) {
            if (!warned) {
                warned = true;
                Thieves.LOGGER.warn("[targets] cannot read structures at {}: {}", pos.toShortString(), t.toString());
            }
            return null;
        }
    }

    /** Said once per run: a broken tag should be loud the first time and quiet afterwards. */
    private static volatile boolean warned = false;

    /** "minecolonies:barbarian_camp" reads as "Barbarian Camp" on a mark. */
    private static String pretty(final ResourceLocation id) {
        final String[] words = id.getPath().split("_");
        final StringBuilder out = new StringBuilder();
        for (final String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.length() == 0 ? id.toString() : out.toString();
    }
}
