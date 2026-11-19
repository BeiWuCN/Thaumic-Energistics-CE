package com.beiwu.thaumicenergistics_ce.util;

import java.util.function.ToIntFunction;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import com.leclowndu93150.thaumaturge.api.transaction.TransactionContext;

public final class ThETransaction {

    private ThETransaction() {}

    public static int apply(ToIntFunction<TransactionContext> change) {
        try (Transaction transaction = Transaction.openRoot()) {
            int result = change.applyAsInt(transaction);
            transaction.commit();
            return result;
        }
    }

    public static int preview(ToIntFunction<TransactionContext> change) {
        try (Transaction transaction = Transaction.openRoot()) {
            return change.applyAsInt(transaction);
        }
    }
}
