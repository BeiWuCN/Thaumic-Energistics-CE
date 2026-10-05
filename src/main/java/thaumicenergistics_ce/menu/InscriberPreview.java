package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

/**
 * The result well and the 7x3 pattern wells, both read-only, so only the side drawing them can decide
 * what they show: the result from the grid, the wells from the core item.
 * Each is recomputed on a change, keyed by a signature and the game tick, and both are unfilled on
 * the side that cannot read the item, which is why the menu never derives them once for both sides.
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

    /** The result well's container: the resolved pattern's output, or empty. */
    SimpleContainer well() {
        return result;
    }

    SimpleContainer mirrors() {
        return mirrors;
    }

    /** The result of the grid as it stands, resolved here on this side: what the well draws. */
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
     * Fills the 7x3 wells from the core, each frame but only on a change: they are read-only slots, so
     * only the side drawing them can write what they show.
     */
    void refreshMirrors() {
        ItemStack core = menu.slotStack(MenuKnowledgeInscriber.IDX_CORE);
        // An int key, not a string: the old getItem() + '|' + getComponentsPatch() reserialised the
        // core's whole stored pattern list sixty times a second.
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
