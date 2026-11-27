package com.beiwu.thaumicenergistics_ce.integration.ae2wtlib;

import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.SlotSemantic;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;

/**
 * AE2WTLib 通用终端的纯门面入口：仅暴露 {@link #enabled()}、常量与转发方法，
 * 自身字节码不直接引用任何 {@code ae2wtlib_api} 或 {@code ItemWT} 子类。
 *
 * <p><b>为什么必须拆到私有静态内部类 {@link Impl}</b>：JVM 在链接验证当前类时，
 * 对子类型赋值（如 {@code AE2WTArcaneCraftingTerminalItem -> WirelessTerminalItem}）或字段类型
 * 会提前加载子类及其超类 {@code ItemWT}。拆入独立嵌套类后，缺 {@code ae2wtlib} 时调用
 * {@link #enabled()} 不会加载 {@code Impl}，彻底避免 {@code NoClassDefFoundError}。
 *
 * <p><b>注册时机</b>：{@code AddTerminalEvent} 是一次性回调列表，由 api 自己的
 * {@code RegisterEvent(ITEM)} 监听器执行并随即关闭。模组构造阶段登记最稳妥——
 * 那时任何注册表事件都还没跑。{@code mods.toml} 里 api 声明 {@code ordering="BEFORE"}
 * 是第二道保险。
 */
public final class AE2WTIntegration {

    /** 合成终端的 WTDefinition 键。与 {@code data/.../wireless_universal_terminal/*.json} 里的 terminalName 一致。 */
    public static final String CRAFTING_TERMINAL_NAME = "thaumicenergistics_arcane_crafting";

    /** 源质终端的 WTDefinition 键，同上。 */
    public static final String ESSENTIA_TERMINAL_NAME = "thaumicenergistics_essentia";

    /** 通用终端切换按钮覆盖层的图集：本 mod 自己的 states.png，两格各 16x16。 */
    private static final ResourceLocation STATES_SHEET =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/states.png");

    private AE2WTIntegration() {}

    /** AE2WTLib 是否在场。缺它时两个无线终端退回独立实现（不吃通用终端）。 */
    public static boolean enabled() {
        return ModList.get().isLoaded("ae2wtlib");
    }

    /** 无线源质终端物品的工厂；调用方须先确认 {@link #enabled()}。 */
    public static WirelessTerminalItem createEssentiaTerminal() {
        return Impl.createEssentiaTerminal();
    }

    /** 无线奥术合成终端物品的工厂；调用方须先确认 {@link #enabled()}。 */
    public static WirelessTerminalItem createCraftingTerminal() {
        return Impl.createCraftingTerminal();
    }

    /** 把源质访问卡与 Vis 连接卡登记给 AE2WTLib 的通用终端（{@code ItemWUT}）。 */
    public static void registerUniversalTerminalUpgrades() {
        if (!enabled()) {
            return;
        }
        Impl.registerUniversalTerminalUpgrades();
    }

    /** 把两个无线终端登记进通用终端体系。 */
    public static void registerTerminals() {
        if (!enabled()) {
            return;
        }
        Impl.registerTerminals();
    }

    /**
     * 取 AE2WTLib 的奇点槽语义（{@code AE2wtlibSlotSemantics.SINGULARITY}）。
     *
     * <p>不能用 {@code SlotSemantics.get("AE2WTLIB_SINGULARITY")} 软查：AE2WTLib 不在模组启动阶段
     * 加载 {@code AE2wtlibSlotSemantics}，只有在首次打开其自身菜单或界面时才触发静态初始化。
     * 若玩家先打开本 mod 无线终端，软查会拿到 {@code null} 并回退到自研语义，导致
     * {@code ScrollingUpgradesPanel} 拿不到奇点槽并误把第 1 个升级槽当成奇点槽隐藏。
     * 经 {@link Impl} 直接读静态字段可主动触发其类初始化，同时保持外层门面零可选依赖引用。
     */
    public static @Nullable SlotSemantic singularitySlotSemantic() {
        return enabled() ? Impl.singularitySlotSemantic() : null;
    }

    /**
     * 承载所有对 {@code ae2wtlib_api} 与 {@code ItemWT} 子类引用的实现类。
     * 仅在 {@link #enabled()} 为真后才会触发加载与字节码验证。
     */
    private static final class Impl {

        /**
         * 物品实例缓存。{@code WTDefinition.ofOrNull(stack)} 用引用相等把物品对到定义上，
         * 注册器与 {@link #registerTerminals()} 必须拿到同一个实例，不能各造一个。
         */
        private static AE2WTArcaneCraftingTerminalItem craftingTerminal;

