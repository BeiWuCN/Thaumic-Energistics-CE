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
     * 晶体路径下无属性灵气那部分的价（工作台加价乘装备折扣）。目前刻意没接上：
     * [TerminalCrystalPayment] 原样用规划器给的灵气价，套上它同一场合成会凭空贵四分之一。
     */
    public static int auraVisForCrystals(IArcaneRecipe recipe, Player player) {
        if (recipe.getBaseVis() <= 0) {
            return 0;
        }
        float modifier = WandEconomy.CRAFT_AURA_SURCHARGE * gearModifier(player);
        return Math.max(1, Mth.ceil(recipe.getBaseVis() * modifier));
    }

    private static float gearModifier(Player player) {
        return Math.max(
                1.0F - CasterManager.getTotalVisDiscount(player), WandEconomy.MIN_CONSUMPTION_MODIFIER);
    }
}
