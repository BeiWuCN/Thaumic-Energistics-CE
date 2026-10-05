package thaumicenergistics_ce.blockentity.inscriber;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * The Knowledge Inscriber: the slots the player fills and the two answers read off them - what the grid
 * resolves to, and what the button would do with that recipe.
 * <ul>
 *   <li>The slots live in {@link InscriberInventory}, the resolution in {@link InscriberResolution}.
 *   <li>What is left here is the face other packages call: the slots, the codes and the menu.
 * </ul>
 */
public class BlockEntityKnowledgeInscriber extends ThEBaseBlockEntity {

    public static final int CORE_SLOT = 0;
    public static final int MIRROR_SLOT_START = 1;
    public static final int MIRROR_SLOT_COUNT = 21;
    public static final int GRID_SLOT_START = MIRROR_SLOT_START + MIRROR_SLOT_COUNT;
    public static final int GRID_SLOT_COUNT = 9;
    public static final int SLOT_COUNT = GRID_SLOT_START + GRID_SLOT_COUNT;

    public static final int STATUS_READY = 0;
    public static final int STATUS_ACTIONABLE = 7;
    public static final int STATUS_ENCODED = 1;
    public static final int STATUS_NO_RECIPE = 2;
    public static final int STATUS_CORE_FULL = 3;
    public static final int STATUS_ALREADY_STORED = 4;
    public static final int STATUS_RESEARCH_LOCKED = 5;

    private final InscriberInventory inventory = new InscriberInventory(this);
    private final InscriberResolution resolution = new InscriberResolution(this, inventory);

    public BlockEntityKnowledgeInscriber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KNOWLEDGE_INSCRIBER.get(), pos, state);
    }

    /** Called from the inventory on every change that is not part of a held-back grid write. */
    void contentsChanged() {
        resolution.markDirty();
        resolution.refresh();
    }

    // ------------------------------------------------------------------
    // Slots
    // ------------------------------------------------------------------

    public SimpleContainer getInventory() {
        return inventory.inventory();
    }

    public boolean hasCore() {
        return inventory.hasCore();
    }

    public List<ItemStack> gridCells() {
        return inventory.cells();
    }

    public void setGridCell(int cell, ItemStack stack) {
        inventory.setCell(cell, stack);
    }

    /** One change, not nine: the grid goes in with the notifications held back. */
    public void setGrid(List<ItemStack> cells) {
        inventory.setAll(cells);
    }

    public void clearGrid() {
        inventory.clear();
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    public int status() {
        return resolution.status();
    }

    public void refreshResolution() {
        resolution.refresh();
    }

    public boolean canStore() {
        return resolution.canStore();
    }

    public @Nullable ThEArcanePattern currentPattern() {
        return resolution.pattern();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    public int save(@Nullable Player player) {
        return resolution.save(player);
    }

    public int deleteStored(@Nullable Player player) {
        return resolution.deleteStored(player);
    }

    public int lastResult() {
        return resolution.lastResult();
    }

    public boolean canStore(Player player) {
        return resolution.canStore(player);
    }

    // ------------------------------------------------------------------
    // Stored patterns
    // ------------------------------------------------------------------

    public List<ItemStack> storedOutputs() {
        return resolution.storedOutputs();
    }

    public void dropContents() {
        inventory.dropItems();
    }

    // ------------------------------------------------------------------
    // Menu, persistence, sync
    // ------------------------------------------------------------------

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return MachineMenus.knowledgeInscriber(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        inventory.saveItems(tag, registries);
        resolution.writeTo(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.loadItems(tag, registries);
        resolution.readFrom(tag);
    }

    // No custom update tag: the core is kept in step by the menu's own slot sync, and pushing a container
    // through a block update only gave the client a second, stale copy.

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.thaumicenergistics_ce.knowledge_inscriber");
    }
}
