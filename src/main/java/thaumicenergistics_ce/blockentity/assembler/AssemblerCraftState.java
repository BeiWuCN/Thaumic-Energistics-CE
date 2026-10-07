package thaumicenergistics_ce.blockentity.assembler;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;

/**
 * 组装机正在运行的合成：产出什么、还欠什么，以及为何在等待。
 * 从 {@link BlockEntityArcaneAssembler} 拆出，状态与其存档格式放在一起。
 */
final class AssemblerCraftState {

    /** 运行中的合成仍欠的 vis 和晶体；存下来，重载后还能完成。 */
    private static final String TAG_CRAFT_PRICE = "CraftPrice";
    private static final String TAG_CRAFT_CRYSTALS = "CraftCrystals";

    private boolean crafting;
    private int craftTicks;

    private @Nullable ThEArcanePattern currentPattern;

    /** 运行中的合成同意支付的 vis，存下来不推导。
     * 核心可在合成中途被移除，合成 CPU 会永远等一个已经推出去的任务。 */
    private int craftPrice;

    private List<ItemStack> craftCrystals = List.of();

    /** AE2 为被推送的合成提取的原料，只为永不完成时归还而留着。
     * 本机器用 vis 和晶体制作产物。 */
    private final List<ItemStack> heldInputs = new ArrayList<>();

    private int stalledTicks;

    private @Nullable Component lastWait;

    private @Nullable Component lastRefusal;

    boolean isCrafting() {
        return crafting;
    }

    void setCrafting(boolean crafting) {
        this.crafting = crafting;
    }

    int craftTicks() {
        return craftTicks;
    }

    void setCraftTicks(int craftTicks) {
        this.craftTicks = craftTicks;
    }

    void addCraftTicks(int ticks) {
        craftTicks += ticks;
    }

    @Nullable ThEArcanePattern currentPattern() {
        return currentPattern;
    }

    void setCurrentPattern(@Nullable ThEArcanePattern currentPattern) {
        this.currentPattern = currentPattern;
    }

    int craftPrice() {
        return craftPrice;
    }

    void setCraftPrice(int craftPrice) {
        this.craftPrice = craftPrice;
    }

    List<ItemStack> craftCrystals() {
        return craftCrystals;
    }

    void setCraftCrystals(List<ItemStack> craftCrystals) {
        this.craftCrystals = craftCrystals;
    }

    List<ItemStack> heldInputs() {
        return heldInputs;
    }

    int stalledTicks() {
        return stalledTicks;
    }

    void noteStall(Component reason) {
        stalledTicks++;
        lastWait = reason;
    }

    void clearStall() {
        stalledTicks = 0;
    }

    @Nullable Component lastWait() {
        return lastWait;
    }

    @Nullable Component lastRefusal() {
        return lastRefusal;
    }

    void setLastRefusal(@Nullable Component lastRefusal) {
        this.lastRefusal = lastRefusal;
    }

    void reset() {
        crafting = false;
        craftTicks = 0;
        currentPattern = null;
        craftPrice = 0;
        craftCrystals = List.of();
        lastWait = null;
        heldInputs.clear();
    }

    void begin(ThEArcanePattern pattern, int price, List<ItemStack> crystals) {
        crafting = true;
        lastRefusal = null;
        lastWait = null;
        craftTicks = 0;
        stalledTicks = 0;
        currentPattern = pattern;
        craftPrice = price;
        craftCrystals = crystals;
    }

    void readNbt(CompoundTag tag, HolderLookup.Provider registries) {
        crafting = tag.getBoolean("Crafting");
        craftTicks = tag.getInt("CraftTicks");
        craftPrice = tag.getInt(TAG_CRAFT_PRICE);
        craftCrystals = readCrystalStacks(tag, registries);
    }

    void writeNbt(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Crafting", crafting);
        tag.putInt("CraftTicks", craftTicks);
        // 随合成保存；重载后完成它只需要这个标签和产物槽。
        tag.putInt(TAG_CRAFT_PRICE, craftPrice);
        ListTag crystals = new ListTag();
        for (ItemStack stack : craftCrystals) {
            crystals.add(stack.save(registries));
        }
        tag.put(TAG_CRAFT_CRYSTALS, crystals);
    }

    private static List<ItemStack> readCrystalStacks(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag saved = tag.getList(TAG_CRAFT_CRYSTALS, Tag.TAG_COMPOUND);
        if (saved.isEmpty()) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, saved.getCompound(i));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    void readSync(CompoundTag tag) {
        crafting = tag.getBoolean("Crafting");
        craftTicks = tag.getInt("CraftTicks");
    }

    void writeSync(CompoundTag tag) {
        tag.putBoolean("Crafting", crafting);
        tag.putInt("CraftTicks", craftTicks);
    }
}
