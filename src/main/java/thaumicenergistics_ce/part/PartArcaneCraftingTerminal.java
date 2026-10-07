package thaumicenergistics_ce.part;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.arcane.ArcaneTerminalLink;
import thaumicenergistics_ce.arcane.EssentiaCrystals;
import thaumicenergistics_ce.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.init.ModMenuTypes;

/**
 * 一台 ME 合成终端，其合成网格是奥术工作台的网格：网格、水晶槽位
 * 与法杖像在工作台上一样为合成付费，而网络只承担 vis，来自
 * 法杖槽位里的法杖或网格的电力。这些物品栏属于部件自己而非
 * 网络，因为合成网格装着玩家此刻正在摆放的东西，网络不得取走它。配方只有在其材料
 * 就位于网格中时才匹配，所以材料从不从网络拉取；填满网格才是物品列表与 JEI 转移的用途。
 * 从未从网络拉取；填满网格才是物品列表与 JEI 转移的用途。
 */
public class PartArcaneCraftingTerminal extends AbstractTerminalPart
        implements ArcaneTerminalHost {

    public static final ResourceLocation INV_CRAFTING = AppEng.makeId("arcane_crafting_terminal_crafting");

    public static final ResourceLocation INV_WAND = ThEIds.id("arcane_crafting_terminal_wand");

    /**
     * 放进网格的水晶无法支付配方的水晶开销：被数了两遍——既算材料
     * 又算支付——{@code ArcaneShapedRecipePattern.matches} 会拒绝每个需要水晶的配方。
     */
    public static final ResourceLocation INV_CRYSTALS = ThEIds.id("arcane_crafting_terminal_crystals");

    @PartModels
    public static final ResourceLocation MODEL_BASE = ThEIds.id("parts/arcane_crafting_terminal_base");

    @PartModels
    public static final ResourceLocation MODEL_OFF = ThEIds.id("parts/arcane_crafting_terminal_off");

    @PartModels
    public static final ResourceLocation MODEL_ON = ThEIds.id("parts/arcane_crafting_terminal_on");

    @PartModels
    public static final ResourceLocation MODEL_HAS_CHANNEL =
            ThEIds.id("parts/arcane_crafting_terminal_has_channel");

    public static final List<ResourceLocation> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL =
            new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public static final int GRID_SIZE = 9;

    public static final int WAND_SLOT = 0;

    public static final int CRYSTAL_SLOTS = 6;

    public static final int CRYSTAL_COLUMN = 3;

    /** 虚拟工作台的所有者与宿主，不含位置：固定不变，因此搬动终端不会破坏过去的合成。 */
    public static final UUID CONTEXT_HOST =
            UUID.nameUUIDFromBytes("thaumicenergistics_ce:arcane_crafting_terminal".getBytes());

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandInv = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalInv = new AppEngInternalInventory(this, CRYSTAL_SLOTS);

    /** 一张卡，即 vis 连接卡；它放入的槽位就是 AE2 挂在这个部件上的那个。 */
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(getPartItem(), 1, this::onUpgradesChanged);

    public PartArcaneCraftingTerminal(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.5);
        // 在门口就拒绝：物品短暂存在一下，就足以让菜单把它同步出去。
        wandInv.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return isWand(stack);
            }
        });
        // 同样的道理：槽位里放着杂物会让水晶需求被读成付不起。
        crystalInv.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return EssentiaCrystals.isCrystal(stack);
            }
        });
    }

    /** 某个物品堆是否为法杖。按物品类而非标签识别，依赖因此保持单向。 */
    public static boolean isWand(ItemStack stack) {
        return TcWand.isWand(stack);
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide) {
            MenuOpener.open(getMenuType(player), player, MenuLocators.forPart(this));
        }
        return true;
    }

    /** 当链接物品被潜行使用到这个部件上时写入配对；不潜行则什么都不
     * 写，因为手持该物品从终端旁走过不得把它重新绑定。 */
    @Override
    public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 pos) {
        if (!(held.getItem() instanceof ArcaneTerminalLink link) || !player.isSecondaryUseActive()) {
            return super.onUseItemOn(held, player, hand, pos);
        }
        BlockPos where = getBlockEntity().getBlockPos();
        if (!player.level().isClientSide) {
            link.pairWith(held, player.level(), where, getSide());
        }
        player.displayClientMessage(
                Component.translatable(
                        "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal.paired",
                        where.getX(), where.getY(), where.getZ()),
                true);
        return true;
    }

    @Override
    public PartArcaneCraftingTerminal arcaneTerminal() {
        return this;
    }

    public AppEngInternalInventory craftingGrid() {
        return craftingGrid;
    }

    public AppEngInternalInventory wandInventory() {
        return wandInv;
    }

    public AppEngInternalInventory crystalInventory() {
        return crystalInv;
    }

    /** 部件自己的升级槽位：AE2 的菜单构建器在这里读取它并画出玩家使用的槽位。 */
    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    /** 卡片装入或取出时 AE2 会调用它；部件把自己的内容物留在世界中。 */
    private void onUpgradesChanged() {
        // 不在世界中的部件没有东西可保存，而 AE2 在加载期间也会调用这里。
        if (getHost() != null) {
            saveChanges();
        }
    }

    private appeng.api.networking.@Nullable IGrid gridOrNull() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    /**
     * 为一次合成提供灵气中的 vis，先模拟后提交；没有它 Thaumaturge 会以
     * {@code PAYMENT_UNAVAILABLE} 拒绝，因为无类型的 {@code baseVis} 来自线缆所没有的缓冲。
     * @return 提供的 centivis，绝不超过 {@code needCentivis}
     */
    public int supplyAura(int needCentivis, boolean simulate) {
        if (needCentivis <= 0 || !isActive()) {
            return 0;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return 0;
        }
        if (TerminalAuraPayment.visConnectionInstalled(this)) {
            // 卡片直接从线缆周围的灵气支付，所以既不问网格也不问电力。
            return TerminalAuraPayment.payAura(
                    level, getBlockEntity().getBlockPos(), needCentivis, simulate);
        }
        IGrid grid = gridOrNull();
        if (grid == null) {
            return 0;
        }
        IEnergyService energy =
                grid.getService(IEnergyService.class);
        if (energy == null) {
            return 0;
        }
        // 线缆周围的灵气，用网络的电力支付：无线终端付的是
        // 同样的费率，但取自它玩家周围的灵气。见 [TerminalAuraPayment]。
        return TerminalAuraPayment.pay(level, getBlockEntity().getBlockPos(), energy, needCentivis, simulate);
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(INV_CRAFTING)) {
            return craftingGrid;
        }
        if (id.equals(INV_WAND)) {
            return wandInv;
        }
        if (id.equals(INV_CRYSTALS)) {
            return crystalInv;
        }
        return super.getSubInventory(id);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (ItemStack stack : craftingGrid) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        for (ItemStack stack : wandInv) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        for (ItemStack stack : crystalInv) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        for (ItemStack stack : upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        craftingGrid.clear();
        wandInv.clear();
        crystalInv.clear();
        upgrades.clear();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        craftingGrid.readFromNBT(data, "craftingGrid", registries);
        wandInv.readFromNBT(data, "wandInv", registries);
        // 旧世界没有这个键；键缺失则保持为空，所以这是一次安全的升级。
        crystalInv.readFromNBT(data, "crystalInv", registries);
        upgrades.readFromNBT(data, "upgrades", registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        craftingGrid.writeToNBT(data, "craftingGrid", registries);
        wandInv.writeToNBT(data, "wandInv", registries);
        crystalInv.writeToNBT(data, "crystalInv", registries);
        upgrades.writeToNBT(data, "upgrades", registries);
    }
}
