package me.lovkar.thieves;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import me.lovkar.thieves.block.BlockHutHideout;
import me.lovkar.thieves.block.ThievesTileEntity;
import me.lovkar.thieves.colony.BuildingHideout;
import me.lovkar.thieves.colony.HideoutModules;
import me.lovkar.thieves.colony.JobThief;
import me.lovkar.thieves.item.MarkItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Colonist Thieves - a MineColonies profession that robs other people's settlements.
 *
 * <p>One building, the <b>Hideout</b>, and one job, the <b>Thief</b>. You craft a <b>Mark</b>, use
 * it on somewhere worth robbing - a barbarian camp, a village, a Waking World kingdom, another
 * player's colony where the server allows it - and hand it in at the Hideout. The thief leaves the
 * world for a while and comes home clean, seen, or caught.</p>
 *
 * <p>Registration follows the Voyager pattern, which is the MC Trade Post pattern: our own
 * block-entity type for the hut (MineColonies' own type only accepts MineColonies hut blocks), a
 * hut block that reports its registry name in our namespace, and job and building entries through
 * DeferredRegisters on MineColonies' own registries.</p>
 */
@Mod(Thieves.MODID)
public class Thieves {
    public static final String MODID = "colonist_thieves";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static final String HUT_NAME = "blockhuthideout";
    public static final ResourceLocation JOB_ID = ResourceLocation.fromNamespaceAndPath(MODID, "thief");
    public static final ResourceLocation BUILDING_ID = ResourceLocation.fromNamespaceAndPath(MODID, "hideout");

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<JobEntry> JOBS =
            DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS, MODID);
    public static final DeferredRegister<BuildingEntry> BUILDINGS =
            DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, MODID);

    public static final DeferredBlock<BlockHutHideout> BLOCK_HUT =
            BLOCKS.register(HUT_NAME, BlockHutHideout::new);
    public static final DeferredItem<Item> ITEM_HUT =
            ITEMS.register(HUT_NAME, () -> new ItemBlockHut(BLOCK_HUT.get(), new Item.Properties()));

    /** A blank mark, and the same item once it points at somewhere. One item, two states. */
    public static final DeferredItem<Item> MARK =
            ITEMS.register("mark", () -> new MarkItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThievesTileEntity>> BUILDING_BE =
            BLOCK_ENTITIES.register("colonybuilding",
                    () -> BlockEntityType.Builder.of(ThievesTileEntity::new, BLOCK_HUT.get()).build(null));

    public static final DeferredHolder<JobEntry, JobEntry> JOB = JOBS.register(JOB_ID.getPath(),
            () -> new JobEntry.Builder()
                    .setJobProducer(JobThief::new)
                    .setJobViewProducer(() -> DefaultJobView::new)
                    .setRegistryName(JOB_ID)
                    .createJobEntry());

    public static final DeferredHolder<BuildingEntry, BuildingEntry> BUILDING =
            BUILDINGS.register(BUILDING_ID.getPath(),
                    () -> new BuildingEntry.Builder()
                            .setBuildingBlock(BLOCK_HUT.get())
                            .setBuildingProducer(BuildingHideout::new)
                            .setBuildingViewProducer(() -> EmptyView::new)
                            .setRegistryName(BUILDING_ID)
                            .addBuildingModuleProducer(HideoutModules.WORK)
                            .addBuildingModuleProducer(HideoutModules.SETTINGS)
                            .addBuildingModuleProducer(HideoutModules.BOARD)
                            .addBuildingModuleProducer(HideoutModules.LEDGER)
                            .addBuildingModuleProducer(HideoutModules.SIGHTINGS)
                            .addBuildingModuleProducer(BuildingModules.BED)
                            .addBuildingModuleProducer(BuildingModules.MIN_STOCK)
                            .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
                            .createBuildingEntry());

    /** MineColonies' creative tab that lists every hut block. */
    private static final ResourceKey<CreativeModeTab> HUTS_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("minecolonies", "mchuts"));

    public Thieves(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        JOBS.register(modEventBus);
        BUILDINGS.register(modEventBus);
        modEventBus.addListener(EventPriority.HIGH, Thieves::registerCapabilities);
        modEventBus.addListener(Thieves::addToCreativeTab);
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(Thieves::lendVoiceLines));
        modEventBus.addListener(Thieves::registerPayloads);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ThievesConfig.SPEC);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ThievesCommands::register);
        LOGGER.info("Colonist Thieves loaded - the Hideout is open");
    }

    /**
     * What the hut window may ask the server to do.
     *
     * <p>Three, now: take a mark down, look around, and send him there. Everything else the hut
     * window shows is a reading, and readings travel with the building. All three are re-checked
     * end to end on the server - permission, building type, and the place looked up again from its
     * own position - because a packet from a client is a request, never an instruction.</p>
     */
    private static void registerPayloads(final net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        registrar.playToServer(
                me.lovkar.thieves.network.UnpinMessage.TYPE,
                me.lovkar.thieves.network.UnpinMessage.STREAM_CODEC,
                me.lovkar.thieves.network.UnpinMessage::handle);
        registrar.playToServer(
                me.lovkar.thieves.network.LookAroundMessage.TYPE,
                me.lovkar.thieves.network.LookAroundMessage.STREAM_CODEC,
                me.lovkar.thieves.network.LookAroundMessage::handle);
        registrar.playToServer(
                me.lovkar.thieves.network.SendThereMessage.TYPE,
                me.lovkar.thieves.network.SendThereMessage.STREAM_CODEC,
                me.lovkar.thieves.network.SendThereMessage::handle);
    }

    /** Racks and chests inside the hut: MineColonies reads the hut inventory through this. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BUILDING_BE.get(),
                (be, side) -> be.getItemHandlerCap(side));
    }

    /**
     * MineColonies looks citizen voice lines up by job path and crashes the server tick for a job
     * it has no entry for. A thief speaks with the Nether Miner's lines - somebody who goes out,
     * stays away and does not talk about it.
     */
    private static void lendVoiceLines() {
        final Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> voices =
                ModSoundEvents.CITIZEN_SOUND_EVENTS;
        final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> lines =
                voices.containsKey("netherworker") ? voices.get("netherworker") : voices.get("unemployed");
        if (lines == null) {
            LOGGER.warn("no citizen voice lines to lend the thief");
            return;
        }
        voices.put(JOB_ID.getPath(), lines);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HUTS_TAB)) {
            event.accept(ITEM_HUT.get());
            event.accept(MARK.get());
        }
    }
}
