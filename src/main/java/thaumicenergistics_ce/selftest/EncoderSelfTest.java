package thaumicenergistics_ce.selftest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.init.ModBlocks;

/**
 * Round-trips the Distillation Encoder's slots through NBT: no pattern may reach the source well.
 * <ul>
 *   <li>Written after a silent bug that destroyed items: entries named no slot and the load read them in
 *       order, but the source well came first, so an empty source well shifted every pattern one well early.
 *   <li>Off unless {@code THAUMICENERGISTICS_ENCODER_SELFTEST=true}; asserts on the stored format.
 * </ul>
 */
public final class EncoderSelfTest {

    private static boolean hasRun;

    /** The level to check on, armed by {@link #run}; null while nothing is pending. */
    private static ServerLevel pendingLevel;
    private static int waitedTicks;

    private EncoderSelfTest() {}

    /** Arms the battery from the first login; {@link #onServerTick} runs it once the aspect index answers. */
    public static void run(PlayerEvent.PlayerLoggedInEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_ENCODER_SELFTEST"))) {
            return;
        }
        if (hasRun || pendingLevel != null) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null) {
            return;
        }
        pendingLevel = level;
        waitedTicks = 0;
    }

    /** Runs the battery on the first tick with a published aspect index, or at the wait bound without one. */
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = pendingLevel;
        if (level == null) {
            return;
        }
        if (AspectIndexWait.keepWaiting(level, waitedTicks)) {
            waitedTicks++;
            return;
        }
        pendingLevel = null;
        hasRun = true;

        List<String> failures = new ArrayList<>();
        checkTheSaveNamesItsSlots(level, failures);
        checkAPatternDoesNotMoveIntoTheSourceWell(level, failures);
        checkTheOldFormComesBackToTheRightWells(level, failures);
        report(failures);
    }

    /** Asserts the written form itself: every entry must name its slot, and the empty source well must stay
     * unnamed. A round trip alone passes if the writer goes back to a bare list. */
    private static void checkTheSaveNamesItsSlots(ServerLevel level, List<String> failures) {
        ItemStack written = writtenPattern();
        if (written.isEmpty()) {
            failures.add("AE2 would not encode a processing pattern, so the save format cannot be checked");
            return;
        }

        BlockEntityDistillationEncoder encoder = newEncoder(level);
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, newBlankPattern());
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_ENCODED, written);

        CompoundTag tag = encoder.saveCustomOnly(level.registryAccess());
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

    /** The bug end to end, driven through the machine's own {@code encode()} rather than a hand-built tag: a
     * written pattern in the pattern well, nothing in the source well, saved and loaded again. */
    private static void checkAPatternDoesNotMoveIntoTheSourceWell(ServerLevel level, List<String> failures) {
        ItemStack sourceItem = firstDistillableItem(level);
        if (sourceItem == null) {
            // Not a failure: with no known aspects there is no state to drive, so the silence is a skip.
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
        encoder.setSourceTemplate(ItemStack.EMPTY);

        BlockEntityDistillationEncoder reloaded = roundTrip(level, encoder);
        expectNoPatternInTheSourceWell(reloaded, failures);
        expectWell(failures, "the written pattern", written, reloaded, BlockEntityDistillationEncoder.SLOT_ENCODED);

        // And again with both pattern wells full, since the old form shifted the second one too.
        reloaded.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, newBlankPattern());
        BlockEntityDistillationEncoder twice = roundTrip(level, reloaded);
        expectNoPatternInTheSourceWell(twice, failures);
        expectWell(failures, "the blank pattern", newBlankPattern(), twice, BlockEntityDistillationEncoder.SLOT_BLANK);
        expectWell(failures, "the written pattern", written, twice, BlockEntityDistillationEncoder.SLOT_ENCODED);
    }

    /** The migration: a world saved in the old form must come back with its patterns in the pattern wells,
     * positionally. The old form is produced by {@code SimpleContainer.createTag} itself, not guessed at. */
    private static void checkTheOldFormComesBackToTheRightWells(ServerLevel level, List<String> failures) {
        ItemStack written = writtenPattern();
        if (written.isEmpty()) {
            failures.add("AE2 would not encode a processing pattern, so the old form cannot be checked");
            return;
        }

        BlockEntityDistillationEncoder old = newEncoder(level);
        old.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, newBlankPattern());
        old.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_ENCODED, written);

        BlockEntityDistillationEncoder reloaded = loadLegacy(level, old);
        expectNoPatternInTheSourceWell(reloaded, failures);
        expectWell(failures, "the blank pattern", newBlankPattern(), reloaded, BlockEntityDistillationEncoder.SLOT_BLANK);
        expectWell(failures, "the written pattern", written, reloaded, BlockEntityDistillationEncoder.SLOT_ENCODED);

        // The world the bug had already moved: a pattern saved from the source well must come back in the
        // encoded well, the one it can be taken from.
        BlockEntityDistillationEncoder shifted = newEncoder(level);
        shifted.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_SOURCE, written);

        BlockEntityDistillationEncoder rescued = loadLegacy(level, shifted);
        expectNoPatternInTheSourceWell(rescued, failures);
        expectWell(failures, "the written pattern", written, rescued, BlockEntityDistillationEncoder.SLOT_ENCODED);
    }

    private static BlockEntityDistillationEncoder roundTrip(
            ServerLevel level, BlockEntityDistillationEncoder from) {
        CompoundTag tag = from.saveCustomOnly(level.registryAccess());
        BlockEntityDistillationEncoder reloaded = newEncoder(level);
        reloaded.loadCustomOnly(tag, level.registryAccess());
        return reloaded;
    }

    private static BlockEntityDistillationEncoder loadLegacy(
            ServerLevel level, BlockEntityDistillationEncoder from) {
        CompoundTag legacy = new CompoundTag();
        legacy.put("Inventory", from.getInventory().createTag(level.registryAccess()));
        BlockEntityDistillationEncoder reloaded = newEncoder(level);
        reloaded.loadCustomOnly(legacy, level.registryAccess());
        return reloaded;
    }

    /** The heart of it: nothing that is a pattern may sit in the source well after a load. That well is a
     * ghost slot - the player cannot take anything out and the next template discards what is there. */
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
        BlockEntityDistillationEncoder encoder = new BlockEntityDistillationEncoder(
                new BlockPos(0, -4096, 0), ModBlocks.DISTILLATION_ENCODER.get().defaultBlockState());
        encoder.setLevel(level);
        return encoder;
    }

    private static ItemStack newBlankPattern() {
        return AEItems.BLANK_PATTERN.stack();
    }

    private static ItemStack writtenPattern() {
        AEItemKey in = AEItemKey.of(new ItemStack(Items.STONE));
        AEItemKey out = AEItemKey.of(new ItemStack(Items.DIAMOND));
        if (in == null || out == null) {
            return ItemStack.EMPTY;
        }
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(in, 1)), List.of(new GenericStack(out, 1)));
    }

    private static @Nullable ItemStack firstDistillableItem(ServerLevel level) {
        for (String id : AspectIndexWait.PROBE_ITEM_IDS) {
            ItemStack stack = AspectIndexWait.resolve(level, id);
            if (stack == null) {
                continue;
            }
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
