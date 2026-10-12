package thaumicenergistics_ce.menu;

import thaumicenergistics_ce.config.ThEConfig;

import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.recipe.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public class MenuArcaneCraftingTerminal extends MenuEssentiaTerminalBase
        implements ICraftingGridMenu, InternalInventoryHost {

    public static final int GRID_SIZE = PartArcaneCraftingTerminal.GRID_SIZE;


    public static final SlotSemantic CRYSTALS_LEFT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_LEFT", true);

    public static final SlotSemantic CRYSTALS_RIGHT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_RIGHT", true);

    private final @Nullable PartArcaneCraftingTerminal part;

    private final AppEngInternalInventory gridFallback = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandFallback = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalFallback =
            new AppEngInternalInventory(this, PartArcaneCraftingTerminal.CRYSTAL_SLOTS);

    private final InternalInventory craftingGrid;

    private final InternalInventory wandInventory;

    private final InternalInventory crystals;

    private final @Nullable IEnergySource auraPayer;

    private final AppEngInternalInventory resultInventory =
            new AppEngInternalInventory(this, 1);

    private @Nullable ArcaneCraftingResultSlot resultSlot;

    private int craftInputSignature = -1;
    private final ArcaneTerminalRuntime runtime = new ArcaneTerminalRuntime();

    @GuiSync(111)
    public boolean essentiaAccessEnabled = ThEConfig.ARCANE_ESSENTIA_ACCESS.get();

    @GuiSync(112)
    public boolean inheritedVisConnection;

    @GuiSync(113)
    public boolean sharedEssentiaInstalled;

    private final ArcaneTerminalUpgrades upgrades;

    public boolean usesSharedUpgrades() { return upgrades.shared(); }

    public void updateUpgradeSlotVisibility() {
        upgrades.updateVisibility(getSlots(SlotSemantics.UPGRADE), essentiaAccessEnabled);
    }

    @GuiSync(110)
    public int availableAuraVis;

    private int lastPlaceholderRefusal = -100;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.upgrades = new ArcaneTerminalUpgrades(host, getSlots(SlotSemantics.UPGRADE));
        updateUpgradeSlotVisibility();
        this.part = host instanceof ArcaneTerminalHost arcane ? arcane.arcaneTerminal() : null;
        this.auraPayer = host instanceof IPortableTerminal portable ? portable : null;
        this.craftingGrid = part == null ? gridFallback : part.craftingGrid();
        this.wandInventory = part == null ? wandFallback : part.wandInventory();
        this.crystals = part == null ? crystalFallback : part.crystalInventory();
        inheritedVisConnection = !inheritedVisCard().isEmpty();

        Player menuOwner = playerInventory.player;
        IAEItemFilter placeholdersTakeNothing = new IAEItemFilter() {
            @Override
            public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                refusePlaceholderInsert(menuOwner);
                return false;
            }
        };
        gridFallback.setFilter(placeholdersTakeNothing);
        crystalFallback.setFilter(placeholdersTakeNothing);
        wandFallback.setFilter(placeholdersTakeNothing);

        ArcaneTerminalSlots.add((slot, semantic) -> addSlot(slot, semantic), craftingGrid, wandInventory, crystals);

        this.resultSlot = new ArcaneCraftingResultSlot(
                playerInventory.player,
                getActionSource(),
                energySource,
                storage,
                craftingGrid,
                resultInventory,
                this,
                part);
        addSlot(resultSlot, SlotSemantics.CRAFTING_RESULT);
        ArcaneTerminalSlots.positionResult(resultSlot);

        updateSharedEssentiaState();
        createPlayerInventorySlots(playerInventory);

        resultSlot.refresh();
    }


    @Override
    public IGridNode getGridNode() {
        return getHost() instanceof IActionHost actionHost ? actionHost.getActionableNode() : null;
    }

    @Override
    public InternalInventory getCraftingMatrix() {
        return craftingGrid;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (resultSlot != null) {
            resultSlot.refresh();
        }
    }

    public boolean connectionUnchanged() {
        return isClientSide() || upgrades.connectionUnchanged(part);
    }

    @Override
    public boolean stillValid(Player player) {
        return connectionUnchanged() && super.stillValid(player);
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput input, Player player) {
        if (!connectionUnchanged()) { player.closeContainer(); return; }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public void broadcastChanges() {
        if (!connectionUnchanged()) { getPlayer().closeContainer(); return; }
        if (!isClientSide()) {
            if (!upgrades.prepare()) {
                getPlayer().sendOverlayMessage(Component.translatable(
                        "gui.thaumicenergistics_ce.arcane_terminal.card_conflict"));
                getPlayer().closeContainer();
                return;
            }
            updateSharedEssentiaState();
            essentiaAccessEnabled = ThEConfig.ARCANE_ESSENTIA_ACCESS.get();
            inheritedVisConnection = !inheritedVisCard().isEmpty();
            runtime.chargeWand(getPlayer(), part, auraPayer, inheritedVisConnection, visCard(), wandInventory);
        }
        updateUpgradeSlotVisibility();
        refreshAvailableAura();
        super.broadcastChanges();
        if (resultSlot == null) {
            return;
        }
        int signature = craftInputSignature();
        if (signature != craftInputSignature) {
            craftInputSignature = signature;
            resultSlot.refresh();
        }
    }

    public void refreshAvailableAura() {
        if (!isClientSide()) availableAuraVis = runtime.availableAura(getPlayer(), upgrades.shared(), part);
    }

    private int craftInputSignature() {
        return runtime.inputSignature(getPlayer(), craftingGrid, crystals, wandInventory, visCard(), availableAuraVis);
    }

    public @Nullable PartArcaneCraftingTerminal part() {
        return part;
    }

    public @Nullable ArcaneCraftingResultSlot resultSlot() {
        return resultSlot;
    }

    public @Nullable IEnergySource auraPayer() {
        return auraPayer;
    }

    public static ResourceKey<IAspect> aspectOf(int crystalIndex) {
        return TcWorkbench.primalAt(crystalIndex);
    }

    public List<Slot> crystalSlots() {
        List<Slot> slots = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        slots.addAll(getSlots(CRYSTALS_LEFT));
        slots.addAll(getSlots(CRYSTALS_RIGHT));
        return slots;
    }

    public @Nullable Slot wandSlot() {
        List<Slot> slots = getSlots(SlotSemantics.STORAGE);
        return slots.isEmpty() ? null : slots.getFirst();
    }

    @Override
    protected int transferStackToMenu(ItemStack stack) {
        if (!ThEConfig.QUICK_INSERT.get()) {
            return super.transferStackToMenu(stack);
        }
        ItemStack remaining = stack.copy();
        Slot wand = wandSlot();
        if (wand != null && wand.isActive()) {
            remaining = wand.safeInsert(remaining);
        }
        for (Slot crystal : crystalSlots()) {
            if (remaining.isEmpty()) {
                break;
            }
            if (crystal.isActive()) {
                remaining = crystal.safeInsert(remaining);
            }
        }
        int inserted = stack.getCount() - remaining.getCount();
        return inserted + (remaining.isEmpty() ? 0 : super.transferStackToMenu(remaining));
    }

    public void sendCraftCost(@Nullable ArcaneCraftCost cost) {
        if (isClientSide()) {
            return;
        }
        sendPacketToClient(cost == null
                ? ArcaneCraftCostPayload.none(containerId)
                : ArcaneCraftCostPayload.of(containerId, cost.wandCentivis(), cost.crystalsNeeded()));
    }


    private void updateSharedEssentiaState() {
        if (!isClientSide() && upgrades.shared()) sharedEssentiaInstalled = upgrades.hasEssentiaCard();
    }

    @Override
    protected boolean essentiaAccessGranted() {
        if (isClientSide() && upgrades.shared()) return essentiaAccessEnabled && sharedEssentiaInstalled;
        return ThEConfig.ARCANE_ESSENTIA_ACCESS.get() && upgrades.hasEssentiaCard();
    }

    public boolean hasEssentiaAccessCard() {
        return essentiaAccessGranted();
    }

    public ItemStack visCard() { return upgrades.visCard(); }

    public boolean hasVisConnectionCard() {
        return !visCard().isEmpty();
    }

    private ItemStack inheritedVisCard() { return upgrades.inheritedVisCard(); }

    private void refusePlaceholderInsert(Player player) {
        if (!(player instanceof ServerPlayer server) || server.tickCount - lastPlaceholderRefusal < 40) {
            return;
        }
        lastPlaceholderRefusal = server.tickCount;
        server.sendOverlayMessage(
                Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.not_linked"));
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
    }

}
