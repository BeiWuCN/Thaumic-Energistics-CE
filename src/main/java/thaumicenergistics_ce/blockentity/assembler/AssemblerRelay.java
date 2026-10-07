package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;

/** Thaumaturge 的中继链是否在范围内且能付款，答案在轮询之间缓存。 */
final class AssemblerRelay {

    private long nextRelayReachCheck;
    private @Nullable Boolean relayReach;

    /** 能应答的中继链是否在范围内。仅在判断合成是否
     * 可支付时询问：解析不到任何东西的中继点会接下任务然后饿死它。 */
    boolean networkInReach(BlockEntityArcaneAssembler owner) {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        // 已缓存：合成停滞时调用方每 tick 都跑这里。
        long now = server.getGameTime();
        if (relayReach == null || now >= nextRelayReachCheck) {
            nextRelayReachCheck = now + AssemblerVisSource.RELAY_POLL_INTERVAL;
            // 解析得出不等于付得起：模拟的一个 centivis 决定空节点能否付款。
            // 一条链只有一个末端，而非节点的来源卖的是自己的 vis。
            relayReach = TcAura.relayResolves(server, owner.getBlockPos()) && canSupply(owner, server);
        }
        return relayReach;
    }

    /** 中继链能否给出任一原初要素的 1 centivis：只问第一个原初要素会
     * 拒绝掉链子本可以用另一个要素支付的合成。 */
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
