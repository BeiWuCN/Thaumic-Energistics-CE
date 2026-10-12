package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;

final class AssemblerCraftState {

    private static final String TAG_CRAFT_PRICE = "CraftPrice";
    private static final String TAG_CRAFT_CRYSTALS = "CraftCrystals";

    private boolean crafting;
    private int craftTicks;

    private @Nullable ThEArcanePattern currentPattern;

    private int craftPrice;

    private List<ItemStack> craftCrystals = List.of();

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

    void readNbt(ValueInput input) {
        crafting = input.getBooleanOr("Crafting", false);
        craftTicks = input.getIntOr("CraftTicks", 0);
        craftPrice = input.getIntOr(TAG_CRAFT_PRICE, 0);
        craftCrystals = readCrystalStacks(input);
    }

    void writeNbt(ValueOutput output) {
        output.putBoolean("Crafting", crafting);
        output.putInt("CraftTicks", craftTicks);
        output.putInt(TAG_CRAFT_PRICE, craftPrice);
        output.store(TAG_CRAFT_CRYSTALS, ItemStack.CODEC.listOf(), craftCrystals);
    }

    private static List<ItemStack> readCrystalStacks(ValueInput input) {
        List<ItemStack> saved = input.read(TAG_CRAFT_CRYSTALS, ItemStack.OPTIONAL_CODEC.listOf())
                .orElse(List.of());
        if (saved.isEmpty()) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>(saved.size());
        for (ItemStack stack : saved) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return List.copyOf(stacks);
    }

    void readSync(ValueInput input) {
        crafting = input.getBooleanOr("Crafting", false);
        craftTicks = input.getIntOr("CraftTicks", 0);
    }

    void writeSync(ValueOutput output) {
        output.putBoolean("Crafting", crafting);
        output.putInt("CraftTicks", craftTicks);
    }
}
