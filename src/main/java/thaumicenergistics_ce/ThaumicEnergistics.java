package thaumicenergistics_ce;

import thaumicenergistics_ce.config.ThEConfig;

import appeng.api.AECapabilities;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IInWorldGridNodeHost;

import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.integration.thaumaturge.focus.FocusElements;
import thaumicenergistics_ce.registry.TECEBlockEntities;
import thaumicenergistics_ce.registry.TECEBlocks;
import thaumicenergistics_ce.registry.TECECreativeTab;
import thaumicenergistics_ce.registry.TECEItems;
import thaumicenergistics_ce.registry.TECEMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.item.ItemGolemWirelessBackpack;
import thaumicenergistics_ce.registry.TECENetwork;
import thaumicenergistics_ce.registry.capability.ThEItemCapabilities;
import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusStrategies;
import thaumicenergistics_ce.part.PartVisInterface;
import thaumicenergistics_ce.util.ThELog;

@Mod(ThEIds.MODID)
public final class ThaumicEnergistics {
    public ThaumicEnergistics(IEventBus modBus, ModContainer container) {
        ThELog.LOG.info("ThaumicEnergistics loading");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                ThEConfig.SPEC, "thaumicenergistics_ce-server.toml");

        TECEBlocks.register(modBus);
        TECEItems.register(modBus);
        thaumicenergistics_ce.registry.TECEDataComponents.register(modBus);
        thaumicenergistics_ce.item.WirelessTerminalShortcuts.register();
        TECEBlockEntities.register(modBus);
        TECEMenuTypes.register(modBus);
        TECECreativeTab.register(modBus);
        TECENetwork.register(modBus);
        FocusElements.register(modBus);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(ThEItemCapabilities::install);
        modBus.addListener(ThaumicEnergistics::registerPartCapabilities);
        modBus.addListener(this::registerKeyTypes);
        modBus.addListener(this::commonSetup);

    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        thaumicenergistics_ce.integration.ae2.patternprovider.PatternProviderAddonCompat.registerBlocks(event);
        event.registerBlockEntity(EssentiaCapabilities.STORAGE,
                appeng.core.definitions.AEBlockEntities.PATTERN_PROVIDER.get(),
                (provider, side) -> side == null ? null
                        : ((thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaPatternProvider)
                                provider.getLogic()).essentiaReturnStorage());
        for (BlockEntityType<?> type : List.of(
                TECEBlockEntities.ARCANE_ASSEMBLER.get(),
                TECEBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                TECEBlockEntities.ALCHEMY_PROVIDER.get(),
                TECEBlockEntities.INFUSION_PROVIDER.get(),
                TECEBlockEntities.OCCULT_MONITOR.get(),
                TECEBlockEntities.GACHA_BOX.get())) {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    type,
                    (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
        }

        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                TECEBlockEntities.ALCHEMY_PROVIDER.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                TECEBlockEntities.ALCHEMY_PROVIDER_CONNECTION.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);

        event.registerBlockEntity(
                EssentiaCapabilities.STORAGE,
                TECEBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (IEssentiaStorage)
                        blockEntity);
        event.registerBlockEntity(
                EssentiaCapabilities.TRANSPORT,
                TECEBlockEntities.ESSENTIA_VIBRATION_CHAMBER.get(),
                (blockEntity, context) -> (IEssentiaTransport)
                        blockEntity);

        event.registerBlockEntity(
                EssentiaCapabilities.TRANSPORT,
                TECEBlockEntities.GACHA_BOX.get(),
                (blockEntity, context) -> blockEntity.essentiaTransport(context));

        event.registerBlockEntity(
                Capabilities.Energy.BLOCK,
                TECEBlockEntities.GACHA_BOX.get(),
                (blockEntity, context) -> blockEntity.getEnergyStorage(context));

