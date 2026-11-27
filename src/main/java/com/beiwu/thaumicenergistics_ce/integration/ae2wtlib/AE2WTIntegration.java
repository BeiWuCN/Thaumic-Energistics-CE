package com.beiwu.thaumicenergistics_ce.integration.ae2wtlib;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;

/**
 * AE2WTLib 通用终端的接入点：把两个无线奥术终端登记成 {@code WTDefinition}，
 * 让它们能和别的无线终端合并进通用终端、随通用终端切换。
 *
 * <p><b>为什么整个类只在 ae2wtlib 存在时才被触碰</b>：{@code ItemWT} 与
 * {@code AddTerminalEvent} 都来自可选的 {@code ae2wtlib_api}，缺依赖时那些类不存在。
 * 对外只暴露 {@link #enabled()} 与返回 {@link Item} 的工厂，方法体里的可选类型
 * 要等方法真正执行才解析，所以调用方先问 {@code enabled()} 就不会碰上它们。
 *
 * <p><b>注册时机</b>：{@code AddTerminalEvent} 是一次性回调列表，由 api 自己的
 * {@code RegisterEvent(ITEM)} 监听器执行并随即关闭。模组构造阶段登记最稳妥——
 * 那时任何注册表事件都还没跑。{@code mods.toml} 里 api 声明 {@code ordering="BEFORE"}
 * 是第二道保险。
 */
public final class AE2WTIntegration {

    /** 合成终端的 WTDefinition 键。与 {@code data/.../wireless_universal_terminal/*.json} 里的 terminalName 一致。 */
    public static final String CRAFTING_TERMINAL_NAME = "thaumicenergistics_arcane_crafting";

    /** 编码终端的 WTDefinition 键，同上。 */
    public static final String ENCODING_TERMINAL_NAME = "thaumicenergistics_arcane_encoding";

    /** 通用终端切换按钮覆盖层的图集：本 mod 自己的 states.png，两格各 16x16。 */
    private static final ResourceLocation STATES_SHEET =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/states.png");

    private AE2WTIntegration() {}

    /**
     * 物品实例缓存。{@code WTDefinition.ofOrNull(stack)} 用引用相等把物品对到定义上，
     * 注册器与 {@link #registerTerminals()} 必须拿到同一个实例，不能各造一个。
     */
    private static Item craftingTerminal;

    private static Item encodingTerminal;

    /** AE2WTLib 是否在场。缺它时两个无线终端退回独立实现（自研量子桥 + 不吃通用终端）。 */
    public static boolean enabled() {
        return ModList.get().isLoaded("ae2wtlib");
    }

    /**
     * 合成终端物品的工厂。返回 {@link Item} 而不是具体类型，
     * 免得普通注册类的字节码验证提前解析 {@code ItemWT} 子类。
     */
    public static synchronized Item createCraftingTerminal() {
        if (craftingTerminal == null) {
            craftingTerminal = new AE2WTArcaneCraftingTerminalItem();
        }
        return craftingTerminal;
    }

    /** 编码终端物品的工厂，理由同上。 */
    public static synchronized Item createEncodingTerminal() {
        if (encodingTerminal == null) {
            encodingTerminal = new AE2WTArcaneEncodingTerminalItem();
        }
        return encodingTerminal;
    }

    /**
     * 把源质访问卡也登记给 AE2WTLib 的通用终端。
     *
     * <p>通用终端是另一个物品（{@code ItemWUT}），上面的 {@code Upgrades.add} 全部落在本 mod
     * 自己的 {@code ItemWT} 上，所以「通用终端 + 奥术合成终端升级」那个组合里，
     * {@code getMaxInstallable(源质访问卡, ItemWUT)} 一直是 0：卡插不进，可用升级里也看不到。
     *
     * <p>{@code UpgradeHelper} 就是 AE2WTLib 给这件事准备的入口（它的磁卡也走这里）：
     * 登记一次，所有已注册的无线终端与通用终端都拿到这张卡。副作用是没装奥术终端的通用终端
     * 也能插这张卡——无害，卡只在奥术终端菜单里被读。
     *
     * <p>必须在 {@code AddTerminalEvent} 已经跑过之后调，否则它只知道当时已登记的终端。
     */
    public static void registerUniversalTerminalUpgrades() {
        if (!enabled()) {
            return;
        }
        // 注意：这里只能调 UpgradeHelper，不要自己补 Upgrades.add(能量卡, 物品, 2)。
        // Upgrades.add 对同一对 (卡, 物品) 是往列表里追加，getMaxInstallable 取第一个命中的值，
        // 而 AE2WTLib 的 addUpgrades() 已经用 upgradeCount 登记过了 —— 再 add 一次改不了上限，
        // 只会在 tooltip 里多出一行重复的能量卡。
        //
        // 所以各终端的能量卡上限就是它的 upgradeCount：合成终端 3（槽也是 3），
        // 编码终端 2（本轮从 1 改成 2，槽也是 2）。
        de.mari_023.ae2wtlib.api.registration.UpgradeHelper.addUpgradeToAllTerminals(
                com.beiwu.thaumicenergistics_ce.registry.TECEItems.ESSENTIA_ACCESS_CARD.get(), 1);
    }

