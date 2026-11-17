package me.lovkar.convoygate;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.mojang.authlib.GameProfile;
import me.lovkar.thieves.Thieves;
import me.lovkar.thieves.ThievesConfig;
import me.lovkar.thieves.ai.EntityAIWorkThief;
import me.lovkar.thieves.assess.Assessment;
import me.lovkar.thieves.assess.Profiles;
import me.lovkar.thieves.colony.BuildingHideout;
import me.lovkar.thieves.colony.JobThief;
import me.lovkar.thieves.colony.SightingsModule;
import me.lovkar.thieves.colony.ThievesResearch;
import me.lovkar.thieves.compat.War;
import me.lovkar.thieves.job.Heist;
import me.lovkar.thieves.job.Interception;
import me.lovkar.thieves.job.Outcome;
import me.lovkar.thieves.network.SendThereMessage;
import me.lovkar.thieves.target.Convoys;
import me.lovkar.thieves.target.Heat;
import me.lovkar.thieves.target.Job;
import me.lovkar.thieves.target.Target;
import me.lovkar.thieves.target.TargetKind;
import me.lovkar.thieves.target.Work;
import me.lovkar.war.Warfare;
import me.lovkar.war.api.WarBridge;
import me.lovkar.war.campaign.Campaigns;
import me.lovkar.war.colony.ConvoyModule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Rig gate for T1: a thief intercepting a Colonies at War convoy.
 *
 * <p>Two colonies 400 blocks apart. Oakford has a town hall and a Hideout with one thief hired into
 * it; Ironhold has a town hall and a War Room with two knights, 128 iron and 32 bread on the
 * manifest, and sends them to Oakford through {@code Campaigns.convoy}, the war table's own call -
 * "a convoy from Ironhold to Oakford". The convoy's clock is then moved along its road by hand, and
 * everything the thief does with it goes through the mod's own code: the Hideout's list
 * ({@code SightingsModule.everything}), the Send button's server half ({@code SendThereMessage.send}),
 * the board ({@code takeJob}), setting out ({@code Convoys.onTheRoad}, {@code Heist.begin}), meeting
 * it on the road ({@code Interception.intercept}, the call the thief's AI makes when he reaches it)
 * and the books once he is home ({@code EntityAIWorkThief.reckon}, the call his AI makes then).</p>
 *
 * <p>The thief is hired but not spawned: his AI never runs here, so nothing takes a job the gate
 * pins before the gate does, and outcomes are forced by fixing the one draw of luck in the roll
 * rather than by trying seeds. What it proves: the convoy is offered only within range (which grows
 * with the Hideout level) and never to its own colony; Send pins it as a robbery by id; the job
 * survives a save; a bigger escort raises the catch chance; seen and clean runs take cargo through
 * {@code robConvoy} - the clean one with no standing change, the seen one with a fall - and a caught
 * one takes nothing and costs more; a convoy out of reach or arrived is "the road was empty"; with a
 * 0.2.2-style war mod (the four convoy lookups simulated away) nothing is offered and nothing throws;
 * and the two commands.</p>
 */
@Mod("convoygate")
public final class ConvoyGate {
    private static final Logger LOGGER = LoggerFactory.getLogger("convoygate");
    private static final int SETUP = 40;
    private static final int HIRE = 60;
    private static final int SEND = 140;
    private static final int TIMEOUT = 600;
    private static final int DISTANCE = 400;
    private static final int IRON = 128;
    private static final int BREAD = 32;
    private static final int TOTAL = IRON + BREAD;
    private static final String NAME = "a convoy from Ironhold to Millbrook";

    private int tick;
    private MinecraftServer server;
    private ServerLevel level;
    private IColony home;
    private IColony sender;
    private IColony market;
    private BlockPos homeCentre;
    private BuildingHideout hideout;
    private IBuilding warRoom;
    private ICitizenData thief;
    private int fails;
    private int checks;
    private boolean finished;

    public ConvoyGate(final IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    private void check(final String what, final boolean ok, final Object detail) {
        checks++;
        if (!ok) {
            fails++;
        }
        LOGGER.info("[convoygate] CHECK {}: {} {}", what, ok ? "OK" : "FAIL", detail);
    }

    private void onTick(final ServerTickEvent.Post e) {
        if (finished) {
            return;
        }
        tick++;
        server = e.getServer();
        try {
            if (tick == SETUP) {
                setUp();
            } else if (tick == HIRE) {
                hire();
            } else if (tick == SEND) {
                road();
                finish();
            }
            if (!finished && tick >= TIMEOUT) {
                check("the gate ran to the end", false, "timed out at tick " + tick);
                finish();
            }
        } catch (final Throwable t) {
            LOGGER.error("[convoygate] the probe broke at tick {}", tick, t);
            check("probe runs", false, t.toString());
            finish();
        }
    }

    private void finish() {
        finished = true;
        War.convoyBridgeForTests(null);
        LOGGER.info("[convoygate] {} checks, {} failed", checks, fails);
        LOGGER.info("[convoygate] RESULT {}", fails == 0 && checks > 0 ? "PASS" : "FAIL");
        server.halt(false);
    }

    // ------------------------------------------------------------------ the two colonies

    private void setUp() {
        level = server.overworld();
        homeCentre = surface(8, 8);
        final BlockPos far = surface(8 + DISTANCE, 8);
        final BlockPos beyond = surface(8 - DISTANCE, 8);
        for (final BlockPos c : new BlockPos[] {homeCentre, far, beyond}) {
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    level.setChunkForced((c.getX() >> 4) + cx, (c.getZ() >> 4) + cz, true);
                }
            }
        }
        home = colony(homeCentre, "Oakford", "convoygate_a");
        sender = colony(far, "Ironhold", "convoygate_b");
        market = colony(beyond, "Millbrook", "convoygate_c");
        check("two colonies are created", home != null && sender != null && home.getID() != sender.getID(),
                (home == null ? "none" : home.getID()) + " and " + (sender == null ? "none" : sender.getID()));
        if (home == null || sender == null) {
            return;
        }
        final Block townHall = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("minecolonies", "blockhuttownhall"));
        hut(home, homeCentre, townHall);
        hut(sender, far, townHall);
        if (market != null) {
            hut(market, beyond, townHall);
        }
        final IBuilding hut = hut(home, homeCentre.offset(-12, 0, 0), Thieves.BLOCK_HUT.get());
        hideout = hut instanceof BuildingHideout h ? h : null;
        warRoom = hut(sender, far.offset(10, 0, 0), Warfare.BLOCK_HUT_WAR_ROOM.get());
        check("Oakford has a Hideout, Ironhold a War Room", hideout != null && Campaigns.warRoom(sender) != null,
                hut + " / " + warRoom);
    }

    private BlockPos surface(final int x, final int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
    }

    private IColony colony(final BlockPos center, final String name, final String player) {
        final FakePlayer owner = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(player.getBytes()), player));
        owner.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        return IColonyManager.getInstance().createColony(level, center, owner, name, "Medieval Oak");
    }

    private IBuilding hut(final IColony colony, final BlockPos pos, final Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
        final BlockEntity te = level.getBlockEntity(pos);
        if (!(te instanceof AbstractTileEntityColonyBuilding hut)) {
            LOGGER.warn("[convoygate] no hut block entity at {} ({})", pos, te);
            return null;
        }
        IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        if (building == null) {
            building = colony.getServerBuildingManager().addNewBuilding(hut, level);
        }
        if (building != null && building.getBuildingLevel() < 1) {
            building.setBuildingLevel(1);
        }
        LOGGER.info("[convoygate] {} at {} in {}: {}", BuiltInRegistries.BLOCK.getKey(block), pos, colony.getName(),
                building == null ? "not registered" : building.getClass().getSimpleName() + " level " + building.getBuildingLevel());
        return building;
    }

    // ------------------------------------------------------------------ the thief, the escort and the load

    private void hire() {
        if (hideout == null || warRoom == null) {
            check("a Hideout and a War Room to hire into", false, "missing");
            return;
        }
        // hired on paper only: a thief with a body would run his own AI and take what the gate pins
        thief = home.getCitizenManager().createAndRegisterCivilianData();
        final WorkerBuildingModule crew = hideout.getFirstModuleOccurance(WorkerBuildingModule.class);
        final boolean hired = crew != null && crew.assignCitizen(thief);
        check("a thief is hired into the Hideout", hired && thief.getJob() instanceof JobThief,
                thief.getName() + " -> " + (thief.getJob() == null ? "no job" : thief.getJob().getClass().getSimpleName()));

        final WorkerBuildingModule room = warRoom.getFirstModuleOccurance(WorkerBuildingModule.class);
        for (int i = 0; i < 2; i++) {
            final ICitizenData data = sender.getCitizenManager().createAndRegisterCivilianData();
            sender.getCitizenManager().spawnOrCreateCitizen(data, level, warRoom.getPosition().offset(2, 1, 2 + i));
            room.assignCitizen(data);
        }
        InventoryUtils.addItemStackToProvider(warRoom, new ItemStack(Items.IRON_INGOT, 64));
        InventoryUtils.addItemStackToProvider(warRoom, new ItemStack(Items.IRON_INGOT, 64));
        InventoryUtils.addItemStackToProvider(warRoom, new ItemStack(Items.BREAD, BREAD));
        final ConvoyModule bay = ConvoyModule.of(warRoom);
        final int on = bay == null ? 0 : bay.add(new ItemStack(Items.IRON_INGOT), IRON) + bay.add(new ItemStack(Items.BREAD), BREAD);
        check("Ironhold loads 128 iron and 32 bread", on == TOTAL, "on the manifest " + on);
    }

    // ------------------------------------------------------------------ the road

    private void road() throws Exception {
        if (hideout == null || thief == null || warRoom == null) {
            check("everything is in place to send a convoy", false, "setup failed");
            return;
        }
        final StringBuilder why = new StringBuilder();
        if (market == null) {
            check("a third colony for the convoy to go to", false, "Millbrook was not created");
            return;
        }
        // the convoy goes on to Millbrook, the far side of Oakford: it passes Oakford's Hideout on its way
        final Campaigns.Campaign c = Campaigns.convoy(level, sender, market.getID(), 0, why);
        check("Ironhold sends a convoy to Millbrook, past Oakford, with two guards", c != null && c.guards.length == 2,
                c == null ? why : c.guards.length + " guards, " + c.plunder.size() + " stacks");
        if (c == null) {
            return;
        }
        final long id = c.id;
        final long now = server.overworld().getGameTime();
        final long trip = c.arrive - c.departed;
        final BlockPos post = hideout.getPosition();
        final int weight = ThievesConfig.convoyEscortWeight();
        LOGGER.info("[convoygate] convoy {} from {} to {}, trip {} ticks; Hideout at {}, range {} at level 1, {} at level 5",
                id, c.warRoom.toShortString(), c.target.toShortString(), trip, post.toShortString(),
                Convoys.range(1), Convoys.range(5));

        // --- offered only within range, and the range grows with the hut
        final double far = aim(c, now, trip, post, 240);
        check("240 blocks out: not offered at Hideout level 1, offered at level 5", far > Convoys.range(1)
                        && far <= Convoys.range(5) && Convoys.find(level, post, 1, home, id) == null
                        && Convoys.find(level, post, 5, home, id) != null && listed(id) == null,
                Math.round(far) + " blocks, ranges " + Convoys.range(1) + " / " + Convoys.range(5));

        final double close = aim(c, now, trip, post, 80);
        final SightingsModule.Sighting row = listed(id);
        check("80 blocks out: the Hideout's list offers it by name, read off its two guards", close <= Convoys.range(1)
                        && row != null && row.target().kind() == TargetKind.CONVOY && NAME.equals(row.target().name())
                        && row.target().colonyId() == sender.getID() && row.strength() == 2 * weight,
                Math.round(close) + " blocks, " + (row == null ? "not listed" : row.target().name() + ", watch "
                        + row.strength() + ", key " + row.target().key()));
        check("never its own colony's: Ironhold's own Hideout there would not be offered it",
                Convoys.find(level, post, 5, sender, id) == null && Convoys.offered(level, post, 5, sender).isEmpty(),
                Convoys.offered(level, post, 5, sender).size());
        check("nor to the colony it is bringing goods to: a Millbrook Hideout there would not be offered it",
                Convoys.find(level, post, 5, market, id) == null && Convoys.offered(level, post, 5, market).isEmpty(),
                Convoys.offered(level, post, 5, market).size());
        final boolean theftOn = convoyTheft(false);
        final int whileOff = Convoys.offered(level, post, 5, home).size();
        convoyTheft(theftOn);
        check("allowConvoyTheft off: no convoy is offered; back on: it is again",
                whileOff == 0 && Convoys.find(level, post, 5, home, id) != null, "offered while off " + whileOff);
        if (row == null) {
            return;
        }
        final Target target = row.target();

        // --- the board's path: Send, then the thief takes it and sets out
        final SendThereMessage.Sent sent = SendThereMessage.send(level, hideout, target.pos(), id, Work.SABOTAGE.id());
        final Job pinned = boardJob(id);
        check("Send pins it to the board by id, and only ever as a robbery", sent.pinned() && pinned != null
                        && pinned.work() == Work.ROB && pinned.target().convoy() == id,
                pinned == null ? "nothing pinned" : pinned.work().id() + " " + pinned.target().name());
        final SendThereMessage.Sent again = SendThereMessage.send(level, hideout, BlockPos.ZERO, id, Work.ROB.id());
        check("sending it again pins nothing more", !again.pinned() && hideout.board().size() == 1, hideout.board().size());

        Job mark = hideout.takeJob(job -> job.target().convoy() == id);
        mark = Convoys.onTheRoad(level, mark, post, hideout.getBuildingLevel(), home);
        final Heist out = Heist.begin(mark, post, now, 10, hideout.getBuildingLevel(), 0f);
        check("the thief takes it off the board and sets out for the road, a short walk",
                hideout.board().isEmpty() && out.target().convoy() == id && out.reachesAt() > now
                        && out.dueAt() - out.leftAt() < 3600L,
                "away " + (out.dueAt() - out.leftAt()) + " ticks, there at +" + (out.reachesAt() - now));

        // --- the job survives a save
        final JobThief job = (JobThief) thief.getJob();
        job.setHeist(out);
        final CompoundTag saved = job.serializeNBT(server.registryAccess());
        job.setHeist(null);
        job.deserializeNBT(server.registryAccess(), saved);
        final Heist back = job.getHeist();
        job.setHeist(null);
        check("the thief's convoy job survives a save and a read-back", back != null
                        && back.target().kind() == TargetKind.CONVOY && back.target().convoy() == id
                        && back.target().colonyId() == sender.getID() && back.dueAt() == out.dueAt()
                        && ("convoy/" + id).equals(back.target().key()),
                back == null ? "lost" : back.target().kind().id() + " " + back.target().convoy() + " " + back.target().key());
        // decided on the road and walking home: the new outcome and what he carries survive too
        job.setHeist(out);
        job.setOutcome(Outcome.EMPTY);
        job.addTake(List.of(new ItemStack(Items.IRON_INGOT, 5)));
        final CompoundTag walking = job.serializeNBT(server.registryAccess());
        job.setHeist(null);
        job.setOutcome(null);
        job.getTake().clear();
        job.deserializeNBT(server.registryAccess(), walking);
        final Outcome kept = job.getOutcome();
        final int carried = items(List.copyOf(job.getTake()));
        final boolean stillOut = job.getHeist() != null && job.getHeist().target().convoy() == id;
        job.setHeist(null);
        job.setOutcome(null);
        job.getTake().clear();
        check("...and so does a convoy job already decided on the road", stillOut && kept == Outcome.EMPTY
                && carried == 5, "out " + stillOut + ", outcome " + kept + ", carrying " + carried);
        final CompoundTag older = Heist.begin(Target.ofColony(level.dimension(), sender.getCenter(), "Ironhold",
                sender.getID()), post, now, 10, 1).save();
        final Heist oldBack = Heist.load(older);
        check("a 0.1.1 job (no convoy tag at all) reads back as no convoy", !older.getCompound("target").contains("convoy")
                        && oldBack != null && oldBack.target().convoy() == 0L
                        && ("colony/" + sender.getID()).equals(oldBack.target().key()),
                oldBack == null ? "lost" : oldBack.target().key());

        // --- the escort is the defence
        final int mine = Heist.brings(2, 1, 0);
        final float[] none = Heist.chances(mine, Heist.demands(Profiles.convoy(target, 0, 3, 0), TargetKind.CONVOY, Work.ROB));
        final float[] two = Heist.chances(mine, Heist.demands(Profiles.convoy(target, 2, 3, 0), TargetKind.CONVOY, Work.ROB));
        final float[] six = Heist.chances(mine, Heist.demands(Profiles.convoy(target, 6, 3, 0), TargetKind.CONVOY, Work.ROB));
        check("a bigger escort raises the catch chance", none[2] < two[2] && two[2] < six[2] && none[0] > six[0],
                "caught " + none[2] + " / " + two[2] + " / " + six[2] + " with 0 / 2 / 6 guards, clean " + none[0] + " / " + six[0]);
        final Assessment live = Assessment.of(level, target);
        check("the live reading of this convoy counts its own two guards", live.garrisonAway() == 2
                && live.vigilance() == 2 * weight + Heat.of(level, target.key()), live.line());

        // --- seen, then clean, then caught - each through the AI's own calls
        final int before = War.standing(home, sender.getID());
        final int cargo0 = items(War.convoyCargo(level, id));

        final Heist seenJob = Heist.begin(new Job(target, Work.ROB), post, now, 2, 1, 0f);
        final Interception.Result seen = Interception.intercept(level, seenJob, post, 1, home, 0, 0, luck(12));
        final int cargo1 = items(War.convoyCargo(level, id));
        reckon(seenJob, seen);
        final int afterSeen = War.standing(home, sender.getID());
        check("seen: he takes less through robConvoy, and the standing with Ironhold falls",
                seen.outcome() == Outcome.SPOTTED && items(seen.take()) > 0 && cargo0 - cargo1 == items(seen.take())
                        && seen.take().size() <= Interception.stacksFor(Outcome.SPOTTED, 2, 1)
                        && Interception.stacksFor(Outcome.SPOTTED, 2, 1) < Interception.stacksFor(Outcome.CLEAN, 2, 1)
                        && afterSeen < before,
                seen.outcome().id() + ", took " + items(seen.take()) + " in " + seen.take().size() + " stack(s), cargo "
                        + cargo0 + " -> " + cargo1 + ", standing " + before + " -> " + afterSeen);

        final Heist cleanJob = Heist.begin(new Job(target, Work.ROB), post, now, 30, 3, 0f);
        final Interception.Result clean = Interception.intercept(level, cleanJob, post, 1, home, 0, 0, luck(Integer.MAX_VALUE));
        final int cargo2 = items(War.convoyCargo(level, id));
        reckon(cleanJob, clean);
        final int afterClean = War.standing(home, sender.getID());
        check("clean: robConvoy takes cargo and the standing does not move", clean.outcome() == Outcome.CLEAN
                        && items(clean.take()) > 0 && cargo1 - cargo2 == items(clean.take()) && afterClean == afterSeen,
                clean.outcome().id() + ", took " + items(clean.take()) + ", cargo " + cargo1 + " -> " + cargo2
                        + ", standing " + afterSeen + " -> " + afterClean);
        check("never more than half of what it set out with", cargo0 - cargo2 <= TOTAL / 2, (cargo0 - cargo2) + " of " + TOTAL);

        final Heist caughtJob = Heist.begin(new Job(target, Work.ROB), post, now, 0, 1, 0f);
        final Interception.Result caught = Interception.intercept(level, caughtJob, post, 1, home, 0, 0, luck(0));
        final int cargo3 = items(War.convoyCargo(level, id));
        reckon(caughtJob, caught);
        final int afterCaught = War.standing(home, sender.getID());
        check("caught: he takes nothing, the convoy loses nothing, and the standing falls more than for seen",
                caught.outcome() == Outcome.CAUGHT && caught.take().isEmpty() && cargo3 == cargo2
                        && afterCaught < afterClean && afterClean - afterCaught > before - afterSeen,
                caught.outcome().id() + ", cargo " + cargo2 + " -> " + cargo3 + ", standing " + afterClean + " -> " + afterCaught);
        final List<BuildingHideout.Entry> log = hideout.ledger();
        check("the log names the convoy for each night", log.size() >= 3 && NAME.equals(log.get(0).place())
                        && "convoy".equals(log.get(0).kind()) && "caught".equals(log.get(0).outcome())
                        && "clean".equals(log.get(1).outcome()) && "spotted".equals(log.get(2).outcome()),
                log.isEmpty() ? "empty" : log.get(0).line());

        // --- the road was empty
        aim(c, now, trip, post, 240);
        final int heatBefore = Heat.of(level, target.key());
        final Heist lateJob = Heist.begin(new Job(target, Work.ROB), post, now, 30, 3, 0f);
        final Interception.Result late = Interception.intercept(level, lateJob, post, 1, home, 0, 0, luck(Integer.MAX_VALUE));
        reckon(lateJob, late);
        check("gone out of reach by the time he gets there: the road was empty, and nothing is owed",
                late.outcome() == Outcome.EMPTY && late.take().isEmpty() && items(War.convoyCargo(level, id)) == cargo3
                        && Heat.of(level, target.key()) == heatBefore && War.standing(home, sender.getID()) == afterCaught
                        && "empty".equals(hideout.ledger().get(0).outcome()),
                late.outcome().id() + ", heat " + heatBefore + " -> " + Heat.of(level, target.key()));

        // --- an older war mod: no convoy lookups at all
        aim(c, now, trip, post, 80);
        War.convoyBridgeForTests(OldBridge.class);
        try {
            final boolean nothing = !War.convoys() && Convoys.offered(level, post, 5, home).isEmpty() && listed(id) == null;
            final SendThereMessage.Sent refused = SendThereMessage.send(level, hideout, target.pos(), id, Work.ROB.id());
            final Interception.Result old = Interception.intercept(level,
                    Heist.begin(new Job(target, Work.ROB), post, now, 30, 3, 0f), post, 1, home, 0, 0, luck(Integer.MAX_VALUE));
            check("a 0.2.2-style war mod: nothing offered, nothing pinned, the road is empty", nothing
                            && !refused.pinned() && hideout.board().isEmpty() && old.outcome() == Outcome.EMPTY,
                    "convoys " + War.convoys() + ", pinned " + refused.pinned() + ", " + old.outcome().id());
            check("...and the rest of the bridge still answers", War.present() && War.hasWarRoom(sender)
                    && War.standing(home, sender.getID()) == afterCaught, War.present() + " " + War.hasWarRoom(sender));
        } finally {
            War.convoyBridgeForTests(null);
        }
        check("with the real war mod back it is offered again", Convoys.find(level, post, 1, home, id) != null, "");

        // --- the two commands, typed as an operator standing in Oakford
        final CommandSourceStack op = server.createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atCenterOf(homeCentre));
        server.getCommands().performPrefixedCommand(op, "thieves send rob " + target.pos().getX() + " "
                + target.pos().getZ() + " convoy");
        check("/thieves send ... convoy is refused: a convoy goes by id", hideout.board().isEmpty(), hideout.board().size());
        server.getCommands().performPrefixedCommand(op, "thieves convoy");
        server.getCommands().performPrefixedCommand(op, "thieves convoy " + id);
        final Job typed = boardJob(id);
        hideout.takeJob(any -> true);
        check("/thieves convoy <id> pins it the same way", typed != null && typed.work() == Work.ROB
                && NAME.equals(typed.target().name()), typed == null ? "nothing" : typed.target().name());

        // --- and arrived
        c.departed = now - trip;
        c.arrive = now;
        final Interception.Result arrived = Interception.intercept(level,
                Heist.begin(new Job(target, Work.ROB), post, now, 30, 3, 0f), post, 1, home, 0, 0, luck(Integer.MAX_VALUE));
        check("arrived before he gets there: the road was empty", arrived.outcome() == Outcome.EMPTY
                && arrived.take().isEmpty() && Convoys.find(level, post, 5, home, id) == null, arrived.outcome().id());
    }

    /**
     * Move the convoy along its road until it is about {@code distance} blocks (flat) from the
     * Hideout, coming towards it, and say how far it really is by the thief's own bridge.
     */
    /** Flip the allowConvoyTheft server setting; returns what it was. */
    private static boolean convoyTheft(final boolean on) {
        try {
            final java.lang.reflect.Field f = ThievesConfig.class.getDeclaredField("CONVOY_THEFT");
            f.setAccessible(true);
            final net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value =
                    (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) f.get(null);
            final boolean was = value.get();
            value.set(on);
            return was;
        } catch (final ReflectiveOperationException e) {
            LOGGER.warn("[convoygate] could not flip allowConvoyTheft: {}", e.toString());
            return true;
        }
    }

    private double aim(final Campaigns.Campaign c, final long now, final long trip, final BlockPos at, final double distance) {
        double f = 0.0;
        for (int step = 0; step <= 1000; step++) {
            f = step / 1000.0;
            final double x = c.warRoom.getX() + (c.target.getX() - c.warRoom.getX()) * f;
            final double z = c.warRoom.getZ() + (c.target.getZ() - c.warRoom.getZ()) * f;
            if (Math.hypot(x - at.getX(), z - at.getZ()) <= distance) {
                break;
            }
        }
        c.departed = now - Math.round(trip * f);
        c.arrive = c.departed + trip;
        for (final long[] row : War.convoysNear(level, at, 5000)) {
            if (row[0] == c.id) {
                return Math.hypot(row[5] - at.getX(), row[7] - at.getZ());
            }
        }
        return Double.MAX_VALUE;
    }

    /** The convoy's line on the Hideout's list, as the window would get it, or null. */
    private SightingsModule.Sighting listed(final long id) {
        for (final SightingsModule.Sighting s : hideout.getModuleMatching(SightingsModule.class, m -> true)
                .everything(level, home)) {
            if (s.target().convoy() == id) {
                return s;
            }
        }
        return null;
    }

    private Job boardJob(final long id) {
        for (final Job job : hideout.board()) {
            if (job.target().convoy() == id) {
                return job;
            }
        }
        return null;
    }

    /** He is home: the books, through the same call his AI makes. */
    private void reckon(final Heist heist, final Interception.Result result) {
        EntityAIWorkThief.reckon(level, hideout, home, heist, result.outcome(), items(result.take()),
                ThievesResearch.Bonuses.of(home));
    }

    private static int items(final List<ItemStack> goods) {
        int n = 0;
        for (final ItemStack s : goods) {
            n += s.getCount();
        }
        return n;
    }

    /** A night whose one draw of luck is fixed: 0 is the worst there is, anything past the swing the best. */
    private static RandomSource luck(final int draw) {
        return new LegacyRandomSource(1L) {
            @Override
            public int nextInt(final int bound) {
                return Math.max(0, Math.min(bound - 1, draw));
            }
        };
    }

    /**
     * Colonies at War 0.2.2's bridge, as far as the thief can tell: the six older methods and none
     * of the four convoy ones. The thief's convoy lookups are pointed at this class to prove an
     * older war mod is a world without convoys rather than an error.
     */
    public static final class OldBridge {
        private OldBridge() {
        }

        public static boolean hasWarRoom(final IColony colony) {
            return WarBridge.hasWarRoom(colony);
        }

        public static int walls(final IColony colony) {
            return WarBridge.walls(colony);
        }

        public static boolean isOnCampaign(final ICitizenData citizen) {
            return WarBridge.isOnCampaign(citizen);
        }

        public static int standing(final IColony mine, final int theirs) {
            return WarBridge.standing(mine, theirs);
        }

        public static void offend(final IColony mine, final int theirs, final int amount, final String why) {
            WarBridge.offend(mine, theirs, amount, why);
        }

        public static boolean atWar(final IColony mine, final int theirs) {
            return WarBridge.atWar(mine, theirs);
        }
    }
}
