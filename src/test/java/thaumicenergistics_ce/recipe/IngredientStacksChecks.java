package thaumicenergistics_ce.recipe;

import appeng.api.stacks.AEItemKey;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipe;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.integration.jei.CrucibleRecipeTypes;

@EventBusSubscriber(modid = ThEIds.MODID)
public final class IngredientStacksChecks {
    private static final boolean AUTO_STOP =
            "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_UPGRADE_VERIFICATION"));
    private static boolean completed;
    private static int ticksAfterChecks;
    private IngredientStacksChecks() {}

    @SubscribeEvent
    public static void run(ServerStartedEvent event) {
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_UPGRADE_SELFTEST"))) {
            return;
        }
        try {
            TransactionChecks.run();
            TerminalAuraConfigChecks.run();
            TerminalChunkAuraChecks.run(event.getServer());
            ArcaneCardSyncChecks.run(event.getServer());
            VisCardBindingChecks.run(event.getServer());
            DirectVisNodeChecks.run(event.getServer());
            QuantumTerminalChecks.run(event.getServer());
            EssentiaCellRecipeChecks.run(event.getServer());
            WirelessShortcutChecks.run();
            thaumicenergistics_ce.integration.thaumaturge.golem.GolemRepaintChecks.run(event.getServer());
            thaumicenergistics_ce.blockentity.assembler.AssemblerVisPoolChecks.run(event.getServer());
            TransferAndPaymentChecks.run(event.getServer());
            OccultMonitorRemovalChecks.run(event.getServer());
            ArcaneQuickMoveChecks.run(event.getServer());
            EssentiaSearchChecks.run(event.getServer());
            TerminalVisibilityChecks.run(event.getServer());
            EssentiaBusChecks.run(event.getServer());
            var registries = event.getServer().registryAccess();
            var aspects = registries.lookupOrThrow(IAspect.REGISTRY_KEY);
            var ignis = aspects.getOrThrow(TTAspects.IGNIS);
            Ingredient crystal = DataComponentIngredient.of(TTDataComponents.CRYSTAL_ASPECT.get(),
                    new AspectInstance(ignis, 1), TTItems.ESSENTIA_CRYSTAL.get());
            var variants = IngredientStacks.variants(crystal, registries);
            require(!variants.isEmpty(), "Ignis crystal has no encodable variant");
            require(variants.stream().allMatch(crystal::test), "Crystal component lost during conversion");
            require(variants.stream().allMatch(stack -> stack.getCount() == 1), "Wrong template count");
            require(!crystal.test(TcRegistry.crystalStack(aspects.getOrThrow(TTAspects.AQUA), 1)),
                    "Different aspect accepted");
            var ordinary = IngredientStacks.variants(Ingredient.of(Items.STONE), registries);
            require(ordinary.size() == 1 && ordinary.getFirst().is(Items.STONE), "Ordinary ingredient failed");
            int crucibleCount = 0;
            for (var holder : event.getServer().getRecipeManager().getRecipes()) {
                if (!(holder.value() instanceof CrucibleRecipe recipe)) {
                    continue;
                }
                var inputs = CrucibleRecipeTypes.inputsFor(holder, registries);
                require(inputs != null && !inputs.isEmpty(), "Unencodable crucible recipe: " + holder.id());
                for (var catalyst : inputs.getFirst()) {
                    require(catalyst.what() instanceof AEItemKey key && recipe.catalyst().test(key.toStack(1)),
                            "Crucible catalyst components lost: " + holder.id());
                }
                crucibleCount++;
            }
            require(crucibleCount > 0, "No crucible recipes loaded");
            System.out.println("[ingredient-checks] PASS: " + crucibleCount + " crucible catalysts preserved");
            System.out.println("[ingredient-checks] PASS: component preservation, aspect filtering, ordinary ingredient");
            completed = true;
        } catch (RuntimeException | Error failure) {
            if (AUTO_STOP) {
                event.getServer().halt(false);
            }
            throw failure;
        }
    }

    @SubscribeEvent
    public static void stopAfterChecks(ServerTickEvent.Post event) {
        if (AUTO_STOP && completed && ++ticksAfterChecks >= 100) {
            event.getServer().halt(false);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
