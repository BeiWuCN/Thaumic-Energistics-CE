package thaumicenergistics_ce.recipe.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCostEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcArcanePayment;
import thaumicenergistics_ce.compat.thaumaturge.TcWand;

@EventBusSubscriber(modid = ThEIds.MODID)
public final class TerminalCrystalPayment {
    private TerminalCrystalPayment() {}

    @SubscribeEvent
    public static void onArcaneCraftCost(ArcaneCraftCostEvent event) {
        if (!(event.getWorkbench() instanceof TerminalArcaneCraftingInput terminal)) {
            return;
        }
        ArcaneCraftCost cost = event.getCost();
        AspectList available = terminal.availableCrystals();
        if (available.isEmpty()) {
            return;
        }
        int perCrystal = TcArcanePayment.centivisPerCrystal();
        Map<ResourceKey<IAspect>, Integer> totalCost = new LinkedHashMap<>(cost.wandCentivis());
        for (AspectInstance entry : cost.crystalsNeeded().entries()) {
            ResourceKey<IAspect> key = entry.aspect().unwrapKey().orElseThrow();
            totalCost.merge(key, entry.amount() * perCrystal, Integer::sum);
        }
        Map<ResourceKey<IAspect>, Integer> wandCentivis = new LinkedHashMap<>();
        AspectList crystalsNeeded = AspectList.EMPTY;
        for (var entry : totalCost.entrySet()) {
            ResourceKey<IAspect> key = entry.getKey();
            Holder<IAspect> aspect = Aspects.resolve(event.getPlayer().level(), key);
            if (aspect == null) {
                return;
            }
            int take = Math.min(entry.getValue() / perCrystal, available.amountOf(aspect));
            int left = entry.getValue() - take * perCrystal;
            if (left > 0) {
                wandCentivis.put(key, left);
            }
            if (take > 0) {
                crystalsNeeded = crystalsNeeded.add(aspect, take);
            }
        }
        if (crystalsNeeded.isEmpty()) {
            return;
        }
        event.setCost(new ArcaneCraftCost(
                false, wandCentivis, crystalsNeeded, cost.auraVis(),
                TcWand.canPayExact(terminal.wandStack(), wandCentivis)));
    }
}
