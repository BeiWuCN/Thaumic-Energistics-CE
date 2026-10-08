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
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * 知识铭刻机：玩家填的槽位，以及从槽位读出的两个答案：
 * 网格解析成什么，按钮拿那份配方做什么。
 * 槽位在 {@link InscriberInventory}，解析在 {@link InscriberResolution}；
 * 留在这里的是别的包调用的门面：槽位、状态码和菜单。
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

    /** 物品栏每次改动都调它，被压住的网格写入不调。 */
    void contentsChanged() {
        resolution.markDirty();
        resolution.refresh();
    }

    // ------------------------------------------------------------------
    // 槽位
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

    /** 一次改动，不是九次：写网格期间压住通知。 */
    public void setGrid(List<ItemStack> cells) {
        inventory.setAll(cells);
    }

    public void clearGrid() {
        inventory.clear();
    }

    // ------------------------------------------------------------------
    // 状态
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
    // 操作
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
    // 已存储的样板
    // ------------------------------------------------------------------

    public List<ItemStack> storedOutputs() {
        return resolution.storedOutputs();
    }

    public void dropContents() {
        inventory.dropItems();
    }

    // ------------------------------------------------------------------
    // 菜单、持久化、同步
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // 菜单、持久化、同步
    // ------------------------------------------------------------------

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
