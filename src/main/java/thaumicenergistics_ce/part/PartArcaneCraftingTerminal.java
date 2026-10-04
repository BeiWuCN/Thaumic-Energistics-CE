package thaumicenergistics_ce.part;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.arcane.EssentiaCrystals;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.init.ModMenuTypes;

/**
 * An ME crafting terminal on a cable whose crafting grid is an arcane workbench's; the network supplies
 * what the recipe needs that the player did not bring, paying vis from the wand in the wand slot.
 * <ul>
 *   <li>The inventories are the part's own, not the network's: a crafting grid holds what the player is
 *       arranging right now, which the network must not be able to take.
 * </ul>
 */
public class PartArcaneCraftingTerminal extends AbstractTerminalPart {

    /** The crafting grid under AE2's namespace: AE2 resolves these ids itself and renders the group. */
    public static final ResourceLocation INV_CRAFTING = AppEng.makeId("arcane_crafting_terminal_crafting");

    /** The wand slot, which is ours and has no AE2 meaning. */
    public static final ResourceLocation INV_WAND = ThEIds.id("arcane_crafting_terminal_wand");

    /**
     * A crystal placed <em>in the grid</em> cannot pay a recipe's crystal cost: counted twice, as ingredient
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

    /** Every model that has to be registered for this part, named once for {@code ThaumicEnergistics}. */
    public static final List<ResourceLocation> MODEL_LOCATIONS =
            List.of(MODEL_BASE, MODEL_OFF, MODEL_ON, MODEL_HAS_CHANNEL);

    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL =
            new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public static final int GRID_SIZE = 9;

    public static final int WAND_SLOT = 0;

    /** Crystal slots offered: six, in two columns of three; the menu files 0-2 left, 3-5 right. */
    public static final int CRYSTAL_SLOTS = 6;

    /** Crystal slots drawn down the left of the grid. */
    public static final int CRYSTAL_COLUMN = 3;

    /**
     * The virtual workbench's owner and host, no position: fixed, so moving a terminal breaks no past craft.
     */
    public static final UUID CONTEXT_HOST =
            UUID.nameUUIDFromBytes("thaumicenergistics_ce:arcane_crafting_terminal".getBytes());

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandInv = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalInv = new AppEngInternalInventory(this, CRYSTAL_SLOTS);

    public PartArcaneCraftingTerminal(IPartItem<?> partItem) {
        super(partItem);
        // A terminal costs almost nothing to connect; the work is in the menu, not in ticking.
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

    /** Whether a stack is a wand. By item class, not tag, so the dependency stays one-way. */
    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof ItemWand;
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
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    /**
     * AE per vis. Deliberately the rate {@code PartVisInterface} charges: both buy vis from the same network.
     */
    private static final double AE_PER_VIS = 1_000.0;

    /** Centivis in one vis, as Thaumaturge counts them. */
    private static final int CENTIVIS_PER_VIS = 100;

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
        IGrid grid = gridOrNull();
        if (grid == null) {
            return 0;
        }
        IEnergyService energy =
                grid.getService(IEnergyService.class);
        if (energy == null) {
            return 0;
        }

        BlockPos pos = getBlockEntity().getBlockPos();
        float wanted = (float) needCentivis / CENTIVIS_PER_VIS;
        float available = TcAura.drainVis(level, pos, wanted, true);
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

        // All or nothing: check the power first, or a short commit throws out of the handler.
        if (energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG) < cost) {
            return 0;
        }
        TcAura.drainVis(level, pos, (float) offered / CENTIVIS_PER_VIS, false);
        energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return offered;
    }

    /** Hands AE2 the inventories by the ids it knows them by: one not returned here is unreachable by id. */
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

    /** Drops every inventory when wrenched off: leaving one out loses its contents with the part. */
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
        // Old worlds have no such key; a missing key leaves it empty, so this is a safe upgrade.
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
