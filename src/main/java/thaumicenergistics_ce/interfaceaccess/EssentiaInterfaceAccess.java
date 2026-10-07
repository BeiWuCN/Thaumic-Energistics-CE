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
 * 一个带我们访问卡的 ME 接口：把它接触到的容器里的源质拉进网络，
 * 取出卡时清空两行。标记留在配置行里，不会被清除。流动只有一个方向，
 * 因为源质终端本来就会把源质从网络里取走。
 * 访问由注册表的轮次驱动，而不是 AE2 的 tickable 服务，
 * 所以拔出的卡会立刻停下它。
 */
public final class EssentiaInterfaceAccess {

    /** 每轮之间的 tick 数。与源质总线的量级相同；AE2 自己的接口也是 5。 */
    public static final int ROUND_TICKS = 5;

    /** 每轮从邻居抽出的源质，以点计：8，绝不超过。 */
    public static final int POINTS_PER_ROUND = 8;

    /** 每移动一点所耗的 AE，定价与 [BlockEntityAlchemyProvider.AE_PER_ESSENTIA] 一致。 */
    public static final double AE_PER_POINT = 10.0;

    private final InterfaceLogicHost host;
    private final IManagedGridNode node;

    /** 由第一轮设置，也只有那一轮会清理早先版本留下的标记。 */
    private boolean storageRowCleared;

    /**
     * 把控制器绑定到一个活着的宿主。宿主是被持有而不是再去查找，这样一轮不会去追一个
     * 在两个 tick 之间被破坏的接口。
     */
    public EssentiaInterfaceAccess(InterfaceLogicHost host, IManagedGridNode node) {
        this.host = host;
        this.node = node;
    }

    /**
     * 宿主是否还在、还可以工作：level 或节点消失的方块实体或部件必须退出注册表，
     * 否则每一轮都在走一个幽灵。
     */
    public boolean stillValid() {
        return host.getBlockEntity() != null && node.getGrid() != null;
    }

    /**
     * 一轮：每个被标记的要素都从一个邻居拉进网络，供给不足时取消这次移动，
     * 而不是折半。
     */
    public void runRound() {
        InterfaceLogic logic = host.getInterfaceLogic();
        // 先拉取且只拉一次，这样卡在轮次中途被取出时，标记仍不会被改动。
        if (!logic.getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            return;
        }
        BlockEntity be = host.getBlockEntity();
        IGrid grid = node.getGrid();
        if (be == null || grid == null || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // 用网格自己的存储，而不是 [logic.getInventory()]：有配置行时后者会给出
        // 接口的存储行，于是同一个点会同时落进网络和接口里。
        MEStorage network = grid.getStorageService().getInventory();
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (network == null || energy == null) {
            return;
        }
        cleanStorageRow(logic.getStorage());
        absorb(faces(), logic.getConfig(), network, energy);
    }

    /**
     * 提供给邻居的面，按一轮访问它们的顺序：接口形态只算它自己那一面，
     * 方块形态是全部六个。
     */
    private Direction[] faces() {
        if (host instanceof InterfacePart part) {
            Direction side = part.getSide();
            return side == null ? new Direction[0] : new Direction[] {side};
        }
        return Direction.values();
    }

    /**
     * 丢弃早先版本这张卡让 JEI 写进存储行的要素。只做一次：之后的轮次不再动这一行，
     * 因为那时里面的要素可能就是 AE2 自己对该标记的存货。
     */
    private void cleanStorageRow(ConfigInventory storage) {
        if (storageRowCleared) {
            return;
        }
        storageRowCleared = true;
        EssentiaInterfaceRows.dropStaleAspects(storage);
    }

    /**
     * 把配置行当作白名单。行内没有要素时抽取所有要素；否则只抽取它列出的那些。
     * 其它类型的键不属于我们，会被跳过。
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

    /** 邻居到网络：先付费的顺序，避免插入被拒时把源质吃掉。 */
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
            // 网络不肯全收；把余下的还回去，而不是销毁掉。
            storage.insert(aspect, (int) (taken - inserted), false);
        }
        return (int) inserted;
    }

    /** 网格能付得起多少，只问不花：钳制是为了防止能量充裕的网格溢出。 */
    private int affordable(int units, IEnergyService energy) {
        if (units <= 0) {
            return 0;
        }
        double offered =
                energy.extractAEPower(units * AE_PER_POINT, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return (int) Math.min(units, Math.floor(offered / AE_PER_POINT + 1.0e-6));
    }

    /** 为这些单位付费；当网格实际比预演时看到的更穷时返回 false。 */
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
        // 这里机器就是节点；[IActionHost] 是那唯一的访问器，所以这个 lambda 就是全部。
        return IActionSource.ofMachine(() -> node.getNode());
    }
}
