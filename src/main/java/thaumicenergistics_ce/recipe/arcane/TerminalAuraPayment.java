package thaumicenergistics_ce.recipe.arcane;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.api.upgrades.IUpgradeableObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.registry.TECEItems;

public final class TerminalAuraPayment {

    public static final int CENTIVIS_PER_VIS = 100;

    private TerminalAuraPayment() {}

    public static ItemStack visCard(IUpgradeableObject machine) {
        var upgrades = machine.getUpgrades();
        if (upgrades != null) {
            for (int i = 0; i < upgrades.size(); i++) {
                var card = upgrades.getStackInSlot(i);
                if (card.is(TECEItems.VIS_CONNECTION_CARD.get())) {
                    return card;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static int payChunkAura(Level level, BlockPos where, int needVis,
            TransactionContext transaction) {
        if (needVis <= 0 || level.isClientSide()) {
            return 0;
        }
        int offered = Math.min(needVis, (int) Math.floor(TcAura.drainVis(level, where, needVis, true)));
        return offered <= 0 ? 0 : (int) Math.floor(TcAura.drainVis(level, where, offered, transaction));
    }

    public static int payRelay(com.leclowndu93150.thaumaturge.api.aura.IVisRelaySource relay,
            int need, TransactionContext transaction) {
        int supplied = 0;
        for (var primal : thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler.PRIMALS) {
            if (supplied >= need) {
                break;
            }
            supplied += relay.drainCentivis(primal, need - supplied, transaction);
        }
        return supplied;
    }

    public static ItemStack chargeWand(Level level, ItemStack card, ItemStack wand) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)
                || !thaumicenergistics_ce.compat.thaumaturge.TcWand.isWand(wand)) {
            return wand;
        }
        var relay = thaumicenergistics_ce.item.ItemVisConnectionCard.resolve(server, card);
        if (relay == null) {
            return wand;
        }
        ItemStack charged = wand.copy();
        boolean changed = false;
        try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            for (var primal : thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler.PRIMALS) {
                int room = thaumicenergistics_ce.compat.thaumaturge.TcWand.chargeRoom(charged, primal);
                if (room <= 0) {
                    continue;
                }
                int supplied = relay.drainCentivis(primal, Math.min(room, CENTIVIS_PER_VIS), transaction);
                if (supplied > 0) {
                    thaumicenergistics_ce.compat.thaumaturge.TcWand.addCentivis(charged, primal, supplied);
                    changed = true;
                }
            }
            if (changed) {
                transaction.commit();
            }
        }
        return changed ? charged : wand;
    }

    public static boolean visConnectionInstalled(ItemStack terminal) {
        if (terminal.isEmpty() || !(terminal.getItem() instanceof IUpgradeableItem upgradeable)) {
            return false;
        }
        IUpgradeInventory upgrades = upgradeable.getUpgrades(terminal);
        return upgrades != null && upgrades.isInstalled(TECEItems.VIS_CONNECTION_CARD.get());
    }

    public static boolean visConnectionInstalled(IUpgradeableObject machine) {
        IUpgradeInventory upgrades = machine.getUpgrades();
        return upgrades != null && upgrades.isInstalled(TECEItems.VIS_CONNECTION_CARD.get());
    }

    public static int payAura(Level level, BlockPos where, int needCentivis, TransactionContext transaction) {
        if (needCentivis <= 0 || level == null || level.isClientSide()) {
            return 0;
        }
        float available = TcAura.drainVis(level, where, (float) needCentivis / CENTIVIS_PER_VIS, true);
        if (available <= 0.0F) {
            return 0;
        }
        int offered = Math.min(needCentivis, Math.round(available * CENTIVIS_PER_VIS));
        if (offered <= 0) {
            return 0;
        }
        TcAura.drainVis(level, where, (float) offered / CENTIVIS_PER_VIS, transaction);
        return offered;
    }
}
