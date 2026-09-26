package thaumicenergistics.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.ThEIds;
import thaumicenergistics.arcane.EssentiaCrystals;
import thaumicenergistics.init.ModMenuTypes;

/**
 * The Arcane Crafting Terminal, as a part that goes on a cable.
 *
 * <p>An ME crafting terminal whose crafting grid is an arcane workbench's. The network supplies whatever
 * the recipe needs that the player did not bring, paying the vis out of the wand in the wand slot.
 *
 * <p>The inventories are the part's own rather than the network's, deliberately: a crafting grid holds
 * what a player is arranging right now, which the network must not be able to take.
 */
public class PartArcaneCraftingTerminal extends AbstractTerminalPart {

    /**
     * The crafting grid, as AE2 addresses it. In AE2's namespace rather than ours, because AE2 resolves
     * these ids itself and the crafting grid is a slot group AE2 already knows how to render.
     */
    public static final ResourceLocation INV_CRAFTING = AppEng.makeId("arcane_crafting_terminal_crafting");

    /** The wand slot, which is ours and has no AE2 meaning. */
    public static final ResourceLocation INV_WAND = ThEIds.id("arcane_crafting_terminal_wand");

    /**
     * The six crystal slots. A crystal placed <em>in the grid</em> cannot pay an arcane recipe's crystal
     * cost: it is counted twice over, as ingredient and as payment, and {@code
     * ArcaneShapedRecipePattern.matches} then rejects every recipe that wants a crystal. The reference
     * build's texture has drawn these slots all along; this build had no inventory behind them.
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

    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL =
            new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public static final int GRID_SIZE = 9;

    public static final int WAND_SLOT = 0;

    /**
     * How many crystal slots the terminal offers: six, in two columns of three. The menu files slots 0-2
     * under the left column and 3-5 under the right.
     */
    public static final int CRYSTAL_SLOTS = 6;

    /** Crystal slots drawn down the left of the grid. */
    public static final int CRYSTAL_COLUMN = 3;

    /**
     * Identity for the virtual workbench this terminal crafts as. Thaumaturge's
     * {@code ArcaneWorkbenchContext.virtual} is a craft with an owner and a host but no position; the UUID
     * is fixed rather than taken from the part, so moving a terminal does not invalidate past crafts.
     */
    public static final java.util.UUID CONTEXT_HOST =
            java.util.UUID.nameUUIDFromBytes("thaumicenergistics:arcane_crafting_terminal".getBytes());

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandInv = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalInv = new AppEngInternalInventory(this, CRYSTAL_SLOTS);

