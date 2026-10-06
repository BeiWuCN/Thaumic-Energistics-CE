package thaumicenergistics_ce.focus;

import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.core.definitions.AEItems;
import appeng.hooks.WrenchHook;
import appeng.parts.reporting.AbstractReportingPart;
import appeng.parts.reporting.ConversionMonitorPart;
import appeng.util.InteractionUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import thaumicenergistics_ce.util.ThELog;

/**
 * Everything in AE2 that just needs "a wrench was used here". AE2 has one entry point,
 * {@link WrenchHook}, and a stack is a wrench by the {@code c:tools/wrench} tag, so this swaps a
 * real wrench in, asks AE2 to do the thing, and puts the player's item back. It is server-only
 * and always restored, because a client-side {@code setItemInHand} would desync the prediction.
 */
public final class AEWrench {

    private AEWrench() {}

    /**
     * Runs AE2's wrench action against one block, as if the player held a quartz wrench. Server only.
     *
     * @return true if AE2 did something; false means the block is not one a wrench acts on
     */
    public static boolean use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty()) {
            // AE2 is a hard dependency, so this only happens if its wrench is renamed or removed.
            return false;
        }

        ItemStack held = player.getItemInHand(hand);
        try {
            player.setItemInHand(hand, wrench);
            InteractionResult result = WrenchHook.onPlayerUseBlock(player, level, hand, hit);
            return result.consumesAction();
        } finally {
            player.setItemInHand(hand, held);
        }
    }

    /**
     * Whether the part under this hit is one a wrench click would turn: read-only, and the same answer
     * the client needs before it takes the mining away.
     */
    public static boolean wouldRotatePart(IPartHost host, Vec3 localPos) {
        SelectedPart selected = host.selectPartLocal(localPos);
        return turnable(selected);
    }

    /**
     * Turns the cable part the cursor is over, as if the player held a quartz wrench. Server only, and
     * the slot is always restored: AE2 reads the wrench out of the selected hotbar slot.
     */
    public static boolean rotateSelectedPart(Player player, Level level, IPartHost host, Vec3 localPos) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty() || !InteractionUtil.canWrenchRotate(wrench)) {
            // AE2 is a hard dependency, so this only happens if its wrench is renamed or removed. The
            // same test the part itself makes, read off the stack we are about to put in the slot.
            return false;
        }

        // The host is the caller's business: it already turned the hit into localPos and selected the
        // part, and the same selection is what the player is looking at.
        SelectedPart selected = host.selectPartLocal(localPos);
        if (!turnable(selected)) {
            // A facade, an empty side, or a part with no spin to advance.
            return false;
        }

        // AbstractReportingPart reads the wrench out of the selected hotbar slot and only it turns
        // anything: terminals return true just for opening their menu, so charge on the byte spin instead.
        byte before = ((AbstractReportingPart) selected.part).getSpin();

        // Inventory in 1.21.1 has no setSelectedItem: the selected stack is the item in slot "selected".
        int slot = player.getInventory().selected;
        ItemStack previous = player.getInventory().getItem(slot);

        // AE2 branches on the sneak, and a monitor answers a sneaking click by toggling its lock, so a
        // turn is asked for not sneaking; the flag goes back in the finally, which makes this safe.
        boolean sneaking = player.isShiftKeyDown();
        player.getInventory().setItem(slot, wrench);
        player.setShiftKeyDown(false);
        try {
            selected.part.onUseWithoutItem(player, localPos);
        } finally {
            player.setShiftKeyDown(sneaking);
            player.getInventory().setItem(slot, previous);
        }

        byte after = ((AbstractReportingPart) selected.part).getSpin();
        return after != before;
    }

    /**
     * Whether this selection is a part a turn would move: only the reporting family has a spin, and one of
     * them swallows matching items on a non-sneaking wrench click - AE2's behaviour for a locked monitor.
     */
    private static boolean turnable(SelectedPart selected) {
        if (selected == null || !(selected.part instanceof AbstractReportingPart)) {
            return false;
        }
        return !(selected.part instanceof ConversionMonitorPart monitor) || !monitor.isLocked();
    }

    /**
     * The wrench this focus borrows. AE2 routes a wrench action to
     * {@code AEBaseBlockEntity.disassembleWithWrench}, which is what makes this a disassembly.
     */
    public static ItemStack wrenchStack() {
        return AEItems.CERTUS_QUARTZ_WRENCH.stack();
    }
}
