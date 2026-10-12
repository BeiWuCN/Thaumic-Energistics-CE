package thaumicenergistics_ce.integration.ae2.patternprovider;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.EssentiaMEStorage;
import thaumicenergistics_ce.part.EssentiaNeighbour;

public record EssentiaProviderTarget(@Nullable PatternProviderTarget original, Level level, BlockPos target,
        Direction face, IActionSource source, BooleanSupplier enabled) implements PatternProviderTarget {
    @Override
    public long insert(AEKey what, long amount, Actionable mode) {
        if (!(what instanceof AEssentiaKey)) {
            return original == null ? 0 : original.insert(what, amount, mode);
        }
        if (!enabled.getAsBoolean()) return 0;
        var storage = EssentiaNeighbour.find(level, target, face);
        return storage == null ? original == null ? 0 : original.insert(what, amount, mode)
                : new EssentiaMEStorage(storage).insert(what, amount, mode, source);
    }

    @Override
    public boolean containsPatternInput(Set<AEKey> inputs) {
        if (original != null && original.containsPatternInput(inputs)) return true;
        if (!enabled.getAsBoolean()) return false;
        var storage = EssentiaNeighbour.find(level, target, face);
        if (storage == null) return false;
        for (var entry : new EssentiaMEStorage(storage).getAvailableStacks()) {
            if (inputs.contains(entry.getKey().dropSecondary())) return true;
        }
        return false;
    }
}
