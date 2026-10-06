package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;

/** Whether Thaumaturge's relay chain is in reach and can pay, with the answer cached between polls. */
final class AssemblerRelay {

    private long nextRelayReachCheck;
    private @Nullable Boolean relayReach;

    /** Whether a relay chain that can answer is in reach. Asked only when deciding whether a craft is
     * payable: a relay resolving to nothing would accept the job and starve. */
    boolean networkInReach(BlockEntityArcaneAssembler owner) {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        // Cached: the caller runs this every tick while a craft is stalled.
        long now = server.getGameTime();
        if (relayReach == null || now >= nextRelayReachCheck) {
            nextRelayReachCheck = now + AssemblerVisSource.RELAY_POLL_INTERVAL;
            // Resolving is not paying: one simulated centivis settles whether an empty node can pay.
            // A chain has one end, and a source that is not a node sells its own.
            relayReach = TcAura.relayResolves(server, owner.getBlockPos()) && canSupply(owner, server);
        }
        return relayReach;
    }

    /** Whether the relay chain can give one centivis of any primal: asking only the first primal would
     * refuse a job the chain could pay for out of another. */
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
