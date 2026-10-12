package thaumicenergistics_ce.blockentity.assembler;

import thaumicenergistics_ce.config.ThEConfig;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import net.minecraft.world.item.ItemStack;

public final class AssemblerUpgrades {

    private static final int TICKS_PER_SPEED_UPGRADE = 4;
    private static final int MIN_TICKS_PER_CRAFT = 4;
    private static final int MAX_SPEED_UPGRADES = 4;

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

    void recountSpeedUpgrades() {
        int count = 0;
        for (int i = 0; i < BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT; i++) {
            if (!owner.inventory.getItem(BlockEntityArcaneAssembler.UPGRADE_SLOT_START + i).isEmpty()) {
                count++;
            }
        }
        speedUpgrades = Math.clamp(count, 0, MAX_SPEED_UPGRADES);
    }

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

    void setGearDiscount(int percent) {
        this.gearDiscount = percent;
    }

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
                ThEConfig.ASSEMBLER_TICKS.get() - TICKS_PER_SPEED_UPGRADE * speedUpgrades);
    }

    void readNbt(ValueInput input) {
        speedUpgrades = Math.clamp(input.getIntOr(TAG_SPEED_UPGRADES, 0), 0, MAX_SPEED_UPGRADES);
    }

    void writeNbt(ValueOutput output) {
        output.putInt(TAG_SPEED_UPGRADES, speedUpgrades);
    }
}
