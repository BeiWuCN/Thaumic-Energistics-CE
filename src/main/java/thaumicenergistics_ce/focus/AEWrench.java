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
 * AE2 里凡是要「这里用了一次扳手」的功能。AE2 的入口点是 {@link WrenchHook}，
 * 物品堆靠 {@code c:tools/wrench} 标签判定为扳手：这里换入一把真扳手，请 AE2 做完，
 * 再把玩家的物品放回去。仅服务端，且总会恢复：客户端侧的 {@code setItemInHand} 会让预测失步。
 */
public final class AEWrench {

    private AEWrench() {}

    /**
     * 对单个方块执行 AE2 的扳手操作，像玩家手持石英扳手。仅服务端。
     * @return AE2 做了事就是 true；false 表示这方块扳手动不了
     */
    public static boolean use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty()) {
            // AE2 是硬依赖：只有它的扳手被改名或移除才会走到这里。
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
     * 这次命中的部件是不是扳手点击会转动的：只读，客户端的挖掘判断也要这个答案。
     */
    public static boolean wouldRotatePart(IPartHost host, Vec3 localPos) {
        SelectedPart selected = host.selectPartLocal(localPos);
        return turnable(selected);
    }

    /**
     * 转动光标所指的线缆部件，像玩家手持石英扳手。仅服务端，
     * 槽位总会恢复：AE2 从选中的快捷栏槽位读扳手。
     */
    public static boolean rotateSelectedPart(Player player, Level level, IPartHost host, Vec3 localPos) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty() || !InteractionUtil.canWrenchRotate(wrench)) {
            // AE2 是硬依赖：只有它的扳手被改名或移除才会走到这里。
            // 判断和部件自身做的同一个，读的是马上要放进槽位的那个物品堆。
            return false;
        }

        // 宿主由调用方负责：它把命中转成 localPos 并选中部件，
        // 选出来的就是玩家看到的那个。
        SelectedPart selected = host.selectPartLocal(localPos);
        if (!turnable(selected)) {
            // facade、空面，或没有旋转可推进的部件。
            return false;
        }

        // 只有 AbstractReportingPart 从选中的快捷栏槽位读扳手，也只有它真转东西：
        // 终端只是打开菜单也返回 true，判定得看字节旋转量有没有变。
        byte before = ((AbstractReportingPart) selected.part).getSpin();

        // 1.21.1 的 Inventory 没有 setSelectedItem：选中的物品堆就是 slot "selected" 里的物品。
        int slot = player.getInventory().selected;
        ItemStack previous = player.getInventory().getItem(slot);

        // AE2 按潜行分支，监视器对潜行点击的回应是切换自己的锁定，
        // 请求转动时不潜行；标志在 finally 里还原，这段代码才安全。
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
     * 这个选中项是不是转动会移动的部件：只有报告类家族带旋转量，
     * 有一族在非潜行扳手点击时吞掉匹配物品，也就是 AE2 对已锁定监视器的行为。
     */
    private static boolean turnable(SelectedPart selected) {
        if (selected == null || !(selected.part instanceof AbstractReportingPart)) {
            return false;
        }
        return !(selected.part instanceof ConversionMonitorPart monitor) || !monitor.isLocked();
    }

    /**
     * 该焦点借用的扳手。AE2 把扳手操作路由到
     * {@code AEBaseBlockEntity.disassembleWithWrench}，拆方块走的就是它。
     */
    public static ItemStack wrenchStack() {
        return AEItems.CERTUS_QUARTZ_WRENCH.stack();
    }
}
