package thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.InterfaceScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;

/**
 * The client half of {@link thaumicenergistics_ce.integration.jei.ThEJeiPlugin}: the ghost
 * ingredient handlers, and the screens they drop into. It is a second plugin, because the
 * registerGuiHandlers registration takes a Screen name itself. JEI reaches it only from its client
 * starter, and its UID stays separate from the transfer half.
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
        // One handler per concrete screen class: JEI pairs a Class with a handler of that same type, and the
        // drag target accepts Thaumaturge's aspect ingredient rather than an item.
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
