package thaumicenergistics_ce.menu;

import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.ITerminalHost;
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
import thaumicenergistics_ce.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.menu.slot.WandSlot;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 奥术合成终端的菜单，来自放置的部件或配对的物品。
 * 九个合成单元、一个结果槽、六个侧面槽（水晶按两份计）、一个法杖槽。
 * 结构照抄 AE2 的 {@code CraftingTermMenu}：[CraftingRecipe] 匹配不上奥术配方。
 * 源质手势来自 {@link MenuEssentiaTerminalBase}，要访问卡。
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

    /** 只有无线终端有：自己买灵气，买到的就是玩家周围的灵气。 */
    private final @Nullable IEnergySource auraPayer;

    private final AppEngInternalInventory resultInventory =
            new AppEngInternalInventory(this, 1);

    private @Nullable ArcaneCraftingResultSlot resultSlot;

    private int craftInputSignature = -1;

    /** 上次说「替身槽位不收东西」的时刻。初值 -100 让第一次点击就能出声，之后两秒一次。 */
    private int lastPlaceholderRefusal = -100;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.part = host instanceof ArcaneTerminalHost arcane ? arcane.arcaneTerminal() : null;
        this.auraPayer = host instanceof IPortableTerminal portable ? portable : null;
        this.craftingGrid = part == null ? gridFallback : part.craftingGrid();
        this.wandInventory = part == null ? wandFallback : part.wandInventory();
        this.crystals = part == null ? crystalFallback : part.crystalInventory();

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
    }


    @Override
    public IGridNode getGridNode() {
        return part == null ? null : part.getMainNode().getNode();
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
        return 31 * hash + StackSignatures.of(wandInventory.getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT));
    }

    public @Nullable PartArcaneCraftingTerminal part() {
        return part;
    }

    public @Nullable ArcaneCraftingResultSlot resultSlot() {
        return resultSlot;
    }

    /** 无线终端的电池；放置式终端为 {@code null}，它的 vis 找网络买。 */
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

    public void sendCraftCost(@Nullable ArcaneCraftCost cost) {
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
        return getHost().getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get());
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