        event.registerBlockEntity(
                AspectCapabilities.CONTAINER,
                TECEBlockEntities.INFUSION_PROVIDER.get(),
                (blockEntity, context) -> (IAspectSource)
                        blockEntity);
    }

    public static void registerPartCapabilities(RegisterPartCapabilitiesEvent event) {
        thaumicenergistics_ce.integration.ae2.patternprovider.PatternProviderAddonCompat.registerParts(event);
        event.register(EssentiaCapabilities.STORAGE,
                (appeng.parts.crafting.PatternProviderPart provider, net.minecraft.core.Direction side) -> side == null ? null
                        : ((thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaPatternProvider)
                                provider.getLogic()).essentiaReturnStorage(),
                appeng.parts.crafting.PatternProviderPart.class);
        TcAura.registerVisSource(event, PartVisInterface.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GridLinkables.register(
                    TECEItems.GOLEM_WIFI_BACKPACK.get(), ItemGolemWirelessBackpack.LINKABLE_HANDLER);
            GridLinkables.register(
                    TECEItems.WIRELESS_ESSENTIA_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            GridLinkables.register(
                    TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                    WirelessTerminalItem.LINKABLE_HANDLER);
            registerUpgrades();
            thaumicenergistics_ce.item.QuantumTerminalSupport.registerUpgrades();
            EssentiaBusStrategies.register();
            ThELog.LOG.info("ThaumicEnergistics common setup complete");
            thaumicenergistics_ce.recipe.arcane.TerminalWorkbenchVis.register();
        });
    }

    private static void registerUpgrades() {
        for (var cell : List.of(
                TECEItems.ESSENTIA_CELL_1K.get(),
                TECEItems.ESSENTIA_CELL_4K.get(),
                TECEItems.ESSENTIA_CELL_16K.get(),
                TECEItems.ESSENTIA_CELL_64K.get(),
                TECEItems.ESSENTIA_CELL_CREATIVE.get())) {
            Upgrades.add(AEItems.INVERTER_CARD, cell, 1, CELL_UPGRADE_NAME);
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, cell, 1, CELL_UPGRADE_NAME);
            Upgrades.add(AEItems.VOID_CARD, cell, 1, CELL_UPGRADE_NAME);
        }
        Upgrades.add(
                AEItems.SPEED_CARD,
                TECEItems.ARCANE_ASSEMBLER.get(),
                BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT);
        Upgrades.add(TECEItems.ESSENTIA_ACCESS_CARD.get(), AEParts.IMPORT_BUS, 1);
        Upgrades.add(TECEItems.ESSENTIA_ACCESS_CARD.get(), AEParts.EXPORT_BUS, 1);
        Upgrades.add(TECEItems.ESSENTIA_ACCESS_CARD.get(), AEParts.STORAGE_BUS, 1);
        Upgrades.add(TECEItems.ESSENTIA_ACCESS_CARD.get(), appeng.core.definitions.AEBlocks.PATTERN_PROVIDER, 1);
        Upgrades.add(TECEItems.ESSENTIA_ACCESS_CARD.get(), AEParts.PATTERN_PROVIDER, 1);
        Upgrades.add(TECEItems.ESSENTIA_ACCESS_CARD.get(), TECEItems.ARCANE_CRAFTING_TERMINAL.get(),
                1, ARCANE_TERMINAL_PART_UPGRADE_NAME);
        Upgrades.add(TECEItems.ESSENTIA_ACCESS_CARD.get(), TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                1, ARCANE_TERMINAL_UPGRADE_NAME);
        Upgrades.add(
                TECEItems.VIS_CONNECTION_CARD.get(),
                TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                1,
                ARCANE_TERMINAL_UPGRADE_NAME);
        Upgrades.add(
                TECEItems.VIS_CONNECTION_CARD.get(),
                TECEItems.ARCANE_CRAFTING_TERMINAL.get(),
                1,
                ARCANE_TERMINAL_PART_UPGRADE_NAME);
    }

    private static final String CELL_UPGRADE_NAME = "item.thaumicenergistics_ce.essentia_cell";

    private static final String ARCANE_TERMINAL_UPGRADE_NAME =
            "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal";

    private static final String ARCANE_TERMINAL_PART_UPGRADE_NAME =
            "item.thaumicenergistics_ce.arcane_crafting_terminal";

    private void registerKeyTypes(RegisterEvent event) {
        if (event.getRegistryKey() != AEKeyType.REGISTRY_KEY) {
            return;
        }
        AEKeyTypes.register(AEssentiaKeyType.INSTANCE);
        ThELog.LOG.info("Registered the essentia key type with AE2");
    }
}
