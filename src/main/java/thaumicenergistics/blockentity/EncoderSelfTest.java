package thaumicenergistics.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.init.ModBlocks;

/**
 * Round-trips the Distillation Encoder's slots through NBT and checks that no pattern moves into the source
 * well.
 *
 * <p>This exists because of a bug that destroyed items with nothing on screen to show for it: the save wrote a
 * compact list of the non-empty slots with no index on any entry, the load read that list by position, and the
 * source well is slot 0 - so with the source well empty every pattern came back one well earlier than it was
 * saved. The front one landed in the source well, which is a ghost slot the player cannot take anything out
 * of, and the next template written there discarded it. Nothing threw, nothing was logged, and the well looked
 * like it held an item because the items had moved.
 *
 * <p>So the checks below are written around the states that made it invisible: an empty source well, and both
 * pattern wells full. They assert on the stored format as well as on the round trip, because a loader that
 * agreed with a lossy writer would pass a round trip on its own.
 *
 * <p>Off unless {@code THAUMICENERGISTICS_ENCODER_SELFTEST=true}.
 */
public final class EncoderSelfTest {

    /** One run per server, not one per login. */
    private static boolean hasRun;

    private EncoderSelfTest() {}

    public static void run(PlayerEvent.PlayerLoggedInEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ENCODER_SELFTEST"))) {
            return;
        }
        if (hasRun) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        hasRun = true;

