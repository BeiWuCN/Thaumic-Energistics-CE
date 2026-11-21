package com.beiwu.thaumicenergistics_ce.menu;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.ITerminalHost;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.PatternTermSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import com.beiwu.thaumicenergistics_ce.item.ItemKnowledgeCore;
import com.beiwu.thaumicenergistics_ce.network.InscriberCostPayload;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneEncodingTerminal;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;

/**
 * ME 奥术编码终端的菜单。
 * 没有编码模式：网格只按奥术合成解析，编码结果写进知识核心，不产出样板物品。
 * 知识核心里的样板以只读槽位常驻在右侧，点击选中；网格为空时按编码按钮删除选中项。
 * 成本模型与知识编码器一致：样板自带 vis 与晶体需求，编码本身不额外扣费。
 */
public class MenuArcaneEncodingTerminal extends MenuEssentiaTerminalBase implements InternalInventoryHost {

    public static final int GRID_SIZE = PartArcaneEncodingTerminal.GRID_SIZE;

    public static final int PATTERN_COLUMNS = 3;

    public static final int PATTERN_ROWS = 7;

    public static final int PATTERN_SLOTS = PATTERN_COLUMNS * PATTERN_ROWS;

    /** 知识核心的 21 个样板槽，只读展示，点击选中。 */
    public static final SlotSemantic CORE_PATTERNS =
            SlotSemantics.register("THAUMICENERGISTICS_CORE_PATTERNS", true);

    /** 客户端动作：编码网格，或在网格为空时删除选中样板。 */
    private static final String ACTION_ENCODE = "encode";

    /** 客户端动作：清空编码网格。 */
    private static final String ACTION_CLEAR = "clear";

    /** 客户端动作：选中核心里的某个样板，-1 取消选中。 */
    private static final String ACTION_SELECT = "selectPattern";

    /** 客户端动作：切换「允许替代品」。 */
    private static final String ACTION_SET_SUBSTITUTION = "setSubstitution";

    /** 客户端动作：切换「允许流体替代」。 */
    private static final String ACTION_SET_FLUID_SUBSTITUTION = "setFluidSubstitution";

    public static final int STATUS_READY = 0;
    public static final int STATUS_ENCODED = 1;
    public static final int STATUS_NO_RECIPE = 2;
    public static final int STATUS_CORE_FULL = 3;
    public static final int STATUS_ALREADY_STORED = 4;
    public static final int STATUS_NO_CORE = 6;
    public static final int STATUS_GRID_EMPTY = 7;
    /** 与知识编码器同值：状态码是对外协议，不要各写一套。 */
    public static final int STATUS_RESEARCH_LOCKED = 5;

    /** 网格、核心与选中项的宿主：线缆部件或无线终端物品。 */
    private final @Nullable ArcaneEncodingStorage storage;

    private final AppEngInternalInventory gridFallback = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory coreFallback = new AppEngInternalInventory(this, 1);

    private final InternalInventory grid;

    private final InternalInventory core;

    /** 核心样板镜像：服务端每帧按核心内容刷新，客户端只负责画。 */
    private final AppEngInternalInventory patterns = new AppEngInternalInventory(this, PATTERN_SLOTS);

    private final List<FakeSlot> gridSlots = new ArrayList<>(GRID_SIZE);

    /** 配方产物预览。和 AE2 合成编码页一样是只读展示槽，内容由菜单自己算。 */
    private final PatternTermSlot resultSlot;

    /** 上一次算预览时的网格签名，网格没动就不重翻配方表。 */
    private int lastPreviewSignature = Integer.MIN_VALUE;

    @appeng.menu.guisync.GuiSync(300)
    public int selectedPattern = -1;

    @appeng.menu.guisync.GuiSync(301)
    public int status = STATUS_READY;

    @appeng.menu.guisync.GuiSync(302)
    public boolean hasCore;

    @appeng.menu.guisync.GuiSync(303)
    public int storedPatterns;

    /**
     * 编码时是否把配方材料里的「任意成员」录进样板。关掉的话样板只认它显示的那一个物品。
     * 和 AE2 编码终端一样是本次打开界面的状态，不写进零件。
     */
    @appeng.menu.guisync.GuiSync(304)
    public boolean substitution = true;

