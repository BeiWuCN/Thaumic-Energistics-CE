package com.beiwu.thaumicenergistics_ce.recipe.arcane;

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
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcArcanePayment;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcWand;

/**
 * 两种付费源由终端按钮决定，冻结在 [TerminalArcaneCraftingInput] 里：
 * 法杖模式直接放行 Thaumaturge 的成本，它原生按 2:1 用法杖 6 要素顶掉水晶，
 * 顶不掉的才落进 crystalsNeeded；水晶模式则把这份 wandCentivis 搬回水晶，
 * 让整颗水晶先付，剩余的份额仍留在法杖上。两条路的需求都不减，合成不会变便宜。
 * 改事件不改工作台：Thaumaturge 收下替换后的成本并照它扣费，晶体够不够由它自己复查。
 * 无属性灵气原样用规划器给的价，套上工作台晶体路径的加价会让同一场合成凭空贵四分之一。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class TerminalCrystalPayment {
    private TerminalCrystalPayment() {}

    @SubscribeEvent
    public static void onArcaneCraftCost(ArcaneCraftCostEvent event) {
        if (!(event.getWorkbench() instanceof TerminalArcaneCraftingInput terminal)) {
            return;
        }
        // 法杖模式：Thaumaturge 已按 2 点要素顶 1 颗水晶算好 wandCentivis，原样放行。
        if (!terminal.crystalFirst()) {
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
