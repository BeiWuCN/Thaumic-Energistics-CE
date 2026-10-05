package thaumicenergistics_ce.selftest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.util.ThELog;

/**
 * How the assembler's self-test reaches the machine. The craft state, the vis source and the container
 * listener guard are package-private, so they are reached reflectively - the way SyncSelfTest reaches the
 * sync units - rather than by widening the machine's face for a test.
 */
final class AssemblerTestAccess {

    private static final Class<?>[] NO_ARGUMENTS = new Class<?>[0];

    private AssemblerTestAccess() {}

    /** Holds a real recipe as if pushed, and reports what the machine would bank for it. */
    static void forcePattern(BlockEntityArcaneAssembler machine, ThEArcanePattern pattern) {
        Object craft = read(machine, "craft");
        int price = machine.craftCost(pattern);
        List<?> crystals = (List<?>) reachStatic(
                craftJob(), "crystalStacksOf", new Class<?>[] {ThEArcanePattern.class}, pattern);
        reach(craft, "setCurrentPattern", new Class<?>[] {ThEArcanePattern.class}, pattern);
        reach(craft, "setCrafting", new Class<?>[] {boolean.class}, true);
        reach(craft, "setCraftPrice", new Class<?>[] {int.class}, price);
        reach(craft, "setCraftCrystals", new Class<?>[] {List.class}, crystals);
        // As beginCraft leaves the target well - a copy of the product - but without the notify guard,
        // so the container's listener runs: the round trip is worthless without it.
        machine.getInventory().setItem(BlockEntityArcaneAssembler.TARGET_SLOT, pattern.result().copy());
        int target = (Integer) reach(read(machine, "vis"), "visTarget",
                new Class<?>[] {boolean.class, int.class}, true, price);
        ThELog.LOG.info("[asmtest] vis target for {} ({} vis) is {}, with {} in the buffer",
                pattern.result(), price, target, machine.getBufferedVis());
    }

    /** The craft as it stands, plus what sits in the target well, for a round-trip log line. */
    static String resumeReport(BlockEntityArcaneAssembler machine) {
        Object craft = read(machine, "craft");
        return "crafting=" + reach(craft, "isCrafting", NO_ARGUMENTS)
                + " price=" + reach(craft, "craftPrice", NO_ARGUMENTS)
                + " crystals=" + ((List<?>) reach(craft, "craftCrystals", NO_ARGUMENTS)).size()
                + " output=" + machine.getInventory().getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
    }

    /** Hands the machine a level and lets it pick a craft back up out of what it saved. */
    static void recover(BlockEntityArcaneAssembler machine, Level level) {
        machine.setLevel(level);
        reach(reach(machine, "craftJob", NO_ARGUMENTS), "recoverInterruptedCraft", NO_ARGUMENTS);
    }

    /** Puts a stack in without the container listener; {@link #onInventoryChanged} then runs it by hand. */
    static void setItem(BlockEntityArcaneAssembler machine, int slot, ItemStack stack) {
        write(machine, "suppressNotify", true);
        try {
            machine.getInventory().setItem(slot, stack);
        } finally {
            write(machine, "suppressNotify", false);
        }
    }

    /** Runs the container listener by hand: the insert above suppresses it, and a card put into the
     * machine has to move the count before anything is ever saved. */
    static void onInventoryChanged(BlockEntityArcaneAssembler machine) {
        reach(machine, "onInventoryChanged", NO_ARGUMENTS);
    }

    /** The craft job, package-private like the state it drives. */
    private static Class<?> craftJob() {
        try {
            return Class.forName("thaumicenergistics_ce.blockentity.assembler.AssemblerCraftJob");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("the assembler's craft job is gone", e);
        }
    }

    private static Object read(Object target, String field) {
        try {
            Field found = target.getClass().getDeclaredField(field);
            found.setAccessible(true);
            return found.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(named(target) + "." + field + " could not be read", e);
        }
    }

    private static void write(Object target, String field, Object value) {
        try {
            Field found = target.getClass().getDeclaredField(field);
            found.setAccessible(true);
            found.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(named(target) + "." + field + " could not be written", e);
        }
    }

    private static Object reach(Object target, String method, Class<?>[] types, Object... arguments) {
        return invoke(target.getClass(), target, method, types, arguments);
    }

    private static Object reachStatic(Class<?> owner, String method, Class<?>[] types, Object... arguments) {
        return invoke(owner, null, method, types, arguments);
    }

    private static Object invoke(Class<?> owner, Object target, String method, Class<?>[] types,
            Object[] arguments) {
        try {
            Method found = owner.getDeclaredMethod(method, types);
            found.setAccessible(true);
            return found.invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + method + " could not be reached", e);
        }
    }

    private static String named(Object target) {
        return target.getClass().getSimpleName();
    }
}
