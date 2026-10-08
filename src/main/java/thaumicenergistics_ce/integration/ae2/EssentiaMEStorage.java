package thaumicenergistics_ce.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import thaumicenergistics_ce.essentia.ThEImmediateStorage;
import thaumicenergistics_ce.util.ThETransaction;

/**
 * 把一个 Thaumaturge 源质容器当作存储呈现给 ME 网络。
 * <ul>
 * <li>存储总线挂一个它，罐子的内容随即列进终端、计入网络持有量，
 * 也能像别的存储一样装和抽。
 * <li>数量一侧是 {@code int}、另一侧是 {@code long}，每次换算都夹到范围内。
 * </ul>
 */
public final class EssentiaMEStorage implements MEStorage {

    private final IEssentiaStorage storage;

    public EssentiaMEStorage(IEssentiaStorage storage) {
        this.storage = storage;
    }

    public IEssentiaStorage container() {
        return storage;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (AspectInstance entry : storage.contents().entries()) {
            Holder<IAspect> aspect = entry.aspect();
            if (aspect != null && entry.amount() > 0) {
                AEssentiaKey key = AEssentiaKey.of(aspect);
                if (key != null) {
                    out.add(key, entry.amount());
                }
            }
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEssentiaKey key)) {
            return 0;
        }
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            return 0;
        }
        int wanted = (int) Math.min(amount, Integer.MAX_VALUE);
        return put(aspect, wanted, mode == Actionable.SIMULATE);
    }

    /**
     * AE2 用 {@link Actionable} 提问，Thaumaturge 用事务作答，所以一次模拟就是开一个事务、
     * 永不提交。撤不回事务的存储改直接问，否则这次模拟正好会做出
     * 它本该避免的那个改动。
     */
    private int put(Holder<IAspect> aspect, int wanted, boolean dryRun) {
        if (dryRun && storage instanceof ThEImmediateStorage immediate) {
            return immediate.previewInsert(aspect, wanted);
        }
        return dryRun
                ? ThETransaction.preview(transaction -> storage.insert(aspect, wanted, transaction))
                : ThETransaction.apply(transaction -> storage.insert(aspect, wanted, transaction));
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEssentiaKey key)) {
            return 0;
        }
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            return 0;
        }
        int wanted = (int) Math.min(amount, Integer.MAX_VALUE);
        return take(aspect, wanted, mode == Actionable.SIMULATE);
    }

    /** {@link #put} 的另一半，同样的理由、同样的分岔。 */
    private int take(Holder<IAspect> aspect, int wanted, boolean dryRun) {
        if (dryRun && storage instanceof ThEImmediateStorage immediate) {
            return immediate.previewExtract(aspect, wanted);
        }
        return dryRun
                ? ThETransaction.preview(transaction -> storage.extract(aspect, wanted, transaction))
                : ThETransaction.apply(transaction -> storage.extract(aspect, wanted, transaction));
    }

    public long contentRevision() {
        return storage.contentRevision();
    }

    /**
     * 供 AE2 的诊断输出描述这个存储是什么：一句朴素说明，而不是容器自己的名字，
     * 那要问它的方块实体，而本适配器刻意不持有它。
     */
    @Override
    public Component getDescription() {
        return Component.translatable("gui.thaumicenergistics_ce.essentia_storage");
    }
}
