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
import thaumicenergistics_ce.menu.MenuEssentiaLevelEmitter;
import thaumicenergistics_ce.menu.MenuEssentiaStorageBus;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;
import thaumicenergistics_ce.part.PartEssentiaStorageBus;

/** Menu type registration. */
public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(Registries.MENU, ThEIds.MODID);

    /**
     * AE2's name for a terminal, "终端". The header names only the storage half; which crafting half hangs off
     * it is the section title the style draws above the grid.
     */
    private static final String TERMINAL_TITLE = "gui.ae2.Terminal";

    public static final DeferredHolder<MenuType<?>, MenuType<MenuArcaneAssembler>> ARCANE_ASSEMBLER =
            REGISTRY.register(
                    "arcane_assembler", () -> IMenuTypeExtension.create(MenuArcaneAssembler::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuKnowledgeInscriber>> KNOWLEDGE_INSCRIBER =
            REGISTRY.register(
                    "knowledge_inscriber", () -> IMenuTypeExtension.create(MenuKnowledgeInscriber::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaVibrationChamber>>
            ESSENTIA_VIBRATION_CHAMBER =
                    REGISTRY.register(
                            "essentia_vibration_chamber",
                            () -> IMenuTypeExtension.create(MenuEssentiaVibrationChamber::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaCellWorkbench>> ESSENTIA_CELL_WORKBENCH =
            REGISTRY.register(
                    "essentia_cell_workbench",
                    () -> IMenuTypeExtension.create(MenuEssentiaCellWorkbench::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuDistillationEncoder>> DISTILLATION_ENCODER =
            REGISTRY.register(
                    "distillation_encoder",
                    () -> IMenuTypeExtension.create(MenuDistillationEncoder::new));

    /**
     * The Arcane Crafting Terminal's screen. Built through AE2's {@link MenuTypeBuilder}: it puts the host
     * into the open packet, without which the menu has no network behind it.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuArcaneCraftingTerminal>>
            ARCANE_CRAFTING_TERMINAL = REGISTRY.register(
                    "arcane_crafting_terminal",
                    () -> MenuTypeBuilder.<MenuArcaneCraftingTerminal, ITerminalHost>create(
                                    (menuType, id, playerInventory, host) ->
                                            new MenuArcaneCraftingTerminal(menuType, id, playerInventory, host),
                                    ITerminalHost.class)
                            .withMenuTitle(host -> Component.translatable(TERMINAL_TITLE))
                            .buildUnregistered(ThEIds.id("arcane_crafting_terminal")));

    /**
     * The Essentia Terminal. The builder puts the host - the cable part, or the wireless item in hand - into
     * the open packet. The title is named because AE2's terminal style carries {@code gui.ae2.Terminal}.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaTerminal>> ESSENTIA_TERMINAL =
            REGISTRY.register(
                    "essentia_terminal",
                    () -> MenuTypeBuilder.create(MenuEssentiaTerminal::new, ITerminalHost.class)
                            .withMenuTitle(host -> Component.translatable("gui.thaumicenergistics_ce.essentia_terminal"))
                            .buildUnregistered(ThEIds.id("essentia_terminal")));

    /**
     * The Wireless Essentia Terminal's screen. A second menu type rather than a reuse of the wired one: the
     * builder encodes the host class into the open packet, and the host differs.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaTerminal>> WIRELESS_ESSENTIA_TERMINAL =
            REGISTRY.register(
                    "wireless_essentia_terminal",
                    () -> MenuTypeBuilder.create(MenuEssentiaTerminal::new, IPortableTerminal.class)
                            .withMenuTitle(host ->
                                    Component.translatable("gui.thaumicenergistics_ce.wireless_essentia_terminal"))
                            .buildUnregistered(ThEIds.id("wireless_essentia_terminal")));

    /**
     * The Wireless Arcane Crafting Terminal's screen: the same menu as the wired terminal, opened from a
     * handheld item, which is why the host class - not a second menu class - is what differs.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuArcaneCraftingTerminal>>
            WIRELESS_ARCANE_CRAFTING_TERMINAL = REGISTRY.register(
                    "wireless_arcane_crafting_terminal",
                    () -> MenuTypeBuilder.create(MenuArcaneCraftingTerminal::new, IPortableTerminal.class)
                            .withMenuTitle(host -> Component.translatable(TERMINAL_TITLE))
                            .buildUnregistered(ThEIds.id("wireless_arcane_crafting_terminal")));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaStorageBus>> ESSENTIA_STORAGE_BUS =
            REGISTRY.register(
                    "essentia_storage_bus",
                    () -> MenuTypeBuilder.create(
                                    MenuEssentiaStorageBus::new, PartEssentiaStorageBus.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "gui.thaumicenergistics_ce.essentia_storage_bus"))
                            .buildUnregistered(ThEIds.id("essentia_storage_bus")));

    /**
     * The Essentia Level Emitter's screen. The reporting value rides along as initial data - a setting, not
     * something the server keeps pushing - so the threshold box opens on what the emitter is set to.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaLevelEmitter>> ESSENTIA_LEVEL_EMITTER =
            REGISTRY.register(
                    "essentia_level_emitter",
                    () -> MenuTypeBuilder.create(
                                    MenuEssentiaLevelEmitter::new, PartEssentiaLevelEmitter.class)
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
