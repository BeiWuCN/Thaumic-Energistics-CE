package thaumicenergistics_ce.selftest;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.blockentity.vibrationchamber.BlockEntityEssentiaVibrationChamber;
import thaumicenergistics_ce.blockentity.infusionmonitor.BlockEntityInfusionMonitor;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.blockentity.vibrationchamber.VibrationChamberSync;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.menu.MenuArcaneAssembler;
import thaumicenergistics_ce.util.ThELog;

/**
 * Checks every number this mod syncs, by driving each path with nothing in the world: what a world
 * saves, what a packet carries, what a menu hands a screen. Two readers can disagree in silence -
 * the tooltip just shows the wrong figure - so each is written, read back and compared here. The
 * pieces that live only inside their own package are reached by reflection, as
 * {@link NetworkSelfTest} reaches NeoForge's own tables. On with
 * {@code THAUMICENERGISTICS_SYNC_SELFTEST=true}.
 */
public final class SyncSelfTest {

    /**
     * The names older worlds carry. They are not free to change: a chamber that saved under these and
     * loads under others comes back empty, and nothing would say so.
     */
    private static final List<String> CHAMBER_SAVE_KEYS = List.of(
            "StoredEssentia", "BurnTicksRemaining", "TotalBurnTicks", "AePerTick", "StoredEnergy", "CurrentAspect");

    /** The bubble half of the monitor's update tag, one entry per field the renderer draws. */
    private static final List<String> BUBBLE_KEYS = List.of(
            "Reporting", "BubbleTier", "BubbleInstability", "BubbleStability", "BubbleCrafting", "BubbleCraft",
            "BubbleEssentia");

    private static boolean hasRun;

