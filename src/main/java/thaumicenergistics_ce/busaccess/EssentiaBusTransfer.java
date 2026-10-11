package thaumicenergistics_ce.busaccess;

import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.integration.ae2.EssentiaMEStorage;
import thaumicenergistics_ce.util.ThELog;

public final class EssentiaBusTransfer {
    private EssentiaBusTransfer() {}

    public static boolean importFrom(StackTransferContext context, IEssentiaStorage storage,
            EssentiaBusBuffer buffer) {
        if (!buffer.isEmpty() || Transaction.getLifecycle() != Transaction.Lifecycle.NONE
                || !context.isKeyTypeEnabled(AEssentiaKeyType.INSTANCE)) {
            return false;
        }
        double cost = ThEConfig.BUS_POWER.get();
        var external = new EssentiaMEStorage(storage);
        var network = context.getInternalStorage().getInventory();
        var source = context.getActionSource();
        boolean moved = false;
        for (var entry : storage.contents().sortedByAmount()) {
            if (!context.hasOperationsLeft() || !buffer.isEmpty()) {
                break;
            }
            var key = AEssentiaKey.of(entry.aspect());
            if (key == null || context.isInFilter(key) == context.isInverted()) {
                continue;
            }
            long wanted = Math.min(entry.amount(), (long) context.getOperationsRemaining()
                    * EssentiaBusAccess.AMOUNT_PER_OPERATION);
            wanted = affordable(context, wanted, cost);
            wanted = external.extract(key, wanted, Actionable.SIMULATE, source);
            wanted = network.insert(key, wanted, Actionable.SIMULATE, source);
            int paid = reserveEnergy(context, wanted, cost);
            if (paid <= 0) {
                continue;
            }
            long before = storage.amount(entry.aspect());
            long taken;
            try {
                taken = external.extract(key, paid, Actionable.MODULATE, source);
            } catch (RuntimeException failure) {
                taken = Math.clamp(before - storage.amount(entry.aspect()), 0L, paid);
                ThELog.LOG.warn("[essentia-bus] source extraction failed; recovering {} committed points of {}",
                        taken, key, failure);
            }
            refundEnergy(context, paid - taken, cost);
            if (taken > 0) {
                buffer.store(key, taken);
                context.reduceOperationsRemaining(operationsFor(taken));
                moved = true;
                buffer.returnToNetwork(network, source);
            }
        }
        return moved;
    }

    public static long exportTo(StackTransferContext context, IEssentiaStorage storage, AEKey key, long amount,
            EssentiaBusBuffer buffer) {
        if (!(key instanceof AEssentiaKey essentia) || !buffer.isEmpty()
                || Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return 0;
        }
        double cost = ThEConfig.BUS_POWER.get();
        var external = new EssentiaMEStorage(storage);
        var network = context.getInternalStorage().getInventory();
        var source = context.getActionSource();
        long wanted = affordable(context, amount, cost);
        wanted = network.extract(key, wanted, Actionable.SIMULATE, source);
        wanted = external.insert(key, wanted, Actionable.SIMULATE, source);
        int paid = reserveEnergy(context, wanted, cost);
        if (paid <= 0) {
            return 0;
        }
        long taken = network.extract(key, paid, Actionable.MODULATE, source);
        refundEnergy(context, paid - taken, cost);
        buffer.store(essentia, taken, PowerMultiplier.CONFIG.multiply(cost));
        var aspect = essentia.resolveAspect();
        long before = storage.amount(aspect);
        long inserted;
        try {
            inserted = external.insert(key, taken, Actionable.MODULATE, source);
        } catch (RuntimeException failure) {
            inserted = Math.clamp(storage.amount(aspect) - before, 0L, taken);
            ThELog.LOG.warn("[essentia-bus] target insertion failed after accepting {} points of {}",
                    inserted, key, failure);
        }
        buffer.remove(inserted);
        if (!buffer.isEmpty()) {
            buffer.returnToNetwork(network, source, Long.MAX_VALUE, context.getEnergySource());
        }
        return inserted;
    }

    public static long operationsFor(long amount) {
        return amount <= 0 ? 0 : (amount + EssentiaBusAccess.AMOUNT_PER_OPERATION - 1)
                / EssentiaBusAccess.AMOUNT_PER_OPERATION;
    }

    private static long affordable(StackTransferContext context, long amount, double cost) {
        amount = Math.max(0, Math.min(amount, Integer.MAX_VALUE));
        if (cost == 0 || amount == 0) {
            return amount;
        }
        double offered = context.getEnergySource().extractAEPower(amount * cost,
                Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return Math.min(amount, (long) Math.floor(offered / cost + 1.0e-6));
    }

    private static int reserveEnergy(StackTransferContext context, long amount, double cost) {
        if (amount <= 0) {
            return 0;
        }
        if (cost == 0) {
            return (int) amount;
        }
        double paid = context.getEnergySource().extractAEPower(amount * cost,
                Actionable.MODULATE, PowerMultiplier.CONFIG);
        int points = (int) Math.min(amount, Math.floor(paid / cost + 1.0e-6));
        refundPower(context, Math.max(0, paid - points * cost));
        return points;
    }

    private static void refundEnergy(StackTransferContext context, long points, double cost) {
        if (points > 0) {
            refundPower(context, points * cost);
        }
    }

    private static void refundPower(StackTransferContext context, double amount) {
        if (amount > 0 && context.getEnergySource() instanceof IEnergyService energy) {
            energy.injectPower(PowerMultiplier.CONFIG.multiply(amount), Actionable.MODULATE);
        }
    }
}
