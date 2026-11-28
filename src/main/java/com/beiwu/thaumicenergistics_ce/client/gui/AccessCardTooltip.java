package com.beiwu.thaumicenergistics_ce.client.gui;

import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.config.ThEConfig;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class AccessCardTooltip {
    private AccessCardTooltip() {}

    @SubscribeEvent
    public static void appendDisabledNote(ItemTooltipEvent event) {
        if (!event.getItemStack().is(TECEItems.ESSENTIA_ACCESS_CARD.get())) return;
        // Server configs are synced to clients, so this reads the world's own setting.
        if (ThEConfig.ARCANE_ESSENTIA_ACCESS.get()) return;
        event.getToolTip().add(Component.translatable(
                "tooltip.thaumicenergistics_ce.essentia_access_card.arcane_disabled").withStyle(ChatFormatting.GRAY));
    }
}
