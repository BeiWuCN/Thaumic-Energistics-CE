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
 * The craft an assembler is running: what it makes, what it still owes, and why it is waiting.
 * Split out of {@link BlockEntityArcaneAssembler} so the state and its save format sit together.
 */
final class AssemblerCraftState {

    /** Vis and crystals a running craft still owes, saved so it can finish after a reload. */
    private static final String TAG_CRAFT_PRICE = "CraftPrice";
    private static final String TAG_CRAFT_CRYSTALS = "CraftCrystals";

    private boolean crafting;
    private int craftTicks;

    private @Nullable ThEArcanePattern currentPattern;

    /** Vis the running craft agreed to pay, saved rather than derived: the core can be removed mid-craft
     * and the CPU waits for ever on a job it already pushed. */
    private int craftPrice;

    private List<ItemStack> craftCrystals = List.of();

    /** Ingredients AE2 extracted for the pushed craft, kept only to hand back if it never finishes:
     * this machine makes its product from vis and crystals. */
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
        // Saved with the craft, so finishing it after a reload needs nothing but this tag and the well.
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
