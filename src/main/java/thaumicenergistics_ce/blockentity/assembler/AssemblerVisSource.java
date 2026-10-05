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
 * Where the assembler's vis comes from: the surrounding aura, Thaumaturge's relay chain, and this
 * mod's vis interfaces - plus the pool that banks what they give.
 * Same package, so it reaches the machine's state directly instead of through accessors; the
 * machine keeps its public getters and forwards them here.
 */
final class AssemblerVisSource {

    /** Centivis in one vis: the relay network answers in hundredths of a vis, the aura in whole vis. */
    private static final int CENTIVIS_PER_VIS = 100;

    static final int RELAY_POLL_INTERVAL = 20;

    private final BlockEntityArcaneAssembler owner;
    private final AssemblerVisPool pool = new AssemblerVisPool(BlockEntityArcaneAssembler.PRIMALS.size());
    private final AssemblerRelay relay = new AssemblerRelay();
    private final AssemblerInterfaceFinder interfaces = new AssemblerInterfaceFinder(RELAY_POLL_INTERVAL);

    private long nextRelayPoll;

    /** Centivis below a whole vis, per aspect: a whole vis goes to the aspect that supplied it. */
    private final int[] aspectCentivis = new int[BlockEntityArcaneAssembler.PRIMALS.size()];

    private long nextInterfacePoll;

    /** Aura vis taken but not yet a whole vis: the aura is a float, the pool is whole vis. */
    private float auraRemainder;

    AssemblerVisSource(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    // ------------------------------------------------------------------
    // The pool, forwarded
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
    // Where vis comes from
    // ------------------------------------------------------------------

    float auraAround() {
        return AssemblerAura.around(owner);
    }

    int auraCapacity() {
        return AssemblerAura.capacity(owner);
    }

    /** Whether a relay chain that can answer is in reach. Asked only when deciding whether a craft is
     * payable: a relay resolving to nothing would accept the job and starve. */
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

    /** Tops the vis buffer up from the surrounding aura, from the grid tick rather than the craft loop:
     * aura access is server-thread only. */
    void replenishVis() {
        int target = pool.visTarget(owner.craft.isCrafting(), owner.craft.craftPrice());
        if (owner.getLevel() == null || owner.getLevel().isClientSide() || pool.bufferedVis() >= target) {
            return;
        }
        // Relays first, then the aura: a node's vis lives in the node, so the aura alone reads as starved.
        int before = pool.bufferedVis();
        drainVisFromRelays(target - pool.bufferedVis());
        if (pool.bufferedVis() < target) {
            // Asked directly: a relay picks its own parent, preferring a node over an addon source (relink).
            drainVisFromInterfaces(target - pool.bufferedVis());
        }
        if (pool.bufferedVis() < target) {
            // Aura vis has no aspect, so it lands evenly (bankVisEvenly); a drain returns a float.
            int remaining = target - pool.bufferedVis();
            float thisCall = drainVisAround(remaining);
            float drained = thisCall + auraRemainder;
            int whole = Math.min((int) Math.floor(drained), remaining);
            auraRemainder = drained - whole;
            // The whole vis of this drain: the fraction it could not bank is carried above.
            pool.bankVisEvenly(whole);
        }
        if (pool.bufferedVis() > before) {
            owner.displaySync.markDisplayForUpdate();
        }
    }

    /** Tops the buffer up from Thaumaturge's relay network, as its workbench does: {@code drainCentivis}
     * finds a linked relay and walks its chain; every primal is asked an equal share.
     * @return whole vis obtained; a short answer means "ask the aura as well" */
    private int drainVisFromRelays(int wantVis) {
        if (wantVis <= 0 || !(owner.getLevel() instanceof ServerLevel server)) {
            return 0;
        }
        // Ask the cheap cached question first: each drainCentivis scans for a relay.
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
            // Banked per aspect, whole vis only; the remainder stays with the aspect that earned it.
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
            // Closing is bookkeeping only: nothing moves on reserve.
            reservation.close();
        }
    }

    /** Whether a vis interface beside the machine can sell it anything: presence is not enough, so a
     * one-centivis reservation asks. Cached like {@link #relayNetworkInReach}. */
    boolean interfaceInReach() {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        PartVisInterface source = interfaces.nearby(server, owner.getBlockPos());
        if (source == null) {
            return false;
        }
        // Any primal will do: an interface sells what its node holds, and a node holds one list, not six.
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
