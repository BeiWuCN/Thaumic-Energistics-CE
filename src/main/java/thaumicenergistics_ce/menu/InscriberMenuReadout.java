package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModItems;

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
