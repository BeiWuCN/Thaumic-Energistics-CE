package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCostEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcArcanePayment;

/**
 * 奥术合成终端的付费顺序：先花水晶槽里的水晶，还剩的才算到法杖头上。
 *
 * <p>Thaumaturge 规划器自己的顺序是法杖 → 外部 vis 源 → 晶体
 * （见 {@code WorkbenchPayment#calculateCrystalNeeds}），手搓工作台照旧走那个顺序。
 * 终端里反过来：终端的水晶槽就是玩家为合成备的料，能顶掉法杖那份 vis 就先顶；
 * 水晶顶不掉的份额仍留在法杖上，需求不减，所以合成不会因此变便宜。
 *
 * <p>改成本事件而不是改工作台：Thaumaturge 收下替换后的成本并照它扣费，
 * 晶体够不够由它自己复查（{@code WorkbenchPayment#hasCrystals}），
 * 这里的 {@code affordable} 只是不抢在它前面拦。
 * 终端不注册 {@code IWorkbenchVisSource}，所以没有「外部 vis 源」那一档要改。
 *
 * <p>无属性灵气那部分的价格原样留着：规划器给多少就给多少。
 * 玩家要的是「谁出这份 vis」，不是改价；这里套上工作台晶体路径的加价
 * （{@code TcArcanePayment#auraVisForCrystals}）会让同一场合成凭空贵四分之一。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class TerminalCrystalPayment {
    private TerminalCrystalPayment() {}

    @SubscribeEvent
    public static void onArcaneCraftCost(ArcaneCraftCostEvent event) {
        if (!(event.getWorkbench() instanceof TerminalArcaneCraftingInput terminal)) {
            return;
        }
        ArcaneCraftCost cost = event.getCost();
        if (cost.wandCentivis().isEmpty()) {
            return;
        }
        AspectList available = terminal.availableCrystals();
        if (available.isEmpty()) {
            return;
        }
        int perCrystal = TcArcanePayment.centivisPerCrystal();
        Map<ResourceKey<IAspect>, Integer> wandCentivis = new LinkedHashMap<>(cost.wandCentivis());
        AspectList crystalsNeeded = cost.crystalsNeeded();
        boolean moved = false;
        for (AspectInstance entry : available.entries()) {
            ResourceKey<IAspect> key = entry.aspect().unwrapKey().orElse(null);
            if (key == null) {
                continue;
            }
            Integer wandShare = wandCentivis.get(key);
            if (wandShare == null) {
                continue;
            }
            // 法杖那份是「配方颗数 × 200 centivis」，除掉就是配方要的颗数；
            // 手里的水晶不够就只顶这么多，余下的仍由法杖出。
            int take = Math.min(wandShare / perCrystal, entry.amount());
            if (take <= 0) {
                continue;
            }
            int left = wandShare - take * perCrystal;
            if (left > 0) {
                wandCentivis.put(key, left);
            } else {
                wandCentivis.remove(key);
            }
            crystalsNeeded = crystalsNeeded.add(entry.aspect(), take);
            moved = true;
        }
        if (!moved) {
            return;
        }
        event.setCost(new ArcaneCraftCost(
                false, wandCentivis, crystalsNeeded, cost.auraVis(), true));
    }
}
