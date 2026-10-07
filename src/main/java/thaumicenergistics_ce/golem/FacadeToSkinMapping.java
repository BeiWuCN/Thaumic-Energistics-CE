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
 * 某种方块请求的是哪种背包外观。匹配限于 Thaumaturge 命名空间，按方块 id 走。
 * 匹配刻意用子串：方块、木板、原木、楼梯在玩家眼里是同一种材质。
 * AE2 的伪装板会先被拆开；未列入表的一律返回 null，外观不变。
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
