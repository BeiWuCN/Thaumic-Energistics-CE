package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

/**
 * 结果凹槽和 7x3 样板凹槽，两个都是只读的，显示什么由绘制它的那一侧决定：
 * 结果来自网格，样板槽来自核心物品。
 * 两者都在变化时重算，键是签名加游戏 tick；读不到该物品的那一侧两个都不填，
 * 菜单才不替两侧一次性推导。
 */
final class InscriberPreview {

    private final MenuKnowledgeInscriber menu;

    private final InscriberGridState grid;

    private final SimpleContainer result = new SimpleContainer(1);

    private final SimpleContainer mirrors =
            new SimpleContainer(BlockEntityKnowledgeInscriber.MIRROR_SLOT_COUNT);

    private int mirroredCore = -1;

    private int sampledCore = -1;
    private long coreSignatureTick = Long.MIN_VALUE;

    private int previewedSignature = -1;

    InscriberPreview(MenuKnowledgeInscriber menu, InscriberGridState grid) {
        this.menu = menu;
        this.grid = grid;
    }

    /** 结果凹槽的容器：解析出的样板产物，或空。 */
    SimpleContainer well() {
        return result;
    }

    SimpleContainer mirrors() {
        return mirrors;
    }

    /**
     * 网格当前状态的结果。解析<b>只在服务端</b>做：26.1.2 把配方访问收成
     * {@code Level.recipeAccess()}（只剩属性集与切石机），完整配方表只有服务端有，
     * 客户端取 [Level#getServer] 是 null，硬解析就是崩溃。
     * 结果凹槽是菜单槽位，服务端写进去的东西由原版同步过来，
     * 客户端画的就是同步到的这一份，所以这一侧直接空转。
     */
    void update() {
        Level level = menu.level();
        if (level == null || level.isClientSide()) {
            return;
        }
        int signature = grid.signature();
        if (signature == previewedSignature) {
            return;
        }
        previewedSignature = signature;
        List<ItemStack> cells = grid.cells();
        if (ThEArcanePattern.isGridEmpty(cells)) {
            result.setItem(0, ItemStack.EMPTY);
            return;
        }
        ThEArcanePattern pattern = ThEArcanePattern.resolveGrid(level, cells);
        result.setItem(0, pattern == null ? ItemStack.EMPTY : pattern.result());
    }

    /**
     * 从核心填 7x3 凹槽，每帧都查、只在变化时写：
     * 它们是只读槽位，显示什么由绘制它的那一侧写。
     */
    void refreshMirrors() {
        ItemStack core = menu.slotStack(MenuKnowledgeInscriber.IDX_CORE);
        // 键用 int 不用字符串：旧的 getItem() + '|' + getComponentsPatch()
        // 每秒六十次重新序列化核心存的整个样板列表。
        long now = menu.playerInventory.player.level().getGameTime();
        if (now != coreSignatureTick) {
            coreSignatureTick = now;
            sampledCore = StackSignatures.of(core);
        }
        if (sampledCore == mirroredCore) {
            return;
        }
        mirroredCore = sampledCore;
        HandlerKnowledgeCore handler = menu.handler();
        List<ItemStack> outputs = handler == null ? List.of() : handler.storedOutputs();
        for (int i = 0; i < MenuKnowledgeInscriber.PATTERN_SLOTS; i++) {
            ItemStack wanted = i < outputs.size() ? outputs.get(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(mirrors.getItem(i), wanted)) {
                mirrors.setItem(i, wanted.copy());
            }
        }
    }
}
