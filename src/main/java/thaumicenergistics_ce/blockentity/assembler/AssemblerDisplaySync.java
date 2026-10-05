package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.ClientSyncSend;
import thaumicenergistics_ce.util.ThELog;

/** The assembler's display and its network sync, split out of {@link BlockEntityArcaneAssembler}: what
 * the renderer and the menu draw, how often it goes out, and how an update tag goes out and comes back.
 * Same package, so it reaches the machine's state directly; the four overrides ({@code getUpdateTag},
 * {@code handleUpdateTag}, {@code onDataPacket}) stay on the block entity and delegate here.
 */
final class AssemblerDisplaySync {

    /** Throttle on the per-tick display pushes; walking a craft's progress one packet per tick is not
     * worth it, and the machine still reads as live at this rate. */
    private static final int UPDATE_INTERVAL = 4;

    /** The wire names. The first is spelled the same way by the Jade payload
     * ({@code ArcaneAssemblerProvider.TAG_DISCOUNT}); the sync self-test holds the two equal. */
    private static final String TAG_GEAR_DISCOUNT = "GearDiscount";
    /** The product the renderer previews: sent on a slower clock, so an absent key means "unchanged". */
    private static final String TAG_PREVIEW = "Preview";

    private final BlockEntityArcaneAssembler owner;

    private long lastUpdate;

    /** Renderer-only copy of the running craft's product, written from the update tag: the real one is
     * in {@link BlockEntityArcaneAssembler#TARGET_SLOT}. */
    private ItemStack previewStack = ItemStack.EMPTY;

    AssemblerDisplaySync(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    ItemStack previewStack() {
        return previewStack;
    }

    // ------------------------------------------------------------------
    // Write and read
    // ------------------------------------------------------------------

    /** Adds the synced half of the machine's state to {@code tag}: the craft, the vis pool, the gear
     * discount and - on a slower clock than the rest - the product the renderer previews. */
    void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        owner.craft.writeSync(tag);
        owner.vis.writeNbt(tag);
        tag.putInt(TAG_GEAR_DISCOUNT, owner.upgrades().gearDiscount());
        if (!owner.craft.isCrafting() || owner.craft.craftTicks() == 0 || owner.craft.craftTicks() % 100 == 0) {
            tag.put(TAG_PREVIEW, owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).saveOptional(registries));
        }
    }

    /** Applies an update tag on the client, the route a per-tick update takes. A packet lands here, its
     * default implementation ending in {@code loadAdditional}, which wiped the craft state. */
    void applySyncedState(CompoundTag tag, HolderLookup.Provider registries) {
        owner.suppressNotify = true;
        try {
            owner.craft.readSync(tag);
            owner.vis.readSync(tag);
            owner.upgrades().setGearDiscount(tag.getInt(TAG_GEAR_DISCOUNT));
            // A display: an absent key means "unchanged", the product going out on a slower clock.
            if (tag.contains(TAG_PREVIEW)) {
                previewStack = ItemStack.parseOptional(registries, tag.getCompound(TAG_PREVIEW));
            }
        } finally {
            owner.suppressNotify = false;
        }
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    /** Empties the target and preview slots - what the renderer and the menu draw - reporting only when
     * there was something to clear. */
    void clearDisplay(boolean report) {
        boolean hadAnything =
                !owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).isEmpty();
        owner.suppressNotify = true;
        try {
            owner.inventory.setItem(BlockEntityArcaneAssembler.TARGET_SLOT, ItemStack.EMPTY);
            for (int i = 0; i < BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT; i++) {
                if (!owner.inventory.getItem(BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i).isEmpty()) {
                    hadAnything = true;
                }
                owner.inventory.setItem(BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i, ItemStack.EMPTY);
            }
        } finally {
            owner.suppressNotify = false;
        }
        if (report && hadAnything) {
            ThELog.LOG.info(
                    "[assembler] at {} cleared a leftover craft display: nothing is crafting", owner.blockPos());
        }
    }

    // ------------------------------------------------------------------
    // Display bands
    // ------------------------------------------------------------------

    /** The bands the machine writes for itself: the pattern mirror, the target well and the preview
     * grid. None of it was ever a player's item - they hold copies - so none of it is dropped. */
    static boolean isMachineOwned(int slot) {
        return slot >= BlockEntityArcaneAssembler.PATTERN_SLOT_START
                        && slot < BlockEntityArcaneAssembler.GEAR_SLOT_START
                || slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                        && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START;
    }

    /** The part of that display a player may never put an item into: the target well and the preview
     * grid, both of which the machine overwrites from the running craft. */
    static boolean isDisplaySlot(int slot) {
        return slot == BlockEntityArcaneAssembler.TARGET_SLOT
                || slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                        && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START;
    }

    /** Writes the running craft's display - the product into the target well, the 3x3 into the preview
     * grid - behind the notify guard: without it the container's listener takes every write for a
     * player changing the machine, and rebuilds the pattern list off the core each time. The grid
     * exists here, on the server, since the running craft does; the client is sent a copy. */
    void refreshDisplaySlots(ItemStack target, List<ItemStack> grid) {
        owner.suppressNotify = true;
        try {
            owner.inventory.setItem(BlockEntityArcaneAssembler.TARGET_SLOT, target);
            for (int i = 0; i < BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT; i++) {
                ItemStack cell = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
                owner.inventory.setItem(
                        BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i,
                        cell.isEmpty() ? ItemStack.EMPTY : cell.copy());
            }
        } finally {
            owner.suppressNotify = false;
        }
    }

    /** Rewrites the pattern slots from the advertised set: the mirror a player reads, not a real
     * inventory. */
    void refreshPatternSlots() {
        if (owner.level() == null) {
            return;
        }
        if (owner.patternsDirty) {
            owner.patternsDirty = !owner.rebuildPatterns();
        }
        owner.suppressNotify = true;
        try {
            for (int i = 0; i < BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT; i++) {
                ItemStack stack = ItemStack.EMPTY;
                if (i < owner.cachedPatterns.size()) {
                    List<GenericStack> outputs = owner.cachedPatterns.get(i).getOutputs();
                    if (!outputs.isEmpty() && outputs.getFirst().what() instanceof AEItemKey key) {
                        stack = key.getReadOnlyStack();
                    }
                }
                owner.inventory.setItem(BlockEntityArcaneAssembler.PATTERN_SLOT_START + i, stack);
            }
        } finally {
            owner.suppressNotify = false;
        }
    }

    /** Pushes the display to the watching players, at most every {@link #UPDATE_INTERVAL} ticks. */
    void markDisplayForUpdate() {
        if (owner.level() == null) {
            return;
        }
        long now = owner.level().getGameTime();
        if (now - lastUpdate < UPDATE_INTERVAL) {
            return;
        }
        lastUpdate = now;
        markForUpdate();
    }

    void markForUpdate() {
        if (owner.level() == null) {
            return;
        }
        owner.setChanged();
        ClientSyncSend.sendBlockEntityUpdate(owner);
    }
}