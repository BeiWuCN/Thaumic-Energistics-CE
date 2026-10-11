package thaumicenergistics_ce.recipe;

import appeng.core.definitions.AEBlocks;
import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

public final class EssentiaCellRecipeChecks {
    private EssentiaCellRecipeChecks() {}

    public static void run(MinecraftServer server) {
        for (String tier : new String[] {"1k", "4k", "16k", "64k"}) {
            var shaped = recipe(server, "essentia_cell_" + tier);
            var slots = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
            slots.set(0, new ItemStack(AEBlocks.QUARTZ_GLASS.asItem()));
            slots.set(2, new ItemStack(AEBlocks.QUARTZ_GLASS.asItem()));
            for (int i : new int[] {1, 3, 5}) { slots.set(i, new ItemStack(Items.REDSTONE)); }
            slots.set(4, stack("thaumicenergistics_ce:storage_component_" + tier));
            for (int i : new int[] {6, 7, 8}) { slots.set(i, stack("thaumaturge:ingot_thaumium")); }
            require(shaped.matches(CraftingInput.of(3, 3, slots), server.overworld()), "Thaumium recipe rejected: " + tier);
            for (int i : new int[] {6, 7, 8}) { slots.set(i, new ItemStack(Items.IRON_INGOT)); }
            require(!shaped.matches(CraftingInput.of(3, 3, slots), server.overworld()), "Iron recipe still matches: " + tier);
            var shapeless = recipe(server, "essentia_cell_" + tier + "_from_casing");
            slots = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
            slots.set(0, stack("thaumicenergistics_ce:storage_casing"));
            slots.set(8, stack("thaumicenergistics_ce:storage_component_" + tier));
            require(shapeless.matches(CraftingInput.of(3, 3, slots), server.overworld()), "Casing recipe rejected: " + tier);
            Collections.swap(slots, 0, 8);
            require(shapeless.matches(CraftingInput.of(3, 3, slots), server.overworld()), "Casing recipe depends on order: " + tier);
            slots.set(4, new ItemStack(Items.REDSTONE));
            require(!shapeless.matches(CraftingInput.of(3, 3, slots), server.overworld()), "Extra ingredient accepted: " + tier);
        }
        System.out.println("[essentia-cell-recipes] PASS: all four tiers accept thaumium, reject iron and support shapeless casing assembly");
    }

    private static CraftingRecipe recipe(MinecraftServer server, String path) {
        return (CraftingRecipe) server.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.id().identifier().equals(Identifier.parse("thaumicenergistics_ce:" + path)))
                .findFirst().orElseThrow().value();
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
