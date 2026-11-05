package com.beiwu.thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import com.beiwu.thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import com.beiwu.thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

final class InscriberPreview {

    private final MenuKnowledgeInscriber menu;

    private final InscriberGridState grid;

    private final SimpleContainer result = new SimpleContainer(1);

    private final SimpleContainer mirrors =
            new SimpleContainer(BlockEntityKnowledgeInscriber.MIRROR_SLOT_COUNT);

    private int mirroredCore = -1;

    private int sampledCore = -1;

    private int previewedSignature = -1;

    InscriberPreview(MenuKnowledgeInscriber menu, InscriberGridState grid) {
        this.menu = menu;
        this.grid = grid;
    }

    SimpleContainer well() {
        return result;
    }

    SimpleContainer mirrors() {
        return mirrors;
    }

    void update() {
        Level level = menu.level();
        if (menu.inscriber == null || level == null || level.isClientSide()) {
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

    void refreshMirrors() {
        // The server owns these display slots. Client-side reconstruction races
        // incoming slot packets, especially after clearing or selecting a recipe.
        if (menu.inscriber == null) return;
        ItemStack core = menu.slotStack(MenuKnowledgeInscriber.IDX_CORE);
        sampledCore = StackSignatures.of(core);
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
