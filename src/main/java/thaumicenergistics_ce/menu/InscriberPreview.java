package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

/**
 * 结果凹槽与 7x3 样板凹槽，两者都是只读的，所以只有绘制它们的那一侧才能决定
 * 它们显示什么：结果来自网格，凹槽来自核心物品。
 * 两者都在发生变化时重算，以签名与游戏 tick 为键，而在无法读取该物品的
 * 那一侧两者都不填充，这就是菜单从不替两侧一次性推导它们的原因。
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

    /** 结果凹槽的容器：已解析样板的输出，或空。 */
    SimpleContainer well() {
        return result;
    }

    SimpleContainer mirrors() {
        return mirrors;
    }

    /** 网格当前状态的结果，在本侧解析：这就是凹槽所绘制的内容。 */
    void update() {
        int signature = grid.signature();
        if (signature == previewedSignature) {
            return;
        }
        previewedSignature = signature;
        Level level = menu.level();
        List<ItemStack> cells = grid.cells();
        if (level == null || ThEArcanePattern.isGridEmpty(cells)) {
            result.setItem(0, ItemStack.EMPTY);
            return;
        }
        ThEArcanePattern pattern = ThEArcanePattern.resolveGrid(level, cells);
        result.setItem(0, pattern == null ? ItemStack.EMPTY : pattern.result());
    }

    /**
     * 从核心填充 7x3 凹槽，每帧都做但只在发生变化时：它们是只读槽位，所以
     * 只有绘制它们的那一侧才能写入它们显示的内容。
     */
    void refreshMirrors() {
        ItemStack core = menu.slotStack(MenuKnowledgeInscriber.IDX_CORE);
        // 用 int 作键而不是字符串：旧的 getItem() + '|' + getComponentsPatch() 会每秒
        // 六十次重新序列化核心存储的整个样板列表。
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
