package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
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
        tag.putInt("GearDiscount", owner.upgrades().gearDiscount());
        if (!owner.craft.isCrafting() || owner.craft.craftTicks() == 0 || owner.craft.craftTicks() % 100 == 0) {
            tag.put("Preview", owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).saveOptional(registries));
        }
    }

    /** Applies an update tag on the client, the route a per-tick update takes. A packet lands here, its
     * default implementation ending in {@code loadAdditional}, which wiped the craft state. */
    void applySyncedState(CompoundTag tag, HolderLookup.Provider registries) {
        owner.suppressNotify = true;
        try {
            owner.craft.readSync(tag);
            owner.vis.readSync(tag);
            owner.upgrades().setGearDiscount(tag.getInt("GearDiscount"));
            // A display: an absent key means "unchanged", the product going out on a slower clock.
            if (tag.contains("Preview")) {
                previewStack = ItemStack.parseOptional(registries, tag.getCompound("Preview"));
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
        if (!(owner.level() instanceof ServerLevel server)) {
            return;
        }
        // Only the players watching this chunk, and one packet built once for all of them.
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(owner);
        for (ServerPlayer player :
                server.getChunkSource().chunkMap.getPlayers(new ChunkPos(owner.blockPos()), false)) {
            player.connection.send(packet);
        }
    }
}