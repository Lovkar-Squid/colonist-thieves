package me.lovkar.thieves.target;

import net.minecraft.resources.ResourceLocation;

/**
 * What sort of place a mark points at.
 *
 * <p>The kind is not hardcoded per structure: the world is asked which structures stand at a
 * position and the answer is matched against our own structure tags, so a datapack - ours, a
 * modpack's, anybody's - can make a new structure robbable without a line of code. The kind only
 * decides the shape of the job: how hard it is by default, which loot table is rolled, and whether
 * there is anybody there with a diplomatic opinion about being robbed.</p>
 */
public enum TargetKind {
    /** A raider camp: MineColonies' barbarian, amazon and desert camps, the pirate ship, a pillager outpost. */
    CAMP("camp", "colonist_thieves:camp", 1.0f, false),
    /** A village. Poorer, softer, and the iron golem is the whole problem. */
    VILLAGE("village", "colonist_thieves:village", 0.8f, false),
    /** A hoard: a temple, a mansion, a stronghold - somewhere with a lot in it and nobody to miss it. */
    HOARD("hoard", "colonist_thieves:hoard", 1.3f, false),
    /** A Waking World kingdom. It has a standing with you, and catapults. */
    KINGDOM("kingdom", null, 1.2f, true),
    /** A MineColonies colony - somebody's town, and on a server somebody's feelings. */
    COLONY("colony", null, 1.1f, true),
    /**
     * A Colonies at War convoy on the road: a moving target with no place of its own. Its defence
     * is the escort riding with it, so the kind itself adds nothing - a convoy nobody guards is
     * the easiest night there is. It belongs to the colony that sent it, and that colony can hold
     * a grudge. Found through the war mod, never by position: see {@link Convoys}.
     */
    CONVOY("convoy", null, 1.0f, true);

    private final String id;
    private final String tag;
    private final float difficulty;
    private final boolean diplomatic;

    TargetKind(final String id, final String tag, final float difficulty, final boolean diplomatic) {
        this.id = id;
        this.tag = tag;
        this.difficulty = difficulty;
        this.diplomatic = diplomatic;
    }

    public String id() {
        return id;
    }

    /** The structure tag that marks this kind in the world, or null for kinds found another way. */
    public ResourceLocation tag() {
        return tag == null ? null : ResourceLocation.parse(tag);
    }

    /** A multiplier on how hard this kind is to rob before anything about the place is read. */
    public float difficulty() {
        return difficulty;
    }

    /** True if there is somebody there who can hold a grudge - a colony or a kingdom, not a camp. */
    public boolean diplomatic() {
        return diplomatic;
    }

    /**
     * The loot table rolled for this kind when nothing more specific exists. A convoy has none:
     * what comes home is the convoy's own cargo, taken off it through the war mod.
     */
    public ResourceLocation lootTable() {
        return ResourceLocation.fromNamespaceAndPath("colonist_thieves", "theft/" + id);
    }

    public static TargetKind byId(final String id) {
        for (final TargetKind k : values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return CAMP;
    }
}
