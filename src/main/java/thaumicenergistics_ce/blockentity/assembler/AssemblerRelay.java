package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;

/** Thaumaturge 的中继链在不在范围内、付不付得起，答案在两次轮询之间缓存。 */
final class AssemblerRelay {

    private long nextRelayReachCheck;
    private @Nullable Boolean relayReach;

    /** 能应答的中继链在不在范围内。只在判断合成付不付得起时问：
     * 解析不出来的中继点会接下任务然后把它饿死。 */
    boolean networkInReach(BlockEntityArcaneAssembler owner) {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        // 结果已缓存：合成卡住时调用方每 tick 都跑到这里。
        long now = server.getGameTime();
        if (relayReach == null || now >= nextRelayReachCheck) {
            nextRelayReachCheck = now + AssemblerVisSource.RELAY_POLL_INTERVAL;
            // 解析得出不等于付得起：用 1 centivis 模拟一次，看空节点付不付得出来。
            // 一条链只有一个末端；非节点的来源卖的是自己的 vis。
            relayReach = TcAura.relayResolves(server, owner.getBlockPos()) && canSupply(owner, server);
        }
        return relayReach;
    }

    /** 中继链能不能给出任意一个原初要素的 1 centivis：
     * 只问第一个原初要素，会拒掉本可用另一个要素支付的合成。 */
    private boolean canSupply(BlockEntityArcaneAssembler owner, ServerLevel server) {
        int primals = BlockEntityArcaneAssembler.PRIMALS.size();
        for (int i = 0; i < primals; i++) {
            if (TcAura.drainCentivis(server, owner.getBlockPos(), BlockEntityArcaneAssembler.PRIMALS.get(i), 1, true)
                    > 0) {
                return true;
            }
        }
        return false;
    }
}
