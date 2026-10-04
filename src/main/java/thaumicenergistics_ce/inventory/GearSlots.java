package thaumicenergistics_ce.inventory;

import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import com.leclowndu93150.thaumaturge.api.items.IWarpingGear;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

/**
 * What may go in the Arcane Assembler's four gear slots.
 * <ul>
 *   <li>Shared by the machine's container and by the menu's slots. Two copies of the rule is how the
 *       menu's slots came to accept anything while the container still refused, since only
 *       {@code mayPlace} sees the player's click.
 *   <li>Ordering mirrors slots 0..3 as head, chest, legs, feet.
 * </ul>
 */
public final class GearSlots {

    /** Number of gear slots. */
    public static final int COUNT = 4;

    private GearSlots() {}

    /** The equipment slot a gear slot stands for. */
    public static EquipmentSlot equipmentSlot(int index) {
        return switch (index) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
    }

    /**
     * Whether {@code stack} belongs in a gear slot at all.
     * <p>Deliberately looser than {@link #accepts}: the caller of shift-click routing does not yet know
     * which of the four slots is free, so anything that passes this but no single slot is not gear.
     */
    public static boolean isGear(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof IVisDiscountGear || stack.getItem() instanceof IWarpingGear);
    }

    /**
     * Whether {@code stack} may go in the gear slot {@code index}.
     * <p>One rule, not two: the item must be vis-discount or warping gear, and it must declare the
     * equipment slot this gear slot stands for. Waving discount gear through without the slot check put a
     * helmet in the boots slot.
     * <p>An item declaring no equipment slot is refused, not accepted everywhere: "accept anywhere" is
     * how the slot check went missing.
     */
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
