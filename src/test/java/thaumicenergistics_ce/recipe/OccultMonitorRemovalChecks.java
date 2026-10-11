package thaumicenergistics_ce.recipe;

import com.leclowndu93150.thaumaturge.registry.TTItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import thaumicenergistics_ce.block.BlockOccultMonitor;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModItems;

public final class OccultMonitorRemovalChecks {
    private OccultMonitorRemovalChecks() {}

    public static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        check(level, new BlockPos(12, 200, 12), false, false);
        check(level, new BlockPos(18, 200, 12), true, false);
        check(level, new BlockPos(24, 200, 12), true, true);
        System.out.println("[monitor-removal] PASS: survival mining with/without book, replacement, single book drop");
    }

    private static void check(ServerLevel level, BlockPos pos, boolean withBook, boolean replacement) {
        require(level.getBlockState(pos).isAir(), "Removal fixture position is occupied");
        AABB area = new AABB(pos).inflate(2);
        List<ItemEntity> drops = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof ItemEntity item
                    && area.contains(item.position())) {
                drops.add(item);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        level.setBlockAndUpdate(pos, ModBlocks.OCCULT_MONITOR.get().defaultBlockState());
        var monitor = (BlockEntityOccultMonitor) level.getBlockEntity(pos);
        require(monitor != null, "Monitor block entity missing");
        if (withBook) {
            monitor.interact(new ItemStack(TTItems.THAUMONOMICON.get()), false);
            require(level.getBlockState(pos).getValue(BlockOccultMonitor.BOOK), "Book state was not set");
        }
        try {
            if (replacement) {
                require(level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()), "Replacement failed");
                require(level.getBlockState(pos).is(Blocks.STONE), "Monitor overwrote replacement block");
            } else {
                var player = FakePlayerFactory.getMinecraft(level);
                player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                require(!player.preventsBlockDrops(), "Fixture player prevents block drops");
                require(level.getBlockState(pos).canHarvestBlock(level, pos, player), "Fixture pickaxe cannot harvest monitor");
                require(player.gameMode.destroyBlock(pos), "Survival mining failed");
                require(level.getBlockState(pos).isAir(), "Monitor remained after survival mining");
            }
            require(!(level.getBlockEntity(pos) instanceof BlockEntityOccultMonitor), "Removed monitor still present");
            monitor.dropContents();
            int books = 0;
            int blocks = 0;
            for (ItemEntity item : drops) {
                if (item.getItem().is(TTItems.THAUMONOMICON.get())) {
                    books += item.getItem().getCount();
                }
                if (item.getItem().is(ModItems.OCCULT_MONITOR.get())) {
                    blocks += item.getItem().getCount();
                }
            }
            require(books == (withBook ? 1 : 0), "Wrong book drop count: " + books);
            require(blocks == (replacement ? 0 : 1), "Wrong monitor drop count: " + blocks);
        } finally {
            level.removeBlock(pos, false);
            NeoForge.EVENT_BUS.unregister(listener);
            drops.forEach(ItemEntity::discard);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
