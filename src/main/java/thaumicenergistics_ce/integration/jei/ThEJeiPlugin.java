package thaumicenergistics_ce.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.util.ThELog;

/**
 * Thaumic Energistics' JEI plugin: the recipe transfer half, which JEI asks for on both sides.
 * <ul>
 *   <li>Names no screen class, because this is the half a dedicated server also runs. The ghost ingredient
 *       handlers live in {@code client.jei.ThEJeiClientPlugin}, a second plugin with a UID of its own.
 *   <li>Both use Thaumaturge's arcane recipe category: the Inscriber encodes exactly what it crafts.
 * </ul>
 */
@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "jei_plugin");

    /**
     * Logs on construction: {@code ForgePluginFinder} reports the plugins it found and never the ones it
     * missed, so this line tells "not loaded" apart from "loaded and silent" without a debugger.
     */
    public ThEJeiPlugin() {
        ThELog.LOG.info("JEI plugin constructed ({})", UID);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // A handler with its own recipe type, not an IRecipeTransferInfo: a bare info is wrapped in JEI's
        // BasicRecipeTransferHandler, which assumes one slot per container slot and refuses 12 vs 9.
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        // And the Arcane Crafting Terminal's grid, from the same category; a second handler, not a shared
        // one, because the inscriber's grid is a ghost grid and the terminal's is real.
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        // Ordinary crafting recipes too. The terminal's grid is nine ordinary slots, so a player who opens a
        // plank recipe and finds no transfer button would reasonably read it as a broken terminal.
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(registration.getTransferHelper()), RecipeTypes.CRAFTING);
    }
}
