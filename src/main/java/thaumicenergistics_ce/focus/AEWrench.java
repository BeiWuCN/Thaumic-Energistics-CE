package thaumicenergistics_ce.focus;

import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.core.definitions.AEItems;
import appeng.hooks.WrenchHook;
import appeng.parts.reporting.AbstractReportingPart;
import appeng.parts.reporting.ConversionMonitorPart;
import appeng.util.InteractionUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import thaumicenergistics_ce.util.ThELog;

/**
 * AE2 中一切只需要「这里用了一次扳手」的功能。AE2 有一个入口点
 * {@link WrenchHook}，而物品堆靠 {@code c:tools/wrench} 标签判定为扳手，所以这里
 * 换入一把真实扳手，请 AE2 执行该操作，再把玩家的物品放回去。仅服务端，
 * 且总会恢复，因为客户端侧的 {@code setItemInHand} 会让预测失步。
 */
public final class AEWrench {

    private AEWrench() {}

    /**
     * 对单个方块执行 AE2 的扳手操作，如同玩家手持石英扳手。仅服务端。
     *
     * @return 若 AE2 做了事情则为 true；false 表示该方块不是扳手可作用的方块
     */
    public static boolean use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty()) {
            // AE2 是硬依赖，所以这只会发生在它的扳手被改名或移除时。
            return false;
        }

        ItemStack held = player.getItemInHand(hand);
        try {
            player.setItemInHand(hand, wrench);
            InteractionResult result = WrenchHook.onPlayerUseBlock(player, level, hand, hit);
            return result.consumesAction();
        } finally {
            player.setItemInHand(hand, held);
        }
    }

    /**
     * 这次命中下的部件是否是扳手点击会转动的部件：只读，也是客户端
     * 在拿走挖掘之前需要的同一个答案。
     */
    public static boolean wouldRotatePart(IPartHost host, Vec3 localPos) {
        SelectedPart selected = host.selectPartLocal(localPos);
        return turnable(selected);
    }

    /**
     * 转动光标所指的线缆部件，如同玩家手持石英扳手。仅服务端，且
     * 槽位总会恢复：AE2 是从选中的快捷栏槽位读出扳手的。
     */
    public static boolean rotateSelectedPart(Player player, Level level, IPartHost host, Vec3 localPos) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty() || !InteractionUtil.canWrenchRotate(wrench)) {
            // AE2 是硬依赖，所以这只会发生在它的扳手被改名或移除时。
            // 与部件自身所做的同一个判断，读取的是我们即将放进槽位的那个物品堆。
            return false;
        }

        // 宿主是调用方的事：它已把命中转换成 localPos 并选中了
        // 部件，而这个选中结果正是玩家所看到的东西。
        SelectedPart selected = host.selectPartLocal(localPos);
        if (!turnable(selected)) {
            // 一个 facade、一个空的面，或一个没有旋转可推进的部件。
            return false;
        }

        // AbstractReportingPart 从选中的快捷栏槽位读取扳手，且只有它会转动
        // 任何东西：终端只是打开菜单就返回 true，所以改为按字节旋转量扣费。
        byte before = ((AbstractReportingPart) selected.part).getSpin();

        // 1.21.1 的 Inventory 没有 setSelectedItem：选中的物品堆就是 slot "selected" 中的物品。
        int slot = player.getInventory().selected;
        ItemStack previous = player.getInventory().getItem(slot);

        // AE2 会按潜行分支，而监视器对潜行点击的回应是切换自身的锁定，所以
        // 请求转动时不潜行；该标志在 finally 中还原，这才使这段代码安全。
        boolean sneaking = player.isShiftKeyDown();
        player.getInventory().setItem(slot, wrench);
        player.setShiftKeyDown(false);
        try {
            selected.part.onUseWithoutItem(player, localPos);
        } finally {
            player.setShiftKeyDown(sneaking);
            player.getInventory().setItem(slot, previous);
        }

        byte after = ((AbstractReportingPart) selected.part).getSpin();
        return after != before;
    }

    /**
     * 这个选中项是否是转动会移动的部件：只有报告类家族带旋转量，而其中
     * 一个会在非潜行扳手点击时吞掉匹配的物品——即 AE2 对已锁定监视器的行为。
     */
    private static boolean turnable(SelectedPart selected) {
        if (selected == null || !(selected.part instanceof AbstractReportingPart)) {
            return false;
        }
        return !(selected.part instanceof ConversionMonitorPart monitor) || !monitor.isLocked();
    }

    /**
     * 该焦点借用的扳手。AE2 会把扳手操作路由到
     * {@code AEBaseBlockEntity.disassembleWithWrench}，这正是它成为拆卸的原因。
     */
    public static ItemStack wrenchStack() {
        return AEItems.CERTUS_QUARTZ_WRENCH.stack();
    }
}
