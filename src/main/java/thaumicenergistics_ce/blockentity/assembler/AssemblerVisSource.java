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
 * 组装机的 vis 来源：周围灵气、Thaumaturge 中继链、本 mod 的 vis 接口，加上存它们给的东西的池子。
 */
final class AssemblerVisSource {

    /** 一 vis 等于多少 centivis：中继网络按百分之一 vis 作答，灵气按整 vis 作答。 */
    private static final int CENTIVIS_PER_VIS = 100;

    static final int RELAY_POLL_INTERVAL = 20;

    private final BlockEntityArcaneAssembler owner;
    private final AssemblerVisPool pool = new AssemblerVisPool(BlockEntityArcaneAssembler.PRIMALS.size());
    private final AssemblerRelay relay = new AssemblerRelay();
    private final AssemblerInterfaceFinder interfaces = new AssemblerInterfaceFinder(RELAY_POLL_INTERVAL);

    private long nextRelayPoll;

    /** 按要素记的不足一 vis 的 centivis；整 vis 归供给它的那个要素。 */
    private final int[] aspectCentivis = new int[BlockEntityArcaneAssembler.PRIMALS.size()];

    private long nextInterfacePoll;

    /** 已取的灵气里不足一 vis 的零头：灵气是 float，池子只收整 vis。 */
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

    /** 范围内有没有能作答的中继链。只在判断合成付不付得起时问：解析不到东西的中继会接下任务再断供。 */
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

    /** 用周围灵气补满 vis 缓冲，挂在网格 tick 上，不放在合成循环里：灵气只有服务端线程能碰。 */
    void replenishVis() {
        int target = pool.visTarget(owner.craft.isCrafting(), owner.craft.craftPrice());
        if (owner.getLevel() == null || owner.getLevel().isClientSide() || pool.bufferedVis() >= target) {
            return;
        }
        // 先问中继再问灵气：节点的 vis 存在节点里，只看灵气会误判成断供。
        int before = pool.bufferedVis();
        drainVisFromRelays(target - pool.bufferedVis());
        if (pool.bufferedVis() < target) {
            // 直接问中继，父级由它自己挑：优先节点，它次附属来源（relink）。
            drainVisFromInterfaces(target - pool.bufferedVis());
        }
        if (pool.bufferedVis() < target) {
            // 灵气 vis 没有要素，平摊入账（bankVisEvenly）；drain 返回 float。
            int remaining = target - pool.bufferedVis();
            float thisCall = drainVisAround(remaining);
            float drained = thisCall + auraRemainder;
            int whole = Math.min((int) Math.floor(drained), remaining);
            auraRemainder = drained - whole;
            // 本次抽取的整 vis；存不进去的零头在上面结转。
            pool.bankVisEvenly(whole);
        }
        if (pool.bufferedVis() > before) {
            owner.displaySync.markDisplayForUpdate();
        }
    }

    /** 从 Thaumaturge 中继网络补满缓冲，和它的工作台一样：{@code drainCentivis} 找到已链接的中继再沿链走，
     * 每个元质问同样多的份。
     * @return 拿到的整 vis；偏少表示还要去问灵气 */
    private int drainVisFromRelays(int wantVis) {
        if (wantVis <= 0 || !(owner.getLevel() instanceof ServerLevel server)) {
            return 0;
        }
        // 先问那个便宜的缓存答案：每次 drainCentivis 都要扫一遍中继。
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
            // 按要素入账，只收整 vis；零头留在给它出力的那个要素名下。
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
            // 关闭只走账，预留上不动。
            reservation.close();
        }
    }

    /** 旁边的 vis 接口有没有东西可卖：光摆着不算，要用一次一 centivis 的预留去问。
     * 缓存方式同 {@link #relayNetworkInReach}。 */
    boolean interfaceInReach() {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        PartVisInterface source = interfaces.nearby(server, owner.getBlockPos());
        if (source == null) {
            return false;
        }
        // 哪个元质都行：接口卖的是节点持有的那份列表，节点只有一份。
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
