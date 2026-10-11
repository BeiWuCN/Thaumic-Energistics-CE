package thaumicenergistics_ce.recipe;

import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.config.ThEConfig;

final class TerminalChunkAuraChecks {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var pos = new BlockPos(0, 64, 0);
        level.getChunkAt(pos);
        float original = AuraHelper.getVis(level, pos);
        boolean aeEnabled = ThEConfig.ALLOW_AE_FOR_AURA_VIS.get();
        try {
            ThEConfig.ALLOW_AE_FOR_AURA_VIS.set(false);
            AuraHelper.drainVis(level, pos, Float.MAX_VALUE, false);
            AuraHelper.addVis(level, pos, 20);
            try (var preview = Transaction.openRoot()) {
                require(TerminalAuraPayment.payChunkAura(level, pos, 7, preview) == 7,
                        "Card-free chunk aura payment failed with AE access disabled");
                require(Math.abs(AuraHelper.getVis(level, pos) - 13) < 0.001,
                        "Aura payment did not consume seven whole VIS");
            }
            require(Math.abs(AuraHelper.getVis(level, pos) - 20) < 0.001,
                    "Preview consumed chunk VIS");
            try (var craft = Transaction.openRoot()) {
                require(TerminalAuraPayment.payChunkAura(level, pos, 7, craft) == 7, "Commit failed");
                craft.commit();
            }
            require(Math.abs(AuraHelper.getVis(level, pos) - 13) < 0.001, "Commit did not consume VIS");
            try (var failedCraft = Transaction.openRoot()) {
                require(TerminalAuraPayment.payChunkAura(level, pos, 14, failedCraft) == 13,
                        "Insufficient chunk VIS was fabricated");
            }
            require(Math.abs(AuraHelper.getVis(level, pos) - 13) < 0.001, "Failed craft consumed VIS");
            System.out.println("[terminal-chunk-aura] PASS: no card/AE required, whole VIS units, preview rollback, commit, insufficient VIS rollback");
        } finally {
            AuraHelper.drainVis(level, pos, Float.MAX_VALUE, false);
            AuraHelper.addVis(level, pos, original);
            ThEConfig.ALLOW_AE_FOR_AURA_VIS.set(aeEnabled);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
