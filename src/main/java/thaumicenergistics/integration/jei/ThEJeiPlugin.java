package thaumicenergistics.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThEIds;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.client.ScreenKnowledgeInscriber;

/**
 * Thaumic Energistics' JEI plugin.
 *
 * <p>Only one machine has anything to say to JEI so far, and it says two things: which slots of its
 * screen accept a dragged ingredient, and how to fill those slots from an arcane workbench recipe.
 *
 * <p>Both are registered against Thaumaturge's own arcane recipe category rather than a category of our
 * own. The Knowledge Inscriber encodes exactly the recipes the arcane workbench crafts, so a second page
 * listing them again would only be a way for the two lists to fall out of step.
 */
@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "jei_plugin");

    /**
     * A line in the log when JEI constructs this plugin.
     *
     * <p>{@code ForgePluginFinder} discovers plugins by scanning {@code ModFileScanData} for
     * {@code @JeiPlugin} and instantiating what it finds, and nothing reports a plugin it did <em>not</em>
     * find. So "this mod's JEI integration does nothing" and "this mod's JEI integration is not loaded at
     * all" look identical from outside - both are silence. This constructor runs only if the plugin was
     * found, which tells the two apart without a debugger.
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
        // The buses take essentia rather than items, so their drag targets accept Thaumaturge's aspect
        // ingredient - which JEI already lists, because Thaumaturge registers it.
        //
        // Registered once per concrete screen class rather than once for UpgradeableScreen. JEI's
        // registration takes a Class and a handler for that same type, and a handler written over the
        // parent cannot be paired with a raw Class without erasing the type JEI is checking against.
        registration.addGhostIngredientHandler(
                thaumicenergistics.client.ScreenEssentiaImportBus.class,
                new EssentiaBusGhostIngredientHandler<thaumicenergistics.client.ScreenEssentiaImportBus>());
        registration.addGhostIngredientHandler(
                thaumicenergistics.client.ScreenEssentiaExportBus.class,
                new EssentiaBusGhostIngredientHandler<thaumicenergistics.client.ScreenEssentiaExportBus>());
        // The storage bus too. It was left out at first - its screen was AE2's UpgradeableScreen used
        // directly, and there is no class to register a handler against - which made its config grid the
        // one grid an aspect could not be dragged into.
        registration.addGhostIngredientHandler(
                thaumicenergistics.client.ScreenEssentiaStorageBus.class,
                new EssentiaBusGhostIngredientHandler<thaumicenergistics.client.ScreenEssentiaStorageBus>());
        // And the cell workbench's partition wells, which are the same kind of grid holding the same kind
        // of key.
        registration.addGhostIngredientHandler(
                thaumicenergistics.client.ScreenEssentiaCellWorkbench.class,
                new CellWorkbenchGhostIngredientHandler());
        // And the Distillation Encoder's source well, so the item to distil can be dragged in rather than
        // fetched from a terminal by hand.
        registration.addGhostIngredientHandler(
                thaumicenergistics.client.ScreenDistillationEncoder.class,
                new DistillationEncoderGhostIngredientHandler());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // Registered as a *handler* with its own recipe type, not as an IRecipeTransferInfo. Passing the
        // info alone makes JEI wrap it in its BasicRecipeTransferHandler, which assumes one recipe slot
        // per container slot and refuses this screen outright: an arcane recipe's JEI view has twelve
        // ingredient slots (nine grid cells plus its crystal requirement) and the machine has nine. The
        // class still implements IRecipeTransferInfo, because the handler needs those slot lists to
        // report availability - it just must not be the thing JEI registers.
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberRecipeTransfer(registration.getTransferHelper()),
                ArcaneRecipeTypes.arcane());
        // And the Arcane Crafting Terminal's grid, from the same category. A second handler rather than a
        // shared one because the two machines hold the recipe differently: the inscriber's grid is a ghost
        // grid that records intent, and the terminal's is a real one the items are moved into.
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(registration.getTransferHelper()),
                ArcaneRecipeTypes.arcane());
        // Ordinary crafting recipes too. The terminal's grid is nine ordinary slots, so a player who opens a
        // plank recipe and finds no transfer button would reasonably read it as a broken terminal.
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(registration.getTransferHelper()), RecipeTypes.CRAFTING);
    }
}
