package thaumicenergistics_ce.recipe;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.library.load.registration.RecipeTransferRegistration;
import thaumicenergistics_ce.integration.jei.ArcaneCraftingRecipeTransfer;
import thaumicenergistics_ce.integration.jei.CraftingRecipeTransfer;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;

final class ArcaneJeiTransferChecks {
    static void run(MenuArcaneCraftingTerminal wired, MenuArcaneCraftingTerminal wireless) {
        // Exercise JEI's real class/category registry and menu-type selection, not a simulated map.
        var registration = new RecipeTransferRegistration(null, null, null, null);
        var arcane = new ArcaneCraftingRecipeTransfer(null);
        var crafting = new CraftingRecipeTransfer(null);
        // The actual Thaumaturge category loads client classes. Use JEI's neutral crafting
        // category here to test registry selection for each handler on a dedicated server.
        verify(registration, wired, wireless, crafting);
        verify(registration, wired, wireless, arcane);
        System.out.println("[arcane-jei-transfer] PASS: real JEI registry selects both block/wireless menus for both transfer handlers");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void verify(RecipeTransferRegistration registration, MenuArcaneCraftingTerminal wired,
            MenuArcaneCraftingTerminal wireless, mezz.jei.api.recipe.transfer.IRecipeTransferHandler handler) {
        registration.addRecipeTransferHandler(handler, RecipeTypes.CRAFTING);
        var manager = registration.createRecipeTransferManager();
        try {
            // Calling the internal selection avoids loading client-only category rendering types.
            var lookup = manager.getClass().getDeclaredMethod("getHandler", Class.class,
                    net.minecraft.world.inventory.MenuType.class, IRecipeType.class);
            lookup.setAccessible(true);
            var wiredHandler = (java.util.Optional<?>) lookup.invoke(manager, wired.getClass(), wired.getType(), RecipeTypes.CRAFTING);
            var wirelessHandler = (java.util.Optional<?>) lookup.invoke(manager, wireless.getClass(), wireless.getType(), RecipeTypes.CRAFTING);
            if (wiredHandler.isEmpty() || wirelessHandler.isEmpty()
                    || wiredHandler.get() != handler || wirelessHandler.get() != handler) {
                throw new IllegalStateException("JEI did not select shared transfer handler for both terminal menus");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("JEI registry lookup failed", e);
        }
    }

}