    /**
     * 把两个终端登记进通用终端体系。调用方须先确认 {@link #enabled()}。
     *
     * <p>图标取 {@code states.png} 的 {@code (0,16)} 与 {@code (16,16)}：两格 16x16 是这两个
     * 终端在切换面板里的覆盖层，和物品图标同形，只差几个绿点做区分。
     *
     * <p><b>菜单类型传静态实例，不能传 {@code DeferredHolder.get()}</b>：本回调由 api 在
     * ITEM 的 {@code RegisterEvent} 里执行，而 MENU 注册表排在那之后，那一刻 holder 还没绑定，
     * 取它直接抛 NPE。
     */
    public static void registerTerminals() {
        if (!enabled()) {
            return;
        }
        de.mari_023.ae2wtlib.api.registration.AddTerminalEvent.register(event -> {
            var states = new de.mari_023.ae2wtlib.api.gui.Icon.Texture(STATES_SHEET, 256, 256);
            // 3 个升级槽：量子桥卡 1 张 + 源质访问卡 1 张 + 魔力连接卡 1 张，与原独立实现一致。
            // upgradeCount 写 2 而不是 3：AE2WTLib 拿它当「通过 UpgradeHelper 登记的卡」的可装上限
            // （能量卡上限就是它），写成 3 会让能量卡也允许 3 张、把三个功能槽全占满。
            // 它不决定库存大小——槽数仍由 AE2WTArcaneCraftingTerminalItem.UPGRADE_SLOTS = 3 给，
            // 两者故意不同。量子桥卡与魔力连接卡不经过 UpgradeHelper，各自的 1 张上限不受影响。
            // 附带好处：WUTHandler.getUpgradeCardCount() 是各定义 upgradeCount 之和，
            // 这里 3→2 与编码终端 1→2 正好相抵，通用终端总槽位不变。
            // 图标取 states.png 的 (0,16)：两格 16x16 是这两个终端在切换面板里的覆盖层。
            event.builder(CRAFTING_TERMINAL_NAME,
                            AE2WTArcaneCraftingTerminalMenuHost::new,
                            TECEMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL_TYPE,
                            (AE2WTArcaneCraftingTerminalItem) createCraftingTerminal(),
                            new de.mari_023.ae2wtlib.api.gui.Icon(0, 16, 16, 16, states))
                    .upgradeCount(2)
                    .hotkeyName("thaumicenergistics_ce.wireless_arcane_crafting_terminal")
                    .addTerminal();
            // 编码终端：2 格（源质访问卡 + 能量卡）。upgradeCount 必须与
            // AE2WTArcaneEncodingTerminalItem.UPGRADE_SLOTS 一致：AE2WTLib 的
            // UpgradeHelper 拿 upgradeCount 当卡片可装上限（能量卡上限也来自它），
            // 写 1 的话库存有 2 格也插不进第二张。量子桥卡仍然不行：这个菜单没有奇点槽。
            // 连带影响：WUTHandler.getUpgradeCardCount() 是所有已登记定义 upgradeCount 的和，
            // 这里从 1 改成 2，通用终端的总升级槽与能量卡上限也跟着 +1。
            event.builder(ENCODING_TERMINAL_NAME,
                            AE2WTArcaneEncodingTerminalMenuHost::new,
                            TECEMenuTypes.WIRELESS_ARCANE_ENCODING_TERMINAL_TYPE,
                            (AE2WTArcaneEncodingTerminalItem) createEncodingTerminal(),
                            new de.mari_023.ae2wtlib.api.gui.Icon(16, 16, 16, 16, states))
                    .upgradeCount(2)
                    .hotkeyName("thaumicenergistics_ce.wireless_arcane_encoding_terminal")
                    .addTerminal();
        });
    }
}
