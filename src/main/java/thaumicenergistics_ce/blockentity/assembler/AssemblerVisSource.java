package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.part.PartVisInterface;
import thaumicenergistics_ce.part.VisReservation;

final class AssemblerVisSource {

    private static final int CENTIVIS_PER_VIS = 100;

    static final int RELAY_POLL_INTERVAL = 20;

    private final BlockEntityArcaneAssembler owner;
    private final AssemblerVisPool pool = new AssemblerVisPool(BlockEntityArcaneAssembler.PRIMALS.size());
    private final AssemblerRelay relay = new AssemblerRelay();
    private final AssemblerInterfaceFinder interfaces = new AssemblerInterfaceFinder(RELAY_POLL_INTERVAL);

    private long nextRelayPoll;

    private final int[] aspectCentivis = new int[BlockEntityArcaneAssembler.PRIMALS.size()];

    private long nextInterfacePoll;

    private float auraRemainder;

    AssemblerVisSource(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    int bufferedVis() {
        return pool.bufferedVis();
    }

    int aspectVis(int index) {
        return pool.aspectVis(index);
    }

    int visTarget(boolean crafting, int craftPrice) {
        return pool.visTarget(crafting, craftPrice);
    }

    void spendVis(int amount) {
        pool.spendVis(amount);
    }

    void readNbt(ValueInput input) {
        pool.readNbt(input);
    }

    void writeNbt(ValueOutput output) {
        pool.writeNbt(output);
    }

    void readSync(ValueInput input) {
        pool.readSync(input);
    }

    float auraAround() {
        return AssemblerAura.around(owner);
    }

    int auraCapacity() {
        return AssemblerAura.capacity(owner);
    }

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

    void replenishVis() {
        int target = pool.visTarget(owner.craft.isCrafting(), owner.craft.craftPrice());
        if (owner.getLevel() == null || owner.getLevel().isClientSide() || pool.bufferedVis() >= target) {
            return;
        }
        int before = pool.bufferedVis();
        drainVisFromRelays(target - pool.bufferedVis());
        if (pool.bufferedVis() < target) {
            drainVisFromInterfaces(target - pool.bufferedVis());
        }
        if (pool.bufferedVis() < target) {
            int remaining = target - pool.bufferedVis();
            float thisCall = drainVisAround(remaining);
            float drained = thisCall + auraRemainder;
            int whole = Math.min((int) Math.floor(drained), remaining);
            auraRemainder = drained - whole;
            pool.bankVisEvenly(whole);
        }
        if (pool.bufferedVis() > before) {
            owner.displaySync.markDisplayForUpdate();
        }
    }

    private int drainVisFromRelays(int wantVis) {
        if (wantVis <= 0 || !(owner.getLevel() instanceof ServerLevel server)) {
            return 0;
        }
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
            reservation.close();
        }
    }

    boolean interfaceInReach() {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        PartVisInterface source = interfaces.nearby(server, owner.getBlockPos());
        if (source == null) {
            return false;
        }
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
