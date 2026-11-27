package com.beiwu.thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.integration.ae2.essentia.EssentiaFillHelper;
import com.beiwu.thaumicenergistics_ce.menu.slot.ContainerSlot;
import com.beiwu.thaumicenergistics_ce.network.EssentiaTerminalReceiver;

/**
 * 任何搬运手持罐或瓶内容物而非物品的终端中，属于源质的那一半。
 * 源质终端和无线奥术合成终端都要它；子类继承而非复制，
 * 该手势需要这个菜单的携带堆、槽位与玩家判定。
 * 每次动作都查询它，从不缓存，卡片一离开槽位就把这些手势一起带走。
 */
public abstract class MenuEssentiaTerminalBase extends MEStorageMenu implements EssentiaTerminalReceiver {

    public static final appeng.menu.SlotSemantic QUANTUM_SINGULARITY =
            appeng.menu.SlotSemantics.register("THAUMICENERGISTICS_QUANTUM_SINGULARITY", true);

    /**
     * AE2WTLib 奇点子库存的 id。用字面量而不是 {@code WTMenuHost.INV_SINGULARITY}：
     * 那个常量来自可选的 {@code ae2wtlib_api}，在常量池里写它会让本类在缺依赖时加载失败。
     * 值由 {@code WTMenuHost} 的字节码实证：{@code ae2wtlib:singularity}。
     */
    private static final net.minecraft.resources.ResourceLocation WT_SINGULARITY_INV =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2wtlib", "singularity");

    /**
     * AE2WTLib 自己的奇点槽语义（它的 {@code ScrollingUpgradesPanel} 只收集
     * {@code AE2wtlibSlotSemantics.SINGULARITY} 与 {@code UPGRADE}，且硬编码把列表首槽视为奇点槽）。
     *
     * <p>经纯净门面 {@code AE2WTIntegration.singularitySlotSemantic()} 主动读取静态字段，
     * 而不用 {@code SlotSemantics.get("AE2WTLIB_SINGULARITY")} 软查：AE2WTLib 在模组启动时
     * 不加载 {@code AE2wtlibSlotSemantics}，若玩家先打开本 mod 无线终端，软查会返回 {@code null}
     * 并回退到 {@link #QUANTUM_SINGULARITY}，导致奇点槽进不了滚动面板、第 1 个升级槽反被误藏。
     */
    private static appeng.menu.SlotSemantic ae2wtlibSingularitySemantic() {
        var semantic = com.beiwu.thaumicenergistics_ce.integration.ae2wtlib.AE2WTIntegration
                .singularitySlotSemantic();
        return semantic != null ? semantic : QUANTUM_SINGULARITY;
    }

    private static boolean hasQuantumBridgeCard(ITerminalHost host) {
        return com.beiwu.thaumicenergistics_ce.item.QuantumTerminalSupport.enabled()
                && host instanceof appeng.api.upgrades.IUpgradeableObject upgradeable
                && upgradeable.getUpgrades().isInstalled(
                        com.beiwu.thaumicenergistics_ce.item.QuantumTerminalSupport.card());
    }

