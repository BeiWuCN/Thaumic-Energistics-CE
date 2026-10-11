package thaumicenergistics_ce.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.util.ThELog;

@JeiPlugin
public class ThEJeiPlugin implements IModPlugin {

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "jei_plugin");

    public ThEJeiPlugin() {
        ThELog.LOG.info("JEI plugin constructed ({})", UID);
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.ARCANE_CRAFTING_TERMINAL.get()),
                ArcaneJeiRecipeType.arcane());
        registration.addRecipeCatalyst(new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                ArcaneJeiRecipeType.arcane());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new KnowledgeInscriberRecipeTransfer(registration.getTransferHelper()),
                ArcaneJeiRecipeType.arcane());
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(
                        registration.getTransferHelper(), ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get()),
                ArcaneJeiRecipeType.arcane());
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(
                        registration.getTransferHelper(), ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get()),
                RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(
                new CruciblePatternTransfer(registration.getTransferHelper()),
                CrucibleJeiRecipeType.crucible());
        registration.addRecipeTransferHandler(
                new ArcaneCraftingRecipeTransfer(
                        registration.getTransferHelper(), ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                ArcaneJeiRecipeType.arcane());
        registration.addRecipeTransferHandler(
                new CraftingRecipeTransfer(
                        registration.getTransferHelper(), ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                RecipeTypes.CRAFTING);
    }
}
