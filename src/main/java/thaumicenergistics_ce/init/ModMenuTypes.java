package thaumicenergistics_ce.init;

import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.storage.ITerminalHost;
import appeng.menu.implementations.MenuTypeBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.menu.MenuEssentiaExportBus;
import thaumicenergistics_ce.menu.MenuEssentiaImportBus;
import thaumicenergistics_ce.menu.MenuEssentiaLevelEmitter;
import thaumicenergistics_ce.menu.MenuEssentiaStorageBus;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;
import thaumicenergistics_ce.part.PartEssentiaExportBus;
import thaumicenergistics_ce.part.PartEssentiaImportBus;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;
import thaumicenergistics_ce.part.PartEssentiaStorageBus;

/** Menu type registration. */
public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(Registries.MENU, ThEIds.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MenuArcaneAssembler>> ARCANE_ASSEMBLER =
            REGISTRY.register(
                    "arcane_assembler", () -> IMenuTypeExtension.create(MenuArcaneAssembler::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuKnowledgeInscriber>> KNOWLEDGE_INSCRIBER =
            REGISTRY.register(
                    "knowledge_inscriber", () -> IMenuTypeExtension.create(MenuKnowledgeInscriber::new));

    /**
     * The Essentia Vibration Chamber's screen: what is buffered, how full the slot is, and how far through
     * the current unit of fuel the machine is.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaVibrationChamber>>
            ESSENTIA_VIBRATION_CHAMBER =
                    REGISTRY.register(
                            "essentia_vibration_chamber",
                            () -> IMenuTypeExtension.create(MenuEssentiaVibrationChamber::new));

    /** The Essentia Cell Workbench's screen: the cell, and the partition being edited. */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaCellWorkbench>> ESSENTIA_CELL_WORKBENCH =
            REGISTRY.register(
                    "essentia_cell_workbench",
                    () -> IMenuTypeExtension.create(MenuEssentiaCellWorkbench::new));

    /** The Distillation Encoder's screen: the item, its aspects, and the pattern being written. */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuDistillationEncoder>> DISTILLATION_ENCODER =
            REGISTRY.register(
                    "distillation_encoder",
                    () -> IMenuTypeExtension.create(MenuDistillationEncoder::new));

    /**
     * The Arcane Crafting Terminal's screen.
     *
     * <p>A terminal, so it is built the way the Essentia Terminal below is: through AE2's
     * {@link MenuTypeBuilder}, because the menu is opened from a host that has to travel with it - the part
     * on the cable. The builder is what puts that host in the open packet.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuArcaneCraftingTerminal>>
            ARCANE_CRAFTING_TERMINAL = REGISTRY.register(
                    "arcane_crafting_terminal",
                    // The lambda is written as a block rather than a method reference because this menu has
                    // both a four-argument and a five-argument constructor, and `MenuArcaneCraftingTerminal::new`
                    // matches MenuTypeBuilder's two overloads equally well - which the compiler rejects as
                    // ambiguous rather than picking one. The type arguments are spelled out for the same
                    // reason: with a lambda there is nothing for inference to work from, so it has to be told.
                    () -> MenuTypeBuilder.<MenuArcaneCraftingTerminal, appeng.api.storage.ITerminalHost>create(
                                    (menuType, id, playerInventory, host) ->
                                            new MenuArcaneCraftingTerminal(menuType, id, playerInventory, host),
                                    appeng.api.storage.ITerminalHost.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "gui.thaumicenergistics_ce.ArcaneCraftingTerminal"))
                            .buildUnregistered(ThEIds.id("arcane_crafting_terminal")));

    /**
     * The Essentia Terminal.
     *
     * <p>Built by AE2's own {@link MenuTypeBuilder} rather than the plain extension, because a terminal
     * menu is opened from a host that has to travel with it - the part on the cable, or the wireless item
     * in the player's hand. The builder is what encodes that host into the open packet; without it the
     * server would build a menu with no network behind it. {@code buildUnregistered} is AE2's own form for
     * a terminal, and is what its own terminals use.
     *
     * <p>The title is named here rather than left to the screen style. AE2's terminal style carries a
     * title of its own - the literal translation key {@code gui.ae2.Terminal} - so a terminal that borrows
     * that style is captioned "Terminal" whatever it actually is. This is the hook that overrides it.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaTerminal>> ESSENTIA_TERMINAL =
            REGISTRY.register(
                    "essentia_terminal",
                    () -> MenuTypeBuilder.create(MenuEssentiaTerminal::new, ITerminalHost.class)
                            .withMenuTitle(host -> Component.translatable("gui.thaumicenergistics_ce.essentia_terminal"))
                            .buildUnregistered(ThEIds.id("essentia_terminal")));

    /**
     * The Wireless Essentia Terminal's screen.
     *
     * <p>A second menu type rather than reusing the wired one, because the host differs: a cable terminal
     * is opened from a part, a wireless one from an item in the player's inventory, and AE2's builder
     * encodes that host class into the open packet. The reference build registers the same pair.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaTerminal>> WIRELESS_ESSENTIA_TERMINAL =
            REGISTRY.register(
                    "wireless_essentia_terminal",
                    () -> MenuTypeBuilder.create(MenuEssentiaTerminal::new, IPortableTerminal.class)
                            .withMenuTitle(host ->
                                    Component.translatable("gui.thaumicenergistics_ce.wireless_essentia_terminal"))
                            .buildUnregistered(ThEIds.id("wireless_essentia_terminal")));

    /**
     * The Essentia Import Bus's config screen.
     *
     * <p>The host type is the part class, which is what the builder encodes into the open packet - a bus
     * is opened by clicking the part, so the menu has to be able to name the part it belongs to.
     *
     * <p>The title is named here rather than left to AE2. Without it the builder falls back to AE2's own
     * generic caption for a bus - {@code gui.ae2.ImportBus}, "ME输入总线" - so all three of these screens
     * were labelled as plain ME buses and nothing said they carried essentia. It is the same trap the
     * Essentia Terminal documents below: a screen borrowing AE2's machinery is captioned by AE2 unless it
     * says otherwise.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaImportBus>> ESSENTIA_IMPORT_BUS =
            REGISTRY.register(
                    "essentia_import_bus",
                    () -> MenuTypeBuilder.create(
                                    MenuEssentiaImportBus::new, PartEssentiaImportBus.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "gui.thaumicenergistics_ce.essentia_import_bus"))
                            .buildUnregistered(ThEIds.id("essentia_import_bus")));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaExportBus>> ESSENTIA_EXPORT_BUS =
            REGISTRY.register(
                    "essentia_export_bus",
                    () -> MenuTypeBuilder.create(
                                    MenuEssentiaExportBus::new, PartEssentiaExportBus.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "gui.thaumicenergistics_ce.essentia_export_bus"))
                            .buildUnregistered(ThEIds.id("essentia_export_bus")));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaStorageBus>> ESSENTIA_STORAGE_BUS =
            REGISTRY.register(
                    "essentia_storage_bus",
                    () -> MenuTypeBuilder.create(
                                    MenuEssentiaStorageBus::new, PartEssentiaStorageBus.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "gui.thaumicenergistics_ce.essentia_storage_bus"))
                            .buildUnregistered(ThEIds.id("essentia_storage_bus")));

    /**
     * The Essentia Level Emitter's screen.
     *
     * <p>Carries the reporting value as initial data, so the player's threshold box opens showing what the
     * emitter is actually set to. AE2's own level emitter does the same, and for the same reason: the
     * value is a setting rather than something the server keeps pushing.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaLevelEmitter>> ESSENTIA_LEVEL_EMITTER =
            REGISTRY.register(
                    "essentia_level_emitter",
                    () -> MenuTypeBuilder.create(
                                    MenuEssentiaLevelEmitter::new, PartEssentiaLevelEmitter.class)
                            // Named for the same reason the buses above are: left to itself the builder
                            // captions this with AE2's own generic level emitter title.
                            .withMenuTitle(host -> Component.translatable(
                                    "gui.thaumicenergistics_ce.essentia_level_emitter"))
                            .withInitialData(
                                    (host, buffer) -> buffer.writeVarLong(host.getReportingValue()),
                                    (host, menu, buffer) -> menu.setInitialValue(buffer.readVarLong()))
                            .buildUnregistered(ThEIds.id("essentia_level_emitter")));

    private ModMenuTypes() {}

    public static void register(IEventBus bus) {
        REGISTRY.register(bus);
    }
}
