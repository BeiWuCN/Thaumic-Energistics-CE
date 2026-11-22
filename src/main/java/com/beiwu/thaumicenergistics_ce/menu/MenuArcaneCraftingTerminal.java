package com.beiwu.thaumicenergistics_ce.menu;

import com.beiwu.thaumicenergistics_ce.config.ThEConfig;

import appeng.api.config.Actionable;
import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageHelper;
import appeng.api.stacks.AEItemKey;
import appeng.util.inv.PlayerInternalInventory;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.guisync.ClientActionKey;
import net.minecraft.network.codec.ByteBufCodecs;
import com.beiwu.thaumicenergistics_ce.menu.slot.CrystalSlot;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import net.minecraft.nbt.Tag;
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
import com.beiwu.thaumicenergistics_ce.recipe.arcane.ArcaneTerminalHost;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.EssentiaCrystals;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import com.beiwu.thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import com.beiwu.thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

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

    private final ArcaneTerminalRuntime runtime = new ArcaneTerminalRuntime();
    private int craftInputSignature = -1;

    @GuiSync(111)
    public boolean essentiaAccessEnabled = ThEConfig.ARCANE_ESSENTIA_ACCESS.get();

    private final ArcaneTerminalUpgrades upgrades;

    public void updateUpgradeSlotVisibility() {
        upgrades.updateVisibility(getSlots(SlotSemantics.UPGRADE), essentiaAccessEnabled);
    }

    @GuiSync(110)
    public int availableAuraVis;

    @appeng.menu.guisync.GuiSync(112)
    public boolean crystalFirst = true;
    @appeng.menu.guisync.GuiSync(113)
    public int requiredAuraVis;

    @appeng.menu.guisync.GuiSync(114)
    public int auraDiscountPercent;

    private static final String CRYSTAL_FIRST_PREFERENCE = "thaumicenergistics_ce:arcane_crystal_first";
    private static final ClientActionKey<String> ACTION_FILL_RECIPE_MAX = new ClientActionKey<>("fillRecipeMax");
    private static final ClientActionKey<String> ACTION_FILL_CRYSTALS = new ClientActionKey<>("fillRecipeCrystals");
    private static final ClientActionKey<Void> ACTION_TOGGLE_PAYMENT = new ClientActionKey<>("togglePayment");
    private static final ClientActionKey<Void> ACTION_RETURN_TO_NETWORK = new ClientActionKey<>("returnToNetwork");
    private static final ClientActionKey<Void> ACTION_RETURN_TO_INVENTORY = new ClientActionKey<>("returnToInventory");

    /** 上次说「替身槽位不收东西」的时刻。初值 -100 让第一次点击就能出声，之后两秒一次。 */
    private int lastPlaceholderRefusal = -100;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.upgrades = new ArcaneTerminalUpgrades(host);
        updateUpgradeSlotVisibility();
        this.part = host instanceof ArcaneTerminalHost arcane ? arcane.arcaneTerminal() : null;
        this.auraPayer = host instanceof IPortableTerminal portable ? portable : null;
        var wireless = host instanceof com.beiwu.thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost handheld ? handheld : null;
        this.craftingGrid = wireless != null ? wireless.craftingGrid() : part == null ? gridFallback : part.craftingGrid();
        this.wandInventory = wireless != null ? wireless.wandInventory() : part == null ? wandFallback : part.wandInventory();
        this.crystals = wireless != null ? wireless.crystalInventory() : part == null ? crystalFallback : part.crystalInventory();

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

        createPlayerInventorySlots(playerInventory);
        if (playerInventory.player instanceof ServerPlayer server) {
            var preferences = server.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            crystalFirst = preferences.map(tag -> tag.getBooleanOr(CRYSTAL_FIRST_PREFERENCE, true)).orElse(true);
        }

        resultSlot.refresh();
        registerClientAction(ACTION_TOGGLE_PAYMENT, this::togglePaymentSource);
        registerClientAction(ACTION_FILL_CRYSTALS, ByteBufCodecs.STRING_UTF8, this::fillRecipeCrystals);
        registerClientAction(ACTION_FILL_RECIPE_MAX, ByteBufCodecs.STRING_UTF8, this::fillRecipeMax);
        registerClientAction(ACTION_RETURN_TO_NETWORK, () -> returnCraftingGrid(true));
        registerClientAction(ACTION_RETURN_TO_INVENTORY, () -> returnCraftingGrid(false));
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
        return isClientSide() || getHost().getLinkStatus().connected();
    }

    @Override
    public boolean stillValid(Player player) {
        return (getHost() instanceof com.beiwu.thaumicenergistics_ce.item.QuantumWirelessTerminalMenuHost<?>
                || connectionUnchanged()) && super.stillValid(player);
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput input, Player player) {
        if (!(getHost() instanceof com.beiwu.thaumicenergistics_ce.item.QuantumWirelessTerminalMenuHost<?>)
                && !connectionUnchanged()) { player.closeContainer(); return; }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public void broadcastChanges() {
        if (!(getHost() instanceof com.beiwu.thaumicenergistics_ce.item.QuantumWirelessTerminalMenuHost<?>)
                && !connectionUnchanged()) { getPlayer().closeContainer(); return; }
        if (!isClientSide()) {
            essentiaAccessEnabled = ThEConfig.ARCANE_ESSENTIA_ACCESS.get();
            runtime.chargeWand(getPlayer(), part, auraPayer, visCard(), wandInventory);
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
        if (!isClientSide()) availableAuraVis = runtime.availableAura(getPlayer(), upgrades.wireless(), part);
    }

    public void fillRecipeMax(String recipeId) {
        if (isClientSide()) {
            sendClientAction(ACTION_FILL_RECIPE_MAX, recipeId);
            return;
        }
        if (!isValidMenu() || !stillValid(getPlayer()) || !canCraft() || resultSlot == null
                || !resultSlot.matchesTransferredRecipe(recipeId)) {
            return;
        }

        var byItem = new LinkedHashMap<AEItemKey, List<Integer>>();
        int batches = Integer.MAX_VALUE;
        for (int slot = 0; slot < craftingGrid.size(); slot++) {
            ItemStack stack = craftingGrid.getStackInSlot(slot);
            var key = AEItemKey.of(stack);
            if (key != null) {
                byItem.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
                batches = Math.min(batches, stack.getMaxStackSize());
            }
        }
        if (byItem.isEmpty()) {
            return;
        }

        Inventory playerInventory = getPlayerInventory();
        for (var entry : byItem.entrySet()) {
            var key = entry.getKey();
            var slots = entry.getValue();
            int needed = extraIngredientsFor(slots, batches);
            long available = StorageHelper.poweredExtraction(
                    energySource, storage, key, needed, getActionSource(), Actionable.SIMULATE);
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE && available < needed; slot++) {
                ItemStack stack = playerInventory.getItem(slot);
                if (key.equals(AEItemKey.of(stack))) {
                    available += stack.getCount();
                }
            }
            while (batches > 1 && extraIngredientsFor(slots, batches) > available) {
                batches--;
            }
        }

        for (int batch = 2; batch <= batches; batch++) {
            for (var entry : byItem.entrySet()) {
                var key = entry.getKey();
                for (int slot : entry.getValue()) {
                    ItemStack stack = craftingGrid.getStackInSlot(slot);
                    if (stack.getCount() >= batch) {
                        continue;
                    }
                    if (!takeExtraIngredient(playerInventory, key)) {
                        fillRecipeCrystals(recipeId, batch - 1);
                        broadcastChanges();
                        return;
                    }
                    craftingGrid.setItemDirect(slot, stack.copyWithCount(stack.getCount() + 1));
                }
            }
        }
        fillRecipeCrystals(recipeId, batches);
        broadcastChanges();
    }

    private int extraIngredientsFor(List<Integer> slots, int batches) {
        int needed = 0;
        for (int slot : slots) {
            needed += Math.max(0, batches - craftingGrid.getStackInSlot(slot).getCount());
        }
        return needed;
    }

    private boolean takeExtraIngredient(Inventory inventory, AEItemKey key) {
        if (StorageHelper.poweredExtraction(energySource, storage, key, 1, getActionSource()) > 0) {
            return true;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (key.equals(AEItemKey.of(inventory.getItem(slot)))) {
                return !inventory.removeItem(slot, 1).isEmpty();
            }
        }
        return false;
    }

    public void fillRecipeCrystals(String recipeId) {
        if (isClientSide()) {
            sendClientAction(ACTION_FILL_CRYSTALS, recipeId);
            return;
        }
        fillRecipeCrystals(recipeId, 1);
    }

    private void fillRecipeCrystals(String recipeId, int batches) {
        if (!isValidMenu() || !stillValid(getPlayer()) || !canCraft() || resultSlot == null) {
            return;
        }

        boolean changed = false;
        var slots = crystalSlots();
        for (var entry : resultSlot.crystalsForTransfer(recipeId).entries()) {
            var aspect = entry.aspect().unwrapKey().orElse(null);
            if (aspect == null) {
                continue;
            }
            ItemStack crystal = EssentiaCrystals.create(entry.aspect(), 1);
            var key = AEItemKey.of(crystal);
            if (key == null) {
                continue;
            }
            for (Slot slot : slots) {
                if (!(slot instanceof CrystalSlot target) || !aspect.equals(target.requiredAspect())
                        || !target.mayPlace(crystal)) {
                    continue;
                }
                ItemStack existing = target.getItem();
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, crystal)) {
                    break;
                }
                int wanted = (int) Math.min((long) entry.amount() * batches, target.getMaxStackSize(crystal)) - existing.getCount();
                if (wanted > 0) {
                    long moved = StorageHelper.poweredExtraction(energySource, storage, key, wanted, getActionSource());
                    var inventory = getPlayerInventory();
                    for (int i = 0; i < Inventory.INVENTORY_SIZE && moved < wanted; i++) {
                        if (key.equals(AEItemKey.of(inventory.getItem(i)))) {
                            moved += inventory.removeItem(i, wanted - (int) moved).getCount();
                        }
                    }
                    if (moved > 0) {
                        // Commit the accepted amount directly to the checked slot. No refund is needed.
                        crystals.setItemDirect(target.getContainerSlot(),
                                crystal.copyWithCount(existing.getCount() + (int) moved));
                        changed = true;
                    }
                }
                break;
            }
        }
        if (changed) {
            broadcastChanges();
        }
    }

    public void returnCraftingGrid(boolean toNetwork) {
        if (isClientSide()) {
            sendClientAction(toNetwork ? ACTION_RETURN_TO_NETWORK : ACTION_RETURN_TO_INVENTORY);
            return;
        }
        if (!isValidMenu() || !stillValid(getPlayer()) || (toNetwork && !canCraft())) {
            return;
        }

        var playerInventory = new PlayerInternalInventory(getPlayerInventory());
        boolean movedAny = returnSlots(craftingGrid, playerInventory, toNetwork);
        movedAny |= returnSlots(crystals, playerInventory, toNetwork);
        if (movedAny) {
            broadcastChanges();
        }
    }

    private boolean returnSlots(InternalInventory source, PlayerInternalInventory playerInventory, boolean toNetwork) {
        boolean movedAny = false;
        for (int slot = 0; slot < source.size(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack remaining;
            if (toNetwork) {
                var key = AEItemKey.of(stack);
                if (key == null) {
                    continue;
                }
                long moved = StorageHelper.poweredInsert(energySource, storage, key, stack.getCount(), getActionSource());
                remaining = stack.copyWithCount(stack.getCount() - (int) moved);
            } else {
                remaining = playerInventory.addItems(stack.copy());
            }

            if (remaining.getCount() != stack.getCount()) {
                source.setItemDirect(slot, remaining);
                movedAny = true;
            }
        }
        return movedAny;
    }

    public void togglePaymentSource() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_PAYMENT);
            return;
        }
        if (!(getPlayer() instanceof ServerPlayer server) || !isValidMenu() || !stillValid(server)) {
            return;
        }
        crystalFirst = !crystalFirst;
        var data = server.getPersistentData();
        var preferences = data.getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        preferences.putBoolean(CRYSTAL_FIRST_PREFERENCE, crystalFirst);
        data.put(Player.PERSISTED_NBT_TAG, preferences);
        resultSlot.refresh();
        broadcastChanges();
    }

    private int craftInputSignature() {
        return 31 * runtime.inputSignature(getPlayer(), craftingGrid, crystals, wandInventory, visCard(), availableAuraVis)
                + Boolean.hashCode(crystalFirst);
    }

    public boolean canCraft() {
        return getLinkStatus().connected();
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

    public InternalInventory crystalInventory() { return crystals; }
    public InternalInventory wandInventory() { return wandInventory; }

    public @Nullable Slot wandSlot() {
        List<Slot> slots = getSlots(SlotSemantics.STORAGE);
        return slots.isEmpty() ? null : slots.getFirst();
    }

    @Override
    protected int transferStackToMenu(ItemStack stack) {
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

    public void clearCraftCost() {
        sendCraftCost(null, 0);
    }

    public void sendCraftCost(@Nullable ArcaneCraftCost cost, int discountPercent) {
        auraDiscountPercent = discountPercent;
        requiredAuraVis = cost == null ? 0 : cost.auraVis();
        if (isClientSide()) {
            return;
        }
        sendPacketToClient(cost == null
                ? ArcaneCraftCostPayload.none(containerId)
                : ArcaneCraftCostPayload.of(containerId, cost.wandCentivis(), cost.crystalsNeeded()));
    }


    @Override
    protected boolean essentiaAccessGranted() {
        return (isClientSide() ? essentiaAccessEnabled : ThEConfig.ARCANE_ESSENTIA_ACCESS.get())
                && upgrades.hasEssentiaCard();
    }

    public boolean hasEssentiaAccessCard() {
        return essentiaAccessGranted();
    }

    public ItemStack visCard() { return upgrades.visCard(); }

    public boolean hasVisConnectionCard() {
        return !visCard().isEmpty();
    }


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
