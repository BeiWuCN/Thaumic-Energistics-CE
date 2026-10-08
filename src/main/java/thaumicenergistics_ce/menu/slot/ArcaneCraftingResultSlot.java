package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingInput;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingInput;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingStore;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

/**
 * 奥术合成终端的产物槽位。合成入口点 {@code doClick} 声明在 {@code CraftingTermSlot}。
 * {@code ArcaneCraftingTransaction} 匹配配方并扣费；{@link #refresh} 只预览不付费。
 * 支付来自终端自己的网格、水晶和法杖槽，从不向网络要材料，
 * 见 {@link TerminalArcaneCraftingStore}。
 */
public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    private final @Nullable ServerPlayer serverPlayer;
    private final @Nullable PartArcaneCraftingTerminal part;

    /** 用具体菜单类型，不写 {@link ICraftingGridMenu}：把 vis 开销发到屏幕靠它。 */
    private final thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal ownerMenu;

    /** 上次 {@link #refresh()} 没出产物的原因；没失败就是 {@code NONE}。 */
    private ArcaneCraftingTransaction.Failure lastFailure = ArcaneCraftingTransaction.Failure.NONE;

    public ArcaneCraftingResultSlot(
            Player player,
            IActionSource actionSource,
            IEnergySource energySource,
            MEStorage storage,
            InternalInventory craftingGrid,
            InternalInventory resultInventory,
            thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal ownerMenu,
            @Nullable PartArcaneCraftingTerminal part) {
        super(player, actionSource, energySource, storage, craftingGrid, resultInventory, ownerMenu);
        this.serverPlayer = player instanceof ServerPlayer server ? server : null;
        this.part = part;
        this.ownerMenu = ownerMenu;
    }

    @Override
    public boolean mayPickup(Player player) {
        // 每次取走都走 [doClick] 扣费；原版不能先把产物发出去。
        return false;
    }

    /** 变化时才重算，不每帧：一次完整配方匹配加一次开销计算。 */
    public void refresh() {
        if (part == null || serverPlayer == null) {
            return;
        }
        IArcaneCraftingInput input = buildInput();
        if (input == null) {
            setDisplayedCraftingOutput(ItemStack.EMPTY);
            ownerMenu.sendCraftCost(null);
            lastFailure = ArcaneCraftingTransaction.Failure.NONE;
            return;
        }
        var result = ArcaneCraftingTransaction.preview(workbenchContext(serverPlayer), serverPlayer, input);
        lastFailure = result.failure();
        if (!result.successful()) {
            ThELog.LOG.info("[arcane] no craft offered for the grid: {}", result.failure());
        }
        setDisplayedCraftingOutput(result.successful() ? result.output() : ItemStack.EMPTY);
        // 和结果一起发，屏幕就不会给已经变了的网格画开销。
        ownerMenu.sendCraftCost(result.successful() ? result.cost() : null);
    }

    public ArcaneCraftingTransaction.Failure lastFailure() {
        return lastFailure;
    }

    /**
     * 合成与承载它的事务。没出产物就什么都不留：存储、法杖的 vis、晶体都会退回，
     * 点一下没做完的合成不花玩家任何东西。
     */
    private ArcaneCraftingTransaction.Result craftCommitted(
            ServerPlayer server, IArcaneCraftingInput input, TerminalArcaneCraftingStore store) {
        try (Transaction transaction = Transaction.openRoot()) {
            var result = ArcaneCraftingTransaction.craft(workbenchContext(server), server, input, store, transaction);
            if (result.successful()) {
                transaction.commit();
            }
            return result;
        }
    }

    @Override
    public void doClick(InventoryAction action, Player who) {
        PartArcaneCraftingTerminal terminal = part;
        if (terminal == null || !(who instanceof ServerPlayer server)) {
            return;
        }
        int attempts = switch (action) {
            case CRAFT_SHIFT, CRAFT_ALL -> 64;
            default -> 1;
        };

        for (int i = 0; i < attempts; i++) {
            IArcaneCraftingInput input = buildInput();
            if (input == null || input.isEmpty()) {
                break;
            }
            // 支付来自终端自己的容器：网格、水晶槽和法杖槽，和 Thaumaturge 工作台一致。
            // 向网络收费等于再要一份玩家已经摆好的东西。
            var store = new TerminalArcaneCraftingStore(
                    terminal.craftingGrid(), terminal.crystalInventory(), terminal.wandInventory(), who);
            var result = craftCommitted(server, input, store);
            if (!result.successful()) {
                ThELog.LOG.info(
                        "[arcane] craft click refused: successful={} failure={}",
                        result.successful(), result.failure());
                break;
            }

            // 剩下的开销：网格、晶体和法杖已经由 store 扣过，剩余物也跟着它回到各自槽位。
            ItemStack output = result.output().copy();
            // 先读再交付：[Inventory#add] 会把数量改成没放进去的那部分。
            String produced = output.toString();
            boolean placed = deliver(output, action, who);
            ThELog.LOG.info(
                    "[arcane] craft click committed: produced={} placed={} cost={}",
                    produced,
                    placed,
                    result.cost());
            if (!placed) {
                break;
            }
            refresh();
        }
    }

    /** 普通点击把产物放到光标上，批量合成填满物品栏；提交时费用已扣。
     * @param action 手势，shift 点击按玩家预期填满物品栏
     * @return 产物去了光标还是物品栏；放不下为 {@code false} */
    private boolean deliver(ItemStack output, InventoryAction action, Player who) {
        if (output.isEmpty()) {
            return true;
        }
        boolean bulk = action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL;
        if (!bulk) {
            var carried = this.getMenu().getCarried();
            if (carried.isEmpty()) {
                this.getMenu().setCarried(output);
                return true;
            }
            if (ItemStack.isSameItemSameComponents(carried, output)
                    && carried.getCount() + output.getCount() <= carried.getMaxStackSize()) {
                carried.grow(output.getCount());
                return true;
            }
        }
        if (who.getInventory().add(output)) {
            return true;
        }
        // 费用已扣、产物已存在；调用方把这里读成「停」。
        who.drop(output.copy(), false);
        return false;
    }

    /** 按当前网格建输入，没有可匹配的返回 {@code null}。 */
    private @Nullable IArcaneCraftingInput buildInput() {
        if (part == null) {
            return null;
        }
        // 九个单元，空的也算：Thaumaturge 不管网格里有什么都按九个索引。
        // 列表不能裁剪，见 [TerminalArcaneCraftingInput]。
        List<ItemStack> cells = IntStream.range(0, PartArcaneCraftingTerminal.GRID_SIZE)
                .mapToObj(i -> part.craftingGrid().getStackInSlot(i))
                .toList();
        ItemStack wand = part.wandInventory().getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT);
        // 水晶只从自己的槽位取，不从网格取。
        // 网格单元里的水晶是匹配材料，再算一次支付就对不上了。
        List<ItemStack> crystals = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            crystals.add(part.crystalInventory().getStackInSlot(i));
        }
        // 卡片只在这里读一次：一次合成的两遍灵气处理都读这个冻结结果，
        // 不再问槽位，提交才不会和模拟对不上。
        boolean visConnection = ownerMenu.hasVisConnectionCard();
        return new TerminalArcaneCraftingInput(
                cells, serverPlayer, wand, crystals, part, ownerMenu.auraPayer(), visConnection);
    }

    /** 归这台机器和这名玩家的虚拟工作台：线缆上的终端没有方块可指。 */
    private ArcaneWorkbenchContext workbenchContext(ServerPlayer server) {
        return ArcaneWorkbenchContext.virtual(
                server, PartArcaneCraftingTerminal.CONTEXT_HOST, server.getUUID());
    }
}
