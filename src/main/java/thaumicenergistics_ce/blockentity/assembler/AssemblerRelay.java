package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;

final class AssemblerRelay {

    private long nextRelayReachCheck;
    private @Nullable Boolean relayReach;

    boolean networkInReach(BlockEntityArcaneAssembler owner) {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        long now = server.getGameTime();
        if (relayReach == null || now >= nextRelayReachCheck) {
            nextRelayReachCheck = now + AssemblerVisSource.RELAY_POLL_INTERVAL;
            relayReach = TcAura.relayResolves(server, owner.getBlockPos()) && canSupply(owner, server);
        }
        return relayReach;
    }

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
