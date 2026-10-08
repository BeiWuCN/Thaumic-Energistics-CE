package thaumicenergistics_ce.part;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPartItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.AppEng;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.arcane.ArcaneTerminalLink;
import thaumicenergistics_ce.arcane.EssentiaCrystals;
import thaumicenergistics_ce.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.init.ModMenuTypes;

/**
 * ME 合成终端，合成网格是奥术工作台的那种：网格、水晶槽和法杖按工作台规则付费，
 * 网络只出 vis，来自法杖槽的法杖或网格的电力。物品栏归部件自己，
 * 不归网络，网格装的是玩家此刻摆的东西，网络不能取走。配方只在材料已就位时匹配，
 * 材料从不从网络拉取；填满网格是物品列表和 JEI 转移的活。
 */
public class PartArcaneCraftingTerminal extends AbstractTerminalPart
        implements ArcaneTerminalHost {

    public static final Identifier INV_CRAFTING = AppEng.makeId("arcane_crafting_terminal_crafting");

    public static final Identifier INV_WAND = ThEIds.id("arcane_crafting_terminal_wand");

    /**
     * 放进网格的水晶付不了配方的水晶开销：它既算材料又算支付，
     * {@code ArcaneShapedRecipePattern.matches} 会拒掉每个要水晶的配方。
     */
    public static final Identifier INV_CRYSTALS = ThEIds.id("arcane_crafting_terminal_crystals");

    public static final Identifier MODEL_BASE = ThEIds.id("parts/arcane_crafting_terminal_base");
    public static final Identifier MODEL_OFF = ThEIds.id("parts/arcane_crafting_terminal_off");
    public static final Identifier MODEL_ON = ThEIds.id("parts/arcane_crafting_terminal_on");
    public static final Identifier MODEL_HAS_CHANNEL =
            ThEIds.id("parts/arcane_crafting_terminal_has_channel");

    public static final List<Identifier> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    public static final int GRID_SIZE = 9;

    public static final int WAND_SLOT = 0;

    public static final int CRYSTAL_SLOTS = 6;

    public static final int CRYSTAL_COLUMN = 3;

    /** 虚拟工作台的所有者与宿主，不带坐标。固定不变：搬走终端不会影响过去的合成。 */
    public static final UUID CONTEXT_HOST =
            UUID.nameUUIDFromBytes("thaumicenergistics_ce:arcane_crafting_terminal".getBytes());

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandInv = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalInv = new AppEngInternalInventory(this, CRYSTAL_SLOTS);

    /** 只有一张卡，vis 连接卡；它插的是 [AE2] 挂在本部件上的那个升级槽。 */
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(getPartItem(), 1, this::onUpgradesChanged);

    public PartArcaneCraftingTerminal(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.5);
        // 门口就拒：物品存在一瞬，就够菜单把它同步出去。
        wandInv.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return isWand(stack);
            }
        });
        // 同理：槽里塞杂物，水晶需求会被判成付不起。
        crystalInv.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return EssentiaCrystals.isCrystal(stack);
            }
        });
    }

    /** 判断物品堆是不是法杖。按物品类认，不认标签，依赖保持单向。 */
    public static boolean isWand(ItemStack stack) {
        return TcWand.isWand(stack);
    }


    @Override
    public MenuType<?> getMenuType(Player player) {
        return ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide()) {
            MenuOpener.open(getMenuType(player), player, MenuLocators.forPart(this));
        }
        return true;
    }

    /** 链接物品潜行点到本部件上才写入配对。不潜行不写：手持它路过终端不能重绑。 */
    @Override
    public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 pos) {
        if (!(held.getItem() instanceof ArcaneTerminalLink link) || !player.isSecondaryUseActive()) {
            return super.onUseItemOn(held, player, hand, pos);
        }
        BlockPos where = getBlockEntity().getBlockPos();
        if (!player.level().isClientSide()) {
            link.pairWith(held, player.level(), where, getSide());
        }
        player.sendOverlayMessage(
                Component.translatable(
                        "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal.paired",
                        where.getX(), where.getY(), where.getZ()));
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

    /** 部件自己的升级槽；[AE2] 的菜单构建器在这里读它，画出玩家用的那个槽。 */
    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    /** 卡片进出时 [AE2] 会调这里；部件的内容物留在世界里。 */
    private void onUpgradesChanged() {
        // 不在世界里的部件没东西可存，[AE2] 在加载时也会调这里。
        if (getHost() != null) {
            saveChanges();
        }
    }

    private appeng.api.networking.@Nullable IGrid gridOrNull() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    /**
     * 为合成提供灵气里的 vis；少了它 Thaumaturge 回 {@code PAYMENT_UNAVAILABLE}，
     * 因为无类型的 {@code baseVis} 来自线缆没有的缓冲。灵气会加入交给它的事务，合成中止就把 vis 放回去；
     * 网络的电力不会，问它的那一刻就花掉了。
     * @return 提供的 centivis，从不超过 {@code needCentivis}
     */
    public int supplyAura(int needCentivis, TransactionContext transaction) {
        if (needCentivis <= 0 || !isActive()) {
            return 0;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return 0;
        }
        if (TerminalAuraPayment.visConnectionInstalled(this)) {
            // 卡片直接扣线缆周围的灵气，不问网格也不问电力。
            return TerminalAuraPayment.payAura(
                    level, getBlockEntity().getBlockPos(), needCentivis, transaction);
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
        // 线缆周围的灵气，费用由网络电力结：无线终端费率相同，只是灵气取自玩家身边那块。见 TerminalAuraPayment。
        return TerminalAuraPayment.pay(
                level, getBlockEntity().getBlockPos(), energy, needCentivis, transaction);
    }

    @Override
    public InternalInventory getSubInventory(Identifier id) {
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
    public void readFromNBT(ValueInput input) {
        super.readFromNBT(input);
        craftingGrid.readFromNBT(input, "craftingGrid");
        wandInv.readFromNBT(input, "wandInv");
        // 旧世界没有这个键；键缺失就留空，这个升级不会出事。
        crystalInv.readFromNBT(input, "crystalInv");
        upgrades.readFromNBT(input, "upgrades");
    }

    @Override
    public void writeToNBT(ValueOutput output) {
        super.writeToNBT(output);
        craftingGrid.writeToNBT(output, "craftingGrid");
        wandInv.writeToNBT(output, "wandInv");
        crystalInv.writeToNBT(output, "crystalInv");
        upgrades.writeToNBT(output, "upgrades");
    }
}
