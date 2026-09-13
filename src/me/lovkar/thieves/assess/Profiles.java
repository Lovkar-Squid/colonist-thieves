package me.lovkar.thieves.assess;

import me.lovkar.thieves.compat.Kingdoms;
import me.lovkar.thieves.target.Heat;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.TargetKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The same assessment, for places that are not colonies.
 *
 * <p>A camp has no building manager and a village keeps no books, so their numbers come from what
 * is standing there: how many armed bodies are within a stone's throw, how many villagers, whether
 * an iron golem is walking the fence. That is still "what is actually built" - it is just that for
 * a camp the garrison is the barbarians, not a list of hires.</p>
 *
 * <p>The counts are taken from loaded chunks only. If nobody is near enough for the place to be
 * loaded, the profile falls back to the kind's own average, which is what a thief casing it from
 * a distance would have to go on anyway.</p>
 */
public final class Profiles {
    private Profiles() {
    }

    /** How far round a mark counts as "the place". */
    private static final int REACH = 48;

    public static Assessment structure(final ServerLevel level, final Target target) {
        final TargetKind kind = target.kind();
        final BlockPos at = target.pos();
        final int heat = Heat.of(level, target.key());
        // "is the chunk there" is the wrong question. A chunk two hundred blocks out answers it
        // yes - it is loaded far enough to say which structure it belongs to - while its villagers,
        // its golem and its barbarians are not ticking and therefore not there to count. Counting
        // them anyway reads a full village as deserted and offers the thief a free night. So the
        // question is whether entities are actually running here; if they are not, the kind's own
        // average is the honest answer, and it is what a thief casing the place from a hill would
        // have to go on too.
        if (!level.isPositionEntityTicking(at)) {
            return typical(target, heat);
        }
        final AABB box = new AABB(at).inflate(REACH);
        final List<Monster> armed = level.getEntitiesOfClass(Monster.class, box, m -> m.isAlive());
        final List<Villager> folk = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive());
        final List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, box, g -> g.isAlive());

        final int garrison = armed.size() + golems.size() * 3;
        final int wealth = switch (kind) {
            case HOARD -> 70 + Math.min(20, armed.size() * 2);
            case CAMP -> 30 + Math.min(30, armed.size() * 3);
            case VILLAGE -> 20 + Math.min(40, folk.size() * 4);
            default -> 30;
        };
        // a camp posts no watch; a village has a golem and a bell; a hoard has whatever guards it
        final int watch = switch (kind) {
            case CAMP -> 6 + armed.size();
            case VILLAGE -> 4 + golems.size() * 8;
            case HOARD -> 12 + armed.size() * 2;
            default -> 8;
        } + heat;
        final int softness = kind == TargetKind.VILLAGE ? 30 : 10;
        return new Assessment(target.name(), kind, garrison, 0, garrisonLevel(kind), 0,
                garrison * 3, Math.min(100, wealth), Math.min(100, watch), softness);
    }

    /** What the kind is like on average, for a place whose people are not loaded. */
    private static Assessment typical(final Target target, final int heat) {
        final TargetKind kind = target.kind();
        return switch (kind) {
            case HOARD -> new Assessment(target.name(), kind, 6, 0, 3, 10, 30, 75, 20 + heat, 10);
            case CAMP -> new Assessment(target.name(), kind, 5, 0, 2, 5, 25, 45, 12 + heat, 10);
            case VILLAGE -> new Assessment(target.name(), kind, 1, 0, 0, 0, 5, 35, 8 + heat, 30);
            default -> new Assessment(target.name(), kind, 3, 0, 1, 0, 10, 40, 12 + heat, 20);
        };
    }

    private static int garrisonLevel(final TargetKind kind) {
        return switch (kind) {
            case HOARD -> 3;
            case CAMP -> 2;
            default -> 0;
        };
    }

    /**
     * A Waking World kingdom, which does not have to be measured at all: the town already keeps
     * its own tier, its own wall arcs, its own catapults and its own houses, and those are exactly
     * the four numbers this wants.
     */
    public static Assessment kingdom(final ServerLevel level, final Target target, final Object kingdom) {
        final int heat = Heat.of(level, target.key());
        if (kingdom == null) {
            return new Assessment(target.name(), TargetKind.KINGDOM, 4, 0, 2, 20, 30, 55, 15 + heat, 15);
        }
        final int tier = Kingdoms.tier(kingdom);
        final int arcs = Kingdoms.wallArcs(kingdom);
        final int catapults = Kingdoms.catapults(kingdom);
        final int houses = Kingdoms.houses(kingdom);
        final int garrison = 2 + tier * 2 + catapults;
        final int walls = Math.min(100, arcs * 12);
        final int wealth = Math.min(100, 25 + tier * 12 + houses);
        final int watch = Math.min(100, 8 + tier * 4 + catapults * 3 + heat);
        return new Assessment(Kingdoms.name(kingdom), TargetKind.KINGDOM, garrison, 0, tier * 2,
                walls, garrison * 5, wealth, watch, Math.max(0, 20 - tier * 4));
    }

    /** A colony we have a mark for but cannot read right now - it is in an unloaded dimension. */
    public static Assessment unreadableColony(final Target target) {
        return new Assessment(target.name(), TargetKind.COLONY, 4, 0, 3, 10, 40, 50, 20, 15);
    }
}
