package thaumicenergistics_ce.recipe;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.leclowndu93150.thaumaturge.content.workbench.WorkbenchPayment;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thaumicenergistics_ce.recipe.arcane.TerminalArcaneCraftingInput;
import thaumicenergistics_ce.compat.thaumaturge.TcArcanePayment;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.integration.ae2.essentia.EssentiaFillHelper;

final class TransferAndPaymentChecks {
    private TransferAndPaymentChecks() {}

    static void run(MinecraftServer server) {
        var aspects = server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY);
        Holder<IAspect> ignis = aspects.getOrThrow(TTAspects.IGNIS);
        checkDeposit(ignis, 2, 3, Long.MAX_VALUE);
        checkDeposit(ignis, 2, 3, 0);
        checkDeposit(ignis, 2, 13, 2);
        checkDeposit(ignis, 2, Long.MAX_VALUE, Long.MAX_VALUE);
        checkMixedPayment(server);
        System.out.println("[transfer-payment-checks] PASS: deposit rollback, refused rollback, stacked remainders, mixed payment");
    }

    private static void checkDeposit(Holder<IAspect> aspect, int count, long insertLimit, long rollbackLimit) {
        ItemStack original = TcRegistry.filledPhial(aspect, TcRegistry.phialCapacity());
        original.setCount(count);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("transfer fixture"));
        long before = amount(original);
        TestStorage storage = new TestStorage(insertLimit, rollbackLimit);
        IEnergySource energy = (requested, mode, multiplier) -> requested;
        var result = EssentiaFillHelper.emptyIntoNetwork(storage, energy, IActionSource.empty(), original);
        require(result != null, "Deposit returned no result");
        long remaining = amount(result.remaining());
        int containers = result.remaining().getCount();
        require(result.remaining().get(DataComponents.CUSTOM_NAME) != null, "Container metadata lost");
        for (ItemStack extra : result.overflow()) {
            remaining += amount(extra);
            containers += extra.getCount();
            require(extra.get(DataComponents.CUSTOM_NAME) != null, "Split container metadata lost");
        }
        require(storage.stored + remaining == before, "Deposit duplicated or lost essentia");
        require(containers == count, "Deposit lost containers");
        require(amount(original) == before, "Input changed before caller replaced it");
        if (insertLimit < before && rollbackLimit >= insertLimit) {
            require(storage.stored == 0 && remaining == before, "Failed deposit did not roll back");
        }
    }

    private static long amount(ItemStack stack) {
        return stack.getCapability(EssentiaCapabilities.CONTAINER).getAspects().entries().stream()
                .mapToLong(entry -> (long) entry.amount() * stack.getCount()).sum();
    }

    private static void checkMixedPayment(MinecraftServer server) {
        IArcaneRecipe recipe = server.getRecipeManager().getRecipes().stream()
                .map(holder -> holder.value()).filter(value -> value instanceof IArcaneRecipe)
                .map(value -> (IArcaneRecipe) value)
                .filter(value -> value.crystalCost().entries().stream().anyMatch(entry -> entry.amount() >= 2))
                .findFirst().orElseThrow(() -> new AssertionError("No mixed-payment fixture recipe"));
        var target = recipe.crystalCost().entries().stream().filter(entry -> entry.amount() >= 2).findFirst().orElseThrow();
        var key = target.aspect().unwrapKey().orElseThrow();
        ItemStack wand = new ItemStack(TTItems.WAND.get());
        int perCrystal = TcArcanePayment.centivisPerCrystal();
        WandVisHelper.setStored(wand, key, perCrystal);
        List<ItemStack> crystals = new ArrayList<>();
        for (var entry : recipe.crystalCost().entries()) {
            int count = entry.amount() - (entry.aspect().equals(target.aspect()) ? 1 : 0);
            if (count > 0) {
                crystals.add(TcRegistry.crystalStack(entry.aspect(), count));
            }
        }
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        var input = new TerminalArcaneCraftingInput(
                java.util.Collections.nCopies(9, ItemStack.EMPTY), player, wand, crystals, null);
        var mixed = WorkbenchPayment.plan(recipe, input, player);
        require(mixed.cost().affordable(), "One crystal plus partial wand charge cannot pay");
        require(mixed.cost().wandCentivis().get(key) == perCrystal, "Wrong remaining wand cost");
        require(mixed.cost().crystalsNeeded().amountOf(target.aspect()) == target.amount() - 1,
                "Crystals were not used first");
        require(WandVisHelper.storedIn(wand, key) == perCrystal, "Planning consumed wand charge");
        WandVisHelper.setStored(wand, key, perCrystal - 1);
        require(!WorkbenchPayment.plan(recipe, input, player).cost().affordable(), "Insufficient wand charge accepted");
        crystals.add(TcRegistry.crystalStack(target.aspect(), 1));
        var crystalOnly = new TerminalArcaneCraftingInput(
                java.util.Collections.nCopies(9, ItemStack.EMPTY), player, ItemStack.EMPTY, crystals, null);
        var paid = WorkbenchPayment.plan(recipe, crystalOnly, player);
        require(paid.cost().affordable() && paid.cost().wandCentivis().isEmpty(), "Crystal-only payment failed");
        for (var entry : recipe.crystalCost().entries()) {
            WandVisHelper.setStored(wand, entry.aspect().unwrapKey().orElseThrow(), entry.amount() * perCrystal);
        }
        var unrelated = recipe.crystalCost().entries().stream()
                .map(entry -> entry.aspect().unwrapKey().orElseThrow()).toList();
        var other = TTAspects.PRIMALS.stream().filter(aspect -> !unrelated.contains(aspect)).findFirst();
        if (other.isPresent()) {
            var irrelevantCrystals = List.of(TcRegistry.crystalStack(
                    server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(other.get()), 1));
            var wandOnly = new TerminalArcaneCraftingInput(
                    java.util.Collections.nCopies(9, ItemStack.EMPTY), player, wand, irrelevantCrystals, null);
            require(WorkbenchPayment.plan(recipe, wandOnly, player).cost().paidFromWand(),
                    "Unrelated crystal changed wand-only payment");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestStorage implements MEStorage {
        private final long insertLimit;
        private final long rollbackLimit;
        private long stored;

        private TestStorage(long insertLimit, long rollbackLimit) {
            this.insertLimit = insertLimit;
            this.rollbackLimit = rollbackLimit;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.SIMULATE) {
                return amount;
            }
            long inserted = Math.min(amount, insertLimit);
            stored += inserted;
            return inserted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long taken = Math.min(amount, Math.min(stored, rollbackLimit));
            if (mode == Actionable.MODULATE) {
                stored -= taken;
            }
            return taken;
        }

        @Override
        public Component getDescription() {
            return Component.literal("transfer regression fixture");
        }
    }
}