    public PartArcaneCraftingTerminal(IPartItem<?> partItem) {
        super(partItem);
        // A terminal costs almost nothing to be connected; the work is in the menu, not in ticking.
        getMainNode().setIdlePowerUsage(0.5);
        // Refused at the door: an item that is briefly present is long enough for a menu to sync it.
        wandInv.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    appeng.api.inventories.InternalInventory inventory, int slot, ItemStack stack) {
                return isWand(stack);
            }
        });
        // Same reasoning: a slot holding junk makes the crystal requirement read as unaffordable.
        crystalInv.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
            @Override
            public boolean allowInsert(
                    appeng.api.inventories.InternalInventory inventory, int slot, ItemStack stack) {
                return EssentiaCrystals.isCrystal(stack);
            }
        });
    }

    /**
     * Whether a stack is a wand this terminal can charge from. Checked by item class rather than by tag:
     * in Thaumaturge a wand is one {@code ItemWand} whose rod and caps live in components. Lives here to
     * keep the dependency one-way.
     */
    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof com.leclowndu93150.thaumaturge.content.wands.ItemWand;
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

    /** The grid, for the menu and for the tests. */
    public AppEngInternalInventory craftingGrid() {
        return craftingGrid;
    }

    public AppEngInternalInventory wandInventory() {
        return wandInv;
    }

    /** The six crystal slots' inventory, flat: 0-2 are the left column, 3-5 the right. */
    public AppEngInternalInventory crystalInventory() {
        return crystalInv;
    }

    /** The grid this part is on, or {@code null} when it is not connected to one. */
    private appeng.api.networking.@Nullable IGrid gridOrNull() {
        appeng.api.networking.IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    /**
     * How much AE one vis costs, in AE per vis. Deliberately the rate {@code PartVisInterface} charges: a
     * terminal craft and a vis interface craft buy the same vis from the same network.
     */
    private static final double AE_PER_VIS = 1_000.0;

    /** Centivis in one vis, as Thaumaturge counts them. */
    private static final int CENTIVIS_PER_VIS = 100;

    /**
     * Offers the aura's vis toward an arcane craft, optionally taking it. Without this every craft is
     * refused with {@code PAYMENT_UNAVAILABLE}: Thaumaturge pays the untyped {@code baseVis} out of a
     * workbench block's aura buffer and a cable has none. Called twice per craft - simulated, then
     * committed - and the answers must agree or {@code WorkbenchPayment.commit} throws.
     *
     * @return the centivis supplied, never more than {@code needCentivis}
     */
    public int supplyAura(int needCentivis, boolean simulate) {
        if (needCentivis <= 0 || !isActive()) {
            return 0;
        }
        net.minecraft.world.level.Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return 0;
        }
        appeng.api.networking.IGrid grid = gridOrNull();
        if (grid == null) {
            return 0;
        }
        appeng.api.networking.energy.IEnergyService energy =
                grid.getService(appeng.api.networking.energy.IEnergyService.class);
        if (energy == null) {
            return 0;
        }

        net.minecraft.core.BlockPos pos = getBlockEntity().getBlockPos();
        float wanted = (float) needCentivis / CENTIVIS_PER_VIS;
        float available = AuraHelper.drainVis(level, pos, wanted, true);
        if (available <= 0.0F) {
            return 0;
        }
        int offered = Math.min(needCentivis, Math.round(available * CENTIVIS_PER_VIS));
        double cost = AE_PER_VIS * offered / CENTIVIS_PER_VIS;

        // A craft is paid in full or not at all: supply only what the network can cover.
        double payable = energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (payable < cost) {
            offered = (int) Math.floor(payable / AE_PER_VIS * CENTIVIS_PER_VIS);
            if (offered <= 0) {
                return 0;
            }
            cost = AE_PER_VIS * offered / CENTIVIS_PER_VIS;
        }
        if (offered <= 0) {
            return 0;
        }
        if (simulate) {
            return offered;
        }

        // The whole reservation or nothing. Thaumaturge's workbench payment throws if a commit returns less
        // than its simulation reserved, and that exception leaves the click handler, so the network is asked
        // first and the aura is only drained once the power is there to pay for it.
        if (energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG) < cost) {
            return 0;
        }
        AuraHelper.drainVis(level, pos, (float) offered / CENTIVIS_PER_VIS, false);
        energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return offered;
    }

    /**
     * Hands AE2 the inventories by the ids it knows them by: a sub-inventory never returned here is
     * unreachable by id, and the menu's grid stays empty.
     */
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

    /** Drops every inventory when the part is wrenched off: leaving one out loses its contents with the part. */
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
    }

    @Override
    public void clearContent() {
        super.clearContent();
        craftingGrid.clear();
        wandInv.clear();
        crystalInv.clear();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        craftingGrid.readFromNBT(data, "craftingGrid", registries);
        wandInv.readFromNBT(data, "wandInv", registries);
        // A world saved before the crystal slots existed simply has no such key, and an inventory asked to
        // read a missing key is left empty rather than cleared wrongly - so this is a safe upgrade.
        crystalInv.readFromNBT(data, "crystalInv", registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        craftingGrid.writeToNBT(data, "craftingGrid", registries);
        wandInv.writeToNBT(data, "wandInv", registries);
        crystalInv.writeToNBT(data, "crystalInv", registries);
    }
}
