package thaumicenergistics_ce.blockentity.alchemyprovider;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * 把供应器当作 ME 物品栏来看：网格交付的东西，供外界取走。
 * 源质存放在缓冲里，所以这里的一切询问都转问缓冲，且拒绝抽取，
 * 因为缓冲中的源质正在往外走，绝不会再回到里面。
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
        return provider.insert(aspect, clamp(amount), mode.isSimulate());
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
            ResourceLocation id = entry.aspect().unwrapKey().map(k -> k.location()).orElse(null);
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

    /** 缓冲以 int 计量；单次 AE 插入本来也不会超过一个要素槽位所允许的量。 */
    private static int clamp(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }
}
