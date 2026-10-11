package thaumicenergistics_ce.busaccess;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.parts.storagebus.StorageBusPart;
import net.minecraft.network.chat.Component;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

public record EssentiaBusStorage(MEStorage delegate, StorageBusPart bus) implements MEStorage {
    private boolean allowed(AEKey key) {
        return !(key instanceof AEssentiaKey) || EssentiaBusAccess.enabled(bus);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return allowed(what) ? delegate.insert(what, amount, mode, source) : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return allowed(what) ? delegate.extract(what, amount, mode, source) : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (EssentiaBusAccess.enabled(bus)) {
            delegate.getAvailableStacks(out);
        } else {
            var contents = new KeyCounter();
            delegate.getAvailableStacks(contents);
            for (var entry : contents) {
                if (!(entry.getKey() instanceof AEssentiaKey)) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return allowed(what) && delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }
}
