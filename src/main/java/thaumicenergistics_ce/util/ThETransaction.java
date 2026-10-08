package thaumicenergistics_ce.util;

import java.util.function.ToIntFunction;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 本 mod 对存储做改动的两种走法：Thaumaturge 与 AE2 现在发的是 {@link TransactionContext}，
 * 以前发的是 {@code boolean simulate}。
 *
 * <p>那个 boolean 带着两重含义，而它们不再是同一个调用：
 * <ul>
 * <li>{@code simulate = false} —— 改动就是要发生。{@link #apply} 执行它；有打开的事务就并入，
 *     没有就自己开一个根并提交。
 * <li>{@code simulate = true} —— 什么都不许变。{@link #preview} 在永不提交的事务里跑同一个调用，
 *     每个存储的日志各自把状态放回去。
 * </ul>
 *
 * <p>不认识事务的存储没法这样预览，因为叫它撤销的东西撤不回来；
 * 这种存储靠实现 {@link ThEImmediateStorage} 声明自己。
 */
public final class ThETransaction {

    private ThETransaction() {}

    /**
     * 执行改动并留下它。有打开的事务时改动并入其中，仍可能被开它的人中止；
     * 没有打开的事务时在这里开一个根，也在这里提交。
     *
     * @param change 要做的调用，参数是它所属的事务
     * @return 改动返回什么就是什么
     */
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

    /**
     * 问改动会做什么，什么都不留下。有打开的事务时开一个子事务再丢弃，
     * 所以答案仍归外层事务定。
     *
     * @param change 要做的调用，参数是它所属的事务
     * @return 改动本来会返回什么
     */
    public static int preview(ToIntFunction<TransactionContext> change) {
        TransactionContext open = Transaction.getCurrentOpenedTransaction();
        try (Transaction transaction = open == null ? Transaction.openRoot() : Transaction.open(open)) {
            return change.applyAsInt(transaction);
        }
    }
}
