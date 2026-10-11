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
import appeng.menu.slot.AppEngSlot;
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
import thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.registry.ModItems;
import thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.menu.slot.WandSlot;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

public class MenuArcaneCraftingTerminal extends MenuEssentiaTerminalBase
        implements ICraftingGridMenu, InternalInventoryHost {

    public static final int GRID_SIZE = PartArcaneCraftingTerminal.GRID_SIZE;


    public static final SlotSemantic CRYSTALS_LEFT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_LEFT", true);

    public static final SlotSemantic CRYSTALS_RIGHT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_RIGHT", true);

    private static final int GRID_X = 26;
    private static final int GRID_Y = 96;
    private static final int GRID_PITCH = 18;
    private static final int GRID_COLS = 3;
    private static final int RESULT_X = 134;
    private static final int RESULT_Y = 96;
    private static final int WAND_X = 116;
    private static final int WAND_Y = 114;

    private static final int CRYSTALS_LEFT_X = 8;
    private static final int CRYSTALS_RIGHT_X = 80;

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
    private long lastWandChargeTick = Long.MIN_VALUE;

    @GuiSync(111)
    public boolean essentiaAccessEnabled = ThEConfig.ARCANE_ESSENTIA_ACCESS.get();

    @GuiSync(112)
    public boolean inheritedVisConnection;

    public void updateUpgradeSlotVisibility() {
        var upgrades = getSlots(SlotSemantics.UPGRADE);
        int hidden = -1;
        if (!essentiaAccessEnabled) {
            for (int i = upgrades.size() - 1; i >= 0; i--) {
                if (!upgrades.get(i).hasItem()) {
                    hidden = i;
                    break;
                }
            }
        }
        for (int i = 0; i < upgrades.size(); i++) {
            ((AppEngSlot) upgrades.get(i)).setSlotEnabled(i != hidden);
        }
    }

    @GuiSync(110)
    public int availableAuraVis;

    private int lastPlaceholderRefusal = -100;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
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

        for (int i = 0; i < GRID_SIZE; i++) {
            int column = i % GRID_COLS;
            int row = i / GRID_COLS;
            Slot cell = addSlot(new AppEngSlot(craftingGrid, i), SlotSemantics.CRAFTING_GRID);
            cell.x = GRID_X + column * GRID_PITCH;
            cell.y = GRID_Y + row * GRID_PITCH;
        }

        Slot wandSlot = addSlot(
                new WandSlot(wandInventory, PartArcaneCraftingTerminal.WAND_SLOT),
                SlotSemantics.STORAGE);
        wandSlot.x = WAND_X;
        wandSlot.y = WAND_Y;

        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_COLUMN; i++) {
            Slot slot = addSlot(new CrystalSlot(crystals, i, aspectOf(i)), CRYSTALS_LEFT);
            slot.x = CRYSTALS_LEFT_X;
            slot.y = GRID_Y + i * GRID_PITCH;
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_COLUMN; i++) {
            int index = PartArcaneCraftingTerminal.CRYSTAL_COLUMN + i;
            Slot slot = addSlot(new CrystalSlot(crystals, index, aspectOf(index)), CRYSTALS_RIGHT);
            slot.x = CRYSTALS_RIGHT_X;
            slot.y = GRID_Y + i * GRID_PITCH;
        }

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
        resultSlot.x = RESULT_X;
        resultSlot.y = RESULT_Y;

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

    @Override
    public void broadcastChanges() {
        if (!isClientSide()) {
            essentiaAccessEnabled = ThEConfig.ARCANE_ESSENTIA_ACCESS.get();
            inheritedVisConnection = !inheritedVisCard().isEmpty();
            if (auraPayer != null && part != null && part.isActive() && hasVisConnectionCard()
                    && !inheritedVisConnection
                    && lastWandChargeTick != getPlayer().level().getGameTime()) {
                lastWandChargeTick = getPlayer().level().getGameTime();
                ItemStack wand = wandInventory.getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT);
                ItemStack charged = TerminalAuraPayment.chargeWand(getPlayer().level(), visCard(), wand);
                if (charged != wand) {
                    wandInventory.setItemDirect(PartArcaneCraftingTerminal.WAND_SLOT, charged);
                }
            }
        }
        updateUpgradeSlotVisibility();
        if (!isClientSide()) {
            if (auraPayer != null) {
                availableAuraVis = (int) Math.max(0, TcAura.vis(getPlayer().level(), getPlayer().blockPosition()));
            } else {
                availableAuraVis = part == null || part.getBlockEntity() == null ? 0
                        : (int) Math.max(0, TcAura.vis(getPlayer().level(), part.getBlockEntity().getBlockPos()));
            }
        }
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

    private int craftInputSignature() {
        int hash = 1;
        for (int i = 0; i < PartArcaneCraftingTerminal.GRID_SIZE; i++) {
            hash = 31 * hash + StackSignatures.of(craftingGrid.getStackInSlot(i));
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            hash = 31 * hash + StackSignatures.of(crystals.getStackInSlot(i));
        }
        hash = 31 * hash + StackSignatures.of(visCard());
        hash = 31 * hash + Boolean.hashCode(thaumicenergistics_ce.config.ThEConfig.ALLOW_AE_FOR_AURA_VIS.get());
        hash = 31 * hash + availableAuraVis;
        if (getPlayer().level() instanceof net.minecraft.server.level.ServerLevel server) {
            var relay = thaumicenergistics_ce.item.ItemVisConnectionCard.resolve(server, visCard());
            long relayVis = 0;
            if (relay != null) {
                for (var primal : thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler.PRIMALS) {
                    relayVis += relay.availableCentivis(primal);
                }
            }
            hash = 31 * hash + Long.hashCode(relayVis);
        }
        return 31 * hash + StackSignatures.of(wandInventory.getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT));
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


    @Override
    protected boolean essentiaAccessGranted() {
        return thaumicenergistics_ce.config.ThEConfig.ARCANE_ESSENTIA_ACCESS.get()
                && getHost().getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get());
    }

    public boolean hasEssentiaAccessCard() {
        return essentiaAccessGranted();
    }

    public ItemStack visCard() {
        ItemStack inherited = inheritedVisCard();
        return inherited.isEmpty() ? TerminalAuraPayment.visCard(getHost()) : inherited;
    }

    public boolean hasVisConnectionCard() {
        return !visCard().isEmpty();
    }

    private ItemStack inheritedVisCard() {
        if (!(getHost() instanceof thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost wireless)) {
            return ItemStack.EMPTY;
        }
        // Resolve the live link rather than the part captured when the menu opened.
        // Never write this card into the handheld item's upgrade inventory.
        var bound = wireless.arcaneTerminal();
        return bound == null ? ItemStack.EMPTY : TerminalAuraPayment.visCard(bound).copy();
    }

    private void refusePlaceholderInsert(Player player) {
        if (!(player instanceof ServerPlayer server) || server.tickCount - lastPlaceholderRefusal < 40) {
            return;
        }
        lastPlaceholderRefusal = server.tickCount;
        ThELog.LOG.info("[arcane] 替身槽位拒收：这个终端没连上已放置的奥术合成终端，放进去也没人存");
        server.sendOverlayMessage(
                Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.not_linked"));
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
    }

    @Override
    public boolean isClientSide() {
        return super.isClientSide();
    }
}
