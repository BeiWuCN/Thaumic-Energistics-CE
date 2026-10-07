package thaumicenergistics_ce.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * 用于物品堆与网格的廉价变更检测键。每个键都替换掉了来自
 * {@code getComponentsPatch()} 的字符串——后者把每个组件序列化成 SNBT，而且跑在
 * 一帧一次的路径上，每秒重新序列化六十次。int 哈希可能碰撞，但一次
 * 碰撞只损失一次被跳过的重算，而不会给出错误答案。
 */
public final class StackSignatures {

    private StackSignatures() {}

    public static int of(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return 31 * (31 * BuiltInRegistries.ITEM.getId(stack.getItem()) + stack.getCount())
                + stack.getComponentsPatch().hashCode();
    }

    /**
     * 在一串物品堆上滚动计算的键。顺序有意义，所以这不是求和：两个装着相同物品却在不同
     * 单元的网格代表不同配方，不得哈希相等。
     */
    public static int of(Iterable<ItemStack> stacks) {
        int hash = 1;
        for (ItemStack stack : stacks) {
            hash = 31 * hash + of(stack);
        }
        return hash;
    }
}
