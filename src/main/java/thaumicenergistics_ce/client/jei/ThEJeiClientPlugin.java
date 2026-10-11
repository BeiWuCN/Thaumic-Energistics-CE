package thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.IOBusScreen;
import appeng.client.gui.implementations.StorageBusScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.Identifier;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.client.gui.ScreenDistillationEncoder;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.client.gui.ScreenKnowledgeInscriber;

@JeiPlugin
public class ThEJeiClientPlugin implements IModPlugin {

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "jei_client_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(
                ScreenKnowledgeInscriber.class, new KnowledgeInscriberGhostIngredientHandler());
        registration.addGhostIngredientHandler(
                ScreenEssentiaCellWorkbench.class, new CellWorkbenchGhostIngredientHandler());
        registration.addGhostIngredientHandler(
                ScreenDistillationEncoder.class, new DistillationEncoderGhostIngredientHandler());
        registration.addGhostIngredientHandler(
                IOBusScreen.class, new EssentiaBusGhostIngredientHandler<IOBusScreen>());
        registration.addGhostIngredientHandler(
                StorageBusScreen.class, new EssentiaBusGhostIngredientHandler<StorageBusScreen>());
    }
}
