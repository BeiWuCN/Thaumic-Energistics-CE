package thaumicenergistics_ce.integration.ae2.bus;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.integration.ae2.EssentiaMEStorage;
import thaumicenergistics_ce.part.EssentiaNeighbour;

public final class EssentiaBusStrategies {
    private EssentiaBusStrategies() {}

    public static void register() {
        StackImportStrategy.register(AEssentiaKeyType.INSTANCE, (level, pos, side) -> context -> {
            if (!EssentiaBusAccess.enabled(context.getActionSource().machine().orElse(null))) {
                return false;
            }
            var storage = EssentiaNeighbour.find(level, pos, side);
            var machine = context.getActionSource().machine().orElse(null);
            return storage != null && machine instanceof EssentiaBusHost host
                    && EssentiaBusTransfer.importFrom(context, storage, host.tce$recoveryBuffer());
        });
        StackExportStrategy.register(AEssentiaKeyType.INSTANCE, Export::new);
        ExternalStorageStrategy.register(AEssentiaKeyType.INSTANCE, (level, pos, side) ->
                (extractableOnly, changed) -> {
                    if (EssentiaNeighbour.find(level, pos, side) == null) {
                        return null;
                    }
                    return new MountedStorage(level, pos, side, extractableOnly, changed);
                });
    }

    private record Export(ServerLevel level, BlockPos target, Direction face) implements StackExportStrategy {
        @Override
        public long transfer(StackTransferContext context, AEKey what, long maxAmount) {
            if (!EssentiaBusAccess.enabled(context.getActionSource().machine().orElse(null))) {
                return 0;
            }
            var storage = EssentiaNeighbour.find(level, target, face);
            var machine = context.getActionSource().machine().orElse(null);
            return storage != null && machine instanceof EssentiaBusHost host
                    ? EssentiaBusTransfer.exportTo(context, storage, what, maxAmount, host.tce$recoveryBuffer()) : 0;
        }

        @Override
        public long push(AEKey what, long maxAmount, Actionable mode) {
            if (!EssentiaBusAccess.enabled(EssentiaBusAccess.facingBus(level, target, face))) {
                return 0;
            }
            var storage = EssentiaNeighbour.find(level, target, face);
            return storage == null ? 0
                    : new EssentiaMEStorage(storage).insert(what, maxAmount, mode, IActionSource.empty());
        }
    }

    private record MountedStorage(ServerLevel level, BlockPos target, Direction face,
            boolean extractableOnly, Runnable changed) implements MEStorage {
        private EssentiaMEStorage activeStorage() {
            if (!EssentiaBusAccess.enabled(EssentiaBusAccess.facingBus(level, target, face))) {
                return null;
            }
            var storage = EssentiaNeighbour.find(level, target, face);
            return storage == null ? null : new EssentiaMEStorage(storage);
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            var storage = activeStorage();
            if (storage == null) {
                return;
            }
            var contents = new KeyCounter();
            storage.getAvailableStacks(contents);
            for (var entry : contents) {
                if (!extractableOnly || storage.extract(entry.getKey(), 1, Actionable.SIMULATE,
                        IActionSource.empty()) > 0) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            var storage = activeStorage();
            long moved = storage == null ? 0 : storage.insert(what, amount, mode, source);
            notifyChange(moved, mode);
            return moved;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            var storage = activeStorage();
            long moved = storage == null ? 0 : storage.extract(what, amount, mode, source);
            notifyChange(moved, mode);
            return moved;
        }

        private void notifyChange(long moved, Actionable mode) {
            if (moved > 0 && mode == Actionable.MODULATE) {
                changed.run();
            }
        }

        @Override
        public Component getDescription() {
            return Component.translatable("gui.thaumicenergistics_ce.essentia_storage");
        }
    }
}
