package me.lovkar.thieves;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.lovkar.thieves.assess.Assessment;
import me.lovkar.thieves.assess.Walls;
import me.lovkar.thieves.target.Heat;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.Targets;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /thieves} - what the mod thinks of where you are standing.
 *
 * <p>Kept because "read the log and guess" is a bad way to tune a system whose whole job is to
 * judge places, and because it is how the assessment gets checked against a modpack that changed
 * under us.</p>
 */
public final class ThievesCommands {
    private ThievesCommands() {
    }

    public static void register(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("thieves")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> report(ctx.getSource().getLevel(),
                        BlockPos.containing(ctx.getSource().getPosition()), ctx.getSource()))
                .then(Commands.literal("here")
                        .executes(ctx -> report(ctx.getSource().getLevel(),
                                BlockPos.containing(ctx.getSource().getPosition()), ctx.getSource())))
                .then(Commands.literal("at")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            final int x = IntegerArgumentType.getInteger(ctx, "x");
                                            final int z = IntegerArgumentType.getInteger(ctx, "z");
                                            final ServerLevel level = ctx.getSource().getLevel();
                                            return report(level, new BlockPos(x,
                                                    level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z), z),
                                                    ctx.getSource());
                                        }))))
                .then(Commands.literal("pin")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> pin(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z"), null))
                                        .then(Commands.argument("kind",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(ctx -> pin(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                .getString(ctx, "kind")))))))
                .then(Commands.literal("send")
                        .then(Commands.argument("work",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (final me.lovkar.thieves.target.Work work
                                            : me.lovkar.thieves.target.Work.values()) {
                                        builder.suggest(work.id());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> send(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                .getString(ctx, "work"),
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "z"), null))
                                                .then(Commands.argument("kind",
                                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .executes(ctx -> send(ctx.getSource(),
                                                                com.mojang.brigadier.arguments.StringArgumentType
                                                                        .getString(ctx, "work"),
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                com.mojang.brigadier.arguments.StringArgumentType
                                                                        .getString(ctx, "kind"))))))))
                .then(Commands.literal("intel")
                        .executes(ctx -> intel(ctx.getSource())))
                .then(Commands.literal("log")
                        .executes(ctx -> ledger(ctx.getSource())))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx.getSource())))
                .then(Commands.literal("mode")
                        .then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> mode(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("caution")
                        .then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> caution(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("forget")
                        .executes(ctx -> {
                            Walls.forget();
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("wall scores forgotten - they will be measured again"), false);
                            return 1;
                        })));
    }

    /**
     * Pin a job to the board without crafting a Mark - for an operator testing the mod, and for
     * anybody driving it from outside the game.
     *
     * <p>With a kind given, the place is taken at its word rather than looked up, which is the only
     * way to try a job in a world where the nearest barbarian camp is four thousand blocks away.</p>
     */
    private static int pin(final net.minecraft.commands.CommandSourceStack source, final int x, final int z,
                           final String kind) {
        return send(source, me.lovkar.thieves.target.Work.ROB.id(), x, z, kind);
    }

    /**
     * The same, with a verb.
     *
     * <p>Scouting and sabotage are refused out loud rather than quietly downgraded here: a command
     * is somebody asking for exactly what they typed, and a test that silently got a robbery
     * instead of the sabotage it asked for is a test that proves nothing.</p>
     */
    private static int send(final net.minecraft.commands.CommandSourceStack source, final String verb,
                            final int x, final int z, final String kind) {
        final ServerLevel level = source.getLevel();
        final BlockPos at = new BlockPos(x,
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z), z);
        Target target = Targets.at(level, at);
        if (target == null && kind == null) {
            source.sendFailure(Component.literal("nothing there worth robbing; give a kind to force one"));
            return 0;
        }
        if (kind != null) {
            final me.lovkar.thieves.target.TargetKind forced = me.lovkar.thieves.target.TargetKind.byId(kind);
            target = Target.ofStructure(forced, level.dimension(), at,
                    Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + " at " + x + ", " + z, null);
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level,
                BlockPos.containing(source.getPosition()));
        if (colony == null) {
            source.sendFailure(Component.literal("stand in the colony whose thieves should take the job"));
            return 0;
        }
        me.lovkar.thieves.colony.BuildingHideout hideout = null;
        for (final com.minecolonies.api.colony.buildings.IBuilding building
                : colony.getServerBuildingManager().getBuildings().values()) {
            if (building instanceof me.lovkar.thieves.colony.BuildingHideout found) {
                hideout = found;
                break;
            }
        }
        if (hideout == null) {
            source.sendFailure(Component.literal("that colony has no Hideout"));
            return 0;
        }
        final me.lovkar.thieves.target.Work work = me.lovkar.thieves.target.Work.byId(verb);
        if (!work.id().equalsIgnoreCase(verb)) {
            source.sendFailure(Component.literal("rob, scout or sabotage"));
            return 0;
        }
        if (work.needsWarRoom() && !me.lovkar.thieves.compat.War.hasWarRoom(colony)) {
            source.sendFailure(Component.literal(work.id()
                    + " needs a War Room - build one, or send him to rob"));
            return 0;
        }
        hideout.pin(new me.lovkar.thieves.target.Job(target, work));
        final Target pinned = target;
        final int onBoard = hideout.board().size();
        source.sendSuccess(() -> Component.literal("pinned to the board: " + work.id() + " "
                + pinned.name() + " (" + pinned.kind().id() + ")  the board now holds "
                + onBoard + " job(s)"), false);
        return 1;
    }

    /**
     * What this colony has learned by sending people to look.
     *
     * <p>Scouting and sabotage both leave a reading that fades, and a fading number is the hardest
     * kind of thing to believe from the outside. This prints it.</p>
     */
    private static int intel(final net.minecraft.commands.CommandSourceStack source) {
        final me.lovkar.thieves.colony.BuildingHideout hideout = hideoutAt(source);
        if (hideout == null) {
            source.sendFailure(Component.literal("stand in a colony that has a Hideout"));
            return 0;
        }
        final ServerLevel level = source.getLevel();
        final java.util.List<me.lovkar.thieves.colony.SightingsModule.Sighting> all =
                hideout.getModuleMatching(me.lovkar.thieves.colony.SightingsModule.class, m -> true)
                        .everything(level, hideout.getColony());
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("the hut has heard of nowhere yet"), false);
            return 0;
        }
        final StringBuilder out = new StringBuilder("what this colony knows:");
        int told = 0;
        for (final me.lovkar.thieves.colony.SightingsModule.Sighting sighting : all) {
            final String key = sighting.target().key();
            final float known = me.lovkar.thieves.intel.Intel.known(level, key);
            final float shaken = me.lovkar.thieves.intel.Intel.shaken(level, key);
            if (known <= 0f && shaken <= 0f) {
                continue;
            }
            told++;
            out.append("\n  ").append(sighting.target().name())
                    .append(" - scouted ").append(Math.round(100f * known / me.lovkar.thieves.intel.Intel.KNOWN))
                    .append("%, shaken ").append(Math.round(100f * shaken / me.lovkar.thieves.intel.Intel.SHAKEN))
                    .append("%");
        }
        if (told == 0) {
            out.append("\n  nothing - nobody has been out to look or to break anything");
        }
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return told;
    }

    /**
     * The Hideout's standing orders, from a command.
     *
     * <p>The same three the hut window offers, so an operator - or a test that has no hands - can
     * set them. <b>bold</b> is what a thief needs to be told before he will walk into a place that
     * will obviously catch him, which is exactly the case worth testing.</p>
     */
    private static int caution(final net.minecraft.commands.CommandSourceStack source, final String mode) {
        final String value = switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "bold" -> me.lovkar.thieves.colony.BuildingHideout.CAUTION_BOLD;
            case "quiet" -> me.lovkar.thieves.colony.BuildingHideout.CAUTION_QUIET;
            case "careful" -> me.lovkar.thieves.colony.BuildingHideout.CAUTION_CAREFUL;
            default -> null;
        };
        if (value == null) {
            source.sendFailure(Component.literal("bold, careful or quiet"));
            return 0;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(source.getLevel(),
                BlockPos.containing(source.getPosition()));
        if (colony == null) {
            source.sendFailure(Component.literal("stand in a colony"));
            return 0;
        }
        int changed = 0;
        for (final com.minecolonies.api.colony.buildings.IBuilding building
                : colony.getServerBuildingManager().getBuildings().values()) {
            if (!(building instanceof me.lovkar.thieves.colony.BuildingHideout hideout)) {
                continue;
            }
            try {
                hideout.getSetting(me.lovkar.thieves.colony.BuildingHideout.CAUTION).set(value);
                hideout.markDirty();
                changed++;
            } catch (final Throwable t) {
                source.sendFailure(Component.literal("could not set it: " + t));
                return 0;
            }
        }
        if (changed == 0) {
            source.sendFailure(Component.literal("that colony has no Hideout"));
            return 0;
        }
        final int count = changed;
        source.sendSuccess(() -> Component.literal("standing orders now '" + mode.toLowerCase(java.util.Locale.ROOT)
                + "' at " + count + " Hideout(s)"), false);
        return count;
    }

    /** What the Hideout remembers: the last twenty nights, newest first. */
    private static int ledger(final net.minecraft.commands.CommandSourceStack source) {
        final me.lovkar.thieves.colony.BuildingHideout hideout = hideoutAt(source);
        if (hideout == null) {
            source.sendFailure(Component.literal("stand in a colony that has a Hideout"));
            return 0;
        }
        final java.util.List<me.lovkar.thieves.colony.BuildingHideout.Entry> past = hideout.ledger();
        if (past.isEmpty()) {
            source.sendSuccess(() -> Component.literal("the log is empty - nobody has been out yet"), false);
            return 0;
        }
        final StringBuilder out = new StringBuilder("the Hideout's log, newest first:");
        for (final me.lovkar.thieves.colony.BuildingHideout.Entry e : past) {
            out.append("\n  ").append(e.line());
        }
        out.append("\n  on the board now: ").append(hideout.board().size()).append(" job(s)");
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return past.size();
    }

    /** Wait to be told, or go looking. */
    private static int mode(final net.minecraft.commands.CommandSourceStack source, final String mode) {
        final String value = switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "auto" -> me.lovkar.thieves.colony.BuildingHideout.MODE_AUTO;
            case "manual" -> me.lovkar.thieves.colony.BuildingHideout.MODE_MANUAL;
            default -> null;
        };
        if (value == null) {
            source.sendFailure(Component.literal("auto or manual"));
            return 0;
        }
        final me.lovkar.thieves.colony.BuildingHideout hideout = hideoutAt(source);
        if (hideout == null) {
            source.sendFailure(Component.literal("stand in a colony that has a Hideout"));
            return 0;
        }
        try {
            hideout.getSetting(me.lovkar.thieves.colony.BuildingHideout.WORK_MODE).set(value);
            hideout.markDirty();
        } catch (final Throwable t) {
            source.sendFailure(Component.literal("could not set it: " + t));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("the Hideout now works "
                + mode.toLowerCase(java.util.Locale.ROOT)), false);
        return 1;
    }

    /**
     * What the hut's Statistics tab is holding, read out where a test can see it.
     *
     * <p>The tab itself cannot be opened without hands, and a counter that is written but never
     * read is not a tested counter. This prints the same two numbers the window computes for every
     * id it knows: the all-time total, and the value inside the window the tab opens on - which is
     * <em>yesterday</em>, and is the reason a hut with a month of history can still greet you with
     * an empty panel.</p>
     *
     * <p>The label goes through {@link Component#translatable} on purpose: a key our lang file has
     * missed comes out as the raw key, so this doubles as the proof that the eight labels resolve.
     * </p>
     */
    private static int stats(final net.minecraft.commands.CommandSourceStack source) {
        final me.lovkar.thieves.colony.BuildingHideout hideout = hideoutAt(source);
        if (hideout == null) {
            source.sendFailure(Component.literal("stand in a colony that has a Hideout"));
            return 0;
        }
        final com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule module =
                hideout.getModuleMatching(
                        com.minecolonies.core.colony.buildings.modules.BuildingStatisticsModule.class,
                        m -> true);
        if (module == null) {
            source.sendFailure(Component.literal("this Hideout has no statistics module"));
            return 0;
        }
        final com.minecolonies.api.colony.managers.interfaces.IStatisticsManager manager =
                module.getBuildingStatisticsManager();
        final java.util.List<String> ids = new java.util.ArrayList<>(manager.getStatTypes());
        java.util.Collections.sort(ids);
        final int day = hideout.getColony().getDay();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "nothing counted yet (colony day " + day + ") - the tab would say No statistics found"),
                    false);
            return 0;
        }
        final net.minecraft.network.chat.MutableComponent out =
                Component.literal("Hideout statistics, colony day " + day + ":");
        int shownByDefault = 0;
        for (final String id : ids) {
            final int total = manager.getStatTotal(id);
            final int yesterday = manager.getStatsInPeriod(id, day - 1, day);
            if (yesterday > 0) {
                shownByDefault++;
            }
            out.append(Component.literal("\n  "))
                    .append(Component.translatable(
                            "com.minecolonies.coremod.gui.townhall.stats." + id, total))
                    .append(Component.literal("   [id " + id + ", yesterday " + yesterday + "]"));
        }
        final int visible = shownByDefault;
        out.append(Component.literal("\n  -> the tab opens on yesterday and hides zeroes: "
                + visible + " of " + ids.size() + " rows would show"));
        source.sendSuccess(() -> out, false);
        return ids.size();
    }

    /** The first Hideout of the colony the command was typed in, or null. */
    private static me.lovkar.thieves.colony.BuildingHideout hideoutAt(
            final net.minecraft.commands.CommandSourceStack source) {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(source.getLevel(),
                BlockPos.containing(source.getPosition()));
        if (colony == null) {
            return null;
        }
        for (final com.minecolonies.api.colony.buildings.IBuilding building
                : colony.getServerBuildingManager().getBuildings().values()) {
            if (building instanceof me.lovkar.thieves.colony.BuildingHideout hideout) {
                return hideout;
            }
        }
        return null;
    }

    private static int report(final ServerLevel level, final BlockPos pos,
                              final net.minecraft.commands.CommandSourceStack source) {
        final Target target = Targets.at(level, pos);
        if (target == null) {
            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
            if (colony == null) {
                source.sendSuccess(() -> Component.literal("nothing here worth robbing"), false);
                return 0;
            }
        }
        final Assessment assessment = Assessment.of(level, target);
        final int heat = target == null ? 0 : Heat.of(level, target.key());
        source.sendSuccess(() -> Component.literal(
                (target == null ? "somewhere" : target.kind().id() + "  " + target.name())
                        + "\n  " + assessment.line()
                        + "\n  heat " + heat
                        + "\n  a thief rolls against " + Math.round(assessment.vigilance()
                        * (target == null ? 1f : target.kind().difficulty()))
                        + ", an army against " + assessment.strength()), false);
        return 1;
    }
}
