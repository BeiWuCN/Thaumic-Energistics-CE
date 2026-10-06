package thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.InterfaceScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.client.gui.ScreenEssentiaStorageBus;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;

/**
 * The client half of {@link thaumicenergistics_ce.integration.jei.ThEJeiPlugin}: the ghost ingredient
 * handlers, and the screens they drop into.
 * <ul>
 *   <li>A second plugin, because the registration {@code registerGuiHandlers} takes names Screen itself.
 *   <li>JEI reaches it only from its client starter; its UID is its own, separate from the transfer half.
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
        // Buses take essentia, not items, so their drag targets accept Thaumaturge's aspect ingredient. One
        // handler per concrete screen class: JEI pairs a Class with a handler of that same type.
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
        // AE2's own ME interface, once our access card is in it: both host forms share this one screen,
        // so this single line covers the block and the cable part.
        registration.addGhostIngredientHandler(
                InterfaceScreen.class, new EssentiaInterfaceGhostIngredientHandler());
    }
}