        List<String> failures = new ArrayList<>();
        checkTheSaveNamesItsSlots(level, failures);
        checkAPatternDoesNotMoveIntoTheSourceWell(level, failures);
        checkTheOldFormComesBackToTheRightWells(level, failures);
        report(failures);
    }

    /**
     * Asserts the written form itself: every entry has to name its slot, and the empty source well has to stay
     * unnamed. A round trip through the loader alone would pass for the wrong reason if the writer were ever
     * changed back to a bare list, since a bare list read by position is exactly what the two agree on.
     */
    private static void checkTheSaveNamesItsSlots(ServerLevel level, List<String> failures) {
        ItemStack written = writtenPattern();
        if (written.isEmpty()) {
            failures.add("AE2 would not encode a processing pattern, so the save format cannot be checked");
            return;
        }

        BlockEntityDistillationEncoder encoder = newEncoder(level);
        // The source well is left empty on purpose: it is the slot the old form could not record the absence of.
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, newBlankPattern());
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_ENCODED, written);

        CompoundTag tag = new CompoundTag();
        encoder.saveAdditional(tag, level.registryAccess());
        ListTag saved = tag.getList(ContainerHelper.TAG_ITEMS, Tag.TAG_COMPOUND);
        if (saved.size() != 2) {
            failures.add("the save holds " + saved.size() + " item entries for 2 non-empty wells");
            return;
        }
        boolean[] named = new boolean[BlockEntityDistillationEncoder.SLOT_COUNT];
        for (int entry = 0; entry < saved.size(); entry++) {
            CompoundTag cell = saved.getCompound(entry);
            if (!cell.contains("Slot")) {
                failures.add("saved entry " + entry + " names no slot, so a container whose source well is"
                        + " empty cannot be restored - every pattern comes back a well early");
                return;
            }
            int slot = cell.getByte("Slot");
            if (slot < 0 || slot >= BlockEntityDistillationEncoder.SLOT_COUNT) {
                failures.add("saved entry " + entry + " names slot " + slot + ", outside the "
                        + BlockEntityDistillationEncoder.SLOT_COUNT + " this build has");
                return;
            }
            named[slot] = true;
        }
        if (named[BlockEntityDistillationEncoder.SLOT_SOURCE]) {
            failures.add("the save names the source well, which was empty when it was written");
        }
        if (!named[BlockEntityDistillationEncoder.SLOT_BLANK] || !named[BlockEntityDistillationEncoder.SLOT_ENCODED]) {
            failures.add("the save does not name both pattern wells");
        }
    }

    /**
     * The bug end to end, driven through the machine's own {@code encode()} rather than a hand-built tag: a
     * written pattern in the pattern well, nothing in the source well - the state the owner reported finding
     * their patterns in - saved and loaded again.
     */
    private static void checkAPatternDoesNotMoveIntoTheSourceWell(ServerLevel level, List<String> failures) {
        ItemStack sourceItem = firstDistillableItem(level);
        if (sourceItem == null) {
            // Not a failure: a pack whose aspect index knows none of these items has nothing to encode, so
            // there is no state to drive. Reported so the silence reads as a skip.
            System.out.println("[encoder] no item with known aspects, so the encode path is unchecked");
            return;
        }

        BlockEntityDistillationEncoder encoder = newEncoder(level);
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, newBlankPattern());
        encoder.setSourceTemplate(sourceItem);
        if (encoder.availableAspects().isEmpty()) {
            failures.add("the aspect index offers nothing for " + sourceItem
                    + ", so a pattern cannot be written at all");
            return;
        }
        encoder.setSelectedAspect(0);
        if (!encoder.encode()) {
            failures.add("the encoder refused to write a pattern from " + sourceItem);
            return;
        }
        ItemStack written = encoder.getInventory()
                .getItem(BlockEntityDistillationEncoder.SLOT_ENCODED)
                .copy();
        if (written.isEmpty()) {
            failures.add("encode() reported success and left the written well empty");
            return;
        }
        // The blank was spent and the source template is cleared: exactly the state the owner described, with
        // patterns in the pattern wells and nothing in the source well.
        encoder.setSourceTemplate(ItemStack.EMPTY);

        BlockEntityDistillationEncoder reloaded = roundTrip(level, encoder);
        expectNoPatternInTheSourceWell(reloaded, failures);
        expectWell(failures, "the written pattern", written, reloaded, BlockEntityDistillationEncoder.SLOT_ENCODED);

        // And again with both pattern wells full, since the old form shifted the second one too: the blank
        // well holds a blank, the written well holds the pattern, the source well holds nothing.
        reloaded.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, newBlankPattern());
        BlockEntityDistillationEncoder twice = roundTrip(level, reloaded);
        expectNoPatternInTheSourceWell(twice, failures);
        expectWell(failures, "the blank pattern", newBlankPattern(), twice, BlockEntityDistillationEncoder.SLOT_BLANK);
        expectWell(failures, "the written pattern", written, twice, BlockEntityDistillationEncoder.SLOT_ENCODED);
    }

    /**
     * The migration: a world saved in the old form has to come back with its patterns in the pattern wells, not
     * positionally. The old form is produced here by the old writer itself - {@code SimpleContainer.createTag}
     * - so this is the shape a real world carries and not a guess at it.
     */
    private static void checkTheOldFormComesBackToTheRightWells(ServerLevel level, List<String> failures) {
        ItemStack written = writtenPattern();
        if (written.isEmpty()) {
            failures.add("AE2 would not encode a processing pattern, so the old form cannot be checked");
            return;
        }

        // What the old save held in the state the owner described: both pattern wells full, source well empty.
        BlockEntityDistillationEncoder old = newEncoder(level);
        old.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, newBlankPattern());
        old.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_ENCODED, written);

        BlockEntityDistillationEncoder reloaded = loadLegacy(level, old);
        expectNoPatternInTheSourceWell(reloaded, failures);
        expectWell(failures, "the blank pattern", newBlankPattern(), reloaded, BlockEntityDistillationEncoder.SLOT_BLANK);
        expectWell(failures, "the written pattern", written, reloaded, BlockEntityDistillationEncoder.SLOT_ENCODED);

        // And the world the bug had already moved: the pattern sat in the source well when it was saved, which
        // is the form a re-save of the shifted container writes. It belongs in the well it can be taken from.
        BlockEntityDistillationEncoder shifted = newEncoder(level);
        shifted.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_SOURCE, written);

        BlockEntityDistillationEncoder rescued = loadLegacy(level, shifted);
        expectNoPatternInTheSourceWell(rescued, failures);
        expectWell(failures, "the written pattern", written, rescued, BlockEntityDistillationEncoder.SLOT_ENCODED);
    }

    /** Saves a machine's own tag and reads it back into a fresh one. */
    private static BlockEntityDistillationEncoder roundTrip(
            ServerLevel level, BlockEntityDistillationEncoder from) {
        CompoundTag tag = new CompoundTag();
        from.saveAdditional(tag, level.registryAccess());
        BlockEntityDistillationEncoder reloaded = newEncoder(level);
        reloaded.loadAdditional(tag, level.registryAccess());
        return reloaded;
    }

    /** Saves a machine the way the pre-fix code did and reads that back into a fresh one. */
    private static BlockEntityDistillationEncoder loadLegacy(
            ServerLevel level, BlockEntityDistillationEncoder from) {
        CompoundTag legacy = new CompoundTag();
        // The old saveAdditional, verbatim: a bare list with no index on any entry.
        legacy.put("Inventory", from.getInventory().createTag(level.registryAccess()));
        BlockEntityDistillationEncoder reloaded = newEncoder(level);
        reloaded.loadAdditional(legacy, level.registryAccess());
        return reloaded;
    }

    /**
     * The heart of it: nothing that is a pattern may sit in the source well after a load. That well is a
     * template well - the player cannot take anything out of it and the next template written into it
     * discards whatever is there - so a pattern that lands in it is a pattern that will be destroyed.
     */
    private static void expectNoPatternInTheSourceWell(
            BlockEntityDistillationEncoder encoder, List<String> failures) {
        ItemStack source = encoder.getInventory().getItem(BlockEntityDistillationEncoder.SLOT_SOURCE);
        if (AEItems.BLANK_PATTERN.is(source) || PatternDetailsHelper.isEncodedPattern(source)) {
            failures.add("the source well came back holding " + describe(source)
                    + " - the saved slots are being read by position, and the next template written into the"
                    + " ghost well will discard it");
        }
    }

    private static void expectWell(
            List<String> failures,
            String what,
            ItemStack expected,
            BlockEntityDistillationEncoder encoder,
            int slot) {
        ItemStack actual = encoder.getInventory().getItem(slot);
        if (!ItemStack.matches(expected, actual)) {
            failures.add(what + " came back in well " + slot + " as " + describe(actual)
                    + " instead of " + describe(expected));
        }
    }

    private static BlockEntityDistillationEncoder newEncoder(ServerLevel level) {
        // Detached, like the other self-tests: only its level is set, and only because reading a tag needs the
        // registry access. Nothing is placed in the world.
        BlockEntityDistillationEncoder encoder = new BlockEntityDistillationEncoder(
                new BlockPos(0, -4096, 0), ModBlocks.DISTILLATION_ENCODER.get().defaultBlockState());
        encoder.setLevel(level);
        return encoder;
    }

    private static ItemStack newBlankPattern() {
        return AEItems.BLANK_PATTERN.stack();
    }

    /** A written AE pattern, of the kind a finished encode leaves in the written well. */
    private static ItemStack writtenPattern() {
        AEItemKey in = AEItemKey.of(new ItemStack(Items.STONE));
        AEItemKey out = AEItemKey.of(new ItemStack(Items.DIAMOND));
        if (in == null || out == null) {
            return ItemStack.EMPTY;
        }
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(in, 1)), List.of(new GenericStack(out, 1)));
    }

    /**
     * An item the aspect index has a composition for, or {@code null} when the pack has none of the few tried -
     * the machine offers nothing to distil without one, so the encode path cannot be driven at all.
     */
    private static @Nullable ItemStack firstDistillableItem(ServerLevel level) {
        var items = level.registryAccess().lookupOrThrow(Registries.ITEM);
        for (String id : new String[] {"minecraft:bone", "minecraft:stone", "minecraft:coal"}) {
            var holder = items.get(
                    ResourceKey.create(Registries.ITEM, ResourceLocation.parse(id)));
            if (holder.isEmpty()) {
                continue;
            }
            ItemStack stack = new ItemStack(holder.get().value());
            var composition = AspectIndexAccess.of(stack);
            if (composition == null || composition.isEmpty()) {
                continue;
            }
            for (var entry : composition.entries()) {
                if (entry.amount() > 0) {
                    return stack;
                }
            }
        }
        return null;
    }

    private static String describe(ItemStack stack) {
        return stack.isEmpty() ? "nothing" : stack.getCount() + "x " + stack.getItem();
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            System.out.println("[encoder] passed");
            return;
        }
        System.out.println("[encoder] FAILED (" + failures.size() + ")");
        for (String failure : failures) {
            System.out.println("[encoder]   - " + failure);
        }
    }
}
