package thaumicenergistics_ce.interfaceaccess;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.parts.misc.InterfacePart;
import appeng.util.ConfigInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThELog;

/**
 * 带访问卡的 ME 接口：把碰到的容器里的源质拉进网络；取出卡时清空两行。
 * 标记留在配置行里，不清除。流向只有一个方向，源质终端本来就会从网络取源质。
 * 访问由注册表轮次驱动，不走 AE2 的 tickable 服务，拔出卡立刻停。
 */
public final class EssentiaInterfaceAccess {

    /** 每轮间隔 5 tick。与源质总线同量级，AE2 自己的接口也是 5。 */
    public static final int ROUND_TICKS = 5;

    /** 每轮从邻居抽 8 点源质，封顶。 */
    public static final int POINTS_PER_ROUND = 8;

    /** 每移动一点耗 10 AE，定价与 [BlockEntityAlchemyProvider.AE_PER_ESSENTIA] 一致。 */
    public static final double AE_PER_POINT = 10.0;

    private final InterfaceLogicHost host;
    private final IManagedGridNode node;

    /** 第一轮设上；只有那一轮清理旧版本留下的标记。 */
    private boolean storageRowCleared;

    /**
     * 持有宿主，不再去查找，一轮不会追两个 tick 之间被拆掉的接口。
     */
    public EssentiaInterfaceAccess(InterfaceLogicHost host, IManagedGridNode node) {
        this.host = host;
        this.node = node;
    }

    /**
     * 宿主还在不在。level 或节点没了的方块实体和部件要退出注册表，
     * 不然每轮都在走一个幽灵。
     */
    public boolean stillValid() {
        return host.getBlockEntity() != null && node.getGrid() != null;
    }

    /**
     * 一轮：每个被标记的要素从一个邻居拉进网络；供给不足就整次取消，不折半。
     */
    public void runRound() {
        InterfaceLogic logic = host.getInterfaceLogic();
        // 先拉一次，只拉一次；卡在轮次中途被抽出，标记也不动。
        if (!logic.getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            return;
        }
        BlockEntity be = host.getBlockEntity();
        IGrid grid = node.getGrid();
        if (be == null || grid == null || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // 用网格自己的存储，不用 [logic.getInventory()]：有配置行时它给的是接口存储行，
        // 同一个点会同时落进网络和接口。
        MEStorage network = grid.getStorageService().getInventory();
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (network == null || energy == null) {
            return;
        }
        cleanStorageRow(logic.getStorage());
        absorb(faces(), logic.getConfig(), network, energy);
    }

    /**
     * 按轮次访问顺序列出的邻居面：接口形态只算正面，方块形态六个面全算。
     */
    private Direction[] faces() {
        if (host instanceof InterfacePart part) {
            Direction side = part.getSide();
            return side == null ? new Direction[0] : new Direction[] {side};
        }
        return Direction.values();
    }

    /**
     * 扔掉旧版本这张卡让 JEI 写进存储行的要素。只做一次：后面各轮不再碰这一行，
     * 那时里面的要素可能就是 AE2 自己存的货。
     */
    private void cleanStorageRow(ConfigInventory storage) {
        if (storageRowCleared) {
            return;
        }
        storageRowCleared = true;
        EssentiaInterfaceRows.dropStaleAspects(storage);
    }

    /**
     * 配置行当白名单：行里没要素就抽全部，有就只抽列出的那些。
     * 别的类型的键不归我们管，跳过。
     */
    private void absorb(
            Direction[] faces,
            ConfigInventory config,
            MEStorage network,
            IEnergyService energy) {
        List<AEKey> allowed = EssentiaInterfaceRows.whitelist(config);
        for (Direction face : faces) {
            IEssentiaStorage neighbour = EssentiaNeighbour.at(host, face);
            if (neighbour == null) {
                continue;
            }
            int budget = POINTS_PER_ROUND;
            for (AspectInstance entry : neighbour.contents().sortedByAmount()) {
                if (budget <= 0) {
                    break;
                }
                Holder<IAspect> aspect = entry.aspect();
                if (aspect == null || entry.amount() <= 0) {
                    continue;
                }
                AEssentiaKey key = AEssentiaKey.of(aspect);
                if (key == null || !EssentiaInterfaceRows.mayEnter(allowed, key)) {
                    continue;
                }
                budget -= pull(neighbour, network, aspect, Math.min(budget, entry.amount()), energy);
            }
        }
    }

    /** 从邻居到网络。先付费，插入被拒才不会吞掉源质。 */
    private int pull(
            IEssentiaStorage storage,
            MEStorage network,
            Holder<IAspect> aspect,
            int available,
            IEnergyService energy) {
        int affordable = affordable(Math.min(POINTS_PER_ROUND, available), energy);
        if (affordable <= 0) {
            return 0;
        }
        long have = storage.extract(aspect, affordable, true);
        int wanted = (int) Math.min(have, affordable);
        if (wanted <= 0 || !pay(wanted, energy)) {
            return 0;
        }
        int taken = storage.extract(aspect, wanted, false);
        if (taken <= 0) {
            return 0;
        }
        long inserted = network.insert(AEssentiaKey.of(aspect), taken, Actionable.MODULATE, actionSource());
        if (inserted < taken) {
            // 网络不肯全收，余下的还回去，不销毁。
            storage.insert(aspect, (int) (taken - inserted), false);
        }
        return (int) inserted;
    }

    /** 网格付得起多少，只问不花。钳制防的是能量充裕的网格溢出。 */
    private int affordable(int units, IEnergyService energy) {
        if (units <= 0) {
            return 0;
        }
        double offered =
                energy.extractAEPower(units * AE_PER_POINT, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return (int) Math.min(units, Math.floor(offered / AE_PER_POINT + 1.0e-6));
    }

    /** 付这些单位的费；网格实际比预演时更穷就返回 false。 */
    private boolean pay(int units, IEnergyService energy) {
        double cost = units * AE_PER_POINT;
        double paid = energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (paid + 1.0e-6 < cost) {
            ThELog.LOG.debug("[essentia-interface] the grid paid {} of {} AE", paid, cost);
            return false;
        }
        return true;
    }

    private IActionSource actionSource() {
        // 机器就是节点；[IActionHost] 只有这一个访问器，lambda 到此为止。
        return IActionSource.ofMachine(() -> node.getNode());
    }
}
