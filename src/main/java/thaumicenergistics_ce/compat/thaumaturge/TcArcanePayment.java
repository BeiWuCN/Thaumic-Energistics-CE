package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.casters.CasterManager;
import com.leclowndu93150.thaumaturge.content.wands.WandEconomy;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Thaumaturge 奥术付费的常数和灵气折扣。
 * 都在 {@code content} 包里，不在 {@code api} 包，会变动；
 * 终端的「晶体先付」靠这几个数换算，它们挪到哪这里就跟到哪。
 */
public final class TcArcanePayment {
    private TcArcanePayment() {}

    /** 一颗魔力水晶在法杖口径下值多少 centivis —— 两边换算共用它（一水晶 = 2 vis = 200）。 */
    public static int centivisPerCrystal() {
        return WandEconomy.CRYSTAL_SUBSTITUTE_VIS * WandEconomy.CENTIVIS_PER_VIS;
    }

    /**
     * 一次合成改由晶体付之后，无属性灵气那部分要多少钱：工作台走晶体路径的价
     * （{@code CRAFT_AURA_SURCHARGE} 加价乘装备折扣）。
     * <b>目前没接上</b>：{@link thaumicenergistics_ce.arcane.TerminalCrystalPayment}
     * 原样保留规划器算的灵气价，怕同一场合成凭空贵四分之一；
     * 要让终端严格按工作台晶体路径计价，把 {@code event.setCost(...)} 的第 4 参换成它。
     */
    public static int auraVisForCrystals(IArcaneRecipe recipe, Player player) {
        if (recipe.visCost() <= 0) {
            return 0;
        }
        float modifier = WandEconomy.CRAFT_AURA_SURCHARGE * gearModifier(player);
        return Math.max(1, Mth.ceil(recipe.visCost() * modifier));
    }

    private static float gearModifier(Player player) {
        return Math.max(
                1.0F - CasterManager.getTotalVisDiscount(player), WandEconomy.MIN_CONSUMPTION_MODIFIER);
    }
}