        private static AE2WTEssentiaTerminalItem essentiaTerminal;

        private Impl() {}

        static SlotSemantic singularitySlotSemantic() {
            return de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics.SINGULARITY;
        }

        static synchronized WirelessTerminalItem createEssentiaTerminal() {
            if (essentiaTerminal == null) {
                essentiaTerminal = new AE2WTEssentiaTerminalItem();
            }
            return essentiaTerminal;
        }

        static synchronized WirelessTerminalItem createCraftingTerminal() {
            if (craftingTerminal == null) {
                craftingTerminal = new AE2WTArcaneCraftingTerminalItem();
            }
            return craftingTerminal;
        }

        /**
         * 通用终端是独立物品，{@code ThaumicEnergisticsCE} 里落在本 mod 终端上的
         * {@code Upgrades.add} 不会作用于它。这里直接登记到 {@code AE2wtlibAPI.getWUT()}
         * 而非 {@code UpgradeHelper.addUpgradeToAllTerminals}：一方面避免把本 mod 专属卡
         * 登记到无关无线终端（含无线源质终端与原生终端）上，另一方面防止与本 mod 终端的
         * 单独登记重复导致 Tooltip 重复。
         */
        static void registerUniversalTerminalUpgrades() {
            var universalTerminal = de.mari_023.ae2wtlib.api.AE2wtlibAPI.getWUT();
            if (universalTerminal == net.minecraft.world.item.Items.AIR) {
                return;
            }
            appeng.api.upgrades.Upgrades.add(
                    com.beiwu.thaumicenergistics_ce.registry.TECEItems.ESSENTIA_ACCESS_CARD.get(),
                    universalTerminal,
                    1);
            appeng.api.upgrades.Upgrades.add(
                    com.beiwu.thaumicenergistics_ce.registry.TECEItems.VIS_CONNECTION_CARD.get(),
                    universalTerminal,
                    1);
        }

        /**
         * 把两个终端登记进通用终端体系。
         *
         * <p><b>菜单类型传静态实例，不能传 {@code DeferredHolder.get()}</b>：本回调由 api 在
         * ITEM 的 {@code RegisterEvent} 里执行，而 MENU 注册表排在那之后，那一刻 holder 还没绑定，
         * 取它直接抛 NPE。
         */
        static void registerTerminals() {
            de.mari_023.ae2wtlib.api.registration.AddTerminalEvent.register(event -> {
                var states = new de.mari_023.ae2wtlib.api.gui.Icon.Texture(STATES_SHEET, 256, 256);
                // 合成终端：2 个升级槽，upgradeCount(2) 与 AE2WTArcaneCraftingTerminalItem.UPGRADE_SLOTS 对齐；
                // AE2WTLib 的 UpgradeHelper 用它登记能量卡上限与量子桥卡（1 张），源质访问卡与 Vis 连接卡单独登记。
                event.builder(CRAFTING_TERMINAL_NAME,
                                AE2WTArcaneCraftingTerminalMenuHost::new,
                                TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL_TYPE,
                                (AE2WTArcaneCraftingTerminalItem) createCraftingTerminal(),
                                new de.mari_023.ae2wtlib.api.gui.Icon(0, 16, 16, 16, states))
                        .upgradeCount(2)
                        .hotkeyName("thaumicenergistics_ce.wireless_arcane_crafting_terminal")
                        .addTerminal();
                var essentiaIcon = new de.mari_023.ae2wtlib.api.gui.Icon.Texture(
                        ThEIds.id("textures/item/wireless_essentia_terminal.png"), 16, 16);
                // 源质终端：2 个升级槽，upgradeCount(2) 与 AE2WTEssentiaTerminalItem.UPGRADE_SLOTS 对齐；
                // 支持 AE2WTLib 自动登记的能量卡与量子桥卡（奇点槽由 MenuEssentiaTerminalBase 挂载）。
                event.builder(ESSENTIA_TERMINAL_NAME,
                                AE2WTEssentiaTerminalMenuHost::new,
                                TECEMenuTypes.WIRELESS_ESSENTIA_TERMINAL_TYPE,
                                (AE2WTEssentiaTerminalItem) createEssentiaTerminal(),
                                new de.mari_023.ae2wtlib.api.gui.Icon(0, 0, 16, 16, essentiaIcon))
                        .upgradeCount(2)
                        .hotkeyName("thaumicenergistics_ce.wireless_essentia_terminal")
                        .addTerminal();
            });
        }
    }
}
