package thaumicenergistics_ce.integration.ae2.patternprovider;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

public final class EssentiaProviderReturn extends SnapshotJournal<GenericStack[]> implements IEssentiaStorage {
    private final appeng.helpers.externalstorage.GenericStackInv inventory;
    private final java.util.function.BooleanSupplier enabled;
    private long revision;
    private boolean changingTransactionally;

    public EssentiaProviderReturn(EssentiaPatternProvider provider) {
        inventory = provider.essentiaBuffer();
        enabled = provider::essentiaEnabled;
    }

    @Override
    public AspectList contents() {
        AspectList result = AspectList.EMPTY;
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (stack != null && stack.what() instanceof AEssentiaKey key) {
                var aspect = key.resolveAspect();
                if (aspect != null) result = result.add(aspect, (int) Math.min(stack.amount(), Integer.MAX_VALUE));
            }
        }
        return result;
    }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
        if (amount <= 0 || !enabled.getAsBoolean()) return 0;
        var key = AEssentiaKey.of(aspect);
        if (key == null) return 0;
        var inv = inventory;
        long accepted = inv.insert(key, amount, Actionable.SIMULATE, IActionSource.empty());
        if (accepted <= 0) return 0;
        updateSnapshots(transaction);
        changingTransactionally = true;
        try {
            return (int) inv.insert(key, accepted, Actionable.MODULATE, IActionSource.empty());
        } finally {
            changingTransactionally = false;
        }
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, TransactionContext transaction) { return 0; }

    @Override
    public long contentRevision() { return revision; }

    public void inventoryChanged() {
        if (!changingTransactionally) revision++;
    }

    @Override
    protected GenericStack[] createSnapshot() {
        var inv = inventory;
        var snapshot = new GenericStack[inv.size()];
        for (int i = 0; i < snapshot.length; i++) snapshot[i] = inv.getStack(i);
        return snapshot;
    }

    @Override
    protected void revertToSnapshot(GenericStack[] snapshot) {
        changingTransactionally = true;
        try {
            for (int i = 0; i < snapshot.length; i++) inventory.setStack(i, snapshot[i]);
        } finally {
            changingTransactionally = false;
        }
    }

    @Override
    protected void onRootCommit(GenericStack[] original) {
        if (!java.util.Arrays.equals(original, createSnapshot())) revision++;
    }
}