    /** 桶装输入能不能用网络里的流体顶替。编进样板，因为它要用在合成那一步。 */
    @appeng.menu.guisync.GuiSync(305)
    public boolean fluidSubstitution = true;

    private int lastMirroredCore = -1;

    public MenuArcaneEncodingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
        this.storage = host instanceof ArcaneEncodingStorage found ? found : null;
        this.grid = storage == null ? gridFallback : storage.encodingGrid();
        this.core = storage == null ? coreFallback : storage.encodingCore();

        Player owner = playerInventory.player;
        IAEItemFilter placeholdersTakeNothing = new IAEItemFilter() {
            @Override
            public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                refusePlaceholderInsert(owner);
                return false;
            }
        };
        gridFallback.setFilter(placeholdersTakeNothing);
        coreFallback.setFilter(placeholdersTakeNothing);

        for (int i = 0; i < GRID_SIZE; i++) {
            FakeSlot slot = new FakeSlot(grid, i);
            slot.setHideAmount(true);
            gridSlots.add((FakeSlot) addSlot(slot, SlotSemantics.CRAFTING_GRID));
        }

        // 产物预览：AE2 的合成编码页把结果画在箭头右边那个框里，位置来自 json 的
        // CRAFTING_RESULT（left 106 / bottom 140），正好在 Vis 读数下面。
        this.resultSlot = new PatternTermSlot();
        addSlot(resultSlot, SlotSemantics.CRAFTING_RESULT);

        AppEngSlot coreSlot = new AppEngSlot(core, PartArcaneEncodingTerminal.CORE_SLOT) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(TECEItems.KNOWLEDGE_CORE.get());
            }
        };
        addSlot(coreSlot, SlotSemantics.STORAGE);

        for (int i = 0; i < PATTERN_SLOTS; i++) {
            AppEngSlot slot = new AppEngSlot(patterns, i) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            };
            addSlot(slot, CORE_PATTERNS);
        }

        registerClientAction(ACTION_ENCODE, this::encode);
        registerClientAction(ACTION_CLEAR, this::clearGrid);
        registerClientAction(ACTION_SELECT, Integer.class, this::selectPattern);
        registerClientAction(ACTION_SET_SUBSTITUTION, Boolean.class, this::setSubstitution);
        registerClientAction(ACTION_SET_FLUID_SUBSTITUTION, Boolean.class, this::setFluidSubstitution);
    }

    public InternalInventory gridInventory() {
        return grid;
    }

    public InternalInventory coreInventory() {
        return core;
    }

    public List<Slot> gridSlots() {
        return new ArrayList<>(getSlots(SlotSemantics.CRAFTING_GRID));
    }

    public List<Slot> patternSlots() {
        return getSlots(CORE_PATTERNS);
    }

    /**
     * 编码按钮。网格有内容时解析成奥术样板存进核心；网格为空且选中了核心里的样板时，
     * 把那条样板删掉。删除只在网格为空时发生，避免一次点击同时做两件事。
     */
    public void encode() {
        if (isClientSide()) {
            sendClientAction(ACTION_ENCODE);
            return;
        }
        Level level = filledLevel();
        if (level == null || storage == null) {
            status = STATUS_READY;
            return;
        }
        HandlerKnowledgeCore handler = coreHandler(level);
        if (handler == null) {
            status = STATUS_NO_CORE;
            return;
        }
        List<ItemStack> cells = gridCells();
        if (ThEArcanePattern.isGridEmpty(cells)) {
            int selected = storage.selectedEncodingPattern();
            var stored = handler.patterns();
            if (selected < 0 || selected >= stored.size()) {
                status = STATUS_GRID_EMPTY;
                return;
            }
            if (!handler.removeByResult(stored.get(selected).result())) {
                status = STATUS_GRID_EMPTY;
                return;
            }
            storage.setSelectedEncodingPattern(-1);
            status = STATUS_ENCODED;
            broadcastChanges();
            return;
        }
        ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, cells);
        if (resolved == null) {
            status = STATUS_NO_RECIPE;
            return;
        }
        // 两个开关只影响这一次编码的结果：关掉替代品就把格位标签抹掉，
        // 流体替代则跟着样板存下去，由组装机在抽取原料时决定用不用。
        ThEArcanePattern pattern = resolved.withEncodingFlags(substitution, fluidSubstitution);
        // 研究门槛与知识编码器同一套：样板能存，前提是玩家解锁了产出它的研究。
        if (getPlayer() instanceof net.minecraft.server.level.ServerPlayer server
                && !pattern.gate()
                        .map(gate -> com.leclowndu93150.thaumaturge.api.recipe.ResearchGate.passes(server, gate))
                        .orElse(true)) {
            status = STATUS_RESEARCH_LOCKED;
            return;
        }
        if (handler.patternFor(pattern.result()) != null) {
            status = STATUS_ALREADY_STORED;
            return;
        }
        if (!handler.store(pattern)) {
            status = STATUS_CORE_FULL;
            return;
        }
        for (int i = 0; i < GRID_SIZE; i++) {
            grid.setItemDirect(i, ItemStack.EMPTY);
        }
        status = STATUS_ENCODED;
        broadcastChanges();
    }

    /**
     * 清空编码网格，对应 AE2 合成编码页上的那个小叉按钮。
     * 网格是模板格：FakeSlot 不会真的把物品从玩家身上扣走，所以这里只抹掉"要编什么"，
     * 不往背包里塞东西——照抄合成终端的退回逻辑会凭空复制物品。
     */
    public void clearGrid() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }
        boolean changed = false;
        for (int i = 0; i < GRID_SIZE; i++) {
            if (!grid.getStackInSlot(i).isEmpty()) {
                grid.setItemDirect(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        status = STATUS_READY;
        if (changed) {
            broadcastChanges();
        }
    }

    /**
     * JEI 转移过来的九格模板。只写网格，不搬运真实物品：
     * 编码网格记录的是"要编什么"，网络与背包里的库存不在这里扣。
     */
    public void applyEncodedGrid(List<ItemStack> cells) {
        if (isClientSide()) {
            return;
        }
        for (int i = 0; i < GRID_SIZE && i < cells.size(); i++) {
            grid.setItemDirect(i, cells.get(i));
        }
        status = STATUS_READY;
        broadcastChanges();
    }

    /** 界面上的「允许替代品」开关；客户端只发动作，值以服务端为准。 */
    public void setSubstitution(boolean value) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_SUBSTITUTION, value);
            return;
        }
        substitution = value;
    }

    /** 界面上的「允许流体替代」开关。 */
    public void setFluidSubstitution(boolean value) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_FLUID_SUBSTITUTION, value);
            return;
        }
        fluidSubstitution = value;
    }

    public void selectPattern(int index) {
        if (isClientSide()) {
            sendClientAction(ACTION_SELECT, index);
            return;
        }
        if (storage == null) {
            return;
        }
        var stored = coreHandler(filledLevel());
        int size = stored == null ? 0 : stored.size();
        storage.setSelectedEncodingPattern(index >= 0 && index < size ? index : -1);
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (!isClientSide()) {
            refreshState();
            refreshMirrors();
        }
        super.broadcastChanges();
    }

    /**
     * 源质行的可见性与交互性是两件事：可见性默认就开（见 {@code EssentiaVisibility}），
     * 交互性只看升级槽里有没有卡。没卡的终端照样看得到源质，只是右键存不进去。
     */
    @Override
    protected boolean essentiaAccessGranted() {
        // 玩家看到的就是宿主自己的升级栏；AE2 菜单按它构建。
        return getHost().getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get());
    }

    /** 屏幕问它只为隐藏手势；服务端在动东西之前会再问一次。 */
    public boolean hasEssentiaAccessCard() {
        return essentiaAccessGranted();
    }

    /**
     * 网格内容被服务端同步下来时重算产物预览。{@link #resultSlot} 是
     * {@code PatternTermSlot}，只活在客户端：服务端算出来的东西没有同步通道，
     * 而且服务端每 tick 翻一次配方表也不该做。
     */
    @Override
    public void setItem(int slotId, int stateId, ItemStack stack) {
        super.setItem(slotId, stateId, stack);
        refreshPreview();
    }

    @Override
    public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
        super.initializeContents(stateId, items, carried);
        refreshPreview();
    }

    /**
     * 产物预览跟着网格走：格子里摆出能解析的奥术配方就显示产物，否则空着。
     * 只在客户端算，且网格没动就不重翻配方表。
     */
    private void refreshPreview() {
        if (!isClientSide()) {
            return;
        }
        int signature = gridSignature();
        if (signature == lastPreviewSignature) {
            return;
        }
        lastPreviewSignature = signature;
        if (ThEArcanePattern.isGridEmpty(gridCells())) {
            resultSlot.setResultItem(ItemStack.EMPTY);
            return;
        }
        ThEArcanePattern pattern = currentPattern();
        resultSlot.setResultItem(pattern == null ? ItemStack.EMPTY : pattern.result().copy());
    }

    /** 九格内容的一把哈希：格位、物品与数量都算进去。 */
    private int gridSignature() {
        int hash = 1;
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = grid.getStackInSlot(i);
            int cell = stack.isEmpty() ? 0 : stack.copyWithCount(1).hashCode() * 31 + stack.getCount();
            hash = hash * 31 + cell;
        }
        return hash;
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (!isClientSide()) {
            // 网格一改，上一次的结果就过期了。不复位的话 NO_RECIPE 会把按钮锁死：
            // 按钮禁用后点不动，状态也就永远回不到 READY。
            status = STATUS_READY;
            refreshState();
        }
    }

    /** 把核心里的样板抄进只读槽位；只在核心内容变化时写。 */
    private void refreshMirrors() {
        ItemStack coreStack = core.getStackInSlot(PartArcaneEncodingTerminal.CORE_SLOT);
        int signature = coreStack.isEmpty() ? 0 : coreStack.copyWithCount(1).hashCode() * 31 + coreStack.getComponents().hashCode();
        if (signature == lastMirroredCore) {
            return;
        }
        lastMirroredCore = signature;
        HandlerKnowledgeCore handler = coreHandler(filledLevel());
        List<ItemStack> outputs = handler == null ? List.of() : handler.storedOutputs();
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            ItemStack wanted = i < outputs.size() ? outputs.get(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(patterns.getStackInSlot(i), wanted)) {
                patterns.setItemDirect(i, wanted.copy());
            }
        }
    }

    private void refreshState() {
        HandlerKnowledgeCore handler = coreHandler(filledLevel());
        hasCore = handler != null;
        storedPatterns = handler == null ? 0 : handler.size();
        if (storage != null && storage.selectedEncodingPattern() >= storedPatterns) {
            storage.setSelectedEncodingPattern(-1);
        }
        selectedPattern = storage == null ? -1 : storage.selectedEncodingPattern();
    }

    private @Nullable HandlerKnowledgeCore coreHandler(@Nullable Level level) {
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(core.getStackInSlot(PartArcaneEncodingTerminal.CORE_SLOT), level.registryAccess());
    }

    private @Nullable Level filledLevel() {
        return getPlayer().level();
    }

    private List<ItemStack> gridCells() {
        List<ItemStack> cells = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            cells.add(grid.getStackInSlot(i));
        }
        return cells;
    }

    /** 当前网格解析出的成本；没有配方时为 null。 */
    public @Nullable ThEArcanePattern currentPattern() {
        Level level = filledLevel();
        if (level == null) {
            return null;
        }
        List<ItemStack> cells = gridCells();
        return ThEArcanePattern.isGridEmpty(cells) ? null : ThEArcanePattern.resolveGrid(level, cells);
    }

    /** 编码网格是否为空；空网格下按钮改为删除选中样板。 */
    public boolean gridEmpty() {
        return ThEArcanePattern.isGridEmpty(gridCells());
    }

    private void refusePlaceholderInsert(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        player.displayClientMessage(
                Component.translatable("gui.thaumicenergistics_ce.arcane_terminal.not_linked"), true);
    }

    @Override
    public boolean stillValid(Player player) {
        return storage != null && super.stillValid(player);
    }

    /** 这些容器只服务菜单自身，网格与核心的存档由部件负责。 */
    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
    }
}
