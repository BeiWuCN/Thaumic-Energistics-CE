package thaumicenergistics_ce.arcane;

import thaumicenergistics_ce.config.ThEConfig;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.api.upgrades.IUpgradeableObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.init.ModItems;

public final class TerminalAuraPayment {

    public static final double AE_PER_VIS = 1_000.0;

    public static final int CENTIVIS_PER_VIS = 100;

    private TerminalAuraPayment() {}

    public static int pay(
            Level level,
            BlockPos where,
            @Nullable IEnergySource energy,
            int needCentivis,
            TransactionContext transaction) {
        if (!ThEConfig.ALLOW_AE_FOR_AURA_VIS.get() || needCentivis <= 0 || energy == null) {
            return 0;
        }
        float wanted = (float) needCentivis / CENTIVIS_PER_VIS;
        float available = TcAura.drainVis(level, where, wanted, true);
        if (available <= 0.0F) {
            return 0;
        }
        int offered = Math.min(needCentivis, Math.round(available * CENTIVIS_PER_VIS));
        double aePerVis = ThEConfig.AE_PER_VIS.get();
        double cost = aePerVis * offered / CENTIVIS_PER_VIS;

        double payable = energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (payable < cost) {
            offered = (int) Math.floor(payable / aePerVis * CENTIVIS_PER_VIS);
            if (offered <= 0) {
                return 0;
            }
            cost = aePerVis * offered / CENTIVIS_PER_VIS;
        }
        if (energy.extractAEPower(cost, Actionable.SIMULATE, PowerMultiplier.CONFIG) < cost) {
            return 0;
        }
        TcAura.drainVis(level, where, (float) offered / CENTIVIS_PER_VIS, transaction);
        energy.extractAEPower(cost, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return offered;
    }

    public static ItemStack visCard(IUpgradeableObject machine) {
        var upgrades = machine.getUpgrades();
        if (upgrades != null) {
            for (int i = 0; i < upgrades.size(); i++) {
                var card = upgrades.getStackInSlot(i);
                if (card.is(ModItems.VIS_CONNECTION_CARD.get())) {
                    return card;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static int payBoundAura(Level level, BlockPos where, ItemStack card,
            int need, TransactionContext transaction) {
        int supplied = payAura(level, where, need, transaction);
        if (supplied >= need || !(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return supplied;
        }
        var relay = thaumicenergistics_ce.item.ItemVisConnectionCard.resolve(server, card);
        if (relay == null) {
            return supplied;
        }
        return supplied + payRelay(relay, need - supplied, transaction);
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

    public static boolean visConnectionInstalled(ItemStack terminal) {
        if (terminal.isEmpty() || !(terminal.getItem() instanceof IUpgradeableItem upgradeable)) {
            return false;
        }
        IUpgradeInventory upgrades = upgradeable.getUpgrades(terminal);
        return upgrades != null && upgrades.isInstalled(ModItems.VIS_CONNECTION_CARD.get());
    }

    public static boolean visConnectionInstalled(IUpgradeableObject machine) {
        IUpgradeInventory upgrades = machine.getUpgrades();
        return upgrades != null && upgrades.isInstalled(ModItems.VIS_CONNECTION_CARD.get());
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
