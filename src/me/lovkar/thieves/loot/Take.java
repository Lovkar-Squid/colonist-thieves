package me.lovkar.thieves.loot;

import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.target.Target;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * What comes home in the sack.
 *
 * <p>Every item a thief ever steals comes out of a <b>datapack loot table</b>, never out of a list
 * in this class. That was the call made before a line of the mod was written, and it is the
 * difference between a mod and a mod other people build on: a modpack can decide what a barbarian
 * camp is worth robbing for without touching Java, and a mod that adds a new kind of settlement
 * can make it robbable with one json file.</p>
 *
 * <p>Two tables are tried, in this order:</p>
 * <ol>
 *   <li>{@code colonist_thieves:theft/structure/&lt;namespace&gt;/&lt;path&gt;} - this exact place,
 *       for a pack that wants a pillager outpost to give different things from a barbarian camp;</li>
 *   <li>{@code colonist_thieves:theft/&lt;kind&gt;} - the kind's own table, which ships with the mod.</li>
 * </ol>
 *
 * <p>Luck carries the quality of the job: a clean night on a rich town rolls more, and rolls
 * better, than a job the thief was lucky to walk away from.</p>
 */
public final class Take {
    private Take() {
    }

    /** At best this many rolls of the table; a bad job gets one. */
    private static final int MAX_ROLLS = 6;

    public static List<ItemStack> roll(final ServerLevel level, final Target target, final float quality,
                                       final int hideoutLevel, final RandomSource random) {
        final List<ItemStack> out = new ArrayList<>();
        final LootTable table = tableFor(level, target);
        if (table == null) {
            Thieves.LOGGER.warn("no loot table for {} - the thief comes home empty", target.kind().id());
            return out;
        }
        final LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(target.pos()))
                .withLuck(quality * 4f + hideoutLevel * 0.5f)
                .create(LootContextParamSets.CHEST);
        final int rolls = Math.max(1, Math.round(quality * (MAX_ROLLS - 1) + 1));
        for (int i = 0; i < rolls; i++) {
            out.addAll(table.getRandomItems(params, random));
        }
        return out;
    }

    private static LootTable tableFor(final ServerLevel level, final Target target) {
        if (target.structure() != null) {
            final ResourceLocation exact = ResourceLocation.fromNamespaceAndPath(Thieves.MODID,
                    "theft/structure/" + target.structure().getNamespace() + "/" + target.structure().getPath());
            final LootTable specific = lookUp(level, exact);
            if (specific != null && specific != LootTable.EMPTY) {
                return specific;
            }
        }
        return lookUp(level, target.kind().lootTable());
    }

    private static LootTable lookUp(final ServerLevel level, final ResourceLocation id) {
        try {
            return level.getServer().reloadableRegistries()
                    .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        } catch (final Throwable t) {
            return null;
        }
    }
}
