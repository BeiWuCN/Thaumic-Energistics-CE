package com.beiwu.thaumicenergistics_ce.util;

import appeng.api.stacks.GenericStack;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/**
 * 一件物品里装着哪一味源质。
 * <p>照 AE2 处理容器物品（一桶水标记成水）的思路：看它<b>装了什么</b>，不看它是什么物品。
 * 装安瓿的是安瓿，将来要是别的东西也带 {@code ASPECTS}，这里一样认得出来。
 * <p>只认「只装一味」的：混装的容器没有唯一答案，标哪一味都是猜。
 */
public final class EssentiaContainers {

    private EssentiaContainers() {}

    public static @Nullable GenericStack contained(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        AspectList aspects = stack.get(TTDataComponents.ASPECTS.get());
        if (aspects == null || aspects.entries().size() != 1) {
            return null;
        }
        AspectInstance entry = aspects.entries().get(0);
        AEssentiaKey key = AEssentiaKey.of(entry.aspect());
        if (key == null || entry.amount() <= 0) {
            return null;
        }
        // 数量照实报：安瓿装多少就是多少，标记只取类型，用不上这个数。
        return new GenericStack(key, entry.amount());
    }
}
