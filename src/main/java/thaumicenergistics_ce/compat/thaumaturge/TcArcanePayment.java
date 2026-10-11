package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.casters.CasterManager;
import com.leclowndu93150.thaumaturge.content.wands.WandEconomy;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class TcArcanePayment {
    private TcArcanePayment() {}

    public static int centivisPerCrystal() {
        return WandEconomy.CRYSTAL_SUBSTITUTE_VIS * WandEconomy.CENTIVIS_PER_VIS;
    }

    public static int auraVisForCrystals(IArcaneRecipe recipe, Player player) {
        if (recipe.visCost() <= 0) {
            return 0;
        }
        float modifier = WandEconomy.CRAFT_AURA_SURCHARGE * gearModifier(player);
        return Math.max(1, Mth.ceil(recipe.visCost() * modifier));
    }

    private static float gearModifier(Player player) {
        return Math.max(
                1.0F - CasterManager.visDiscountOf(player), WandEconomy.MIN_CONSUMPTION_MODIFIER);
    }
}
