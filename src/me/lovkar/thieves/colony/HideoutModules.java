package me.lovkar.thieves.colony;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import me.lovkar.thieves.Thieves;

/** The Hideout's modules. */
public final class HideoutModules {
    private HideoutModules() {
    }

    /**
     * Who works here. <b>Agility</b> is the thief's craft - getting in, getting out, not being
     * where the torch is - and <b>Adaptability</b> is what he falls back on when the plan was
     * wrong, which it usually is. The crew grows with the building, the way it does at a barracks:
     * one, then two, then three.
     */
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> WORK =
            new BuildingEntry.ModuleProducer<>("hideout_work",
                    () -> new WorkerBuildingModule(Thieves.JOB.get(), Skill.Agility, Skill.Adaptability,
                            false, BuildingHideout::crewSize),
                    () -> WorkerBuildingModuleView::new);

    /** How careful the colony wants its thieves to be, and whether they wait to be told. */
    public static final BuildingEntry.ModuleProducer<SettingsModule, SettingsModuleView> SETTINGS =
            new BuildingEntry.ModuleProducer<>("hideout_settings",
                    () -> (SettingsModule) new SettingsModule()
                            .with(BuildingHideout.CAUTION, new StringSetting(
                                    BuildingHideout.CAUTION_CAREFUL,
                                    BuildingHideout.CAUTION_BOLD,
                                    BuildingHideout.CAUTION_QUIET))
                            // waits to be told, or goes looking - the first is the default because
                            // a thief who lets himself out on night one is a surprise, not a feature
                            .with(BuildingHideout.WORK_MODE, new StringSetting(
                                    BuildingHideout.MODE_MANUAL,
                                    BuildingHideout.MODE_AUTO)),
                    () -> SettingsModuleView::new);

    /**
     * The board on the wall: who is out tonight, and what is still pinned up.
     *
     * <p>A tab rather than a line in the worker panel because the board is the building's own
     * state and the crew's whereabouts are the first thing anybody opens a Hideout to find out.
     * It is also the only place a mark pinned in error can be taken down again.</p>
     */
    public static final BuildingEntry.ModuleProducer<BoardModule, BoardModuleView> BOARD =
            new BuildingEntry.ModuleProducer<>("hideout_board",
                    BoardModule::new, () -> BoardModuleView::new);

    /** The book under the board - every night the hut remembers, newest first. */
    public static final BuildingEntry.ModuleProducer<LedgerModule, LedgerModuleView> LEDGER =
            new BuildingEntry.ModuleProducer<>("hideout_ledger",
                    LedgerModule::new, () -> LedgerModuleView::new);

    /**
     * Somewhere to rob: every colony and kingdom the hut can see, plus what looking around finds.
     *
     * <p>The Mark stays - it is how you point at the place you are standing in - but nobody should
     * have to walk four thousand blocks to aim a thief at a town they can see on the map.</p>
     */
    public static final BuildingEntry.ModuleProducer<SightingsModule, SightingsModuleView> SIGHTINGS =
            new BuildingEntry.ModuleProducer<>("hideout_sightings",
                    SightingsModule::new, () -> SightingsModuleView::new);
}
