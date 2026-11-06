package com.beiwu.thaumicenergistics_ce.item;
import com.leclowndu93150.thaumaturge.api.transaction.SnapshotJournal;
import com.leclowndu93150.thaumaturge.api.transaction.TransactionContext;

import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcAura;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;

@EventBusSubscriber(modid=ThEIds.MODID)
public final class VisToolRepair {
    private VisToolRepair() {}

    public static boolean repair(ItemStack stack, ServerLevel level, BlockPos pos) {
        return repair(stack, level, pos, null);
    }

    public static boolean repair(ItemStack stack, ServerLevel level, BlockPos pos,
            com.leclowndu93150.thaumaturge.api.transaction.@org.jspecify.annotations.Nullable TransactionContext parent) {
        if (!(stack.getItem() instanceof ThaumicTools.Repairable) || !stack.isDamaged()) return false;
        int repair = Math.min(10, stack.getDamageValue());
        float cost = repair / 10.0F;
        // A short extraction rolls back; repair never eats a partial payment.
        try (Transaction tx = Transaction.open(parent)) {
            float paid = TcAura.drainVis(level, pos, cost, tx);
            if (paid + 0.00001F < cost) return false;
            new com.leclowndu93150.thaumaturge.api.transaction.SnapshotJournal<Integer>() {
                void apply() {
                    updateSnapshots(tx);
                    stack.setDamageValue(stack.getDamageValue() - repair);
                }
                @Override protected Integer createSnapshot() { return stack.getDamageValue(); }
                @Override protected void revertToSnapshot(Integer damage) { stack.setDamageValue(damage); }
            }.apply();
            tx.commit();
        }
        return true;
    }

    @SubscribeEvent public static void anvil(AnvilUpdateEvent event) {
        var input = event.getLeft();
        var output = event.getOutput();
        if (input.getItem() instanceof ThaumicTools.Repairable && output.is(input.getItem())
                && output.getDamageValue() < input.getDamageValue()) {
            // Combining enchantments and renaming remain available, but cannot repair the tool.
            output.setDamageValue(input.getDamageValue());
            event.setOutput(output);
        }
    }

    @SubscribeEvent public static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof ThaumicTools.Repairable)) return;
        if (stack.getItem() instanceof ThaumicTools.Intrinsic tool && event.getEntity() != null) {
            var registry = event.getEntity().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            registry.get(tool.intrinsic()).ifPresent(holder -> event.getToolTip().add(
                    appeng.core.localization.GuiText.IntrinsicEnchant.text(Enchantment.getFullname(holder, 1))));
        }
        event.getToolTip().add(Component.translatable("thaumicenergistics_ce.tools.vis_repair")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
