package thaumicenergistics_ce.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.client.ScreenKnowledgeInscriber;

/**
 * Thaumic Energistics' JEI plugin.
 * <ul>
 *   <li>Both registrations use Thaumaturge's arcane recipe category rather than one of our own: the
 *       Knowledge Inscriber encodes exactly what the arcane workbench crafts, so a second page listing
 *       those recipes again would only let the two lists fall out of step.
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
        ThaumicEnergistics.LOG.info("JEI plugin constructed ({})", UID);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(
                ScreenKnowledgeInscriber.class, new KnowledgeInscriberGhostIngredientHandler());
        // Buses take essentia, not items, so their drag targets accept Thaumaturge's aspect ingredient.
        // One handler per concrete screen class: JEI pairs a Class with a handler of that same type.
        registration.addGhostIngredientHandler(
                thaumicenergistics_ce.client.ScreenEssentiaImportBus.class,
                new EssentiaBusGhostIngredientHandler<thaumicenergistics_ce.client.ScreenEssentiaImportBus>());
        registration.addGhostIngredientHandler(
                thaumicenergistics_ce.client.ScreenEssentiaExportBus.class,
                new EssentiaBusGhostIngredientHandler<thaumicenergistics_ce.client.ScreenEssentiaExportBus>());
        // The storage bus too: while its screen was AE2's UpgradeableScreen there was no class to register
        // against, so an aspect could not be dragged into its config grid.
        registration.addGhostIngredientHandler(
                thaumicenergistics_ce.client.ScreenEssentiaStorageBus.class,
                new EssentiaBusGhostIngredientHandler<thaumicenergistics_ce.client.ScreenEssentiaStorageBus>());
        // And the cell workbench's partition wells: the same kind of grid holding the same kind of key.
        registration.addGhostIngredientHandler(
                thaumicenergistics_ce.client.ScreenEssentiaCellWorkbench.class,
                new CellWorkbenchGhostIngredientHandler());
        // And the Distillation Encoder's source well, so the item to distil can be dragged in rather than
        // fetched from a terminal by hand.
        registration.addGhostIngredientHandler(
                thaumicenergistics_ce.client.ScreenDistillationEncoder.class,
                new DistillationEncoderGhostIngredientHandler());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // A handler with its own recipe type, not an IRecipeTransferInfo: a bare info is wrapped in JEI's
        // BasicRecipeTransferHandler, which assumes one slot per container slot and refuses 12 vs 9.
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberRecipeTransfer(registration.getTransferHelper()),
                ArcaneRecipeTypes.arcane());
        // And the Arcane Crafting Terminal's grid, from the same category; a second handler, not a shared
        // one, because the inscriber's grid is a ghost grid and the terminal's is real.
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(registration.getTransferHelper()),
                ArcaneRecipeTypes.arcane());
        // Ordinary crafting recipes too. The terminal's grid is nine ordinary slots, so a player who opens a
        // plank recipe and finds no transfer button would reasonably read it as a broken terminal.
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(registration.getTransferHelper()), RecipeTypes.CRAFTING);
    }
}
