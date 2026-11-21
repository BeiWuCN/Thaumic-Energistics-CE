package com.beiwu.thaumicenergistics_ce.part;

import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.menu.ArcaneEncodingStorage;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import com.beiwu.thaumicenergistics_ce.registry.TECEMenuTypes;

/**
 * ME 奥术编码终端：AE2 样板编码终端的外壳，里面装的是知识编码器。
 * 没有编码模式，只有奥术合成一种；编码结果直接写进知识核心，不产出样板物品。
 * 网格归部件自己，与合成终端同理：玩家摆什么网络不能替它决定。
 */
public class PartArcaneEncodingTerminal extends AbstractTerminalPart implements ArcaneEncodingStorage {

    public static final ResourceLocation INV_GRID = ThEIds.id("arcane_encoding_terminal_grid");

    public static final ResourceLocation INV_CORE = ThEIds.id("arcane_encoding_terminal_core");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/arcane_encoding_terminal_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/arcane_encoding_terminal_on");

    /** 连上网络时那套 1.5 倍亮度的正面覆盖层。 */
    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL =
            ThEIds.id("parts/arcane_encoding_terminal_has_channel");

    public static final List<ResourceLocation> MODEL_LOCATIONS =
            List.of(MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    /**
     * 正面那三层只是发光层，壳体另外由 {@code ae2:part/display_base} 提供：
     * 它带 monitor_sides / monitor_back / monitor_front 六个面，末端显示器、终端、
     * 样板编码终端全都是这么拼的。少了这一层就只剩一张悬空的贴图，
     * 侧面和背面直接透到线缆上。
     */
    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL =
            new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL, MODEL_STATUS_HAS_CHANNEL);

    public static final int GRID_SIZE = 9;

    public static final int CORE_SLOT = 0;

    private final AppEngInternalInventory grid = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory core = new AppEngInternalInventory(this, 1);

    /**
     * 这个终端只开一个升级槽，给源质访问卡：插上才能看见网络源质、
     * 右键安瓿才存得进去。以前一个槽都不开，编码终端看上去像个「没有升级」的黑盒。
     */
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(getPartItem(), UPGRADE_SLOTS, this::onUpgradesChanged);

    /** 和合成终端一样的单槽；写常量是为了让登记那一边有同一个数字可用。 */
    public static final int UPGRADE_SLOTS = 1;

    /** 被选中的知识核心样板下标；-1 表示没有选中。 */
    private int selectedPattern = -1;

    public PartArcaneEncodingTerminal(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.5);
        core.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return stack.is(TECEItems.KNOWLEDGE_CORE.get());
            }
        });
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return TECEMenuTypes.ARCANE_ENCODING_TERMINAL.get();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide) {
            MenuOpener.open(getMenuType(player), player, MenuLocators.forPart(this));
        }
        return true;
    }

    public AppEngInternalInventory grid() {
        return grid;
    }

    public AppEngInternalInventory coreInventory() {
        return core;
    }

    public ItemStack coreStack() {
        return core.getStackInSlot(CORE_SLOT);
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    /** 卡片进出时 [AE2] 会调这里；部件的内容物留在世界里。 */
    private void onUpgradesChanged() {
        if (getHost() != null) {
            saveChanges();
        }
    }

    @Override
    public int selectedEncodingPattern() {
        return selectedPattern;
    }

    @Override
    public void setSelectedEncodingPattern(int index) {
        this.selectedPattern = index;
        saveChanges();
    }

    @Override
    public InternalInventory encodingGrid() {
        return grid;
    }

    @Override
    public InternalInventory encodingCore() {
        return core;
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(INV_GRID)) {
            return grid;
        }
        if (id.equals(INV_CORE)) {
            return core;
        }
        return super.getSubInventory(id);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (ItemStack stack : grid) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        for (ItemStack stack : core) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        // 升级槽里的卡也得掉：部件被捧下来时，里面的访问卡无处可存。
        for (ItemStack stack : upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        grid.clear();
        core.clear();
        upgrades.clear();
        selectedPattern = -1;
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        grid.readFromNBT(data, "grid", registries);
        core.readFromNBT(data, "core", registries);
        upgrades.readFromNBT(data, "upgrades", registries);
        selectedPattern = data.getInt("selectedPattern");
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        grid.writeToNBT(data, "grid", registries);
        core.writeToNBT(data, "core", registries);
        upgrades.writeToNBT(data, "upgrades", registries);
        data.putInt("selectedPattern", selectedPattern);
    }
}
