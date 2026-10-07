package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModItems;

/**
 * 菜单交给界面的按钮输入：核心槽位和机器的状态，经数据槽位镜像，
 * 客户端读到的是服务端最后解析出的结果。
 * 状态取自机器自身；唯一例外是不许存该配方的玩家，只有读数能看到，
 * 这里就问读数，不问机器。
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
     * 不看配方，菜单到底能不能编码。客户端读同步的数据槽位：自己的槽位副本不总是可靠。
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
     * 按钮是删除不是存储时返回 true。这不是玩家挑的模式：
     * 解析不出来的网格就是 Invalid，核心存了多少样板都一样。
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
