package thaumicenergistics_ce.busaccess;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.util.ConfigInventory;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThELog;

public final class EssentiaBusBuffer {
    public static final String SAVE_KEY = "tceEssentiaRecovery";
    private final ConfigInventory inventory;
    private boolean reportedFailure;
    private double refundPerPoint;

    public EssentiaBusBuffer(Runnable changed) {
        inventory = ConfigInventory.storage(1).supportedType(AEssentiaKeyType.INSTANCE)
                .allowOverstacking(true).changeListener(changed).build();
    }

    public GenericStack held() {
        return inventory.getStack(0);
    }

    public boolean isEmpty() {
        return held() == null;
    }

    public void clear() {
        inventory.setStack(0, null);
        refundPerPoint = 0;
    }

    public void store(AEssentiaKey key, long amount) {
        store(key, amount, 0);
    }

    public void store(AEssentiaKey key, long amount, double refundPerPoint) {
        if (amount <= 0) {
            return;
        }
        var held = held();
        if (held != null && !held.what().equals(key)) {
            throw new IllegalStateException("Essentia recovery must finish before another aspect is withdrawn");
        }
        this.refundPerPoint = refundPerPoint;
        inventory.setStack(0, new GenericStack(key, amount + (held == null ? 0 : held.amount())));
    }

    public void remove(long amount) {
        var held = held();
        if (held != null && amount > 0) {
            long remaining = held.amount() - Math.min(amount, held.amount());
            inventory.setStack(0, remaining == 0 ? null : new GenericStack(held.what(), remaining));
            if (remaining == 0) {
                refundPerPoint = 0;
            }
        }
    }

    public long returnToNetwork(MEStorage network, IActionSource source) {
        return returnToNetwork(network, source, Long.MAX_VALUE);
    }

    public long returnToNetwork(MEStorage network, IActionSource source, long maximum) {
        return returnToNetwork(network, source, maximum, null);
    }

    public long returnToNetwork(MEStorage network, IActionSource source, long maximum, IEnergySource energy) {
        var held = held();
        if (held == null) {
            return 0;
        }
        long inserted;
        try {
            inserted = network.insert(held.what(), Math.min(maximum, held.amount()), Actionable.MODULATE, source);
            reportedFailure = false;
        } catch (RuntimeException failure) {
            if (!reportedFailure) {
                ThELog.LOG.warn("[essentia-bus] recovery insertion failed; keeping {} points of {} in the bus",
                        held.amount(), held.what(), failure);
                reportedFailure = true;
            }
            return 0;
        }
        double refund = inserted * refundPerPoint;
        remove(inserted);
        if (refund > 0 && energy instanceof IEnergyService service) {
            service.injectPower(refund, Actionable.MODULATE);
        }
        return inserted;
    }

    public void write(ValueOutput output) {
        inventory.writeToChildTag(output, SAVE_KEY);
        if (refundPerPoint > 0 && !isEmpty()) {
            output.putDouble(SAVE_KEY + "Refund", refundPerPoint);
        }
    }

    public void read(ValueInput input) {
        inventory.readFromChildTag(input, SAVE_KEY);
        double saved = input.getDoubleOr(SAVE_KEY + "Refund", 0);
        refundPerPoint = isEmpty() || !Double.isFinite(saved) ? 0 : Math.max(0, saved);
    }
}
