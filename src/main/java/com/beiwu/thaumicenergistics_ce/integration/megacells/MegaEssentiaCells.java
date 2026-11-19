package com.beiwu.thaumicenergistics_ce.integration.megacells;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.item.ItemEssentiaCell;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Optional content has no hard linkage to Mega Cells classes, and no registry entries without the mod. */
public final class MegaEssentiaCells {
    public static final String MOD_ID = "megacells";
    private static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(ThEIds.MODID);
    private static final List<DeferredItem<Item>> COMPONENTS = new ArrayList<>();
    private static final List<DeferredItem<ItemEssentiaCell>> CELLS = new ArrayList<>();
    private static DeferredItem<Item> casing;

    private MegaEssentiaCells() {}

    public static void register(IEventBus bus) {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        casing = REGISTRY.registerSimpleItem("advanced_storage_casing", new Item.Properties().rarity(Rarity.RARE));
        int[] tiers = {1, 4, 16, 64, 256};
        for (int i = 0; i < tiers.length; i++) {
            int megabytes = tiers[i];
            double drain = 3.0 + i * 0.5;
            COMPONENTS.add(REGISTRY.registerSimpleItem("storage_component_" + megabytes + "m"));
            CELLS.add(REGISTRY.registerItem("essentia_cell_" + megabytes + "m",
                    properties -> ItemEssentiaCell.createMega(properties, megabytes, drain),
                    new Item.Properties().rarity(Rarity.RARE)));
        }
        REGISTRY.register(bus);
    }

    public static void display(CreativeModeTab.Output output) {
        if (casing == null) return;
        output.accept(casing.get());
        COMPONENTS.forEach(item -> output.accept(item.get()));
        CELLS.forEach(item -> output.accept(item.get()));
    }

    public static void registerUpgrades() {
        for (var item : CELLS) {
            Upgrades.add(AEItems.INVERTER_CARD, item.get(), 1, "item.thaumicenergistics_ce.essentia_cell");
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, item.get(), 1, "item.thaumicenergistics_ce.essentia_cell");
            Upgrades.add(AEItems.VOID_CARD, item.get(), 1, "item.thaumicenergistics_ce.essentia_cell");
        }
    }
}
