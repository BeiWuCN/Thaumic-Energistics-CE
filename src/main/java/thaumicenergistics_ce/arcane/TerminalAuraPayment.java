package thaumicenergistics_ce.arcane;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.api.upgrades.IUpgradeableObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.init.ModItems;

/**
 * 一次灵气支付的花费：某个位置上的灵气，要么用 AE 购买，要么直接取用灵气。
 * 共用同一份实现，已放置终端与无线终端因此在兑换率上不会走偏。两者的
 * 差别只在这个位置：已放置终端抽取它所在的区块，无线终端抽取玩家
 * 所在的区块，因为随身携带的工作台没有自己的方块。
 */
public final class TerminalAuraPayment {

    public static final double AE_PER_VIS = 1_000.0;

    public static final int CENTIVIS_PER_VIS = 100;

    private TerminalAuraPayment() {}

    /**
     * 在 {@code where} 抽取灵气并用 {@code energy} 支付，先模拟后提交；
     * 全有或全无，因此提交额不足也不会从 Thaumaturge 的支付处理器里抛异常。
     * @return 提供的 centivis，绝不会超过 {@code needCentivis}
     */
    public static int pay(
            Level level, BlockPos where, @Nullable IEnergySource energy, int needCentivis, boolean simulate) {
        if (needCentivis <= 0 || energy == null) {
            return 0;
        }
        float wanted = (float) needCentivis / CENTIVIS_PER_VIS;
        float available = TcAura.drainVis(level, where, wanted, true);
        if (available <= 0.0F) {
            return 0;
        }
        int offered = Math.min(needCentivis, Math.round(available * CENTIVIS_PER_VIS));
        double cost = AE_PER_VIS * offered / CENTIVIS_PER_VIS;

        double payable = energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (payable < cost) {
            offered = (int) Math.floor(payable / AE_PER_VIS * CENTIVIS_PER_VIS);
            if (offered <= 0) {
                return 0;
            }
            cost = AE_PER_VIS * offered / CENTIVIS_PER_VIS;
        }
        if (simulate) {
            return offered;
        }
        if (energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG) < cost) {
            return 0;
        }
        TcAura.drainVis(level, where, (float) offered / CENTIVIS_PER_VIS, false);
        energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return offered;
    }

    /**
     * 终端物品堆是否装有 vis 连接卡：只有装了，合成才会从灵气中取无属性
     * vis。升级无法读取的物品堆返回 false，保持用电力支付的路径。
     */
    public static boolean visConnectionInstalled(ItemStack terminal) {
        if (terminal.isEmpty() || !(terminal.getItem() instanceof IUpgradeableItem upgradeable)) {
            return false;
        }
        IUpgradeInventory upgrades = upgradeable.getUpgrades(terminal);
        return upgrades != null && upgrades.isInstalled(ModItems.VIS_CONNECTION_CARD.get());
    }

    /**
     * 玩家打开的那台机器是否装有 vis 连接卡；随身终端与插在线缆上的终端
     * 都通过各自的升级物品栏作答。
     */
    public static boolean visConnectionInstalled(IUpgradeableObject machine) {
        IUpgradeInventory upgrades = machine.getUpgrades();
        return upgrades != null && upgrades.isInstalled(ModItems.VIS_CONNECTION_CARD.get());
    }

    /**
     * 为装有 vis 连接卡的终端在 {@code where} 抽取灵气。没有任何能量源参与，
     * 因此这次合成的无属性 vis 不花网络一分钱。
     * @return 提供的 centivis，绝不会超过 {@code needCentivis}
     */
    public static int payAura(Level level, BlockPos where, int needCentivis, boolean simulate) {
        if (needCentivis <= 0 || level == null || level.isClientSide()) {
            return 0;
        }
        float available = TcAura.drainVis(level, where, (float) needCentivis / CENTIVIS_PER_VIS, true);
        if (available <= 0.0F) {
            return 0;
        }
        int offered = Math.min(needCentivis, Math.round(available * CENTIVIS_PER_VIS));
        if (offered <= 0) {
            return 0;
        }
        if (simulate) {
            return offered;
        }
        // 提交的数额就是上一趟读到的值，而不是重新读取：同一场合成的两次灵气查询
        // 不一致时 Thaumaturge 会抛异常，所以灵气是刻意只问一次的。
        TcAura.drainVis(level, where, (float) offered / CENTIVIS_PER_VIS, false);
        return offered;
    }
}
