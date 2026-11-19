package com.beiwu.thaumicenergistics_ce.loot;

import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/** One replacement roll per archaeology reward, never from ordinary mining or chests. */
public final class ArchaeologyTemplateModifier extends LootModifier {
    public static final MapCodec<ArchaeologyTemplateModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, ArchaeologyTemplateModifier::new));

    public ArchaeologyTemplateModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var table = context.getQueriedLootTableId();
        if (table != null && table.getPath().startsWith("archaeology/")
                && context.getRandom().nextFloat() < 0.015F) {
            loot.clear();
            loot.add(new ItemStack(TECEItems.THAUMIC_UPGRADE_TEMPLATE.get()));
        }
        return loot;
    }

    @Override public MapCodec<ArchaeologyTemplateModifier> codec() { return CODEC; }
}
