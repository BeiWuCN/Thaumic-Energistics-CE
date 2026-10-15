package thaumicenergistics_ce.arcane;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.api.upgrades.IUpgradeableObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.init.ModItems;

/** Environmental VIS payment from the placed terminal or handheld player chunk. */
public final class TerminalAuraPayment {




    private TerminalAuraPayment() {}

    /** Whether this terminal has a wand charging card installed. */
    public static boolean visConnectionInstalled(ItemStack terminal) {
        if (terminal.isEmpty() || !(terminal.getItem() instanceof IUpgradeableItem upgradeable)) {
            return false;
        }
        IUpgradeInventory upgrades = upgradeable.getUpgrades(terminal);
        return upgrades != null && upgrades.isInstalled(ModItems.VIS_CONNECTION_CARD.get());
    }

    /**
     * 玩家打开的那台机器是否装了 vis 连接卡；
     * 随身终端和插在线缆上的终端各由自己的升级物品栏作答。
     */
    public static boolean visConnectionInstalled(IUpgradeableObject machine) {
        IUpgradeInventory upgrades = machine.getUpgrades();
        return upgrades != null && upgrades.isInstalled(ModItems.VIS_CONNECTION_CARD.get());
    }

    /** Simulate or commit environmental VIS, expressed in whole VIS. */
    public static int payAura(Level level, BlockPos where, int needVis, boolean simulate) {
        if (needVis <= 0 || level == null || level.isClientSide()) {
            return 0;
        }
        float available = TcAura.drainVis(level, where, (float) needVis, true);
        if (available <= 0.0F) {
            return 0;
        }
        int offered = Math.min(needVis, (int) Math.floor(available));
        if (offered <= 0) {
            return 0;
        }
        if (simulate) {
            return offered;
        }
        // 提交的数额用上一趟读到的值，不重读：同一场合成的两次灵气查询不一致时
        // Thaumaturge 会抛异常，灵气故意只问一次。
        TcAura.drainVis(level, where, (float) offered, false);
        return offered;
    }
}
