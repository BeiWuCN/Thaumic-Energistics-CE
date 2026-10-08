package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.leclowndu93150.thaumaturge.api.items.IVisDiscountGear;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 奥术组装机的速度升级，以及穿戴装备给的 vis 折扣。
 * 两者都只是对机器自身物品栏记账，不需要网格。
 * 卡数不是它自己的数字，是从升级槽里数出来的。
 * 从 {@link BlockEntityArcaneAssembler} 拆出，菜单和 Jade 供应器都要读它。
 */
public final class AssemblerUpgrades {

    private static final int BASE_TICKS_PER_CRAFT = 20;
    private static final int TICKS_PER_SPEED_UPGRADE = 4;
    private static final int MIN_TICKS_PER_CRAFT = 4;
    private static final int MAX_SPEED_UPGRADES = 4;

    /** 存档键，永不改名：旧世界的数值会在加载时被丢掉。
     * 先读回，再由 {@link #recountSpeedUpgrades()} 取代，只有槽位里的卡算数。 */
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

    /** 数机器自己升级槽里的卡；物品栏才是真相。
     * 菜单把卡写进那些槽位，另存的数字只会和它们漂移。 */
    void recountSpeedUpgrades() {
        int count = 0;
        for (int i = 0; i < BlockEntityArcaneAssembler.UPGRADE_SLOT_COUNT; i++) {
            if (!owner.inventory.getItem(BlockEntityArcaneAssembler.UPGRADE_SLOT_START + i).isEmpty()) {
                count++;
            }
        }
        speedUpgrades = Math.clamp(count, 0, MAX_SPEED_UPGRADES);
    }

    /** 物品栏变动后重新计数，数字确实变了才推送显示。
     * 插入的卡要出现在 tooltip 上，机器里别的东西移动则不用。 */
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

    /** 把服务端的折扣带到客户端副本；那里没有装备可汇总。 */
    void setGearDiscount(int percent) {
        this.gearDiscount = percent;
    }

    /** 四个装备槽的折扣汇总：从收取的 vis 里减掉的百分比。 */
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

    void readNbt(ValueInput input) {
        speedUpgrades = Math.clamp(input.getIntOr(TAG_SPEED_UPGRADES, 0), 0, MAX_SPEED_UPGRADES);
    }

    void writeNbt(ValueOutput output) {
        output.putInt(TAG_SPEED_UPGRADES, speedUpgrades);
    }
}
