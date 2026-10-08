package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * 通往 Thaumaturge 源质晶体的桥梁：奥术配方给出的是要素，不是物品，
 * 编码器和组装机都在这里把要素映射成晶体物品堆。
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
     * 创建一叠 {@code count} 个为 {@code aspect} 配好的晶体，写入与 Thaumaturge 工厂相同的数据组件，
     * 结果可与工作台产出的晶体互换。
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
