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
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            var bound = new thaumicenergistics_ce.part.PartArcaneCraftingTerminal(
                    thaumicenergistics_ce.registry.TECEItems.ARCANE_CRAFTING_TERMINAL.get()) {
                @Override
                public int supplyAura(int need, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                    return TerminalAuraPayment.payChunkAura(level, pos, need, tx);
                }
            };
            var wirelessInput = new thaumicenergistics_ce.recipe.arcane.TerminalArcaneCraftingInput(
                    java.util.Collections.nCopies(9, net.minecraft.world.item.ItemStack.EMPTY), player,
                    net.minecraft.world.item.ItemStack.EMPTY, java.util.List.of(), bound,
                    (amount, mode, multiplier) -> { throw new IllegalStateException("Wireless crafting unnecessarily used AE"); }, false);
            try {
                var supply = thaumicenergistics_ce.recipe.arcane.TerminalWorkbenchVis.class.getDeclaredMethod(
                        "supplyAura", com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext.class,
                        net.minecraft.server.level.ServerPlayer.class,
                        com.leclowndu93150.thaumaturge.api.recipe.IArcaneWorkbench.class, int.class,
                        net.neoforged.neoforge.transfer.transaction.TransactionContext.class);
                supply.setAccessible(true);
                try (var preview = Transaction.openRoot()) {
                    require((int) supply.invoke(null, null, player, wirelessInput, 7, preview) == 7,
                            "Wireless workbench source ignored the bound chunk with AE disabled");
                }
                require(Math.abs(AuraHelper.getVis(level, pos) - 20) < 0.001,
                        "Wireless preview consumed bound chunk VIS");
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            ThEConfig.ALLOW_AE_FOR_AURA_VIS.set(true);
            double[] charge = {10000};
            appeng.api.networking.energy.IEnergySource battery = (amount, mode, multiplier) -> {
                double paid = Math.min(amount, charge[0]);
                if (mode == appeng.api.config.Actionable.MODULATE) charge[0] -= paid;
                return paid;
            };
            try (var preview = Transaction.openRoot()) {
                require(TerminalAuraPayment.pay(level, pos, battery, 100, preview) == 100,
                        "Wireless AE payment preview failed");
                require(charge[0] == 10000, "Preview charged AE before commit");
            }
            require(charge[0] == 10000 && Math.abs(AuraHelper.getVis(level, pos) - 20) < 0.001,
                    "Aborted payment retained AE or VIS debit");
            try (var commit = Transaction.openRoot()) {
                require(TerminalAuraPayment.pay(level, pos, battery, 100, commit) == 100, "AE payment failed");
                commit.commit();
            }
            require(charge[0] == 10000 - ThEConfig.AE_PER_VIS.get()
                    && Math.abs(AuraHelper.getVis(level, pos) - 19) < 0.001, "Committed payment not charged exactly once");
            AuraHelper.addVis(level, pos, 1);
            ThEConfig.ALLOW_AE_FOR_AURA_VIS.set(false);
            System.out.println("[terminal-ae-transaction] PASS: preview/abort no charge, committed AE/VIS charge exactly once");
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
