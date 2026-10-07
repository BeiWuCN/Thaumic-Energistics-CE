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
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.menu.slot.ArcaneCraftingResultSlot;
import thaumicenergistics_ce.menu.slot.CrystalSlot;
import thaumicenergistics_ce.network.ArcaneCraftCostPayload;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 奥术合成终端的菜单，通过放置的部件或通过配对物品进入。
 * 它包含九个合成单元、一个结果槽、六个侧面槽（其中水晶按两份计）以及
 * 一个法杖槽，其结构仿照 AE2 的 {@code CraftingTermMenu}，因为 [CraftingRecipe] 永远
 * 匹配不上奥术配方。它的源质手势来自 {@link MenuEssentiaTerminalBase}，以
 * 访问卡为门槛。
 */
public class MenuArcaneCraftingTerminal extends MenuEssentiaTerminalBase
        implements ICraftingGridMenu, InternalInventoryHost {

    public static final int GRID_SIZE = PartArcaneCraftingTerminal.GRID_SIZE;


    public static final SlotSemantic CRYSTALS_LEFT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_LEFT", true);

    public static final SlotSemantic CRYSTALS_RIGHT =
            SlotSemantics.register("THAUMICENERGISTICS_CRYSTALS_RIGHT", true);

    // 取自屏幕样式，它把网格底部锚定在 26,158，并按三列断开。
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
     * 尚未解析出终端时，三个容器的替身：槽位始终存在，否则看不到
     * 所绑区块的客户端会构建出与服务端不同的菜单。
     */
    private final AppEngInternalInventory gridFallback = new AppEngInternalInventory(this, GRID_SIZE);

    private final AppEngInternalInventory wandFallback = new AppEngInternalInventory(this, 1);

    private final AppEngInternalInventory crystalFallback =
            new AppEngInternalInventory(this, PartArcaneCraftingTerminal.CRYSTAL_SLOTS);

    private final InternalInventory craftingGrid;

    private final InternalInventory wandInventory;

    private final InternalInventory crystals;

    /** 仅为无线终端设置：它自购灵气，而这份灵气就是其玩家周围的灵气。 */
    private final @Nullable IEnergySource auraPayer;

    private final AppEngInternalInventory resultInventory =
            new AppEngInternalInventory(this, 1);

    private @Nullable ArcaneCraftingResultSlot resultSlot;

    private int craftInputSignature = -1;

    public MenuArcaneCraftingTerminal(
            MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host, false);
        this.part = host instanceof ArcaneTerminalHost arcane ? arcane.arcaneTerminal() : null;
        this.auraPayer = host instanceof IPortableTerminal portable ? portable : null;
        this.craftingGrid = part == null ? gridFallback : part.craftingGrid();
        this.wandInventory = part == null ? wandFallback : part.wandInventory();
        this.crystals = part == null ? crystalFallback : part.crystalInventory();

        // 1. 工作台合成单元。
        for (int i = 0; i < GRID_SIZE; i++) {
            int column = i % GRID_COLS;
            int row = i / GRID_COLS;
            Slot cell = addSlot(new AppEngSlot(craftingGrid, i), SlotSemantics.CRAFTING_GRID);
            // AE2 会用屏幕样式替换这些值；仍先设置，以便在那之前读取槽位。
            cell.x = GRID_X + column * GRID_PITCH;
            cell.y = GRID_Y + row * GRID_PITCH;
        }

        // 2. 法杖槽。[STORAGE]：AE2 对工具没有语义；这个槽负责摆放和 shift 点击。
        Slot wandSlot = addSlot(
                new AppEngSlot(wandInventory, PartArcaneCraftingTerminal.WAND_SLOT),
                SlotSemantics.STORAGE);
        wandSlot.x = WAND_X;
        wandSlot.y = WAND_Y;

        // 3. 六个水晶槽，网格每侧竖排三个。每个都固定到 Thaumaturge 自己的
        // 元要素顺序中的一个，且网格中的水晶按两份计。见 [CrystalSlot]。
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

        // 4. 结果槽，在它所读取的存储、能量与网格之后。
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

        // 5. 玩家自己的槽位，最后添加且只加一次 —— 父类调用传入 [createPlayerSlots] = false。
        createPlayerInventorySlots(playerInventory);

        // 结果只填一次，这样上次留下的网格在打开时就能显示。
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

    /** 无线终端的电池；对放置式终端为 {@code null}，后者的 vis 由网络购买。 */
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
                : ArcaneCraftCostPayload.of(containerId, cost.wandCentivis()));
    }


    /** 终端物品自带升级槽里的那张卡就是全部权限；不读取其它任何东西。 */
    @Override
    protected boolean essentiaAccessGranted() {
        // AE2 的菜单是按宿主自己的升级物品栏构建的，所以这就是玩家看到的那个槽。
        return getHost().getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get());
    }

    /** 屏幕问这个只是为了隐藏手势；服务端在移动任何东西之前会再问一次。 */
    public boolean hasEssentiaAccessCard() {
        return essentiaAccessGranted();
    }

    /** vis 卡是否插在玩家打开的那个终端里：由宿主自己的槽位作答，因此包里
     * 的第二个终端不能替正在使用的那个说话。 */
    public boolean hasVisConnectionCard() {
        return TerminalAuraPayment.visConnectionInstalled(getHost());
    }

    /** 不持久化：结果由网格推导而来，保存下来的结果会比网格活得久。 */
    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        // 无需保存：结果由网格推导而来。
    }

    @Override
    public boolean isClientSide() {
        return super.isClientSide();
    }
}
