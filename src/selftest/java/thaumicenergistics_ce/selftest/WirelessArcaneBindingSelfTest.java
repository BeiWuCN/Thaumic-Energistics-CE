package thaumicenergistics_ce.selftest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.arcane.ArcaneTerminalLink;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

/**
 * What the wireless arcane terminal writes when it is paired, and what it reads back.
 * <ul>
 *   <li>Three ways of getting no placed terminal: nothing paired, a pairing over a block that holds none,
 *       and a pairing from another dimension. The first two need no second level and are checked here.
 *   <li>The write itself is read back as the item's own tag: a key renamed on one side only would leave
 *       the terminal silently unable to find the one it was pointed at.
 * </ul>
 */
public final class WirelessArcaneBindingSelfTest {

    private static boolean hasRun;

    private WirelessArcaneBindingSelfTest() {}

    public static void run(ServerStartedEvent event) {
        if (hasRun) {
            return;
        }
        hasRun = true;

        ServerLevel level = event.getServer().overworld();
        List<String> failures = new ArrayList<>();

        ItemStack terminal = new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
        if (!(terminal.getItem() instanceof ArcaneTerminalLink link)) {
            failures.add("the wireless arcane terminal is not an ArcaneTerminalLink, so a placed terminal"
                    + " has no way to pair with it");
            report(failures);
            return;
        }
        if (ItemWirelessArcaneCraftingTerminal.pairedTerminal(level, terminal) != null) {
            failures.add("a fresh wireless arcane terminal already resolves a placed terminal");
        }

        BlockPos empty = level.getSharedSpawnPos().above(4);
        link.pairWith(terminal, level, empty, Direction.NORTH);
        CompoundTag written = tagOf(terminal);
        if (written == null
                || !written.contains("ArcaneDimension")
                || !written.contains("ArcanePos")
                || !written.contains("ArcaneSide")) {
            failures.add("pairing wrote " + written + ", expected the dimension, the position and the side");
        } else {
            if (written.getLong("ArcanePos") != empty.asLong()) {
                failures.add("pairing stored position " + written.getLong("ArcanePos") + ", expected "
                        + empty.asLong());
            }
            String dimension = level.dimension().location().toString();
            if (!dimension.equals(written.getString("ArcaneDimension"))) {
                failures.add("pairing stored dimension " + written.getString("ArcaneDimension")
                        + ", expected " + dimension);
            }
            if (!Direction.NORTH.getName().equals(written.getString("ArcaneSide"))) {
                failures.add("pairing stored side " + written.getString("ArcaneSide") + ", expected "
                        + Direction.NORTH.getName());
            }
        }
        if (ItemWirelessArcaneCraftingTerminal.pairedTerminal(level, terminal) != null) {
            failures.add("a terminal paired over a block that holds no terminal still resolved one");
        }
        report(failures);
    }

    private static CompoundTag tagOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static void report(List<String> failures) {
        if (failures.isEmpty()) {
            ThELog.LOG.info("[wireless-arcane] self-test passed");
            return;
        }
        for (String failure : failures) {
            ThELog.LOG.error("[wireless-arcane] FAIL {}", failure);
        }
        ThELog.LOG.error("[wireless-arcane] self-test failed with {} problem(s)", failures.size());
    }
}
