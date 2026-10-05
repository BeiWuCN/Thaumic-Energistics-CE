package thaumicenergistics_ce.init.capability;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.items.IItemHandler;
import thaumicenergistics_ce.blockentity.BlockEntityDistillationEncoder;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.util.ThELog;

/**
 * What a pipe may reach, held against what a broken machine gives back.
 * <ul>
 * <li>The encoder's source well holds a name and the assembler's mirror, target and preview bands hold
 * copies, so a band that reached one would hand out an item nobody ever put in.
 * <li>The last check breaks a machine for real: only a drop shows that the source well is left out,
 * where the two bands above only show that it is out of a pipe's reach.
 * </ul>
 */
public final class MachineItemBandSelfTest {

    private static final String TAG = "item-bands";

    private static boolean hasRun;

    private MachineItemBandSelfTest() {}

    /** Entry point for the self-test source set's bootstrap; one run per server. */
    public static void run(ServerStartedEvent event) {
        if (hasRun) {
            return;
        }
        hasRun = true;

        List<String> failures = new ArrayList<>();
        checkEncoderBand(failures);
        checkAssemblerBand(failures);
        checkInscriberBand(failures);
        checkEncoderDrop(event.getServer().overworld(), failures);
        report(failures);
    }

    /** The source well is the one slot a JEI drag fills for free, so no pipe may see it. */
    private static void checkEncoderBand(List<String> failures) {
        BlockEntityDistillationEncoder encoder = new BlockEntityDistillationEncoder(
                BlockPos.ZERO, ModBlocks.DISTILLATION_ENCODER.get().defaultBlockState());
        ItemStack named = new ItemStack(Items.DIAMOND);
        ItemStack blank = new ItemStack(Items.PAPER);
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_SOURCE, named);
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, blank);

        IItemHandler handler = ThEItemCapabilities.encoder(encoder);
        if (handler.getSlots() != 2) {
            failures.add("the encoder shows a pipe " + handler.getSlots() + " slots, not its 2 pattern wells");
            return;
        }
        if (!ItemStack.matches(handler.getStackInSlot(0), blank)) {
            failures.add("the encoder's first slot to a pipe is not the blank well");
        }
        expectOutOfReach(failures, handler, named, "the encoder's source well");
    }

    /** The bands the machine writes itself: a pipe that could take one would fence unpaid copies. */
    private static void checkAssemblerBand(List<String> failures) {
        BlockEntityArcaneAssembler machine = new BlockEntityArcaneAssembler(
                BlockPos.ZERO, ModBlocks.ARCANE_ASSEMBLER.get().defaultBlockState());
        ItemStack mirror = new ItemStack(Items.DIAMOND);
        ItemStack target = new ItemStack(Items.EMERALD);
        ItemStack preview = new ItemStack(Items.GOLD_INGOT);
        ItemStack gear = new ItemStack(Items.IRON_HELMET);
        machine.getInventory().setItem(BlockEntityArcaneAssembler.PATTERN_SLOT_START, mirror);
        machine.getInventory().setItem(BlockEntityArcaneAssembler.TARGET_SLOT, target);
        machine.getInventory().setItem(BlockEntityArcaneAssembler.PREVIEW_SLOT_START, preview);
        machine.getInventory().setItem(BlockEntityArcaneAssembler.GEAR_SLOT_START, gear);

        IItemHandler handler = ThEItemCapabilities.assembler(machine, null);
        if (handler == null) {
            failures.add("the assembler answers a pipe nothing at all");
            return;
        }
        expectOutOfReach(failures, handler, mirror, "the assembler's pattern mirror");
        expectOutOfReach(failures, handler, target, "the assembler's target well");
        expectOutOfReach(failures, handler, preview, "the assembler's preview grid");

        boolean gearSeen = false;
        for (int slot = 0; slot < handler.getSlots() && !gearSeen; slot++) {
            gearSeen = ItemStack.matches(handler.getStackInSlot(slot), gear);
        }
        if (!gearSeen) {
            failures.add("the assembler's gear band is not reachable, so its band has no player slots");
        }
    }

    /** The inscriber's screen shows one usable slot; the twenty-one wells beside it only draw what the
     * core stores, so a pipe that could reach them could park twenty-one stacks in a hidden room. */
    private static void checkInscriberBand(List<String> failures) {
        BlockEntityKnowledgeInscriber inscriber = new BlockEntityKnowledgeInscriber(
                BlockPos.ZERO, ModBlocks.KNOWLEDGE_INSCRIBER.get().defaultBlockState());
        SimpleContainer container = inscriber.getInventory();
        ItemStack core = new ItemStack(ModItems.KNOWLEDGE_CORE.get());
        ItemStack junk = new ItemStack(Items.DIAMOND);

        if (!container.canPlaceItem(BlockEntityKnowledgeInscriber.CORE_SLOT, core)) {
            failures.add("the inscriber's core slot will not take a knowledge core");
        }
        if (container.canPlaceItem(BlockEntityKnowledgeInscriber.CORE_SLOT, junk)) {
            failures.add("the inscriber's core slot takes an item that is not a knowledge core");
        }
        for (int i = 0; i < BlockEntityKnowledgeInscriber.MIRROR_SLOT_COUNT; i++) {
            int slot = BlockEntityKnowledgeInscriber.MIRROR_SLOT_START + i;
            if (container.canPlaceItem(slot, junk) || container.canPlaceItem(slot, core)) {
                failures.add("the inscriber's pattern well " + i + " takes an item");
            }
        }

        IItemHandler handler = ThEItemCapabilities.inscriber(inscriber);
        if (handler.getSlots() != 1) {
            failures.add("the inscriber shows a pipe " + handler.getSlots() + " slots, not the core alone");
            return;
        }
        if (handler.insertItem(0, junk, true).getCount() != junk.getCount()) {
            failures.add("a pipe can put a non-core item into the inscriber's core slot");
        }
        if (handler.insertItem(0, core, true).getCount() == core.getCount()) {
            failures.add("a pipe cannot put a knowledge core into the inscriber's core slot");
        }
    }

    /** The reported bug: a named source well came back as an item when the block was broken. */
    private static void checkEncoderDrop(ServerLevel level, List<String> failures) {
        BlockPos pos = level.getSharedSpawnPos();
        BlockEntityDistillationEncoder encoder = new BlockEntityDistillationEncoder(
                pos, ModBlocks.DISTILLATION_ENCODER.get().defaultBlockState());
        encoder.setLevel(level);
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_SOURCE, new ItemStack(Items.DIAMOND));
        encoder.getInventory().setItem(BlockEntityDistillationEncoder.SLOT_BLANK, new ItemStack(Items.PAPER));

        // Counted either side of the drop: the spawn area may already hold items of the player's.
        List<ItemEntity> before = drops(level, pos);
        encoder.dropContents();
        List<ItemEntity> after = drops(level, pos);
        if (count(after, Items.DIAMOND) > count(before, Items.DIAMOND)) {
            failures.add("breaking the encoder handed the source well back, so a JEI drag mints an item");
        }
        if (count(after, Items.PAPER) <= count(before, Items.PAPER)) {
            failures.add("the pattern well was not dropped, so this check cannot show the well was skipped");
        }
        // Leaves the test world as it was found: only what this check added goes.
        for (ItemEntity drop : after) {
            if (!before.contains(drop) && drop.getItem().is(Items.PAPER)) {
                drop.discard();
            }
        }
    }

    /** Fails for every slot that reports or hands over this stack, and for any that would take it. */
    private static void expectOutOfReach(
            List<String> failures, IItemHandler handler, ItemStack wanted, String what) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (ItemStack.matches(handler.getStackInSlot(slot), wanted)) {
                failures.add(what + " is on show to a pipe, as slot " + slot);
            }
            if (ItemStack.matches(handler.extractItem(slot, wanted.getMaxStackSize(), true), wanted)) {
                failures.add(what + " can be taken out of slot " + slot);
            }
            if (handler.insertItem(slot, wanted, true).getCount() != wanted.getCount()) {
                failures.add(what + " can be pushed into slot " + slot);
            }
        }
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos) {
        return new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4.0)));
    }

    private static int count(List<ItemEntity> drops, Item item) {
        int count = 0;
        for (ItemEntity drop : drops) {
            if (drop.getItem().is(item)) {
                count++;
            }
        }
        return count;
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[{}] self-test passed", TAG);
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[{}] FAIL {}", TAG, failure);
        }
    }
}
