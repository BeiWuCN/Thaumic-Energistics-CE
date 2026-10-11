package thaumicenergistics_ce.recipe;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.config.ThEConfig;

public final class TerminalAuraConfigChecks {
    private TerminalAuraConfigChecks() {}

    public static void run() {
        if (!Boolean.FALSE.equals(ThEConfig.ALLOW_AE_FOR_AURA_VIS.getDefault())) {
            throw new IllegalStateException("AE aura access must default to disabled");
        }
        boolean previous = ThEConfig.ALLOW_AE_FOR_AURA_VIS.get();
        try {
            ThEConfig.ALLOW_AE_FOR_AURA_VIS.set(false);
            try (var transaction = Transaction.openRoot()) {
                int paid = TerminalAuraPayment.pay(null, BlockPos.ZERO,
                        (amount, mode, multiplier) -> {
                            throw new IllegalStateException("Disabled payment touched energy");
                        }, 100, transaction);
                if (paid != 0) {
                    throw new IllegalStateException("Disabled payment supplied aura");
                }
            }
            System.out.println("[terminal-aura-config] PASS: disabled by default, no aura or energy access");
        } finally {
            ThEConfig.ALLOW_AE_FOR_AURA_VIS.set(previous);
        }
    }
}
