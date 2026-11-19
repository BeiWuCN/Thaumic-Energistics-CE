package com.beiwu.thaumicenergistics_ce.menu;
import com.beiwu.thaumicenergistics_ce.config.ThEConfig;

import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageHelper;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.PlayerInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
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
import com.beiwu.thaumicenergistics_ce.recipe.arcane.TerminalArcaneCraftingInput;
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

    /** 客户端动作：把合成网格退回 ME 网络。 */
    private static final String ACTION_CLEAR_TO_NETWORK = "clearToNetwork";

    /** 客户端动作：把合成网格退回玩家背包。 */
    private static final String ACTION_CLEAR_TO_PLAYER = "clearToPlayer";

    /** 客户端动作：翻转付费源，法杖 6 要素 2:1 顶水晶 ↔ 用水晶槽的整颗晶体先付。 */
    private static final String ACTION_TOGGLE_PAYMENT = "togglePayment";

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

    /** 结果槽的刷新签名与节流：vis 卡与绑定 relay 的存量也参与，见 [ArcaneTerminalRuntime]。 */
    private final ArcaneTerminalRuntime runtime = new ArcaneTerminalRuntime();

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

    private int craftInputSignature = -1;
    private boolean lastConnected;
    @appeng.menu.guisync.GuiSync(110)
    public int availableAuraVis;

    @appeng.menu.guisync.GuiSync(111)
    public boolean essentiaAccessEnabled;

    /** 付费源模式：true 先用水晶槽的整颗晶体，false 让法杖 6 要素按 2:1 顶水晶。 */
    @appeng.menu.guisync.GuiSync(112)
    public boolean crystalFirst;

    /** 上次说「替身槽位不收东西」的时刻。初值 -100 让第一次点击就能出声，之后两秒一次。 */
    private int lastPlaceholderRefusal = -100;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.part = host instanceof ArcaneTerminalHost arcane ? arcane.arcaneTerminal() : null;
        this.auraPayer = host instanceof IPortableTerminal portable ? portable : null;
        var wireless = host instanceof com.beiwu.thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost w ? w : null;
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

        // 开菜单时刷一次结果，上次留下的网格才显示得出来。
        resultSlot.refresh();

        registerClientAction(ACTION_CLEAR_TO_NETWORK, this::clearCraftingGridToNetwork);
        registerClientAction(ACTION_CLEAR_TO_PLAYER, this::clearCraftingGridToPlayer);
        registerClientAction(ACTION_TOGGLE_PAYMENT, this::togglePaymentSource);
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
            int limit = getHost() instanceof com.beiwu.thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost
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

    /**
     * 把网格里的东西退回 ME 网络。按钮在客户端，实际搬运只在服务端做。
     * 每格单独算插入量：网络满、断电或被过滤时插多少扣多少，剩下的留在原格，
     * 不整个丢掉。走 [setItemDirect] 才会触发部件 NBT 或无线物品组件的存档。
     */
    public void clearCraftingGridToNetwork() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_TO_NETWORK);
            return;
        }
        boolean changed = false;
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = craftingGrid.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }
            long moved = StorageHelper.poweredInsert(
                    energySource, storage, key, stack.getCount(), getActionSource());
            if (moved <= 0) {
                continue;
            }
            ItemStack left = stack.copy();
            left.shrink((int) Math.min(moved, left.getCount()));
            craftingGrid.setItemDirect(i, left);
            changed = true;
        }
        if (changed) {
            broadcastChanges();
        }
    }

    /**
     * 把网格里的东西退回玩家背包，热键栏优先、两遍填充，先合并已有堆再占空格。
     * 装不下的部分留在网格：这里没有掉落，也不会像结果槽那样交出费用后丢产物。
     */
    public void clearCraftingGridToPlayer() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR_TO_PLAYER);
            return;
        }
        PlayerInternalInventory playerInv = new PlayerInternalInventory(getPlayerInventory());
        boolean changed = false;
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = craftingGrid.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack left = stack.copy();
            for (int emptyLoop = 0; emptyLoop < 2; emptyLoop++) {
                boolean allowEmpty = emptyLoop == 1;
                for (int j = 9; j-- > 0;) {
                    if (playerInv.getStackInSlot(j).isEmpty() == allowEmpty) {
                        left = playerInv.getSlotInv(j).addItems(left);
                    }
                }
                for (int j = 9; j < Inventory.INVENTORY_SIZE; j++) {
                    if (playerInv.getStackInSlot(j).isEmpty() == allowEmpty) {
                        left = playerInv.getSlotInv(j).addItems(left);
                    }
                }
            }
            if (left.getCount() != stack.getCount()) {
                craftingGrid.setItemDirect(i, left);
                changed = true;
            }
        }
        if (changed) {
            broadcastChanges();
        }
    }

    /** 切换付费源。客户端只发信号，翻转和清签名都在服务端做，下一次 broadcast 重算结果。 */
    public void togglePaymentSource() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_PAYMENT);
            return;
        }
        crystalFirst = !crystalFirst;
        craftInputSignature = -1;
        broadcastChanges();
    }

    public boolean crystalFirst() {
        return crystalFirst;
    }

    /**
     * 结果槽的重算键。用 [ArcaneTerminalRuntime] 的实现：它把 vis 连接卡和它绑定的 relay
     * 存量也算进去。原先这里漏了这两项，插卡、换绑定、relay 耗尽时签名不变，
     * 结果槽就一直显示上一次的「合不了」，直到玩家动一下网格。
     */
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
                || getHost() instanceof com.beiwu.thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost);
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

    /**
     * 网格刚摆好时补晶体：把缺的那几颗从 ME 网络拉进六个晶体槽。
     * JEI 转移只认九个格子，晶体槽是终端自己的容器，客户端改不动，只能走服务端。
     * <p>全补或全不补：网络里少一味就把已取出的整笔退回，一颗都不放进去 ——
     * 半补的槽位会让玩家以为合成条件已经满足，点下去才发现还差。
     */
    public void fillCrystalsFromNetwork() {
        if (isClientSide() || !(getPlayer() instanceof ServerPlayer server)) {
            return;
        }
        AspectList need = crystalCostOfGrid(server);
        if (need == null || need.isEmpty()) {
            return;
        }
        // 先全部取到手上，任何一味不够就整笔退回。
        List<ItemStack> pulled = new ArrayList<>();
        for (AspectInstance entry : need.entries()) {
            int deficit = entry.amount() - countCrystals(entry.aspect());
            if (deficit <= 0) {
                continue;
            }
            ItemStack crystalsForAspect = EssentiaCrystals.create(entry.aspect(), deficit);
            AEItemKey key = AEItemKey.of(crystalsForAspect);
            if (key == null) {
                refund(pulled);
                return;
            }
            long got = storage.extract(key, deficit, appeng.api.config.Actionable.MODULATE, getActionSource());
            if (got < deficit) {
                if (got > 0) {
                    storage.insert(key, got, appeng.api.config.Actionable.MODULATE, getActionSource());
                }
                refund(pulled);
                return;
            }
            pulled.add(crystalsForAspect);
        }
        // 全部到手才落进槽位。
        for (ItemStack stack : pulled) {
            int slot = crystalSlotFor(EssentiaCrystals.aspectOf(stack));
            if (slot < 0) {
                // 没有绑这个要素的槽位：还回网络，别吞掉玩家的东西。
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) {
                    storage.insert(key, stack.getCount(), appeng.api.config.Actionable.MODULATE, getActionSource());
                }
                continue;
            }
            ItemStack existing = crystals.getStackInSlot(slot);
            if (existing.isEmpty()) {
                crystals.setItemDirect(slot, stack);
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                existing.grow(stack.getCount());
                crystals.setItemDirect(slot, existing);
            }
        }
        broadcastChanges();
    }

    /** 把已经取出的晶体还回网络；中途放弃时用。 */
    private void refund(List<ItemStack> pulled) {
        for (ItemStack stack : pulled) {
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                storage.insert(key, stack.getCount(), appeng.api.config.Actionable.MODULATE, getActionSource());
            }
        }
    }

    /**
     * 这个网格背后的配方要几颗什么晶体；没有配方、或研究没解锁时为 {@code null}。
     * 走 Thaumaturge 自己的匹配，和合成时看到的是同一份需求。
     */
    private @Nullable AspectList crystalCostOfGrid(ServerPlayer server) {
        List<ItemStack> cells = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            cells.add(craftingGrid.getStackInSlot(i));
        }
        List<ItemStack> crystalSlots = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            crystalSlots.add(crystals.getStackInSlot(i));
        }
        TerminalArcaneCraftingInput input = new TerminalArcaneCraftingInput(
                cells,
                server,
                wandInventory.getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT),
                crystalSlots,
                part,
                auraPayer,
                hasVisConnectionCard(),
                crystalFirst);
        var inspection = ArcaneCraftingTransaction.inspect(
                ArcaneWorkbenchContext.virtual(
                        server, PartArcaneCraftingTerminal.CONTEXT_HOST, server.getUUID()),
                server,
                input);
        if (!inspection.successful() || inspection.requirements() == null) {
            return null;
        }
        return inspection.requirements().crystals();
    }

    /** 六个槽里这个要素一共有几颗。 */
    private int countCrystals(Holder<IAspect> aspect) {
        int total = 0;
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            ItemStack stack = crystals.getStackInSlot(i);
            Holder<IAspect> carried = EssentiaCrystals.aspectOf(stack);
            if (carried != null && carried.equals(aspect)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 绑这个要素的晶体槽；没有就是 -1。 */
    private int crystalSlotFor(@Nullable Holder<IAspect> aspect) {
        if (aspect == null) {
            return -1;
        }
        ResourceKey<IAspect> key = aspect.unwrapKey().orElse(null);
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            if (key.equals(aspectOf(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 把这次合成的开销发给屏幕。
     * @param discountPercent 灵气减免百分比，0 表示未聚焦；屏幕据此在「（未聚焦）」和「（n% 减免）」之间选
     */
    public void sendCraftCost(@Nullable ArcaneCraftCost cost, int discountPercent) {
        if (isClientSide()) {
            return;
        }
        sendPacketToClient(cost == null
                ? ArcaneCraftCostPayload.none(containerId)
                : ArcaneCraftCostPayload.of(
                        containerId,
                        cost.wandCentivis(),
                        cost.crystalsNeeded(),
                        cost.auraVis(),
                        discountPercent));
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
