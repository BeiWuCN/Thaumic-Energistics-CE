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
 * 奥术组装机的 Jade 服务端数据。
 * 方块实体走 Jade 的 API：AE2 的网格状态行注册在内部包
 * {@code appeng.integration.modules.igtooltip} 里，附加 mod 挂不上。
 * 部件是例外，走 AE2 公开的 [PartTooltips]。
 * 绘制的一半是 {@code client.jade.ArcaneAssemblerTooltip}，按 {@link #UID} 配对。
 */
public class ArcaneAssemblerProvider implements IServerDataProvider<BlockAccessor> {

    public static final ArcaneAssemblerProvider INSTANCE = new ArcaneAssemblerProvider();

    /** 与 {@code client.jade.ArcaneAssemblerTooltip} 共用；Jade 按 UID 配对两半。 */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "arcane_assembler");

    /** 标签名给绘制那一半读，也是本类的公开约定。
     * {@code AssemblerVisPool}、{@code AssemblerDisplaySync}、{@code AssemblerUpgrades} 机器侧也有一份。 */
    public static final String TAG_VIS = "BufferedVis";
    public static final String TAG_AURA = "AuraAround";
    /** 装备带来的整数百分比 vis 折扣。 */
    public static final String TAG_DISCOUNT = "GearDiscount";
    public static final String TAG_SPEED = "SpeedUpgrades";
    public static final String TAG_PATTERNS = "Patterns";
    public static final String TAG_CRAFTING = "Crafting";
    public static final String TAG_PROGRESS = "CraftProgress";
    public static final String TAG_TARGET = "CraftTarget";
    public static final String TAG_TARGET_STACK = "CraftTargetStack";
    public static final String TAG_INPUTS = "CraftInputs";
    /**
     * 是句子不是翻译键；句子里带数字，与日志输出保持一致。
     */
    public static final String TAG_WAIT = "WaitReason";
    public static final String TAG_REFUSAL = "RefusalReason";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * 节点从 {@code getActionableNode} 取，不要用 {@code getGridNode(null)}。
     * null 面对任何方向都不暴露，能力查询一律答 null。
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
        // vis 取整；百分位不值得占 tooltip 一行。
        tag.putInt(TAG_AURA, Math.round(assembler.getAuraAround()));
        tag.putInt(TAG_DISCOUNT, assembler.upgrades().getGearDiscount());
        tag.putInt(TAG_SPEED, assembler.upgrades().getSpeedUpgrades());
        // 发的是可用样板数，回答 “why is nothing being crafted for me” 的就是它。
        tag.putInt(TAG_PATTERNS, assembler.getAvailablePatterns().size());

        tag.putBoolean(TAG_CRAFTING, assembler.isCrafting());
        if (assembler.isCrafting()) {
            tag.putFloat(TAG_PROGRESS, assembler.getCraftProgress());
            // 用物品堆自己的描述 id；自己拼 "block." 前缀会画出原始键名。
            ItemStack targetStack = assembler.getInventory()
                    .getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
            if (!targetStack.isEmpty()) {
                tag.putString(TAG_TARGET, targetStack.getDescriptionId());
            }
            // 发存好的物品堆；只发 id 画不出来，客户端推导等于重跑一遍配方解析。
            var level = accessor.getLevel();
            if (level != null) {
                var registries = level.registryAccess();
                if (!targetStack.isEmpty()) {
                    tag.put(TAG_TARGET_STACK, targetStack.save(registries));
                }
                // 按网格顺序发，图标像配方一样从左到右读。
                // 只发非空格子；九个空框就是九个空图标。
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
        // 发组件本身；服务端挑原因但不知道玩家语言。
        // NbtOps 带键和参数，客户端解析。
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
