package thaumicenergistics_ce.blockentity.inscriber;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
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
import thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.registry.MachineMenus;
import thaumicenergistics_ce.registry.ModBlockEntities;

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

    void contentsChanged() {
        resolution.markDirty();
        resolution.refresh();
    }


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

    public void setGrid(List<ItemStack> cells) {
        inventory.setAll(cells);
    }

    public void clearGrid() {
        inventory.clear();
    }


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


    public List<ItemStack> storedOutputs() {
        return resolution.storedOutputs();
    }

    public void dropContents() {
        inventory.dropItems();
    }


    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return MachineMenus.knowledgeInscriber(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.saveItems(output);
        resolution.writeTo(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.loadItems(input);
        resolution.readFrom(input);
    }


    @Override
    public Component getDisplayName() {
        return Component.translatable("block.thaumicenergistics_ce.knowledge_inscriber");
    }
    @Override
    public void preRemoveSideEffects(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        dropContents();
        super.preRemoveSideEffects(pos, state);
    }
}
