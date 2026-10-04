package thaumicenergistics_ce.blockentity.assembler;

import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The Arcane Assembler's speed upgrades and the vis discount its worn gear grants.
 * <ul>
 * <li>Both are pure bookkeeping over the machine's own inventory: neither needs the grid, the craft nor
 * the display, so they live here and the block entity asks. The card count is not a number of its own:
 * it is counted off the machine's upgrade slots, so it cannot disagree with what those slots hold.
 * <li>Split out of {@link BlockEntityArcaneAssembler}. Public because the menu and the Jade provider
 * read the machine through it; everything else here is package-private.
 * </ul>
 */
public final class AssemblerUpgrades {

    private static final int BASE_TICKS_PER_CRAFT = 20;
    private static final int TICKS_PER_SPEED_UPGRADE = 4;
    private static final int MIN_TICKS_PER_CRAFT = 4;
    private static final int MAX_SPEED_UPGRADES = 4;

    /** The saved key. Never renamed: an old world's value would be dropped on load. Read back, then
     * superseded by {@link #recountSpeedUpgrades()}: the cards in the slots are what count. */
    private static final String TAG_SPEED_UPGRADES = "SpeedUpgrades";

    private final BlockEntityArcaneAssembler owner;

    private int speedUpgrades;
    private int gearDiscount;

    AssemblerUpgrades(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    public int getSpeedUpgrades() {
        return speedUpgrades;
    }

    public int getGearDiscount() {
        return gearDiscount;
    }

    /** Counts the cards in the machine's own upgrade slots. The inventory is the truth: the menu writes
     * the cards into those slots, so a number kept beside them could only drift away from them. */
    void recountSpeedUpgrades() {
        int count = 0;
        for (int i = 0; i < BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT; i++) {
            if (!owner.inventory.getItem(BlockEntityArcaneAssembler.UPGRADE_SLOT_START + i).isEmpty()) {
                count++;
            }
        }
        speedUpgrades = Math.clamp(count, 0, MAX_SPEED_UPGRADES);
    }

    /** Recounts after the inventory moved, and pushes the display only when the number actually did: an
     * inserted card has to reach the tooltip, something else moving in the machine must not. */
    void refreshSpeedUpgrades() {
        int before = speedUpgrades;
        recountSpeedUpgrades();
        if (speedUpgrades != before) {
            owner.setChanged();
            owner.displaySync.markDisplayForUpdate();
        }
    }

    int gearDiscount() {
        return gearDiscount;
    }

    /** Carries the server's discount to the client copy, which has no gear of its own to add up. */
    void setGearDiscount(int percent) {
        this.gearDiscount = percent;
    }

    /** The four gear slots' discounts, added up: a percentage off the charged vis. */
    void recalculateGearDiscount() {
        int percent = 0;
        for (int i = 0; i < BlockEntityArcaneAssembler.GEAR_SLOT_COUNT; i++) {
            ItemStack stack = owner.inventory.getItem(BlockEntityArcaneAssembler.GEAR_SLOT_START + i);
            if (!stack.isEmpty() && stack.getItem() instanceof IVisDiscountGear gear) {
                percent += gear.getVisDiscount(stack);
            }
        }
        gearDiscount = Math.max(0, percent);
    }

    int ticksPerCraft() {
        return Math.max(MIN_TICKS_PER_CRAFT,
                BASE_TICKS_PER_CRAFT - TICKS_PER_SPEED_UPGRADE * speedUpgrades);
    }

    void readNbt(CompoundTag tag) {
        speedUpgrades = Math.clamp(tag.getInt(TAG_SPEED_UPGRADES), 0, MAX_SPEED_UPGRADES);
    }

    void writeNbt(CompoundTag tag) {
        tag.putInt(TAG_SPEED_UPGRADES, speedUpgrades);
    }
}
