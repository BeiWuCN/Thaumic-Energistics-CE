package com.beiwu.thaumicenergistics_ce.item;

import appeng.hooks.IntrinsicEnchantItem;
import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import appeng.items.tools.quartz.QuartzWrenchItem;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import com.beiwu.thaumicenergistics_ce.ThEIds;

public final class ThaumicTools {
    public static final TagKey<Item> REPAIR_MATERIAL = TagKey.create(Registries.ITEM, ThEIds.id("thaumic_tool_materials"));
    public static final Tier MATERIAL = new Tier() {
        public int getUses() { return 960; }
        public float getSpeed() { return 7.6F; }
        public float getAttackDamageBonus() { return 2.7F; }
        public int getEnchantmentValue() { return 14; }
        public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
            return net.minecraft.world.item.crafting.Ingredient.EMPTY;
        }
        public TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_IRON_TOOL;
        }
    };
    private ThaumicTools() {}

    public interface Repairable {}
    public interface Intrinsic extends Repairable, IntrinsicEnchantItem {
        default ResourceKey<Enchantment> intrinsic() { return Enchantments.FORTUNE; }
        @Override default int getIntrinsicEnchantLevel(ItemStack instance, Holder<Enchantment> enchantment) {
            return enchantment.is(intrinsic()) ? 1 : 0;
        }
    }
    public static final class Pickaxe extends PickaxeItem implements Intrinsic {
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }

        public Pickaxe(Properties p) { super(MATERIAL, p.attributes(PickaxeItem.createAttributes(MATERIAL, 1, -2.8F))); }
    }
    public static final class Sword extends SwordItem implements Intrinsic {
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }

        public Sword(Properties p) { super(MATERIAL, p.attributes(SwordItem.createAttributes(MATERIAL, 3, -2.4F))); }
        @Override public ResourceKey<Enchantment> intrinsic() { return Enchantments.LOOTING; }
    }
    public static final class Axe extends AxeItem implements Intrinsic {
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }

        public Axe(Properties p) { super(MATERIAL, p.attributes(AxeItem.createAttributes(MATERIAL, 5.2F, -3.05F))); }
    }
    public static final class Shovel extends ShovelItem implements Intrinsic {
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }

        public Shovel(Properties p) { super(MATERIAL, p.attributes(ShovelItem.createAttributes(MATERIAL, 1.5F, -3.0F))); }
    }
    public static final class Hoe extends HoeItem implements Intrinsic {
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }

        public Hoe(Properties p) { super(MATERIAL, p.attributes(HoeItem.createAttributes(MATERIAL, -2, -1.0F))); }
    }
    public static final class Wrench extends QuartzWrenchItem implements Repairable {
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }

        public Wrench(Properties p) { super(p.durability(960)); }
    }
    public static final class Knife extends QuartzCuttingKnifeItem implements Repairable {
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }

        public Knife(Properties p) { super(p.durability(960), appeng.items.tools.quartz.QuartzToolType.CERTUS); }
    }

    public static SmithingTemplateItem template(Item.Properties p) {
        return new SmithingTemplateItem(
                Component.translatable("thaumicenergistics_ce.tools.template.applies"),
                Component.translatable("item.thaumicenergistics_ce.thaumic_upgrade_smithing_template"),
                Component.translatable("item.thaumicenergistics_ce.thaumic_upgrade_smithing_template"),
                Component.translatable("thaumicenergistics_ce.tools.template.base"),
                Component.translatable("thaumicenergistics_ce.tools.template.addition"),
                java.util.List.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("container/slot/pickaxe")),
                java.util.List.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/empty_slot_ingot")));
    }
}