    /**
     * 装了 AE2WTLib 的无线终端要奇点槽，但它的槽位语义属于可选 API，奇点库存则可以从
     * 接口按 id 软取（{@code WTMenuHost.getSubInventory}）。取得到就挂，取不到不管；
     * 这样菜单类不需要在常量池里写任何可选类型。
     */
    private void addAe2wtlibQuantumSlot(ITerminalHost host) {
        if (!(host instanceof appeng.api.inventories.ISegmentedInventory segmented)) return;
        var singularity = segmented.getSubInventory(WT_SINGULARITY_INV);
        if (singularity == null) return;
        var slot = new appeng.menu.slot.AppEngSlot(singularity, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return hasQuantumBridgeCard(host)
                        && com.beiwu.thaumicenergistics_ce.item.QuantumTerminalSupport.validSingularity(stack)
                        && super.mayPlace(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        slot.setIcon(appeng.client.gui.Icon.BACKGROUND_SINGULARITY);
        // WT 屏已经把 AE2 自己的 UpgradesPanel 摘掉了，靠它布局的
        // [TerminalScreenVisibilityMixin#joinQuantumSlot] 也一并失效；改用
        // AE2WTLib 的语义，让它的滚动面板把这个槽一起摆。
        addSlot(slot, ae2wtlibSingularitySemantic());
    }

    private void addQuantumSlot(ITerminalHost host) {
        // 只管本 mod 自研宿主：AE2WTLib 版宿主（WTMenuHost）不在这个体系里，
        // 它的奇点槽由 addAe2wtlibQuantumSlot 单独挂载，两边都挂会出现两个槽抢同一张卡。
        if (!com.beiwu.thaumicenergistics_ce.item.QuantumTerminalSupport.enabled()
                || !(host instanceof com.beiwu.thaumicenergistics_ce.item.QuantumWirelessTerminalMenuHost<?> quantum)) return;
        var slot = new appeng.menu.slot.AppEngSlot(quantum.singularityInventory(), 0) {
            @Override
            public boolean isSlotEnabled() {
                // 直接读底层库存而非 hasItem()/getItem()，避免 AppEngSlot.getItem() 回调 isSlotEnabled() 递归。
                return super.isSlotEnabled()
                        && (!quantum.singularityInventory().getStackInSlot(0).isEmpty()
                                || hasQuantumBridgeCard(host));
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return hasQuantumBridgeCard(host)
                        && com.beiwu.thaumicenergistics_ce.item.QuantumTerminalSupport.validSingularity(stack)
                        && super.mayPlace(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        slot.setIcon(appeng.client.gui.Icon.BACKGROUND_SINGULARITY);
        addSlot(slot, QUANTUM_SINGULARITY);
    }


    public MenuEssentiaTerminalBase(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
        addQuantumSlot(host);
        addAe2wtlibQuantumSlot(host);
    }

    protected MenuEssentiaTerminalBase(MenuType<?> menuType, int id, Inventory playerInventory,
            ITerminalHost host, boolean bindInventory) {
        super(menuType, id, playerInventory, host, bindInventory);
        addQuantumSlot(host);
        addAe2wtlibQuantumSlot(host);
    }

    @Override
    protected int transferStackToMenu(ItemStack stack) {
        if (com.beiwu.thaumicenergistics_ce.item.QuantumTerminalSupport.validSingularity(stack)) {
            var slots = getSlots(QUANTUM_SINGULARITY);
            if (slots.isEmpty()) {
                slots = getSlots(ae2wtlibSingularitySemantic());
            }
            if (!slots.isEmpty()) {
                var remaining = stack.copy();
                for (var slot : slots) {
                    if (slot.isActive()) {
                        remaining = slot.safeInsert(remaining);
                    }
                }
                int transferred = stack.getCount() - remaining.getCount();
                if (transferred > 0) {
                    return transferred;
                }
            }
        }
        return super.transferStackToMenu(stack);
    }

    public boolean isServerSide() {
        return !isClientSide();
    }

    @Override
    public int containerId() {
        return this.containerId;
    }

    /** 由子类终端覆写：只有它的访问卡在槽里时才提供这些手势。 */
    protected boolean essentiaAccessGranted() {
        return true;
    }

    /**
     * 对外只读版：容器策略跑在菜单之外（AE2 自己的 FILL_ITEM 路径），
     * 拿不到 {@code protected} 方法，又不该绕过访问卡的门槛。
     */
    public boolean essentiaInteractionAllowed() {
        return essentiaAccessGranted();
    }

    @Override
    public boolean fillFromNetwork(Player player, int where, ResourceLocation aspectId, boolean wholeStack) {
        if (isClientSide() || !essentiaAccessGranted()) {
            return false;
        }
        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return false;
        }
        // 一轮消耗手持堆里的一个物品，交回一个已填充的；
        // 整堆点击就是重复这一轮：第一次被拒时停下，也在堆的最后一个物品处停下。
        int turns = wholeStack ? container.getCount() : 1;
        boolean moved = false;
        for (int turn = 0; turn < turns; turn++) {
            if (!EssentiaFillHelper.isContainerEmpty(container)) {
                break;
            }
            if (!EssentiaFillHelper.fillFromNetwork(
                    player.level(), storage, energySource, getActionSource(), player, container, aspectId)) {
                break;
            }
            moved = true;
        }
        if (moved) {
            // 已填充容器会被另一个物品堆替换，客户端对这两处的副本都过时了。
            broadcastChanges();
        }
        return moved;
    }

    @Override
    public boolean takeFilledPhial(Player player, ResourceLocation aspectId) {
        if (isClientSide() || !essentiaAccessGranted()) {
            return false;
        }
        ItemStack filled = EssentiaFillHelper.takeFilledPhial(
                player.level(), storage, getActionSource(), player, aspectId);
        if (filled == null) {
            return false;
        }
        // 光标空着就放光标（玩家一眼能看到），否则进背包，再不行就掉在脚下。
        if (getCarried().isEmpty()) {
            setCarried(filled);
        } else if (!player.getInventory().add(filled)) {
            player.drop(filled, false);
        }
        broadcastChanges();
        return true;
    }

    @Override
    public void deposit(Player player, int where) {
        if (isClientSide() || !essentiaAccessGranted()) {
            return;
        }

        // 按菜单槽位：对玩家槽位 shift 右键，会清空放在里面的容器。
        if (where >= 0) {
            if (where >= slots.size()) {
                return;
            }
            Slot target = slots.get(where);
            // 只限玩家自己的槽位，AE2 拥有的槽位无法从这里清空。
            if (!isPlayerSideSlot(target)) {
                return;
            }
            ItemStack inSlot = target.getItem();
            if (!EssentiaFillHelper.isSupportedContainer(inSlot)) {
                return;
            }
            ItemStack left = emptyIntoNetwork(player, inSlot);
            if (left != null) {
                target.set(left);
                // 容器在点击之下变了，客户端对该槽位的副本已过时。
                broadcastChanges();
            }
            return;
        }

        ItemStack container = containerAt(player, where);
        if (container == null || !EssentiaFillHelper.isSupportedContainer(container)) {
            return;
        }
        ItemStack left = emptyIntoNetwork(player, container);
        if (left == null) {
            return;
        }
        if (where == ContainerSlot.CURSOR) {
            setCarried(left);
        } else {
            player.setItemInHand(InteractionHand.MAIN_HAND, left);
        }
        broadcastChanges();
    }

    public ItemStack emptyIntoNetwork(Player player, ItemStack stack) {
        if (isClientSide()) {
            return null;
        }
        return EssentiaFillHelper.emptyIntoNetwork(storage, energySource, getActionSource(), player, stack);
    }

    private @Nullable ItemStack containerAt(Player player, int where) {
        return switch (where) {
            case ContainerSlot.CURSOR -> getCarried();
            case ContainerSlot.MAIN_HAND -> player.getMainHandItem();
            default -> null;
        };
    }
}