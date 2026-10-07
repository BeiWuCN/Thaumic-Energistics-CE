package thaumicenergistics_ce.inventory;

import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import com.leclowndu93150.thaumaturge.api.items.IWarpingGear;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

/**
 * 奥术组装机四个装备槽能放什么。机器容器和菜单槽共用这条规则：
 * 两份规则正是菜单什么都收而容器拒绝的成因，只有 [mayPlace] 看得见点击。
 * 顺序对应槽 0..3，头、胸、腿、脚。
 */
public final class GearSlots {

    public static final int COUNT = 4;

    private GearSlots() {}

    public static EquipmentSlot equipmentSlot(int index) {
        return switch (index) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
    }

    public static boolean isGear(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof IVisDiscountGear || stack.getItem() instanceof IWarpingGear);
    }

    public static boolean accepts(int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof IVisDiscountGear) && !(stack.getItem() instanceof IWarpingGear)) {
            return false;
        }
        if (!(stack.getItem() instanceof Equipable equipable)) {
            return false;
        }
        return equipable.getEquipmentSlot() == equipmentSlot(index);
    }
}
