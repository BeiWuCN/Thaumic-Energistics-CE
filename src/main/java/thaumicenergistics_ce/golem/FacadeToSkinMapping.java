package thaumicenergistics_ce.golem;

import appeng.api.implementations.items.IFacadeItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics_ce.ThEIds;

/**
 * Which backpack skin a block asks for. Matching is by block id inside Thaumaturge's namespace
 * and by substring on purpose, since a block, a plank, a log and a set of stairs all mean the
 * same material to a player pointing at them. AE2 facades are unwrapped first, and anything not
 * listed returns null, leaving the skin alone.
 */
public final class FacadeToSkinMapping {

    private FacadeToSkinMapping() {}

    @Nullable
    public static BackpackSkins skinFor(ItemStack stack) {
        Block block = blockOf(stack);
        if (block == null) {
            return null;
        }

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null && ThEIds.THAUMATURGE.equals(id.getNamespace())) {
            String path = id.getPath();
            if (path.contains("thaumium")) {
                return BackpackSkins.Thaumium;
            }
            if (path.contains("tallow")) {
                return BackpackSkins.Tallow;
            }
            if (path.contains("greatwood") || path.contains("silverwood")) {
                return BackpackSkins.Wood;
            }
            if (path.contains("flesh") || path.contains("taint")) {
                return BackpackSkins.Flesh;
            }
        }

        if (block == Blocks.STONE) {
            return BackpackSkins.Stone;
        }
        if (block == Blocks.HAY_BLOCK) {
            return BackpackSkins.Straw;
        }
        if (block == Blocks.BRICKS) {
            return BackpackSkins.Clay;
        }
        if (block == Blocks.IRON_BLOCK) {
            return BackpackSkins.Iron;
        }
        if (block == Blocks.GOLD_BLOCK) {
            return BackpackSkins.Gold;
        }
        if (block == Blocks.DIAMOND_BLOCK) {
            return BackpackSkins.Diamond;
        }
        return null;
    }

    @Nullable
    private static Block blockOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }
        if (stack.getItem() instanceof IFacadeItem facadeItem) {
            try {
                BlockState state = facadeItem.getTextureBlockState(stack);
                return state == null ? null : state.getBlock();
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
