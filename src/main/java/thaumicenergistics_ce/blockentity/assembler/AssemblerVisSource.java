package thaumicenergistics_ce.blockentity.assembler;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.part.PartVisInterface;
import thaumicenergistics_ce.part.VisReservation;

/**
 * 组装机的 vis 从哪里来：周围的灵气、Thaumaturge 的中继链，以及本 mod 的
 * vis 接口——再加上把它们给的东西存起来的池子。
 * 同包内，所以它直接够到机器的状态而不走访问器；机器保留自己的
 * 公开 getter 并把它们转发到这里。
 */
final class AssemblerVisSource {

    /** 一 vis 中的 centivis：中继网络按百分之一 vis 回答，灵气按整数 vis 回答。 */
    private static final int CENTIVIS_PER_VIS = 100;

    static final int RELAY_POLL_INTERVAL = 20;

    private final BlockEntityArcaneAssembler owner;
    private final AssemblerVisPool pool = new AssemblerVisPool(BlockEntityArcaneAssembler.PRIMALS.size());
    private final AssemblerRelay relay = new AssemblerRelay();
    private final AssemblerInterfaceFinder interfaces = new AssemblerInterfaceFinder(RELAY_POLL_INTERVAL);

    private long nextRelayPoll;

    /** 不足一 vis 的 centivis，按要素分：整数 vis 归提供它的那个要素。 */
    private final int[] aspectCentivis = new int[BlockEntityArcaneAssembler.PRIMALS.size()];

    private long nextInterfacePoll;

    /** 已取的灵气 vis 中不足一 vis 的部分：灵气是 float，池子是整数 vis。 */
    private float auraRemainder;

