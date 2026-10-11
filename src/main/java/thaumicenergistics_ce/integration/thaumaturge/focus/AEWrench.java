package thaumicenergistics_ce.integration.thaumaturge.focus;

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

public final class AEWrench {

    private AEWrench() {}

    public static boolean use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty()) {
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

    public static boolean wouldRotatePart(IPartHost host, Vec3 localPos) {
        SelectedPart selected = host.selectPartLocal(localPos);
        return turnable(selected);
    }

    public static boolean rotateSelectedPart(Player player, Level level, IPartHost host, Vec3 localPos) {
        if (level.isClientSide() || player.isSpectator()) {
            return false;
        }

        ItemStack wrench = wrenchStack();
        if (wrench.isEmpty() || !InteractionUtil.canWrenchRotate(wrench)) {
            return false;
        }

        SelectedPart selected = host.selectPartLocal(localPos);
        if (!turnable(selected)) {
            return false;
        }

        byte before = ((AbstractReportingPart) selected.part).getSpin();

        ItemStack previous = player.getInventory().getSelectedItem();

        boolean sneaking = player.isShiftKeyDown();
        player.getInventory().setSelectedItem(wrench);
        player.setShiftKeyDown(false);
        try {
            selected.part.onUseWithoutItem(player, localPos);
        } finally {
            player.setShiftKeyDown(sneaking);
            player.getInventory().setSelectedItem(previous);
        }

        byte after = ((AbstractReportingPart) selected.part).getSpin();
        return after != before;
    }

    private static boolean turnable(SelectedPart selected) {
        if (selected == null || !(selected.part instanceof AbstractReportingPart)) {
            return false;
        }
        return !(selected.part instanceof ConversionMonitorPart monitor) || !monitor.isLocked();
    }

    public static ItemStack wrenchStack() {
        return AEItems.CERTUS_QUARTZ_WRENCH.stack();
    }

    public static boolean isWrench(ItemStack stack) {
        return InteractionUtil.canWrenchDisassemble(stack);
    }
}
