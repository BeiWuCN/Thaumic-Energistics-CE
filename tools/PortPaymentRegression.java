package thaumicenergistics_ce;

import appeng.util.inv.AppEngInternalInventory;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import java.util.stream.IntStream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingInput;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingStore;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

@GameTestHolder(ThEIds.MODID)
@PrefixGameTestTemplate(false)
public final class PortPaymentRegression {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void previewFailureAndCommit(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("230fa360-d4ae-4fa3-a410-a2628d9a068f"), "PortPayment"));
        var pos = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        AuraHelper.addVis(level, pos, 100);
        float initial = AuraHelper.getVis(level, pos);
        var grid = new AppEngInternalInventory(9);
        var crystals = new AppEngInternalInventory(6);
        var wand = new AppEngInternalInventory(1);
        var aer = thaumicenergistics_ce.integration.ae2.AEssentiaKey.of(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumaturge", "aer")).resolveAspect();
        crystals.setItemDirect(0, thaumicenergistics_ce.arcane.EssentiaCrystals.create(aer, 2));
        var wandItem = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof com.leclowndu93150.thaumaturge.content.wands.ItemWand)
                .findFirst().orElseThrow();
        wand.setItemDirect(0, new ItemStack(wandItem));
        grid.setItemDirect(0, new ItemStack(Items.STRUCTURE_VOID, 3));
        var input = new TerminalArcaneCraftingInput(
                IntStream.range(0, 9).mapToObj(i -> grid.getStackInSlot(i).copy()).toList(), player,
                wand.getStackInSlot(0).copy(), IntStream.range(0, 6).mapToObj(crystals::getStackInSlot).toList(),
                null, (amount, mode, multiplier) -> { throw new AssertionError("Crafting charged AE for environmental VIS"); });
        var context = ArcaneWorkbenchContext.virtual(player, PartArcaneCraftingTerminal.CONTEXT_HOST, player.getUUID());
        var preview = ArcaneCraftingTransaction.preview(context, player, input);
        require(preview.successful(), "Preview failed: " + preview.failure());
        require(preview.cost().auraVis() > 0, "Test did not exercise environmental VIS");
        require(AuraHelper.getVis(level, pos) == initial && grid.getStackInSlot(0).getCount() == 3,
                "Preview consumed resources");
        var rejected = ArcaneCraftingTransaction.craft(context, player, input, (cost, simulate) -> false, false);
        require(!rejected.successful() && AuraHelper.getVis(level, pos) == initial,
                "Rejected craft consumed aura");
        var store = new TerminalArcaneCraftingStore(grid, crystals, wand, player);
        var simulated = ArcaneCraftingTransaction.craft(context, player, input, store, true);
        require(simulated.successful() && AuraHelper.getVis(level, pos) == initial
                && grid.getStackInSlot(0).getCount() == 3, "Simulated craft consumed resources");
        var committed = ArcaneCraftingTransaction.craft(context, player, input, store, false);
        require(committed.successful() && committed.output().is(Items.BARRIER), "Commit failed: " + committed.failure());
        require(grid.getStackInSlot(0).getCount() == 2, "Commit consumed wrong ingredients");
        require(crystals.getStackInSlot(0).getCount() == 1, "Commit consumed wrong crystal count");
        require(Math.abs(AuraHelper.getVis(level, pos) - (initial - committed.cost().auraVis())) < 0.01F,
                "Commit charged wrong aura: before=" + initial + " after=" + AuraHelper.getVis(level, pos)
                        + " cost=" + committed.cost());
        var primal = aer.unwrapKey().orElseThrow();
        // Terminals intentionally prefer their crystal slots; empty them to exercise the wand route.
        crystals.setItemDirect(0, ItemStack.EMPTY);
        thaumicenergistics_ce.compat.thaumaturge.TcWand.addCentivis(wand.getStackInSlot(0), primal, 1000);
        int wandBefore = com.leclowndu93150.thaumaturge.content.wands.WandVisHelper.getVis(wand.getStackInSlot(0), primal);
        var withWand = new TerminalArcaneCraftingInput(
                IntStream.range(0, 9).mapToObj(i -> grid.getStackInSlot(i).copy()).toList(), player,
                wand.getStackInSlot(0).copy(), IntStream.range(0, 6).mapToObj(crystals::getStackInSlot).toList(),
                null, (amount, mode, multiplier) -> { throw new AssertionError("AE payment"); });
        float beforeWandCraft = AuraHelper.getVis(level, pos);
        var wandCraft = ArcaneCraftingTransaction.craft(context, player, withWand, store, false);
        require(wandCraft.successful() && !wandCraft.cost().wandCentivis().isEmpty(),
                "Wand did not substitute for crystals: stored=" + wandBefore + " result=" + wandCraft);
        require(crystals.isEmpty(), "Wand craft created a crystal");
        int spent = wandCraft.cost().wandCentivis().get(primal);
        require(com.leclowndu93150.thaumaturge.content.wands.WandVisHelper.getVis(wand.getStackInSlot(0), primal)
                == wandBefore - spent, "Wrong typed wand payment");
        require(Math.abs(AuraHelper.getVis(level, pos) - (beforeWandCraft - wandCraft.cost().auraVis())) < 0.01F,
                "Wand craft charged wrong environmental VIS");
        AuraHelper.drainVis(level, pos, Float.MAX_VALUE, false);
        var unfundedInput = new TerminalArcaneCraftingInput(
                IntStream.range(0, 9).mapToObj(i -> grid.getStackInSlot(i).copy()).toList(), player,
                wand.getStackInSlot(0).copy(), IntStream.range(0, 6).mapToObj(crystals::getStackInSlot).toList(),
                null, (amount, mode, multiplier) -> { throw new AssertionError("AE payment"); });
        var noAura = ArcaneCraftingTransaction.craft(context, player, unfundedInput, store, false);
        require(!noAura.successful() && grid.getStackInSlot(0).getCount() == 1
                && crystals.isEmpty(), "Unfunded craft consumed items");
        System.out.println("PORT PASS real arcane preview, rejection, crystal/wand payment, insufficient aura and commit without AE");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
