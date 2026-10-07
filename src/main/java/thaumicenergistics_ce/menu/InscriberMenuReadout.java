package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModItems;

/**
 * 菜单交给界面的按钮输入：核心槽位与机器的状态，
 * 经数据槽位镜像，所以客户端读到的是服务端最后解析出的结果。
 * 状态取自机器自身，唯一的例外是不被允许存储该配方的玩家，这一点只有
 * 读数能看到，所以菜单在这里询问而不是去问机器。
 */
final class InscriberMenuReadout {

    private final MenuKnowledgeInscriber menu;

    private final ContainerData data;

    InscriberMenuReadout(MenuKnowledgeInscriber menu, Inventory playerInventory) {
        this.menu = menu;
        this.data = new KnowledgeInscriberReadings(menu.inscriber, playerInventory.player, () ->
                menu.slotStack(MenuKnowledgeInscriber.IDX_CORE).is(ModItems.KNOWLEDGE_CORE.get()) ? 1 : 0);
    }

    ContainerData data() {
        return data;
    }

    boolean hasCore() {
        return data.get(MenuKnowledgeInscriber.DATA_HAS_CORE) != 0;
    }

    /**
     * 无论配方如何，菜单究竟能否编码。客户端读取同步的数据槽位，
     * 因为它自己的槽位副本并不总是被可靠地填充。
     */
    boolean canEncode() {
        if (menu.inscriber == null) {
            return hasCore();
        }
        return menu.slotStack(MenuKnowledgeInscriber.IDX_CORE).is(ModItems.KNOWLEDGE_CORE.get());
    }

    int buttonState() {
        if (data.get(MenuKnowledgeInscriber.DATA_HAS_CORE) == 0) {
            return BlockEntityKnowledgeInscriber.STATUS_READY;
        }
        return data.get(MenuKnowledgeInscriber.DATA_STATE);
    }

    /**
     * 按钮将删除而非存储时返回 true，这不是玩家挑选的模式：解析不出任何东西的
     * 网格就是 Invalid，无论核心持有多少样板。
     */
    boolean isDelete() {
        return data.get(MenuKnowledgeInscriber.DATA_HAS_CORE) != 0
                && data.get(MenuKnowledgeInscriber.DATA_STATE) == BlockEntityKnowledgeInscriber.STATUS_ALREADY_STORED;
    }

    boolean isActionable() {
        if (isDelete()) {
            return true;
        }
        return data.get(MenuKnowledgeInscriber.DATA_HAS_CORE) != 0
                && data.get(MenuKnowledgeInscriber.DATA_STATE) == BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE;
    }
}
