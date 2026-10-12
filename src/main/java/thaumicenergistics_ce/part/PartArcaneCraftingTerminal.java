package thaumicenergistics_ce.part;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
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
import thaumicenergistics_ce.recipe.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.recipe.arcane.ArcaneTerminalLink;
import thaumicenergistics_ce.recipe.arcane.EssentiaCrystals;
import thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;
import thaumicenergistics_ce.registry.TECEMenuTypes;

public class PartArcaneCraftingTerminal extends AbstractTerminalPart
        implements ArcaneTerminalHost, appeng.api.networking.ticking.IGridTickable {

    public static final Identifier INV_CRAFTING = AppEng.makeId("arcane_crafting_terminal_crafting");

    public static final Identifier INV_WAND = ThEIds.id("arcane_crafting_terminal_wand");

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

    public static final UUID CONTEXT_HOST =
            UUID.nameUUIDFromBytes("thaumicenergistics_ce:arcane_crafting_terminal".getBytes());

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandInv = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalInv = new AppEngInternalInventory(this, CRYSTAL_SLOTS);

    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(getPartItem(), 2, this::onUpgradesChanged);

    public PartArcaneCraftingTerminal(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.5)
                .addService(appeng.api.networking.ticking.IGridTickable.class, this);
        wandInv.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return isWand(stack);
            }
        });
        crystalInv.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return EssentiaCrystals.isCrystal(stack);
            }
        });
    }

    public static boolean isWand(ItemStack stack) {
        return TcWand.isWand(stack);
    }


    @Override
    public MenuType<?> getMenuType(Player player) {
        return TECEMenuTypes.ARCANE_CRAFTING_TERMINAL.get();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide()) {
            MenuOpener.open(getMenuType(player), player, MenuLocators.forPart(this));
        }
        return true;
    }

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

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private void onUpgradesChanged() {
        if (getHost() != null) {
            saveChanges();
        }
    }


    public int supplyAura(int needVis, TransactionContext transaction) {
        if (needVis <= 0 || !isActive()) {
            return 0;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return 0;
        }
        return TerminalAuraPayment.payChunkAura(
                level, getBlockEntity().getBlockPos(), needVis, transaction);
    }

    @Override
    public appeng.api.networking.ticking.TickingRequest getTickingRequest(IGridNode node) {
        return new appeng.api.networking.ticking.TickingRequest(20, 20, false);
    }

    @Override
    public appeng.api.networking.ticking.TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (isActive() && TerminalAuraPayment.visConnectionInstalled(this)) {
            ItemStack wand = wandInv.getStackInSlot(WAND_SLOT);
            ItemStack charged = TerminalAuraPayment.chargeWand(getLevel(), TerminalAuraPayment.visCard(this), wand);
            if (charged != wand) {
                wandInv.setItemDirect(WAND_SLOT, charged);
                saveChanges();
            }
        }
        return appeng.api.networking.ticking.TickRateModulation.SAME;
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
