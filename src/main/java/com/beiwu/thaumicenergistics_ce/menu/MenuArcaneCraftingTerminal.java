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
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
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
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import com.beiwu.thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import com.beiwu.thaumicenergistics_ce.menu.slot.CrystalSlot;
import com.beiwu.thaumicenergistics_ce.menu.slot.WandSlot;
import com.beiwu.thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 奥术合成终端的菜单，来自放置的部件或配对的物品。
 * 九个合成单元、一个结果槽、六个侧面槽（水晶按两份计）、一个法杖槽。
 * 结构照抄 AE2 的 {@code CraftingTermMenu}：[CraftingRecipe] 匹配不上奥术配方。
 * 源质手势来自 {@link MenuEssentiaTerminalBase}，要访问卡。
 * 网络只认宿主：放置式是部件所在的那张网格，配对式是无线访问点链接的那张网格，
 * 配对只提供工作台状态，不换网络。{@link ICraftingGridMenu#getGridNode} 只认宿主那颗节点：
 * AE2 的 {@code MEStorageMenu#broadcastChanges} 同时发 storage 的清单和 getGridNode 那张网格的
 * 可合成集合，返回配对部件的节点就会把另一个网络的条目混进同一个终端里。
 */
public class MenuArcaneCraftingTerminal extends MenuEssentiaTerminalBase
        implements ICraftingGridMenu, InternalInventoryHost {

    public static final int GRID_SIZE = PartArcaneCraftingTerminal.GRID_SIZE;


    public static final SlotSemantic CRYSTALS_LEFT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_LEFT", true);

    public static final SlotSemantic CRYSTALS_RIGHT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_RIGHT", true);

    // 网格底部锚在 26,158，三列一行，取自屏幕样式。
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

    /**
     * 终端还没解析出来时的替身容器：只管占住槽位，槽位数量两侧不一致菜单就会错位。
     * 它不承担存储：收下的东西没有任何存档接着，关掉菜单就没了；见构造函数里的过滤器。
     */
    private final AppEngInternalInventory gridFallback = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandFallback = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalFallback =
            new AppEngInternalInventory(this, PartArcaneCraftingTerminal.CRYSTAL_SLOTS);

    private final InternalInventory craftingGrid;

    private final InternalInventory wandInventory;

    private final InternalInventory crystals;

    /** 只有无线终端有：用于标识手持终端，环境 VIS 取自玩家所在区块。 */
    private final @Nullable IEnergySource auraPayer;

    private final AppEngInternalInventory resultInventory =
            new AppEngInternalInventory(this, 1);

    private @Nullable ArcaneCraftingResultSlot resultSlot;

    private final ArcaneTerminalRuntime runtime = new ArcaneTerminalRuntime();
    private int craftInputSignature = -1;
    private boolean lastConnected;
    @appeng.menu.guisync.GuiSync(110)
    public int availableAuraVis;

    @appeng.menu.guisync.GuiSync(111)
    public boolean essentiaAccessEnabled;

    @appeng.menu.guisync.GuiSync(112)
    public boolean crystalFirst = true;
    @appeng.menu.guisync.GuiSync(113)
    public int requiredAuraVis;

    @appeng.menu.guisync.GuiSync(114)
    public int auraDiscountPercent;

    private static final String CRYSTAL_FIRST_PREFERENCE = "thaumicenergistics_ce:arcane_crystal_first";
    private static final String ACTION_FILL_RECIPE_MAX = "fillRecipeMax";
    private static final String ACTION_FILL_CRYSTALS = "fillRecipeCrystals";
    private static final String ACTION_TOGGLE_PAYMENT = "togglePayment";
    private static final String ACTION_RETURN_TO_NETWORK = "returnToNetwork";
    private static final String ACTION_RETURN_TO_INVENTORY = "returnToInventory";

    /** 上次说「替身槽位不收东西」的时刻。初值 -100 让第一次点击就能出声，之后两秒一次。 */
    private int lastPlaceholderRefusal = -100;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.part = host instanceof ArcaneTerminalHost arcane ? arcane.arcaneTerminal() : null;
        this.auraPayer = host instanceof IPortableTerminal portable ? portable : null;
        var wireless = host instanceof com.beiwu.thaumicenergistics_ce.item.WirelessArcaneCraftingHost w ? w : null;
        this.craftingGrid = wireless != null ? wireless.craftingGrid() : part == null ? gridFallback : part.craftingGrid();
        this.wandInventory = wireless != null ? wireless.wandInventory() : part == null ? wandFallback : part.wandInventory();
        this.crystals = wireless != null ? wireless.crystalInventory() : part == null ? crystalFallback : part.crystalInventory();

        // 替身容器一概拒收：这一侧既写不进存档，也合不了成（part == null 时结果槽直接返回），
        // 收下等于吃掉。玩家点击走 [AppEngSlot#mayPlace] 到这里，与部件容器的过滤器同一条门。
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

        // 1. 工作台合成单元。
        for (int i = 0; i < GRID_SIZE; i++) {
            int column = i % GRID_COLS;
            int row = i / GRID_COLS;
            Slot cell = addSlot(new AppEngSlot(craftingGrid, i), SlotSemantics.CRAFTING_GRID);
            // AE2 随后会用屏幕样式覆盖这些值；先设一次，覆盖之前读槽位才正常。
            cell.x = GRID_X + column * GRID_PITCH;
            cell.y = GRID_Y + row * GRID_PITCH;
        }

        // 2. 法杖槽，[STORAGE] 类型；AE2 对工具没有语义，这个槽只管摆放和 shift 点击。
        //    只收法杖，规则与容器上的过滤器一致：见 [WandSlot]。
        Slot wandSlot = addSlot(
                new WandSlot(wandInventory, PartArcaneCraftingTerminal.WAND_SLOT),
                SlotSemantics.STORAGE);
        wandSlot.x = WAND_X;
        wandSlot.y = WAND_Y;

        // 3. 六个水晶槽，网格每侧竖排三个，各自绑死 Thaumaturge 元要素顺序里的一位。
        // 网格里的水晶按两份计；见 [CrystalSlot]。
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

        // 4. 结果槽，排在它要读的存储、能量、网格之后。
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

        // 5. 玩家槽位，最后加，且只加一次；父类调用传 [createPlayerSlots] = false。
        createPlayerInventorySlots(playerInventory);
        if (playerInventory.player instanceof ServerPlayer server) {
            var preferences = server.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            crystalFirst = !preferences.contains(CRYSTAL_FIRST_PREFERENCE, Tag.TAG_BYTE)
                    || preferences.getBoolean(CRYSTAL_FIRST_PREFERENCE);
        }

        // 开菜单时刷一次结果，上次留下的网格才显示得出来。
        resultSlot.refresh();
        registerClientAction(ACTION_TOGGLE_PAYMENT, this::togglePaymentSource);
        registerClientAction(ACTION_FILL_CRYSTALS, String.class, this::fillRecipeCrystals);
        registerClientAction(ACTION_FILL_RECIPE_MAX, String.class, this::fillRecipeMax);
        registerClientAction(ACTION_RETURN_TO_NETWORK, () -> returnCraftingGrid(true));
        registerClientAction(ACTION_RETURN_TO_INVENTORY, () -> returnCraftingGrid(false));
    }


    /**
     * 与 AE2 自己的 {@code WirelessCraftingTermMenu} 同构：节点就是宿主的那颗 ——
     * 无线终端是访问点所在的网格，放置式是部件自己的网格。
     * 配对部件的节点接在这里会把另一个网络的可合成集合混进终端条目，见类注释。
     */
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
            var pos = part == null ? getPlayer().blockPosition() : part.getBlockEntity().getBlockPos();
            availableAuraVis = Math.max(0, (int) com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcAura.vis(getPlayer().level(), pos));
        }
        if (!isClientSide()) essentiaAccessEnabled = com.beiwu.thaumicenergistics_ce.config.ThEConfig.ARCANE_ESSENTIA_ACCESS.get();
        updateUpgradeSlotVisibility();
        super.broadcastChanges();
        if (resultSlot == null) {
            return;
        }
        if (!isClientSide()) chargeWand();
        boolean connected = canCraft();
        int signature = craftInputSignature();
        if (signature != craftInputSignature || connected != lastConnected) {
            craftInputSignature = signature;
            lastConnected = connected;
            resultSlot.refresh();
        }
    }

    @Override
    public boolean isValidForSlot(Slot slot, ItemStack stack) {
        if (stack.is(TECEItems.ESSENTIA_ACCESS_CARD.get())
                && getSlots(SlotSemantics.UPGRADE).contains(slot)
                && !com.beiwu.thaumicenergistics_ce.config.ThEConfig.ARCANE_ESSENTIA_ACCESS.get()) return false;
        return super.isValidForSlot(slot, stack);
    }

    public void updateUpgradeSlotVisibility() {
        var slots = getSlots(SlotSemantics.UPGRADE);
        int hidden = -1;
        if (!essentiaAccessEnabled) {
            int limit = getHost() instanceof com.beiwu.thaumicenergistics_ce.item.WirelessArcaneCraftingHost
                    ? Math.min(2, slots.size()) : slots.size();
            for (int i = limit - 1; i >= 0; i--) {
                if (!slots.get(i).hasItem()) { hidden = i; break; }
            }
        }
        for (int i = 0; i < slots.size(); i++) ((AppEngSlot) slots.get(i)).setSlotEnabled(i != hidden);
    }

    private void chargeWand() {
        if (!(getPlayer().level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        ItemStack wand = wandInventory.getStackInSlot(0);
        if (!com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcWand.isWand(wand)) return;
        for (ItemStack card : getHost().getUpgrades()) {
            if (!card.is(TECEItems.VIS_CONNECTION_CARD.get())) continue;
            ItemStack charged = com.beiwu.thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment.chargeWand(server, card, wand);
            if (charged != wand) wandInventory.setItemDirect(0, charged);
            return;
        }
    }

    @Override
    protected int transferStackToMenu(ItemStack stack) {
        var remaining = stack.copy();
        if (com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcWand.isWand(stack)) {
            var slot = wandSlot();
            if (slot != null && slot.mayPlace(remaining)) remaining = slot.safeInsert(remaining);
        } else {
            for (Slot slot : crystalSlots()) {
                if (slot.mayPlace(remaining)) remaining = slot.safeInsert(remaining);
            }
        }
        int moved = stack.getCount() - remaining.getCount();
        return moved > 0 ? moved : super.transferStackToMenu(stack);
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
        var preferences = data.getCompound(Player.PERSISTED_NBT_TAG);
        preferences.putBoolean(CRYSTAL_FIRST_PREFERENCE, crystalFirst);
        data.put(Player.PERSISTED_NBT_TAG, preferences);
        resultSlot.refresh();
        broadcastChanges();
    }

    private int craftInputSignature() {
        ItemStack card = ItemStack.EMPTY;
        for (ItemStack upgrade : getHost().getUpgrades()) {
            if (upgrade.is(TECEItems.VIS_CONNECTION_CARD.get())) {
                card = upgrade;
                break;
            }
        }
        return runtime.inputSignature(
                getPlayer(), craftingGrid, crystals, wandInventory, card, availableAuraVis);
    }

    public @Nullable PartArcaneCraftingTerminal part() {
        return part;
    }

    public InternalInventory craftingInventory() { return craftingGrid; }
    public InternalInventory crystalInventory() { return crystals; }
    public InternalInventory wandInventory() { return wandInventory; }

    public boolean canCraft() {
        return getLinkStatus().connected() && (part != null
                || getHost() instanceof com.beiwu.thaumicenergistics_ce.item.WirelessArcaneCraftingHost);
    }

    public @Nullable ArcaneCraftingResultSlot resultSlot() {
        return resultSlot;
    }

    /** 无线终端的电池；放置式终端为 {@code null}。 */
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


    /** 权限只看终端物品升级槽里那张卡，别处一概不读。 */
    @Override
    protected boolean essentiaAccessGranted() {
        // 玩家看到的就是宿主自己的升级栏；AE2 菜单按它构建。
        return (isClientSide() ? essentiaAccessEnabled : com.beiwu.thaumicenergistics_ce.config.ThEConfig.ARCANE_ESSENTIA_ACCESS.get())
                && getHost().getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get());
    }

    /** 屏幕问它只为隐藏手势；服务端在动东西之前会再问一次。 */
    public boolean hasEssentiaAccessCard() {
        return essentiaAccessGranted();
    }

    /** vis 卡是否插在打开的那个终端上。
     * 答话的是宿主自己的槽位，包里第二张终端顶不了。 */
    public boolean hasVisConnectionCard() {
        return TerminalAuraPayment.visConnectionInstalled(getHost());
    }

    /**
     * 替身槽位拒收时说一句为什么：光不放行，玩家只会觉得界面卡住了。
     * 只在服务端出声，两秒一次。
     */
    private void refusePlaceholderInsert(Player player) {
        if (!(player instanceof ServerPlayer server) || server.tickCount - lastPlaceholderRefusal < 40) {
            return;
        }
        lastPlaceholderRefusal = server.tickCount;
        server.displayClientMessage(
                Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.not_linked"), true);
    }

    /** 不写存档：结果由网格推导，存下来的会活得比网格久。 */
    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
    }

    @Override
    public boolean isClientSide() {
        return super.isClientSide();
    }
}
