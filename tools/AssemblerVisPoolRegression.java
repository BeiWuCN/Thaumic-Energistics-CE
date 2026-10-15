package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.nbt.CompoundTag;

/** Standalone regression checks; never included in the release JAR. */
public final class AssemblerVisPoolRegression {
    public static void main(String[] args) {
        var pool = new AssemblerVisPool(6);
        pool.bankVisEvenly(1000);
        check(pool, 300);
        pool.spendVis(37);
        check(pool, 263);
        pool.bankVis(200, 5);
        check(pool, 300);
        pool.setBufferedVis(-5);
        check(pool, 0);

        var legacy = new CompoundTag();
        legacy.putInt("BufferedVis", 512);
        legacy.putIntArray("AspectVis", new int[] {Integer.MAX_VALUE, -5, 100, 100, 100, 100});
        pool.readNbt(legacy);
        check(pool, 300);
        var saved = new CompoundTag();
        pool.writeNbt(saved);
        var reloaded = new AssemblerVisPool(6);
        reloaded.readNbt(saved);
        check(reloaded, 300);

        for (int i = 0; i < 10000; i++) {
            reloaded.spendVis(7);
            reloaded.bankVis(3, i % 6);
            reloaded.bankVisEvenly(4);
            check(reloaded, 300);
        }
        legacy.putInt("BufferedVis", -100);
        reloaded.readSync(legacy);
        check(reloaded, 0);
        System.out.println("PASS: VIS capacity, old saves, corrupt data, synchronization and conservation (10000 cycles)");
    }

    private static void check(AssemblerVisPool pool, int expected) {
        if (pool.bufferedVis() != expected) throw new AssertionError("Expected " + expected + ", got " + pool.bufferedVis());
        int sum = 0;
        for (int i = 0; i < 6; i++) {
            int amount = pool.aspectVis(i);
            if (amount < 0 || amount > 300) throw new AssertionError("Invalid aspect amount " + amount);
            sum += amount;
        }
        if (sum != expected) throw new AssertionError("Aspect sum " + sum + " differs from " + expected);
    }
}
