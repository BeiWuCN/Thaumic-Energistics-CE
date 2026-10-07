package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/**
 * 奥术组装机的 Jade 服务端数据。针对 Jade 的 API 编写而不是 AE2 的，因为 AE2
 * 是通过内部的 {@code appeng.integration.modules.igtooltip} 包来注册方块实体的网格状态行的，
 * 附加 mod 挂钩不到那个包；部件是例外，走 AE2 公开的 [PartTooltips]；另一半绘制是
 * {@code client.jade.ArcaneAssemblerTooltip}，按 {@link #UID} 配对。
 * {@code client.jade.ArcaneAssemblerTooltip}，按 {@link #UID} 配对。
 */
public class ArcaneAssemblerProvider implements IServerDataProvider<BlockAccessor> {

    public static final ArcaneAssemblerProvider INSTANCE = new ArcaneAssemblerProvider();

    /** 与 {@code client.jade.ArcaneAssemblerTooltip} 共用：Jade 按 UID 配对这两半。 */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "arcane_assembler");

    /** 由绘制的那一半读回，因此下面的传输格式是本类自己的公开约定。其中
     * 三个名字也被机器自己写入的一个标签所拼写（{@code AssemblerVisPool}、
     * {@code AssemblerDisplaySync}、{@code AssemblerUpgrades}）：两处取值一致，但文档
     * 各自独立，改名必须同步改到两边。 */
    public static final String TAG_VIS = "BufferedVis";
    public static final String TAG_AURA = "AuraAround";
    /** 所装装备带来的整数百分比 vis 折扣。 */
    public static final String TAG_DISCOUNT = "GearDiscount";
    public static final String TAG_SPEED = "SpeedUpgrades";
    public static final String TAG_PATTERNS = "Patterns";
    public static final String TAG_CRAFTING = "Crafting";
    public static final String TAG_PROGRESS = "CraftProgress";
    public static final String TAG_TARGET = "CraftTarget";
    public static final String TAG_TARGET_STACK = "CraftTargetStack";
    public static final String TAG_INPUTS = "CraftInputs";
    /**
     * 普通句子，而不是翻译键：它们携带着数字，而且是刻意与日志
     * 得到的句子保持一致。
     */
    public static final String TAG_WAIT = "WaitReason";
    public static final String TAG_REFUSAL = "RefusalReason";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * 从 {@code getActionableNode} 取节点，绝不要用 {@code getGridNode(null)}：null 面
     * 不对任何方向暴露，因此无论机器多正常，能力查询都会答 null。
     */
    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (!(blockEntity instanceof BlockEntityArcaneAssembler assembler)) {
            return;
        }
        IGridNode node = assembler.getActionableNode();
        JadeGridState.of(node).write(tag, node);

        tag.putInt(TAG_VIS, assembler.getBufferedVis());
        // 取整到整数 vis：百分位不值得占用一行 tooltip。
        tag.putInt(TAG_AURA, Math.round(assembler.getAuraAround()));
        tag.putInt(TAG_DISCOUNT, assembler.upgrades().getGearDiscount());
        tag.putInt(TAG_SPEED, assembler.upgrades().getSpeedUpgrades());
        // 可用样板而非已存样板：也就是回答 "why is nothing being crafted for me" 的那个数字。
        tag.putInt(TAG_PATTERNS, assembler.getAvailablePatterns().size());

        tag.putBoolean(TAG_CRAFTING, assembler.isCrafting());
        if (assembler.isCrafting()) {
            tag.putFloat(TAG_PROGRESS, assembler.getCraftProgress());
            // 用物品堆自己的描述 id，而不是 "block." + 注册表 id：前缀错了会画出原始键名。
            ItemStack targetStack = assembler.getInventory()
                    .getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
            if (!targetStack.isEmpty()) {
                tag.putString(TAG_TARGET, targetStack.getDescriptionId());
            }
            // 以已保存的物品堆形式发送：光有 id 画不出来，而在客户端推导物品堆
            // 会重新解析服务端已经解析过的配方。
            var level = accessor.getLevel();
            if (level != null) {
                var registries = level.registryAccess();
                if (!targetStack.isEmpty()) {
                    tag.put(TAG_TARGET_STACK, targetStack.save(registries));
                }
                // 按网格顺序，这样图标像配方一样从左到右阅读。只发送非空单元格：
                // 九个空框就是九个什么都没有的图标。
                ListTag inputs = new ListTag();
                for (int i = 0; i < BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT; i++) {
                    ItemStack cell = assembler.getInventory()
                            .getItem(BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i);
                    if (!cell.isEmpty()) {
                        inputs.add(cell.save(registries));
                    }
                }
                tag.put(TAG_INPUTS, inputs);
            }
        }
        // 以组件形式发送，而不是它们的英文文本：服务端挑出原因但无法知道
        // 玩家的语言。NbtOps 携带键及其参数；由客户端解析它们。
        Component wait = assembler.waitReason();
        if (wait != null) {
            tag.put(TAG_WAIT, encode(wait));
        }
        Component refusal = assembler.refusalReason();
        if (refusal != null) {
            tag.put(TAG_REFUSAL, encode(refusal));
        }
    }

    private static Tag encode(Component component) {
        return ComponentSerialization.CODEC
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .orElse(new CompoundTag());
    }
}
