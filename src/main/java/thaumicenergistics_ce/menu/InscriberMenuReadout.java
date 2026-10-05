package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModItems;

/**
 * The button's inputs as the menu hands them to the screen: the core slot and the machine's status,
 * mirrored through the data slots, so the client reads what the server last resolved.
 * The status is the machine's own except for a player who may not store the recipe, which only the
 * readings can see, so the menu asks here rather than at the machine.
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
     * Whether the menu could encode at all, whatever the recipe. The client reads the synced data slot,
     * since its own slot copy is not reliably filled.
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
     * True when the button would delete rather than store, not a player-picked mode: a grid resolving to
     * nothing is Invalid however many patterns the core holds.
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
