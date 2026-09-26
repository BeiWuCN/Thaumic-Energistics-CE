package thaumicenergistics.inventory;

import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import com.leclowndu93150.thaumaturge.api.items.IWarpingGear;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

/**
 * What may go in the Arcane Assembler's four gear slots.
 *
 * <p>Shared by the machine's own container and by the menu's slots. Two copies of this rule is how the
 * menu's slots came to accept anything while the container still refused: a slot's {@code mayPlace} is
 * what the player's click goes through, and without it the container's {@code canPlaceItem} is never
 * consulted.
 *
 * <p>Ordering mirrors slot 0..3 as head, chest, legs, feet.
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
     *
     * <p>Deliberately looser than {@link #accepts}: used for shift-click routing, where the caller does
     * not yet know which of the four slots is free. Something that passes this but no single slot is
     * simply not gear, and is handled by the ordinary inventory shuffle.
     */
    public static boolean isGear(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof IVisDiscountGear || stack.getItem() instanceof IWarpingGear);
    }

    /**
     * Whether {@code stack} may go in the gear slot {@code index}.
     *
     * <p>One rule, not two: the item must be vis-discount or warping gear <em>and</em> it must declare the
     * equipment slot this gear slot stands for. An earlier version waved discount gear through without
     * checking the slot, on the reasoning that it is gear "of its own accord" - which let a helmet sit in
     * the boots slot, since a helmet is discount gear too.
     *
     * <p>An item that declares no equipment slot at all is refused rather than accepted everywhere. Every
     * piece of Thaumaturge's discount gear extends {@code ArmorItem} and so declares one; something that
     * does not is, by definition, not wearable, and "accept anywhere" is how the slot check went missing in
     * the first place.
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
