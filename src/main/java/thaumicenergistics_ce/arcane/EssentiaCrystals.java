package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * 通往 Thaumaturge 源质晶体的桥梁：奥术配方给出的是要素而不是物品，
 * 因此编码器与组装机都在这里把要素映射成晶体物品堆。
 */
public final class EssentiaCrystals {
    private EssentiaCrystals() {}

    public static boolean isCrystal(ItemStack stack) {
        return TcRegistry.isCrystal(stack);
    }

    public static @Nullable Holder<IAspect> aspectOf(ItemStack stack) {
        if (!isCrystal(stack)) {
            return null;
        }
        return TcRegistry.crystalAspect(stack);
    }

    /**
     * 创建一叠为 {@code aspect} 配置好的 {@code count} 个晶体，写入与 Thaumaturge
     * 工厂相同的数据组件，因此结果可与工作台产出的晶体互换。
     */
    public static ItemStack create(Holder<IAspect> aspect, int count) {
        return TcRegistry.crystalStack(aspect, count);
    }

    public static int countOf(Iterable<ItemStack> stacks, Holder<IAspect> aspect) {
        int total = 0;
        for (ItemStack stack : stacks) {
            Holder<IAspect> carried = aspectOf(stack);
            if (carried != null && carried.equals(aspect)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
