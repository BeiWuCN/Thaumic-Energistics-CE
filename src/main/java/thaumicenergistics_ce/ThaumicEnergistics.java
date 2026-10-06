package thaumicenergistics_ce;

import appeng.api.AECapabilities;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.PartModels;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.focus.FocusElements;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModCreativeTab;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.init.ModNetwork;
import thaumicenergistics_ce.init.SelfTestHook;
import thaumicenergistics_ce.init.capability.ThEItemCapabilities;
import thaumicenergistics_ce.interfaceaccess.EssentiaInterfaceRegistry;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartEssentiaExportBus;
import thaumicenergistics_ce.part.PartEssentiaImportBus;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;
import thaumicenergistics_ce.part.PartEssentiaStorageBus;
import thaumicenergistics_ce.part.PartEssentiaTerminal;
import thaumicenergistics_ce.part.PartVisInterface;
import thaumicenergistics_ce.util.ThELog;

/**
 * Thaumic Energistics - bridges Thaumaturge essentia with Applied Energistics 2 ME networks.
 * <ul>
 *   <li>Target: Minecraft 1.21.1, NeoForge 21.1.250, Thaumaturge, AE2 19.2.x.</li>
 *   <li>Arcane autocrafting: the Knowledge Inscriber stores an ingredient grid as an AE2 pattern in a
 *       knowledge core; that machine advertises those recipes and runs them for ambient vis.</li>
 * </ul>
 */
@Mod(ThEIds.MODID)
public final class ThaumicEnergistics {
    public ThaumicEnergistics(IEventBus modBus, ModContainer container) {
        ThELog.LOG.info("ThaumicEnergistics loading");

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenuTypes.register(modBus);
        ModCreativeTab.register(modBus);
        ModNetwork.register(modBus);
        FocusElements.register(modBus);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(ThEItemCapabilities::install);
        modBus.addListener(ThaumicEnergistics::registerPartCapabilities);
        modBus.addListener(this::registerKeyTypes);
        modBus.addListener(this::commonSetup);

        registerPartModels();
        // The ME interface's access card works on the game bus rather than a grid tickable, since AE2
        // reports nothing when a card goes in or out - see EssentiaInterfaceRegistry.
        EssentiaInterfaceRegistry.register();
        SelfTestHook.install();
    }

    /**
     * Registers every part model. The {@code @PartModels} annotation is only a marker in AE2 19, and a
     * location the renderer cannot find is a crash the moment the part is placed, so this walks them.
     */
    private static void registerPartModels() {
        List<ResourceLocation> models = new ArrayList<>();
        models.addAll(PartEssentiaTerminal.MODEL_LOCATIONS);
        models.addAll(PartEssentiaImportBus.MODEL_LOCATIONS);
        models.addAll(PartEssentiaExportBus.MODEL_LOCATIONS);
        models.addAll(PartEssentiaStorageBus.MODEL_LOCATIONS);
        models.addAll(PartEssentiaLevelEmitter.MODEL_LOCATIONS);
        models.addAll(PartArcaneCraftingTerminal.MODEL_LOCATIONS);
        // The P2P part draws itself with AE2's own P2P set, status models included.
        models.addAll(PartVisInterface.MODEL_LOCATIONS);
        PartModels.registerModels(models);
    }

