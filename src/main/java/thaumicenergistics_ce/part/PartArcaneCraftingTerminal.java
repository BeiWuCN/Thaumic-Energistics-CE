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
 * An ME crafting terminal whose crafting grid is an arcane workbench's: the grid, crystal slots
 * and the wand pay for the craft as on a workbench, while the network covers only the vis, from
 * the wand in the wand slot or from the grid's power. The inventories are the part's own, not the
 * network's, since a crafting grid holds what the player is arranging right now and the network
 * must not take it. A recipe matches only once its ingredients sit in the grid, so ingredients are
 * never pulled from the network; filling the grid is what the item list and JEI transfer are for.
 */
public class PartArcaneCraftingTerminal extends AbstractTerminalPart
        implements ArcaneTerminalHost {

    public static final ResourceLocation INV_CRAFTING = AppEng.makeId("arcane_crafting_terminal_crafting");

    public static final ResourceLocation INV_WAND = ThEIds.id("arcane_crafting_terminal_wand");

    /**
     * A crystal placed in the grid cannot pay a recipe's crystal cost: counted twice, as ingredient
     * and payment, {@code ArcaneShapedRecipePattern.matches} rejects every recipe wanting a crystal.
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

    /**
     * The virtual workbench's owner and host, no position: fixed, so moving a terminal breaks no past craft.
     */
    public static final UUID CONTEXT_HOST =
            UUID.nameUUIDFromBytes("thaumicenergistics_ce:arcane_crafting_terminal".getBytes());

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandInv = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalInv = new AppEngInternalInventory(this, CRYSTAL_SLOTS);

    /** One card, the vis connection card; the slot it goes in is the one AE2 hangs off this part. */
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(getPartItem(), 1, this::onUpgradesChanged);

    public PartArcaneCraftingTerminal(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.5);
        // Refused at the door: an item briefly present is long enough for a menu to sync it.
        wandInv.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return isWand(stack);
            }
        });
        // Same reasoning: a slot holding junk makes the crystal requirement read as unaffordable.
        crystalInv.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    InternalInventory inventory, int slot, ItemStack stack) {
                return EssentiaCrystals.isCrystal(stack);
            }
        });
    }

    /** Whether a stack is a wand. Recognised by item class, not tag, so the dependency stays one-way. */
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

    /** Writes the pairing when a linking item is sneaked onto this part; without the sneak nothing is
     * written, since a walk past the terminal with the item in hand must not rebind it. */
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

    /** The part's own upgrade slot: AE2's menu builder reads it here and draws the slot the player uses. */
    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    /** AE2 calls this as the card goes in or comes out; the part keeps its own contents in the world. */
    private void onUpgradesChanged() {
        // A part standing outside a world has nothing to save, and AE2 calls this during a load too.
        if (getHost() != null) {
            saveChanges();
        }
    }

    private appeng.api.networking.@Nullable IGrid gridOrNull() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    /**
     * Offers the aura's vis toward a craft, simulated then committed; without it Thaumaturge refuses with
     * {@code PAYMENT_UNAVAILABLE}, since the untyped {@code baseVis} comes from a buffer a cable lacks.
     * @return the centivis supplied, never more than {@code needCentivis}
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
            // The card pays straight out of the aura around the cable, so no grid and no power are asked.
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
        // The aura around the cable, paid for with the network's power: the wireless terminal pays the
        // same rate, but from the aura around its player. See TerminalAuraPayment.
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
        // Old worlds have no such key; a missing key leaves it empty, so this is a safe upgrade.
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
