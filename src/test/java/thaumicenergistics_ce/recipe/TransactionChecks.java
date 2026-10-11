package thaumicenergistics_ce.recipe;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import thaumicenergistics_ce.util.ThETransaction;

final class TransactionChecks {
    private TransactionChecks() {}

    static void run() {
        Balance balance = new Balance();
        require(ThETransaction.preview(balance::take) == 10 && balance.amount == 100, "Preview consumed resources");
        require(ThETransaction.apply(balance::take) == 10 && balance.amount == 90, "Root payment did not commit");
        try (Transaction transaction = Transaction.openRoot()) {
            ThETransaction.apply(balance::take);
            require(balance.amount == 80, "Nested payment not applied");
            ThETransaction.preview(balance::take);
            require(balance.amount == 80, "Nested preview affected parent payment");
        }
        require(balance.amount == 90, "Cancelled craft consumed resources");
        try (Transaction transaction = Transaction.openRoot()) {
            ThETransaction.apply(balance::take);
            transaction.commit();
        }
        require(balance.amount == 80, "Parent commit lost payment");
        try {
            ThETransaction.apply(transaction -> {
                balance.take(transaction);
                throw new IllegalStateException("expected failure");
            });
            throw new AssertionError("Payment exception swallowed");
        } catch (IllegalStateException expected) {
            require(balance.amount == 80, "Failed payment consumed resources");
        }
        System.out.println("[transaction-checks] PASS: preview, commit, cancellation, nested preview, exception rollback");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Balance extends SnapshotJournal<Integer> {
        private int amount = 100;

        int take(TransactionContext transaction) {
            updateSnapshots(transaction);
            amount -= 10;
            return 10;
        }

        @Override
        protected Integer createSnapshot() {
            return amount;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            amount = snapshot;
        }
    }
}
