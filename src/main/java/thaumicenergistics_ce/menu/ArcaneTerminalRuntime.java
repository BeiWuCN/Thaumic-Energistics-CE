package thaumicenergistics_ce.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.compat.thaumaturge.TcAura;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.item.ItemVisConnectionCard;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment;

/** Tracks recipe refresh inputs and per-tick wand charging independently of menu slots. */
final class ArcaneTerminalRuntime {
    private long lastWandChargeTick = Long.MIN_VALUE;

    int availableAura(Player player, boolean wireless, @Nullable PartArcaneCraftingTerminal part) {
        if (wireless) return (int) Math.max(0, TcAura.vis(player.level(), player.blockPosition()));
        return part == null || part.getBlockEntity() == null ? 0
                : (int) Math.max(0, TcAura.vis(part.getLevel(), part.getBlockEntity().getBlockPos()));
    }

    void chargeWand(Player player, @Nullable PartArcaneCraftingTerminal part,
            @Nullable IEnergySource payer, boolean inheritedVis, ItemStack card, InternalInventory wandInventory) {
        if (payer == null || part == null || !part.isActive() || card.isEmpty() || inheritedVis
                || lastWandChargeTick == player.level().getGameTime()) return;
        lastWandChargeTick = player.level().getGameTime();
        var wand = wandInventory.getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT);
        var charged = TerminalAuraPayment.chargeWand(player.level(), card, wand);
        if (charged != wand) wandInventory.setItemDirect(PartArcaneCraftingTerminal.WAND_SLOT, charged);
    }

    int inputSignature(Player player, InternalInventory grid, InternalInventory crystals,
            InternalInventory wand, ItemStack card, int availableAura) {
        int hash = 1;
        for (int i = 0; i < PartArcaneCraftingTerminal.GRID_SIZE; i++) {
            hash = 31 * hash + StackSignatures.of(grid.getStackInSlot(i));
        }
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            hash = 31 * hash + StackSignatures.of(crystals.getStackInSlot(i));
        }
        hash = 31 * hash + StackSignatures.of(card);
        hash = 31 * hash + Boolean.hashCode(ThEConfig.ALLOW_AE_FOR_AURA_VIS.get());
        hash = 31 * hash + availableAura;
        if (player.level() instanceof ServerLevel server) {
            var relay = ItemVisConnectionCard.resolve(server, card);
            long relayVis = 0;
            if (relay != null) {
                for (var primal : BlockEntityArcaneAssembler.PRIMALS) relayVis += relay.availableCentivis(primal);
            }
            hash = 31 * hash + Long.hashCode(relayVis);
        }
        return 31 * hash + StackSignatures.of(wand.getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT));
    }
}
