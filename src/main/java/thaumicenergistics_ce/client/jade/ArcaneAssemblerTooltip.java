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
 * 奥术组装机 Jade tooltip 的绘制半边，另一半是 {@link ArcaneAssemblerProvider}。
 * 它要的东西都在服务端写好的数据标签里，注册表查询用客户端自己的，
 * 这一半因此放在客户端代码树。两半都报告 {@link ArcaneAssemblerProvider#UID}，
 * Jade 靠它配对。
 */
public final class ArcaneAssemblerTooltip implements IBlockComponentProvider {

    public static final ArcaneAssemblerTooltip INSTANCE = new ArcaneAssemblerTooltip();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(JadeGridState.TAG)) {
            // 只有方块实体不是组装机时才走到这里：宁可什么都不显示，也不显示错行。
            return;
        }
        var helper = IElementHelper.get();

        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        if (tag.getBoolean(ArcaneAssemblerProvider.TAG_CRAFTING)) {
            tooltip.add(helper.text(
                    Component.translatable("jade.thaumicenergistics_ce.arcane_assembler.crafting")
                            .withStyle(ChatFormatting.WHITE)));
            // 箭头那一行：左投入右产出，「在做什么」和「用什么做」都不用文字；
            // 图标用全尺寸，Jade 的 sprite 是 22x16。
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

        // 合并成一行不拆两行：存款和可抽取回答同一个问题，
        // 拆开会被读成两个可比的事实。第一个数字是缓存，不是单纯的 vis。
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

        // 等待中的机器和空闲的机器从外面看一样；
        // 等着它的合成 CPU 只显示一个停住的计时器。
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
     * 返回该组件，标签缺失或读不出来时为 {@code null}：
     * 在「等待」下面留一行空的，等于宣称这台机器什么都没等。
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
