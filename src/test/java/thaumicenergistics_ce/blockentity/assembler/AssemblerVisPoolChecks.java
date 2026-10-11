package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

public final class AssemblerVisPoolChecks {
    private AssemblerVisPoolChecks() {}

    public static void run(MinecraftServer server) {
        var pool = new AssemblerVisPool(6);
        pool.bankVisEvenly(1000);
        check(pool.bufferedVis() == 300 && pool.visTarget(true, 1728) == 300);
        pool.spendVis(100);
        pool.bankVis(500, 3);
        check(pool.bufferedVis() == 300);
        var old = new CompoundTag();
        old.putInt("BufferedVis", 512);
        old.putIntArray("AspectVis", new int[] {100, 100, 100, 100, 100, 12});
        pool.readNbt(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), old));
        int total = 0;
        for (int i = 0; i < 6; i++) { total += pool.aspectVis(i); }
        check(pool.bufferedVis() == 300 && total == 300);
        pool.readSync(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), old));
        total = 0;
        for (int i = 0; i < 6; i++) { total += pool.aspectVis(i); }
        check(pool.bufferedVis() == 300 && total == 300);
        System.out.println("[assembler-vis-cap] PASS: all sources and crafting target capped at 300; legacy save and display totals reconciled");
    }

    private static void check(boolean condition) {
        if (!condition) { throw new IllegalStateException("Assembler vis cache exceeded 300 or its display disagreed"); }
    }
}
