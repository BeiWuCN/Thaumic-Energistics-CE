package thaumicenergistics_ce.registry;

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
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(Registries.MENU, ThEIds.MODID);

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

    public static final DeferredHolder<MenuType<?>, MenuType<MenuArcaneCraftingTerminal>>
            ARCANE_CRAFTING_TERMINAL = REGISTRY.register(
                    "arcane_crafting_terminal",
                    () -> MenuTypeBuilder.<MenuArcaneCraftingTerminal, ITerminalHost>create(
                                    (menuType, id, playerInventory, host) ->
                                            new MenuArcaneCraftingTerminal(menuType, id, playerInventory, host),
                                    ITerminalHost.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "gui.thaumicenergistics_ce.ArcaneCraftingTerminal"))
                            .buildUnregistered(ThEIds.id("arcane_crafting_terminal")));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaTerminal>> ESSENTIA_TERMINAL =
            REGISTRY.register(
                    "essentia_terminal",
                    () -> MenuTypeBuilder.create(MenuEssentiaTerminal::new, ITerminalHost.class)
                            .withMenuTitle(host -> Component.translatable("gui.thaumicenergistics_ce.essentia_terminal"))
                            .buildUnregistered(ThEIds.id("essentia_terminal")));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaTerminal>> WIRELESS_ESSENTIA_TERMINAL =
            REGISTRY.register(
                    "wireless_essentia_terminal",
                    () -> MenuTypeBuilder.create(MenuEssentiaTerminal::new, IPortableTerminal.class)
                            .withMenuTitle(host ->
                                    Component.translatable("gui.thaumicenergistics_ce.wireless_essentia_terminal"))
                            .buildUnregistered(ThEIds.id("wireless_essentia_terminal")));

    public static final DeferredHolder<MenuType<?>, MenuType<MenuArcaneCraftingTerminal>>
            WIRELESS_ARCANE_CRAFTING_TERMINAL = REGISTRY.register(
                    "wireless_arcane_crafting_terminal",
                    () -> MenuTypeBuilder.create(MenuArcaneCraftingTerminal::new, IPortableTerminal.class)
                            .withMenuTitle(host -> Component.translatable(
                                    "gui.thaumicenergistics_ce.wireless_arcane_crafting_terminal"))
                            .buildUnregistered(ThEIds.id("wireless_arcane_crafting_terminal")));

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
