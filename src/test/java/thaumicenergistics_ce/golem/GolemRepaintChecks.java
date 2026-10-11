package thaumicenergistics_ce.golem;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.leclowndu93150.thaumaturge.registry.TTEntities;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public final class GolemRepaintChecks {
    private GolemRepaintChecks() {}

    public static void run(MinecraftServer server) {
        try {
            check(server);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void check(MinecraftServer server) throws ReflectiveOperationException {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        var golem = new EntityThaumaturgeGolem(TTEntities.THAUMATURGE_GOLEM.get(), server.overworld()) {
            @Override public Optional<UUID> ownerIdentity() { return Optional.of(player.getUUID()); }
        };
        GolemBackpackHandler.setLink(golem, GlobalPos.of(server.overworld().dimension(), BlockPos.ZERO));
        var repaint = GolemBackpackHandler.class.getDeclaredMethod("repaint", EntityThaumaturgeGolem.class,
                Player.class, ItemStack.class, BackpackSkins.class);
        repaint.setAccessible(true);
        var material = new ItemStack(Items.GOLD_BLOCK, 8);
        require((boolean) repaint.invoke(null, golem, player, material, BackpackSkins.Gold));
        require(material.getCount() == 8 && GolemBackpackHandler.getFacade(golem).isEmpty());
        require(GolemBackpackHandler.getSkin(golem) == BackpackSkins.Gold);
        var sent = GolemBackpackTickHandler.class.getDeclaredField("LAST_SENT");
        sent.setAccessible(true);
        var statuses = (Map<?, ?>) sent.get(null);
        require(((int[]) statuses.get(golem.getUUID()))[1] == BackpackSkins.Gold.ordinal());
        require((boolean) repaint.invoke(null, golem, player, material, BackpackSkins.Gold));
        require(material.getCount() == 8 && GolemBackpackHandler.getFacade(golem).isEmpty());
        GolemBackpackHandler.clear(golem);
        System.out.println("[golem-repaint] PASS: material retained, no refundable duplicate, immediate skin sync and repeated repaint");
    }

    private static void require(boolean condition) {
        if (!condition) { throw new IllegalStateException("Golem repaint consumed material or delayed synchronization"); }
    }
}
