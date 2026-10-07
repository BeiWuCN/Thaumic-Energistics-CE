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
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;
import thaumicenergistics_ce.menu.MenuEssentiaVibrationChamber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;

/** 菜单类型注册。 */
public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(Registries.MENU, ThEIds.MODID);

    /**
     * AE2 给终端起的名字，"终端"。标题只指明存储那一半；挂在它上面的是哪一半合成，
     * 由样式在网格上方绘制的分区标题决定。
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
     * 奥术合成终端的界面。通过 AE2 的 {@link MenuTypeBuilder} 构建：它会把宿主
     * 放进打开界面的数据包里，没有它菜单背后就没有网络。
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
     * 源质终端。构建器会把宿主——线缆部件，或手中的无线物品——放进
     * 打开界面的数据包。标题被显式指定，因为 AE2 的终端样式自带 {@code gui.ae2.Terminal}。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaTerminal>> ESSENTIA_TERMINAL =
            REGISTRY.register(
                    "essentia_terminal",
                    () -> MenuTypeBuilder.create(MenuEssentiaTerminal::new, ITerminalHost.class)
                            .withMenuTitle(host -> Component.translatable("gui.thaumicenergistics_ce.essentia_terminal"))
                            .buildUnregistered(ThEIds.id("essentia_terminal")));

    /**
     * 无线源质终端的界面。它另设一个菜单类型，而不是复用有线那个：
     * 构建器会把宿主类编码进打开界面的数据包，而两者的宿主不同。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuEssentiaTerminal>> WIRELESS_ESSENTIA_TERMINAL =
            REGISTRY.register(
                    "wireless_essentia_terminal",
                    () -> MenuTypeBuilder.create(MenuEssentiaTerminal::new, IPortableTerminal.class)
                            .withMenuTitle(host ->
                                    Component.translatable("gui.thaumicenergistics_ce.wireless_essentia_terminal"))
                            .buildUnregistered(ThEIds.id("wireless_essentia_terminal")));

    /**
     * 无线奥术合成终端的界面：与有线终端是同一个菜单，只是从手持物品打开，
     * 所以不同之处在于宿主类，而不在于另设一个菜单类。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<MenuArcaneCraftingTerminal>>
            WIRELESS_ARCANE_CRAFTING_TERMINAL = REGISTRY.register(
                    "wireless_arcane_crafting_terminal",
                    () -> MenuTypeBuilder.create(MenuArcaneCraftingTerminal::new, IPortableTerminal.class)
                            .withMenuTitle(host -> Component.translatable(TERMINAL_TITLE))
                            .buildUnregistered(ThEIds.id("wireless_arcane_crafting_terminal")));

    /**
     * 源质标准发信器的界面。上报值作为初始数据一并带过去——它是一项设置，而不是
     * 服务端不断推送的东西——因此阈值框打开时显示的就是发信器当前的设定值。
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
