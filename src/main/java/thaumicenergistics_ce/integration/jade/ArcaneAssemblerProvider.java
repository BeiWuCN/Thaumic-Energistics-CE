package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.util.ThEItemTags;

public class ArcaneAssemblerProvider implements IServerDataProvider<BlockAccessor> {

    public static final ArcaneAssemblerProvider INSTANCE = new ArcaneAssemblerProvider();

    public static final Identifier UID =
            Identifier.fromNamespaceAndPath(ThEIds.MODID, "arcane_assembler");

    public static final String TAG_VIS = "BufferedVis";
    public static final String TAG_AURA = "AuraAround";
    public static final String TAG_DISCOUNT = "GearDiscount";
    public static final String TAG_SPEED = "SpeedUpgrades";
    public static final String TAG_PATTERNS = "Patterns";
    public static final String TAG_CRAFTING = "Crafting";
    public static final String TAG_PROGRESS = "CraftProgress";
    public static final String TAG_TARGET = "CraftTarget";
    public static final String TAG_TARGET_STACK = "CraftTargetStack";
    public static final String TAG_INPUTS = "CraftInputs";
    public static final String TAG_WAIT = "WaitReason";
    public static final String TAG_REFUSAL = "RefusalReason";

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (!(blockEntity instanceof BlockEntityArcaneAssembler assembler)) {
            return;
        }
        IGridNode node = assembler.getActionableNode();
        JadeGridState.of(node).write(tag, node);

        tag.putInt(TAG_VIS, assembler.getBufferedVis());
        tag.putInt(TAG_AURA, Math.round(assembler.getAuraAround()));
        tag.putInt(TAG_DISCOUNT, assembler.upgrades().getGearDiscount());
        tag.putInt(TAG_SPEED, assembler.upgrades().getSpeedUpgrades());
        tag.putInt(TAG_PATTERNS, assembler.getAvailablePatterns().size());

        tag.putBoolean(TAG_CRAFTING, assembler.isCrafting());
        if (assembler.isCrafting()) {
            tag.putFloat(TAG_PROGRESS, assembler.getCraftProgress());
            ItemStack targetStack = assembler.getInventory()
                    .getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
            if (!targetStack.isEmpty()) {
                tag.putString(TAG_TARGET, targetStack.getItem().getDescriptionId());
            }
            var level = accessor.getLevel();
            if (level != null) {
                var registries = level.registryAccess();
                if (!targetStack.isEmpty()) {
                    tag.put(TAG_TARGET_STACK, ThEItemTags.save(targetStack, registries));
                }
                ListTag inputs = new ListTag();
                for (int i = 0; i < BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT; i++) {
                    ItemStack cell = assembler.getInventory()
                            .getItem(BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i);
                    if (!cell.isEmpty()) {
                        inputs.add(ThEItemTags.save(cell, registries));
                    }
                }
                tag.put(TAG_INPUTS, inputs);
            }
        }
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