    AssemblerVisSource(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    // ------------------------------------------------------------------
    // 池子，转发
    // ------------------------------------------------------------------

    int bufferedVis() {
        return pool.bufferedVis();
    }

    int aspectVis(int index) {
        return pool.aspectVis(index);
    }

    String aspectVisTrace() {
        return pool.aspectVisTrace();
    }

    int visTarget(boolean crafting, int craftPrice) {
        return pool.visTarget(crafting, craftPrice);
    }

    void spendVis(int amount) {
        pool.spendVis(amount);
    }

    void readNbt(CompoundTag tag) {
        pool.readNbt(tag);
    }

    void writeNbt(CompoundTag tag) {
        pool.writeNbt(tag);
    }

    void readSync(CompoundTag tag) {
        pool.readSync(tag);
    }

    // ------------------------------------------------------------------
    // vis 从哪来
    // ------------------------------------------------------------------

    float auraAround() {
        return AssemblerAura.around(owner);
    }

    int auraCapacity() {
        return AssemblerAura.capacity(owner);
    }

    /** 能作答的中继链是否在范围内。只在判断一次合成是否
     * 付得起时询问：解析不到任何东西的中继会接下任务然后断供。 */
    boolean relayNetworkInReach() {
        return relay.networkInReach(owner);
    }

    private int relayCarryTotal() {
        int total = 0;
        for (int value : aspectCentivis) {
            total += value;
        }
        return total;
    }

    /** 用周围的灵气补满 vis 缓冲，从网格 tick 而非合成循环里做：
     * 灵气访问仅限服务端线程。 */
    void replenishVis() {
        int target = pool.visTarget(owner.craft.isCrafting(), owner.craft.craftPrice());
        if (owner.getLevel() == null || owner.getLevel().isClientSide() || pool.bufferedVis() >= target) {
            return;
        }
        // 先问中继，再问灵气：节点的 vis 存在节点里，只看灵气会显得断供。
        int before = pool.bufferedVis();
        drainVisFromRelays(target - pool.bufferedVis());
        if (pool.bufferedVis() < target) {
            // 直接询问：中继自己挑父级，优先节点而不是附属来源（relink）。
            drainVisFromInterfaces(target - pool.bufferedVis());
        }
        if (pool.bufferedVis() < target) {
            // 灵气 vis 没有要素，所以平均入账（bankVisEvenly）；drain 返回 float。
            int remaining = target - pool.bufferedVis();
            float thisCall = drainVisAround(remaining);
            float drained = thisCall + auraRemainder;
            int whole = Math.min((int) Math.floor(drained), remaining);
            auraRemainder = drained - whole;
            // 本次抽取的整数 vis：它存不进去的零头在上面结转。
            pool.bankVisEvenly(whole);
        }
        if (pool.bufferedVis() > before) {
            owner.displaySync.markDisplayForUpdate();
        }
    }

    /** 从 Thaumaturge 的中继网络补满缓冲，与它的工作台一样：{@code drainCentivis}
     * 找到已链接的中继并沿链行走；每个元质被索取相等的份额。
     * @return 取得的整数 vis；回答偏少意味着“也要去问灵气” */
    private int drainVisFromRelays(int wantVis) {
        if (wantVis <= 0 || !(owner.getLevel() instanceof ServerLevel server)) {
            return 0;
        }
        // 先问那个便宜的缓存问题：每次 drainCentivis 都要扫一遍中继。
        if (!relayNetworkInReach()) {
            return 0;
        }
        long now = server.getGameTime();
        if (now < nextRelayPoll) {
            return 0;
        }
        nextRelayPoll = now + RELAY_POLL_INTERVAL;
        int wantCentivis = wantVis * CENTIVIS_PER_VIS - relayCarryTotal();
        if (wantCentivis <= 0) {
            return 0;
        }
        int primals = BlockEntityArcaneAssembler.PRIMALS.size();
        int share = (wantCentivis + primals - 1) / primals;
        int taken = 0;
        for (int i = 0; i < primals; i++) {
            if (taken >= wantCentivis) {
                break;
            }
            int ask = Math.min(share, wantCentivis - taken);
            int got = TcAura.drainCentivis(
                    server, owner.getBlockPos(), BlockEntityArcaneAssembler.PRIMALS.get(i), ask, false);
            taken += got;
            // 按要素入账，只记整数 vis；零头留在挣得它的那个要素名下。
            int carried = aspectCentivis[i] + got;
            pool.bankVis(carried / CENTIVIS_PER_VIS, i);
            aspectCentivis[i] = carried % CENTIVIS_PER_VIS;
        }
        return taken / CENTIVIS_PER_VIS;
    }

    private int drainVisFromInterfaces(int wantVis) {
        if (wantVis <= 0 || !(owner.getLevel() instanceof ServerLevel server)) {
            return 0;
        }
        PartVisInterface source = interfaces.nearby(server, owner.getBlockPos());
        if (source == null) {
            return 0;
        }
        long now = server.getGameTime();
        if (now < nextInterfacePoll) {
            return 0;
        }
        nextInterfacePoll = now + RELAY_POLL_INTERVAL;
        int wantCentivis = wantVis * CENTIVIS_PER_VIS - relayCarryTotal();
        if (wantCentivis <= 0) {
            return 0;
        }
        int primals = BlockEntityArcaneAssembler.PRIMALS.size();
        int share = (wantCentivis + primals - 1) / primals;
        int taken = 0;
        for (int i = 0; i < primals && taken < wantCentivis; i++) {
            int ask = Math.min(share, wantCentivis - taken);
            int got = reserveFrom(source, BlockEntityArcaneAssembler.PRIMALS.get(i), ask);
            taken += got;
            int carried = aspectCentivis[i] + got;
            pool.bankVis(carried / CENTIVIS_PER_VIS, i);
            aspectCentivis[i] = carried % CENTIVIS_PER_VIS;
        }
        return taken / CENTIVIS_PER_VIS;
    }

    private static int reserveFrom(PartVisInterface source, ResourceKey<IAspect> aspect, int centivis) {
        VisReservation reservation = source.reserve(aspect, centivis);
        if (reservation == null) {
            return 0;
        }
        try {
            return reservation.commit();
        } finally {
            // 关闭只是账目处理：预留上没有任何东西移动。
            reservation.close();
        }
    }

    /** 机器旁边的 vis 接口是否能卖给它任何东西：光有存在不够，所以要
     * 用一次一 centivis 的预留去问。缓存方式与 {@link #relayNetworkInReach} 相同。 */
    boolean interfaceInReach() {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        PartVisInterface source = interfaces.nearby(server, owner.getBlockPos());
        if (source == null) {
            return false;
        }
        // 任何元质都行：接口卖的是它节点持有的东西，而节点持有一份列表，不是六份。
        int primals = BlockEntityArcaneAssembler.PRIMALS.size();
        for (int i = 0; i < primals; i++) {
            VisReservation probe = source.reserve(BlockEntityArcaneAssembler.PRIMALS.get(i), 1);
            if (probe != null) {
                probe.close();
                return true;
            }
        }
        return false;
    }

    private float drainVisAround(int amount) {
        return AssemblerAura.drain(owner, amount);
    }
}
