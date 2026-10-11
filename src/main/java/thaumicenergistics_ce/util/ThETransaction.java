package thaumicenergistics_ce.util;

import java.util.function.ToIntFunction;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class ThETransaction {

    private ThETransaction() {}

    public static int apply(ToIntFunction<TransactionContext> change) {
        TransactionContext open = Transaction.getCurrentOpenedTransaction();
        if (open != null) {
            return change.applyAsInt(open);
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int result = change.applyAsInt(transaction);
            transaction.commit();
            return result;
        }
    }

    public static int preview(ToIntFunction<TransactionContext> change) {
        TransactionContext open = Transaction.getCurrentOpenedTransaction();
        try (Transaction transaction = open == null ? Transaction.openRoot() : Transaction.open(open)) {
            return change.applyAsInt(transaction);
        }
    }
}
