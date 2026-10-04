package thaumicenergistics_ce.inventory;

import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import com.leclowndu93150.thaumaturge.api.items.IWarpingGear;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

/**
 * What may go in the Arcane Assembler's four gear slots.
 * <ul>
 *   <li>Shared by the machine's container and the menu's slots: two copies of the rule is how the menu
 *       came to accept anything while the container refused: only {@code mayPlace} sees the click.
 *   <li>Ordering mirrors slots 0..3 as head, chest, legs, feet.
 * </ul>
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
