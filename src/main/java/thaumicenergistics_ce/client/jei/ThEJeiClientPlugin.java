package thaumicenergistics_ce.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.client.gui.ScreenEssentiaExportBus;
import thaumicenergistics_ce.client.gui.ScreenEssentiaImportBus;
import thaumicenergistics_ce.client.gui.ScreenEssentiaStorageBus;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;

/**
 * The client half of {@link thaumicenergistics_ce.integration.jei.ThEJeiPlugin}: the ghost ingredient
 * handlers, and the screens they drop into.
 *
 * <ul>
 *   <li>A second plugin rather than a method on the first: JEI's {@code registerGuiHandlers} takes an
 *       {@code IGuiHandlerRegistration}, whose own signatures carry {@code Screen}, so the call can only be
 *       written where client classes may be named. Splitting the plugin keeps every screen class out of the
 *       common tree, which a dedicated server never loads.</li>
 *   <li>JEI reaches that call only from its client starter: {@code JustEnoughItemsClient} hands a reload
 *       listener to {@code JeiStarter}, and the starter is what asks {@code PluginLoader} for the screen
 *       helper that calls this method. Nothing on a dedicated server runs it.</li>
 *   <li>Its own UID: the recipe transfer half keeps the one it has always had.</li>
 * </ul>
 */
@JeiPlugin
public class ThEJeiClientPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "jei_client_plugin");

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
                ScreenEssentiaImportBus.class,
                new EssentiaBusGhostIngredientHandler<ScreenEssentiaImportBus>());
        registration.addGhostIngredientHandler(
                ScreenEssentiaExportBus.class,
                new EssentiaBusGhostIngredientHandler<ScreenEssentiaExportBus>());
        // The storage bus too: while its screen was AE2's UpgradeableScreen there was no class to register
        // against, so an aspect could not be dragged into its config grid.
        registration.addGhostIngredientHandler(
                ScreenEssentiaStorageBus.class,
                new EssentiaBusGhostIngredientHandler<ScreenEssentiaStorageBus>());
        // And the cell workbench's partition wells: the same kind of grid holding the same kind of key.
        registration.addGhostIngredientHandler(
                ScreenEssentiaCellWorkbench.class, new CellWorkbenchGhostIngredientHandler());
        // And the Distillation Encoder's source well, so the item to distil can be dragged in rather than
        // fetched from a terminal by hand.
        registration.addGhostIngredientHandler(
                ScreenDistillationEncoder.class, new DistillationEncoderGhostIngredientHandler());
    }
}