    /**
     * Exposes the mod's grid machines to AE2's network; without {@code IN_WORLD_GRID_NODE_HOST} a
     * machine forms its own isolated grid and the ME terminal never learns about it.
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Implementing the interface is not enough; an unregistered capability leaves it inert.
        for (BlockEntityType<?> type : List.of(
                ModBlockEntities.ARCANE_ASSEMBLER.get(),
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                ModBlockEntities.ALCHEMY_PROVIDER.get(),
                ModBlockEntities.INFUSION_PROVIDER.get(),
                ModBlockEntities.OCCULT_MONITOR.get())) {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    type,
                    (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
        }

        // Same STORAGE capability Thaumaturge's jars expose; pipes and neighbours treat it as one.
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ALCHEMY_PROVIDER.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        // A container to its neighbours: a jar beside it fills, an alembic beside it empties.
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ALCHEMY_PROVIDER_CONNECTION.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        // STORAGE + TRANSPORT so essentia can be pushed in; wildcard suction - see the class note.
        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);
        event.registerBlockEntity(
                EssentiaCapabilities.TRANSPORT,
                ModBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (IEssentiaTransport)
                        blockEntity);

        // Aspect CONTAINER is the capability an Infusion Altar scans for to draw essentia.
        event.registerBlockEntity(
                AspectCapabilities.CONTAINER,
                ModBlockEntities.INFUSION_PROVIDER.get(),
                (blockEntity, context) -> (IAspectSource)
                        blockEntity);
    }

    /**
     * Exposes TECE's own parts to the rest of the game. Parts are not block entities, so this needs
     * AE2's own event, and the lookup is answered through the cable bus the part sits on. Static and
     * public so the self-test source set can run the very event a part AE2 refuses would throw from.
     */
    public static void registerPartCapabilities(RegisterPartCapabilitiesEvent event) {
        TcAura.registerVisSource(event, PartVisInterface.class);
        // A pipe asks a neighbour only for the transport capability, so without these three the
        // essentia buses can see a tube but a tube cannot see them. The port is whatever the bus
        // faces, which is why it is rebuilt per query rather than held.
        event.register(EssentiaCapabilities.TRANSPORT, (part, context) -> part.transportView(),
                PartEssentiaStorageBus.class);
        event.register(EssentiaCapabilities.TRANSPORT, (part, context) -> part.transportView(),
                PartEssentiaImportBus.class);
        event.register(EssentiaCapabilities.TRANSPORT, (part, context) -> part.transportView(),
                PartEssentiaExportBus.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Skipping this silently breaks memory card binding; must run after item registration.
            GridLinkables.register(
                    ModItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            GridLinkables.register(
                    ModItems.WIRELESS_ESSENTIA_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            // The access point's link slot asks this registry by item, so an unregistered terminal is
            // refused before the player can drop it in - the same silent break as the line above.
            GridLinkables.register(
                    ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            registerUpgrades();
            ThELog.LOG.info("ThaumicEnergistics common setup complete");
            // Else every terminal craft fails with PAYMENT_UNAVAILABLE - see TerminalWorkbenchVis.
            thaumicenergistics_ce.arcane.TerminalWorkbenchVis.register();
        });
    }

    /**
     * Tells AE2 which upgrade cards this mod's machines and cells take; without it their slots show AE2's
     * "available upgrades" header with nothing under it, since that list comes from AE2's own registry.
     */
    private static void registerUpgrades() {
        // AE2's BasicCellInventory reads all three cards; an essentia aspect carries no NBT, so no fuzzy one.
        // The name key is AE2's own fourth argument and collapses the five tiers into one tooltip line.
        for (var cell : List.of(
                ModItems.ESSENTIA_CELL_1K.get(),
                ModItems.ESSENTIA_CELL_4K.get(),
                ModItems.ESSENTIA_CELL_16K.get(),
                ModItems.ESSENTIA_CELL_64K.get(),
                ModItems.ESSENTIA_CELL_CREATIVE.get())) {
            Upgrades.add(AEItems.INVERTER_CARD, cell, 1, CELL_UPGRADE_NAME);
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, cell, 1, CELL_UPGRADE_NAME);
            Upgrades.add(AEItems.VOID_CARD, cell, 1, CELL_UPGRADE_NAME);
        }
        // One card per slot: the number is the machine's own slot count, so the two cannot disagree.
        Upgrades.add(
                AEItems.SPEED_CARD,
                ModItems.ARCANE_ASSEMBLER.get(),
                BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
        // The count is the bus's own slot count (PartEssentiaImportBus#getUpgradeSlots); capacity is
        // absent because MenuEssentiaBusBase keeps 18 fixed config slots and never reads the card.
        for (var bus : List.of(
                ModItems.ESSENTIA_IMPORT_BUS.get(),
                ModItems.ESSENTIA_EXPORT_BUS.get())) {
            Upgrades.add(AEItems.SPEED_CARD, bus, BUS_UPGRADE_SLOTS);
            Upgrades.add(AEItems.REDSTONE_CARD, bus, 1);
        }
        // One access card per ME interface, block form and cable part alike: without these two AE2's
        // upgrade slot refuses our card and the interface's rows can never be marked.
        Upgrades.add(ModItems.ESSENTIA_ACCESS_CARD.get(), AEBlocks.INTERFACE, 1);
        Upgrades.add(ModItems.ESSENTIA_ACCESS_CARD.get(), AEParts.INTERFACE, 1);
    }

    /** The four upgrade slots every essentia bus has; the same number {@code Upgrades.add} should report. */
    private static final int BUS_UPGRADE_SLOTS = 4;

    /** What a card's tooltip calls the whole essentia cell family, at every size. */
    private static final String CELL_UPGRADE_NAME = "item.thaumicenergistics_ce.essentia_cell";

    /**
     * Adds the essentia key type to AE2's registry. Not from the mod constructor: an {@code AEKeyType}
     * is a registry object, so registering before AE2 builds its registry throws.
     */
    private void registerKeyTypes(RegisterEvent event) {
        if (event.getRegistryKey() != AEKeyType.REGISTRY_KEY) {
            return;
        }
        AEKeyTypes.register(AEssentiaKeyType.INSTANCE);
        ThELog.LOG.info("Registered the essentia key type with AE2");
    }
}
