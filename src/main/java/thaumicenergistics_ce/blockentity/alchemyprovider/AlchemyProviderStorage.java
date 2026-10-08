package thaumicenergistics_ce.blockentity.alchemyprovider;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.util.ThETransaction;

/**
 * 把供应器当 ME 物品栏看：网格交出来的东西，供外界取走。源质放在缓冲里，
 * 故这里的一切询问都转问缓冲；抽取被拒，缓冲里的源质正在往外走，不会再回来。
 */
final class AlchemyProviderStorage implements MEStorage {

    private final BlockEntityAlchemyProvider provider;

    AlchemyProviderStorage(BlockEntityAlchemyProvider provider) {
        this.provider = provider;
    }

    @Override
    public long insert(
            AEKey what,
            long amount,
            Actionable mode,
            IActionSource source) {
        if (!(what instanceof AEssentiaKey key) || amount <= 0) {
            return 0;
        }
        Holder<IAspect> aspect = key.resolveAspect();
        if (aspect == null) {
            return 0;
        }
        int wanted = clamp(amount);
        // AE2 用 Actionable 发问，Thaumaturge 用事务作答；空跑就是开了事务却从不提交，
        // 所以改成直接问缓冲。
        return mode.isSimulate()
                ? provider.previewInsert(aspect, wanted)
                : ThETransaction.apply(transaction -> provider.insert(aspect, wanted, transaction));
    }

    @Override
    public long extract(
            AEKey what,
            long amount,
            Actionable mode,
            IActionSource source) {
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var contents = provider.contents();
        for (var entry : contents.entries()) {
            Identifier id = entry.aspect().unwrapKey().map(k -> k.identifier()).orElse(null);
            if (id != null && entry.amount() > 0) {
                out.add(AEssentiaKey.of(id), entry.amount());
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable(
                "block.thaumicenergistics_ce.alchemy_provider");
    }

    /** 缓冲以 int 计量；单次 AE 插入本来也超不过一个要素槽允许的量。 */
    private static int clamp(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }
}
