package com.beiwu.thaumicenergistics_ce.blockentity.assembler;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcAura;
import com.beiwu.thaumicenergistics_ce.part.PartVisInterface;
import com.beiwu.thaumicenergistics_ce.part.VisReservation;

final class AssemblerVisSource {
    private static final int CENTIVIS_PER_VIS = 100;
    static final int RELAY_POLL_INTERVAL = 20;
    private static final String TAG_AURA_RESERVE = "CraftAuraReserve";

    private final BlockEntityArcaneAssembler owner;
    private final AssemblerVisPool pool = new AssemblerVisPool(BlockEntityArcaneAssembler.PRIMALS.size());
    private final AssemblerRelay relay = new AssemblerRelay();
    private final AssemblerInterfaceFinder interfaces = new AssemblerInterfaceFinder(RELAY_POLL_INTERVAL);
    private final int[] aspectCentivis = new int[BlockEntityArcaneAssembler.PRIMALS.size()];
    private long nextPoll;
    // Ordinary chunk Vis is staged for a pending recipe, not mixed into the
    // six elemental bars. Persist fractional Vis so repeated saves lose nothing.
    private float auraReserve;

    AssemblerVisSource(BlockEntityArcaneAssembler owner) { this.owner = owner; }
    int bufferedVis() { return pool.bufferedVis(); }
    int aspectVis(int index) { return pool.aspectVis(index); }
    int visTarget(boolean crafting, int craftPrice) { return pool.visTarget(crafting, craftPrice); }
    float auraAround() { return AssemblerAura.around(owner); }
    int auraCapacity() { return AssemblerAura.capacity(owner); }
    boolean relayNetworkInReach() { return relay.networkInReach(owner); }

    void readNbt(ValueInput input) {
        pool.readNbt(input);
        auraReserve = Math.clamp(input.getFloatOr(TAG_AURA_RESERVE, 0), 0, AssemblerVisPool.IDLE_TARGET);
        int[] carry = input.getIntArray("RelayCentivis").orElse(new int[0]);
        for (int i = 0; i < aspectCentivis.length; i++) {
            aspectCentivis[i] = i < carry.length ? Math.clamp(carry[i], 0, 99) : 0;
        }
    }

    void writeNbt(ValueOutput output) {
        pool.writeNbt(output);
        output.putFloat(TAG_AURA_RESERVE, auraReserve);
        output.putIntArray("RelayCentivis", aspectCentivis);
    }

    void readSync(ValueInput input) { pool.readSync(input); }

    private int[] requirements() {
        var pattern = owner.craft.currentPattern();
        if (pattern == null) return null;
        int[] required = new int[BlockEntityArcaneAssembler.PRIMALS.size()];
        for (AspectInstance crystal : pattern.primalCrystals().entries()) {
            var key = crystal.aspect().unwrapKey().orElse(null);
            int i = BlockEntityArcaneAssembler.PRIMALS.indexOf(key);
            if (i < 0) return null;
            required[i] = crystal.amount();
        }
        return required;
    }

    private AssemblerVisPayment.Plan payment() {
        int[] required = requirements();
        return required == null ? null : AssemblerVisPayment.plan(pool.elements(),
                (int) Math.floor(auraReserve), owner.craft.craftPrice(), required);
    }

    boolean canPayCraft() { return payment() != null; }

    boolean payCraft() {
        var plan = payment();
        if (plan == null) return false;
        auraReserve -= plan.auraVis();
        pool.applyPayment(plan);
        owner.setChanged();
        owner.displaySync.markDisplayForUpdate();
        return true;
    }

    void replenishVis() {
        if (!(owner.getLevel() instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (now < nextPoll) return;
        nextPoll = now + RELAY_POLL_INTERVAL;

        PartVisInterface source = interfaces.nearby(server, owner.getBlockPos());
        boolean relayAvailable = relayNetworkInReach();
        boolean[] chunkFallback = new boolean[aspectCentivis.length];
        int largestRoom = 0;
        for (int i = 0; i < aspectCentivis.length; i++) {
            int room = (AssemblerVisPool.ASPECT_CAPACITY - pool.aspectVis(i)) * 100 - aspectCentivis[i];
            if (room <= 0) continue;
            var primal = BlockEntityArcaneAssembler.PRIMALS.get(i);
            int got = source == null ? 0 : reserveFrom(source, primal, room);
            if (relayAvailable && got < room) {
                got += TcAura.drainCentivis(server, owner.getBlockPos(), primal, room - got, false);
            }
            int carried = aspectCentivis[i] + got;
            pool.bankVis(carried / CENTIVIS_PER_VIS, i);
            aspectCentivis[i] = carried % CENTIVIS_PER_VIS;
            // A supplied type is filled by the interface first. A missing or
            // insufficient source may fall back to chunk Vis in the same cycle.
            chunkFallback[i] = pool.aspectVis(i) < AssemblerVisPool.ASPECT_CAPACITY;
            if (chunkFallback[i]) largestRoom = Math.max(largestRoom,
                    AssemblerVisPool.ASPECT_CAPACITY - pool.aspectVis(i));
        }
        // Reserve plain chunk Vis before spending it on conversions, after
        // interfaces have supplied the actual recipe elements. A fully covered
        // elemental recipe must not drain unrelated chunk Vis.
        if (owner.craft.isCrafting()) {
            int[] required = requirements();
            if (required != null) {
                int deficit = 0;
                for (int i = 0; i < required.length; i++) {
                    deficit = Math.max(deficit, Math.max(0, required[i] - pool.aspectVis(i)));
                }
                int target = Math.min(AssemblerVisPool.IDLE_TARGET, owner.craft.craftPrice() + deficit);
                if (auraReserve < target) auraReserve += AssemblerAura.drain(owner, target - auraReserve);
            }
        }
        // One chunk Vis splits into one of each primal; only missing types are
        // retained. Never relabel the existing contents of another primal tank.
        if (largestRoom > 0 && !owner.craft.isCrafting()) {
            auraReserve += AssemblerAura.drain(owner, Math.max(0, largestRoom - auraReserve));
        }
        int split = Math.min(largestRoom, (int) Math.floor(auraReserve));
        if (split > 0 && !owner.craft.isCrafting()) {
            auraReserve -= split;
            for (int i = 0; i < chunkFallback.length; i++) {
                if (chunkFallback[i]) pool.bankVis(split, i);
            }
        }
        owner.setChanged();
        owner.displaySync.markDisplayForUpdate();
    }

    private static int reserveFrom(PartVisInterface source, ResourceKey<IAspect> aspect, int centivis) {
        VisReservation reservation = source.reserve(aspect, centivis);
        if (reservation == null) return 0;
        try { return reservation.commit(); }
        finally { reservation.close(); }
    }

    boolean interfaceInReach() {
        if (!(owner.getLevel() instanceof ServerLevel server)) return false;
        PartVisInterface source = interfaces.nearby(server, owner.getBlockPos());
        if (source == null) return false;
        for (var primal : BlockEntityArcaneAssembler.PRIMALS) {
            if (source.availableCentivis(primal) > 0) return true;
        }
        return false;
    }
}