    private SyncSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_SYNC_SELFTEST"))) {
            return;
        }
        if (hasRun) {
            return;
        }
        hasRun = true;
        ServerLevel level = event.getServer().overworld();
        List<String> failures = new ArrayList<>();
        int compared = 0;
        compared += checkChamberSave(level, failures);
        compared += checkChamberStream(level, failures);
        compared += checkBubbleTag(level.registryAccess(), failures);
        compared += checkJadeKeys(failures);
        compared += checkDisplayBands(failures);
        compared += checkReadingTables(failures);
        compared += checkReadingMirrors(failures);
        report(failures, compared);
    }

    /**
     * Saves a chamber, loads the tag into a fresh one and compares what came back. The six readings
     * are overwritten first, so the check compares chosen numbers rather than defaults with defaults.
     */
    private static int checkChamberSave(ServerLevel level, List<String> failures) {
        HolderLookup.Provider registries = level.registryAccess();
        BlockEntityEssentiaVibrationChamber source = newChamber();
        Holder<IAspect> ignis = aspect(level, "ignis");
        if (ignis == null) {
            failures.add("no aspect named ignis is registered: the chamber's aspect cannot be checked");
        } else if (source.insert(ignis, 40, false) != 40) {
            failures.add("the chamber took less than the 40 essentia it was offered");
        }
        CompoundTag saved = source.saveWithoutMetadata(registries);
        for (String key : CHAMBER_SAVE_KEYS) {
            if (!saved.contains(key) && !("CurrentAspect".equals(key) && ignis == null)) {
                failures.add("the chamber's tag has no " + key + ": a world saved by an older build will not load");
            }
        }
        saved.putInt("StoredEssentia", 7);
        saved.putInt("BurnTicksRemaining", 11);
        saved.putInt("TotalBurnTicks", 20);
        saved.putDouble("AePerTick", 2.5);
        saved.putDouble("StoredEnergy", 512.0);

        BlockEntityEssentiaVibrationChamber reloaded = newChamber();
        reloaded.loadTag(saved, registries);
        int compared = 0;
        compared += expect("a saved StoredEssentia", 7, reloaded.getStoredEssentia(), failures);
        compared += expect("a saved BurnTicksRemaining", 11, reloaded.getBurnTicksRemaining(), failures);
        compared += expect("a saved TotalBurnTicks", 20, reloaded.getTotalBurnTicks(), failures);
        compared += expect("a saved StoredEnergy", 512, (int) Math.round(reloaded.getStoredEnergy()), failures);
        compared += expect("a saved AePerTick, times ten", 25,
                VibrationChamberSync.reading(reloaded, VibrationChamberSync.AE_PER_TICK), failures);
        if (ignis != null) {
            compared += expect("a saved aspect's colour",
                    VibrationChamberSync.reading(source, VibrationChamberSync.ASPECT_COLOUR),
                    VibrationChamberSync.reading(reloaded, VibrationChamberSync.ASPECT_COLOUR), failures);
        }
        return compared;
    }

    /**
     * Drives the byte stream the chamber sends to a client. Only four of the nine readings travel on
     * it - the rest are recomputed or kept on the server - and those four are what is compared.
     */
    private static int checkChamberStream(ServerLevel level, List<String> failures) {
        BlockEntityEssentiaVibrationChamber source = newChamber();
        Holder<IAspect> ignis = aspect(level, "ignis");
        if (ignis != null) {
            source.insert(ignis, 20, false);
        }
        Method write = reach("thaumicenergistics_ce.blockentity.vibrationchamber.VibrationChamberSync", "writeStream",
                RegistryFriendlyByteBuf.class, BlockEntityEssentiaVibrationChamber.class, failures);
        Method read = reach("thaumicenergistics_ce.blockentity.vibrationchamber.VibrationChamberSync", "readStream",
                RegistryFriendlyByteBuf.class, failures);
        if (write == null || read == null) {
            return 0;
        }
        Object streamed;
        // Both ways of building this buffer are deprecated in this Minecraft line; NetworkSelfTest
        // uses the constructor too, so the file reads the same way as its neighbours.
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            write.invoke(null, buffer, source);
            streamed = read.invoke(null, buffer);
        } catch (ReflectiveOperationException e) {
            failures.add("the chamber's byte stream could not be driven: " + e);
            return 0;
        }
        Object streamedState = value(streamed, "state", failures);
        Object streamedAspect = value(streamed, "aspect", failures);
        int compared = 0;
        compared += expect("the streamed state, as an ordinal", source.getBurnState().ordinal(),
                streamedState instanceof Enum<?> state ? state.ordinal() : -1, failures);
        double streamedRate = number(streamed, "aePerTick", failures).doubleValue();
        if (Double.compare(streamedRate, source.getAePerTick()) != 0) {
            failures.add("the streamed burn rate came back as " + streamedRate + ", not " + source.getAePerTick());
        }
        compared++;
        compared += expect("the streamed essentia", source.getStoredEssentia(),
                number(streamed, "essentia", failures).intValue(), failures);
        if (source.getCurrentAspect() == null ? streamedAspect != null
                : !source.getCurrentAspect().equals(streamedAspect)) {
            failures.add("the streamed aspect came back as " + streamedAspect + ", not " + source.getCurrentAspect());
        }
        compared++;
        if (buffer.readableBytes() != 0) {
            failures.add("the chamber's byte stream left " + buffer.readableBytes() + " byte(s) unread");
        }
        return compared;
    }

    /**
     * Writes the bubble into a tag and reads it back, the route the packet takes.
     * A machine outside a world has no level, so the write half and the read tail both routes share run here.
     */
    private static int checkBubbleTag(HolderLookup.Provider registries, List<String> failures) {
        Object bubble = syncUnit("infusionmonitor.InfusionMonitorSync", failures);
        Method write = reach("thaumicenergistics_ce.blockentity.infusionmonitor.InfusionMonitorSync", "write",
                CompoundTag.class, HolderLookup.Provider.class, failures);
        CompoundTag tag = new CompoundTag();
        if (bubble != null && write != null) {
            try {
                write.invoke(bubble, tag, registries);
            } catch (ReflectiveOperationException e) {
                failures.add("the bubble could not be written into a tag: " + e);
            }
        }
        for (String key : BUBBLE_KEYS) {
            if (!tag.contains(key)) {
                failures.add("the bubble tag has no " + key + ": the bubble cannot be drawn");
            }
        }
        tag.putBoolean("Reporting", true);
        tag.putInt("BubbleTier", InfusionRisk.MAX_TIER + 4);
        tag.putInt("BubbleInstability", 5);
        tag.putInt("BubbleStability", 25);
        tag.putBoolean("BubbleCrafting", true);
        tag.put("BubbleCraft", new ItemStack(Items.STONE, 3).saveOptional(registries));
        CompoundTag line = new CompoundTag();
        line.putString("Aspect", "x");
        line.putInt("Drawn", 1);
        line.putInt("Total", 2);
        ListTag lines = new ListTag();
        lines.add(line);
        tag.put("BubbleEssentia", lines);

        BlockEntityInfusionMonitor reader = newMonitor();
        Method apply = reach("thaumicenergistics_ce.blockentity.infusionmonitor.BlockEntityInfusionMonitor", "applyBubbleState",
                CompoundTag.class, HolderLookup.Provider.class, failures);
        if (apply != null) {
            try {
                apply.invoke(reader, tag, registries);
            } catch (ReflectiveOperationException e) {
                failures.add("the monitor could not take a bubble tag: " + e);
            }
        }
        int compared = 0;
        if (!reader.bubbleReporting()) {
            failures.add("a reporting monitor's bubble did not come back as reporting");
        }
        compared++;
        compared += expect("a bubble tier above the cap", InfusionRisk.MAX_TIER, reader.bubbleTier(), failures);
        compared += expect("a bubble instability", 5, reader.bubbleInstability(), failures);
        if (!"2.5".equals(reader.bubbleStability())) {
            failures.add("a bubble stability of 25 came back as " + reader.bubbleStability() + ", not 2.5");
        }
        compared++;
        if (!reader.bubbleCrafting()) {
            failures.add("a bubble that was crafting did not come back as crafting");
        }
        compared++;
        if (reader.bubbleCraft().getItem() != Items.STONE || reader.bubbleCraft().getCount() != 3) {
            failures.add("a bubble's craft came back as " + reader.bubbleCraft() + ", not 3 stone");
        }
        compared++;
        if (!reader.bubbleEssentia().equals(List.of(new BlockEntityInfusionMonitor.EssentiaLine("x", 1, 2)))) {
            failures.add("a one line bubble came back as " + reader.bubbleEssentia());
        }
        compared++;
        return compared;
    }

    /**
     * The assembler's display wells are the machine's own: the target well and the nine preview wells
     * must refuse a player's item, or a shift click would pocket the product the screen is drawing.
     */
    private static int checkDisplayBands(List<String> failures) {
        Method isDisplaySlot = reach("thaumicenergistics_ce.blockentity.assembler.AssemblerDisplaySync",
                "isDisplaySlot", int.class, failures);
        Method isMachineOwned = reach("thaumicenergistics_ce.blockentity.assembler.AssemblerDisplaySync",
                "isMachineOwned", int.class, failures);
        if (isDisplaySlot == null || isMachineOwned == null) {
            return 0;
        }
        BlockEntityArcaneAssembler machine = new BlockEntityArcaneAssembler(
                BlockPos.ZERO, ModBlocks.ARCANE_ASSEMBLER.get().defaultBlockState());
        ItemStack product = new ItemStack(Items.STONE);
        int compared = 0;
        for (int slot = 0; slot < BlockEntityArcaneAssembler.SLOT_COUNT; slot++) {
            boolean display = slot == BlockEntityArcaneAssembler.TARGET_SLOT
                    || (slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                            && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START);
            boolean owned = (slot >= BlockEntityArcaneAssembler.PATTERN_SLOT_START
                    && slot < BlockEntityArcaneAssembler.GEAR_SLOT_START)
                    || (slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                            && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START);
            compared += expect("slot " + slot + " as a display well", display ? 1 : 0,
                    callBoolean(isDisplaySlot, slot, failures) ? 1 : 0, failures);
            compared += expect("slot " + slot + " as the machine's own", owned ? 1 : 0,
                    callBoolean(isMachineOwned, slot, failures) ? 1 : 0, failures);
            if (display && machine.getInventory().canPlaceItem(slot, product)) {
                failures.add("the display well " + slot + " takes a player's item");
            }
            compared++;
        }
        return compared;
    }

    /** Pins the slot numbers the menus and their screens agree on. A shift here is a silent one. */
    private static int checkReadingTables(List<String> failures) {
        int compared = 0;
        compared += expect("VibrationChamberSync.COUNT", 9, VibrationChamberSync.COUNT, failures);
        compared += expect("VibrationChamberSync.ESSENTIA", 0, VibrationChamberSync.ESSENTIA, failures);
        compared += expect("VibrationChamberSync.ESSENTIA_MAX", 1, VibrationChamberSync.ESSENTIA_MAX, failures);
        compared += expect("VibrationChamberSync.ENERGY", 2, VibrationChamberSync.ENERGY, failures);
        compared += expect("VibrationChamberSync.ENERGY_MAX", 3, VibrationChamberSync.ENERGY_MAX, failures);
        compared += expect("VibrationChamberSync.BURN", 4, VibrationChamberSync.BURN, failures);
        compared += expect("VibrationChamberSync.BURN_TOTAL", 5, VibrationChamberSync.BURN_TOTAL, failures);
        compared += expect("VibrationChamberSync.AE_PER_TICK", 6, VibrationChamberSync.AE_PER_TICK, failures);
        compared += expect("VibrationChamberSync.ASPECT_COLOUR", 7, VibrationChamberSync.ASPECT_COLOUR, failures);
        compared += expect("VibrationChamberSync.STATE", 8, VibrationChamberSync.STATE, failures);
        compared += expect("MenuArcaneAssembler.DATA_SIZE", 11, MenuArcaneAssembler.DATA_SIZE, failures);
        compared += expect("MenuArcaneAssembler.DATA_BUFFERED_VIS", 0,
                MenuArcaneAssembler.DATA_BUFFERED_VIS, failures);
        compared += expect("MenuArcaneAssembler.DATA_ASPECT_AIR", 1, MenuArcaneAssembler.DATA_ASPECT_AIR, failures);
        compared += expect("MenuArcaneAssembler.DATA_ASPECT_WATER", 2,
                MenuArcaneAssembler.DATA_ASPECT_WATER, failures);
        compared += expect("MenuArcaneAssembler.DATA_ASPECT_FIRE", 3,
                MenuArcaneAssembler.DATA_ASPECT_FIRE, failures);
        compared += expect("MenuArcaneAssembler.DATA_ASPECT_ORDER", 4,
                MenuArcaneAssembler.DATA_ASPECT_ORDER, failures);
        compared += expect("MenuArcaneAssembler.DATA_ASPECT_ENTROPY", 5,
                MenuArcaneAssembler.DATA_ASPECT_ENTROPY, failures);
        compared += expect("MenuArcaneAssembler.DATA_ASPECT_EARTH", 6,
                MenuArcaneAssembler.DATA_ASPECT_EARTH, failures);
        compared += expect("MenuArcaneAssembler.DATA_CRAFTING", 7, MenuArcaneAssembler.DATA_CRAFTING, failures);
        compared += expect("MenuArcaneAssembler.DATA_CRAFT_TICK", 8, MenuArcaneAssembler.DATA_CRAFT_TICK, failures);
        compared += expect("MenuArcaneAssembler.DATA_TICKS_PER_CRAFT", 9,
                MenuArcaneAssembler.DATA_TICKS_PER_CRAFT, failures);
        compared += expect("MenuArcaneAssembler.DATA_GEAR_DISCOUNT", 10,
                MenuArcaneAssembler.DATA_GEAR_DISCOUNT, failures);
        return compared;
    }

    /**
     * Every menu reading table, built the way the server builds it - with no machine behind it - is
     * handed a number per slot and asked for it back. This is the route a screen's tooltip takes.
     */
    private static int checkReadingMirrors(List<String> failures) {
        int compared = 0;
        compared += mirror("VibrationChamberReadings",
                new Class<?>[] {BlockEntityEssentiaVibrationChamber.class},
                new Object[] {null}, failures);
        compared += mirror("ArcaneAssemblerReadings",
                new Class<?>[] {BlockEntityArcaneAssembler.class, IntUnaryOperator.class},
                new Object[] {null, (IntUnaryOperator) index -> 0}, failures);
        compared += mirror("KnowledgeInscriberReadings",
                new Class<?>[] {BlockEntityKnowledgeInscriber.class, Player.class, IntSupplier.class},
                new Object[] {null, null, (IntSupplier) () -> 0}, failures);
        return compared;
    }

    /** Writes a number into every slot of one reading table and reads them all back. */
    private static int mirror(String className, Class<?>[] types, Object[] arguments, List<String> failures) {
        ContainerData data;
        try {
            Class<?> reader = Class.forName("thaumicenergistics_ce.menu." + className);
            Constructor<?> constructor = reader.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            data = (ContainerData) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failures.add(className + " could not be built: " + e);
            return 0;
        }
        int count = data.getCount();
        if (count <= 0) {
            failures.add(className + " reports " + count + " reading(s)");
            return 0;
        }
        for (int index = 0; index < count; index++) {
            data.set(index, index * 7 + 3);
        }
        int compared = 0;
        for (int index = 0; index < count; index++) {
            compared += expect(className + " reading " + index, index * 7 + 3, data.get(index), failures);
        }
        return compared;
    }

    /** Builds one of the per-machine sync units; they are package-private on purpose. */
    private static Object syncUnit(String className, List<String> failures) {
        try {
            Constructor<?> constructor =
                    Class.forName("thaumicenergistics_ce.blockentity." + className).getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            failures.add(className + " could not be built: " + e);
            return null;
        }
    }

    /** The Jade payload and the machine's tag name the same numbers from two separate classes: both
     * sides are read here and compared, so a rename that misses one of them fails in the log. */
    private static int checkJadeKeys(List<String> failures) {
        String jade = "thaumicenergistics_ce.integration.jade.";
        String machine = "thaumicenergistics_ce.blockentity.";
        int compared = 0;
        compared += sameWord("the pooled vis", key(jade + "ArcaneAssemblerProvider", "TAG_VIS", failures),
                key(machine + "assembler.AssemblerVisPool", "TAG_BUFFERED_VIS", failures), failures);
        compared += sameWord("the gear discount", key(jade + "ArcaneAssemblerProvider", "TAG_DISCOUNT", failures),
                key(machine + "assembler.AssemblerDisplaySync", "TAG_GEAR_DISCOUNT", failures), failures);
        compared += sameWord("the speed upgrades", key(jade + "ArcaneAssemblerProvider", "TAG_SPEED", failures),
                key(machine + "assembler.AssemblerUpgrades", "TAG_SPEED_UPGRADES", failures), failures);
        compared += sameWord("the monitor's reporting flag",
                key(jade + "InfusionMonitorProvider", "TAG_REPORTING", failures),
                key(machine + "infusionmonitor.InfusionMonitorSync", "TAG_REPORTING", failures), failures);
        return compared;
    }

    /** One key's word, reached from outside its package: a class that moved or a field that was renamed
     * lands in {@code failures}, so the names after it are still checked. */
    private static String key(String className, String field, List<String> failures) {
        try {
            Field found = Class.forName(className).getDeclaredField(field);
            found.setAccessible(true);
            return (String) found.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failures.add(className + "." + field + " could not be read: " + e);
            return null;
        }
    }

    private static int sameWord(String what, String jade, String machine, List<String> failures) {
        if (jade != null && !jade.equals(machine)) {
            failures.add("the Jade key for " + what + " is \"" + jade + "\", but the machine writes \""
                    + machine + "\"");
        }
        return 1;
    }

    private static BlockEntityEssentiaVibrationChamber newChamber() {
        return new BlockEntityEssentiaVibrationChamber(
                BlockPos.ZERO, ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get().defaultBlockState());
    }

    private static BlockEntityInfusionMonitor newMonitor() {
        return new BlockEntityInfusionMonitor(
                BlockPos.ZERO, ModBlocks.INFUSION_MONITOR.get().defaultBlockState());
    }

    private static Holder<IAspect> aspect(ServerLevel level, String path) {
        return AEssentiaKeyType.aspectOf(level, ResourceLocation.fromNamespaceAndPath("thaumaturge", path));
    }

    private static Method reach(String className, String method, Class<?> first, List<String> failures) {
        return reach(className, method, new Class<?>[] {first}, failures);
    }

    private static Method reach(String className, String method, Class<?> first, Class<?> second,
            List<String> failures) {
        return reach(className, method, new Class<?>[] {first, second}, failures);
    }

    private static Method reach(String className, String method, Class<?>[] types, List<String> failures) {
        try {
            Method found = Class.forName(className).getDeclaredMethod(method, types);
            found.setAccessible(true);
            return found;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failures.add(className + "." + method + " could not be reached: " + e);
            return null;
        }
    }

    private static boolean callBoolean(Method method, int argument, List<String> failures) {
        try {
            return (Boolean) method.invoke(null, argument);
        } catch (ReflectiveOperationException e) {
            failures.add(method.getName() + "(" + argument + ") threw " + e);
            return false;
        }
    }

    private static Object value(Object record, String accessor, List<String> failures) {
        try {
            Method method = record.getClass().getDeclaredMethod(accessor);
            method.setAccessible(true);
            return method.invoke(record);
        } catch (ReflectiveOperationException e) {
            failures.add("the streamed " + accessor + " could not be read: " + e);
            return null;
        }
    }

    private static Number number(Object record, String accessor, List<String> failures) {
        Object value = value(record, accessor, failures);
        return value instanceof Number counted ? counted : 0;
    }

    private static int expect(String what, int expected, int actual, List<String> failures) {
        if (expected != actual) {
            failures.add(what + " came back as " + actual + ", not " + expected);
        }
        return 1;
    }

    private static void report(List<String> failures, int compared) {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[sync] self-test passed: {} number(s) went out and came back unchanged", compared);
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[sync] FAIL {}", failure);
        }
        ThELog.LOG.error("[sync] self-test failed with {} problem(s) after {} check(s)",
                failures.size(), compared);
    }
}
