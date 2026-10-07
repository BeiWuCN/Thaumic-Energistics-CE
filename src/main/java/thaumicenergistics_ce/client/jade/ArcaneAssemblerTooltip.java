package thaumicenergistics_ce.client.jade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.integration.jade.ArcaneAssemblerProvider;
import thaumicenergistics_ce.integration.jade.JadeGridState;

/**
 * 奥术组装机的 Jade tooltip：{@link ArcaneAssemblerProvider} 的绘制半边。
 * 它需要的一切都在服务端写好的数据标签里，而注册表查询用的是客户端
 * 自己的，这就是这一半位于客户端代码树的原因。两半都报告
 * {@link ArcaneAssemblerProvider#UID}，Jade 借此把它们配对。
 */
public final class ArcaneAssemblerTooltip implements IBlockComponentProvider {

    public static final ArcaneAssemblerTooltip INSTANCE = new ArcaneAssemblerTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            // 只有方块实体并非组装机时才可能走到这里。宁可什么都不显示，也不显示错行。
            return;
        }
        var helper = IElementHelper.get();

        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        if (tag.getBoolean(ArcaneAssemblerProvider.TAG_CRAFTING)) {
            tooltip.add(helper.text(
                    Component.translatable("jade.thaumicenergistics_ce.arcane_assembler.crafting")
                            .withStyle(ChatFormatting.WHITE)));
            // 箭头那一行：左边是投入什么，右边是产出什么，于是“在做什么”和“用什么
            // 做”都不需要文字；图标用全尺寸，因为 Jade 的 sprite 是 22x16。
            ListTag inputs = tag.getList(ArcaneAssemblerProvider.TAG_INPUTS, Tag.TAG_COMPOUND);
            List<IElement> row = new ArrayList<>();
            var level = accessor.getLevel();
            if (level != null) {
                var registries = level.registryAccess();
                for (int i = 0; i < inputs.size(); i++) {
                    ItemStack input = ItemStack.parseOptional(registries, inputs.getCompound(i));
                    if (!input.isEmpty()) {
                        row.add(helper.item(input));
                    }
                }
            }
            row.add(helper.progress(tag.getFloat(ArcaneAssemblerProvider.TAG_PROGRESS)));
            if (level != null) {
                ItemStack product = ItemStack.parseOptional(level.registryAccess(),
                        tag.getCompound(ArcaneAssemblerProvider.TAG_TARGET_STACK));
                if (!product.isEmpty()) {
                    row.add(helper.item(product));
                }
            }
            tooltip.add(row);
            String target = tag.getString(ArcaneAssemblerProvider.TAG_TARGET);
            if (!target.isEmpty()) {
                tooltip.add(helper.text(
                        Component.translatable("jade.thaumicenergistics_ce.arcane_assembler.produces",
                                        Component.translatable(target))
                                .withStyle(ChatFormatting.GRAY)));
            }
        }

        // 合并成一行而不是两行：存款与可抽取回答的是同一个问题，拆开就会被读成
        // 两个可以比较的事实。第一个数字是缓存，不是单纯的 vis。
        tooltip.add(helper.text(Component.translatable(
                        "jade.thaumicenergistics_ce.arcane_assembler.vis",
                        tag.getInt(ArcaneAssemblerProvider.TAG_VIS),
                        BlockEntityArcaneAssembler.visBufferTarget(),
                        tag.getInt(ArcaneAssemblerProvider.TAG_AURA))
                .withStyle(ChatFormatting.GRAY)));

        int discount = tag.getInt(ArcaneAssemblerProvider.TAG_DISCOUNT);
        if (discount > 0) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.discount", discount)
                    .withStyle(ChatFormatting.GRAY)));
        }

        int speed = tag.getInt(ArcaneAssemblerProvider.TAG_SPEED);
        if (speed > 0) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.speed", speed)
                    .withStyle(ChatFormatting.GRAY)));
        }

        tooltip.add(helper.text(Component.translatable(
                        "jade.thaumicenergistics_ce.arcane_assembler.patterns",
                        tag.getInt(ArcaneAssemblerProvider.TAG_PATTERNS))
                .withStyle(ChatFormatting.GRAY)));

        // 等待中的机器和空闲的机器从外面看一模一样；等着它的合成 CPU 只显示一个
        // 停住的计时器。
        Component wait = decode(tag, ArcaneAssemblerProvider.TAG_WAIT);
        if (wait != null) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.waiting", wait)
                    .withStyle(ChatFormatting.GOLD)));
        }
        Component refusal = decode(tag, ArcaneAssemblerProvider.TAG_REFUSAL);
        if (refusal != null) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.refused", refusal)
                    .withStyle(ChatFormatting.RED)));
        }
    }

    /**
     * 返回该组件，标签缺失或无法读取时为 {@code null}：在“等待”下面留一行空的，
     * 等于宣称这台机器什么都没等。
     */
    private static @Nullable Component decode(CompoundTag tag, String key) {
        Tag encoded = tag.get(key);
        if (encoded == null) {
            return null;
        }
        return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
    }

    @Override
    public ResourceLocation getUid() {
        return ArcaneAssemblerProvider.UID;
    }
}
