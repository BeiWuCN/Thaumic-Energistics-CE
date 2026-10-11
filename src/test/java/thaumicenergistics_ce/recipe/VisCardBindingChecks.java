package thaumicenergistics_ce.recipe;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import java.lang.reflect.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import thaumicenergistics_ce.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.item.ItemVisConnectionCard;
import thaumicenergistics_ce.part.PartVisInterface;

final class VisCardBindingChecks {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var card = new ItemStack(ModItems.VIS_CONNECTION_CARD.get());
        var remote = new BlockPos(28000000, 64, 28000000);
        require(!level.hasChunkAt(remote), "Remote fixture already loaded");
        ItemVisConnectionCard.bind(card, level.dimension().identifier().toString(), remote, Direction.NORTH);
        require(ItemVisConnectionCard.resolve(level, card.copy()) == null, "Unloaded output resolved");
        require(!level.hasChunkAt(remote), "Binding lookup loaded a chunk");
        ItemVisConnectionCard.bind(card, "minecraft:the_nether", BlockPos.ZERO, Direction.SOUTH);
        require(ItemVisConnectionCard.resolve(level, card) == null, "Cross-dimension binding resolved");

        double[] balance = {1000};
        var energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[] {IEnergyService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("extractAEPower")) {
                        double paid = Math.min((double) args[0], balance[0]);
                        if (args[1] == Actionable.MODULATE) balance[0] -= paid;
                        return paid;
                    }
                    if (method.getName().equals("injectPower")) {
                        if (args[1] == Actionable.MODULATE) balance[0] += (double) args[0];
                        return 0.0;
                    }
                    throw new IllegalStateException("Unexpected energy call: " + method);
                });
        var grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(),
                new Class<?>[] {IGrid.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getService")) return energy;
                    throw new IllegalStateException("Unexpected grid call: " + method);
                });
        var relay = new PartVisInterface(ModItems.VIS_INTERFACE.get()) {
            @Override
            public IGrid getGrid() { return grid; }
            @Override
            public int availableCentivis(ResourceKey<IAspect> primal) {
                return primal.equals(TTAspects.AQUA) ? 1000 : 0;
            }
        };
        try (var tx = Transaction.openRoot()) {
            require(TerminalAuraPayment.payRelay(relay, 125, tx) == 125, "Relay did not use an available primal");
            require(balance[0] == 875, "Wrong relay energy cost");
        }
        require(balance[0] == 1000, "Preview consumed relay energy");
        try (var tx = Transaction.openRoot()) {
            require(TerminalAuraPayment.payRelay(relay, 125, tx) == 125, "Relay commit supply failed");
            tx.commit();
        }
        require(balance[0] == 875, "Committed relay charge was refunded");
        try (var outer = Transaction.openRoot()) {
            try (var inner = Transaction.open(outer)) {
                TerminalAuraPayment.payRelay(relay, 100, inner);
                inner.commit();
            }
        }
        require(balance[0] == 875, "Nested rollback did not refund relay energy");
        System.out.println("[vis-card-binding] PASS: unloaded/cross-dimension rejection, no chunk loading, relay fallback, preview/commit/nested rollback");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
