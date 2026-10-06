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
 * The Arcane Assembler's Jade server data: the numbers a tooltip cannot work out for itself.
 * It is written against Jade's API rather than AE2's, since AE2 registers its grid-state line
 * through the internal {@code appeng.integration.modules.igtooltip} package, which an addon
 * cannot hook. The drawing half is {@code client.jade.ArcaneAssemblerTooltip}, paired by
 * {@link #UID}.
 */
public class ArcaneAssemblerProvider implements IServerDataProvider<BlockAccessor> {

    public static final ArcaneAssemblerProvider INSTANCE = new ArcaneAssemblerProvider();

    /** Shared with {@code client.jade.ArcaneAssemblerTooltip}: Jade pairs the two halves by UID. */
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "arcane_assembler");

    /** Read back by the drawing half, so the wire format below is this class's own public contract.
     * Three of these names are also spelled by a tag the machine writes for itself
     * ({@code AssemblerVisPool}, {@code AssemblerDisplaySync}, {@code AssemblerUpgrades}): the values
     * agree and the documents are separate, so a rename must reach both. */
    public static final String TAG_VIS = "BufferedVis";
    public static final String TAG_AURA = "AuraAround";
    /** Whole-percent vis discount from the installed gear. */
    public static final String TAG_DISCOUNT = "GearDiscount";
    public static final String TAG_SPEED = "SpeedUpgrades";
    public static final String TAG_PATTERNS = "Patterns";
    public static final String TAG_CRAFTING = "Crafting";
    public static final String TAG_PROGRESS = "CraftProgress";
    public static final String TAG_TARGET = "CraftTarget";
    public static final String TAG_TARGET_STACK = "CraftTargetStack";
    public static final String TAG_INPUTS = "CraftInputs";
    /**
     * Plain sentences, not translation keys: they carry numbers, and they are deliberately the same
     * sentences the log gets.
     */
    public static final String TAG_WAIT = "WaitReason";
    public static final String TAG_REFUSAL = "RefusalReason";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * Take the node from {@code getActionableNode}, never {@code getGridNode(null)}: a null side is
     * exposed on nothing, so the capability lookup answers null however healthy the machine is.
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
        // Rounded to whole vis: hundredths are not worth a tooltip line.
        tag.putInt(TAG_AURA, Math.round(assembler.getAuraAround()));
        tag.putInt(TAG_DISCOUNT, assembler.upgrades().getGearDiscount());
        tag.putInt(TAG_SPEED, assembler.upgrades().getSpeedUpgrades());
        // Available, not stored patterns: the number that answers "why is nothing being crafted for me".
        tag.putInt(TAG_PATTERNS, assembler.getAvailablePatterns().size());

        tag.putBoolean(TAG_CRAFTING, assembler.isCrafting());
        if (assembler.isCrafting()) {
            tag.putFloat(TAG_PROGRESS, assembler.getCraftProgress());
            // The stack's own description id, not "block." + registry id: the wrong prefix drew a raw key.
            ItemStack targetStack = assembler.getInventory()
                    .getItem(BlockEntityArcaneAssembler.TARGET_SLOT);
            if (!targetStack.isEmpty()) {
                tag.putString(TAG_TARGET, targetStack.getDescriptionId());
            }
            // Sent as saved stacks: an id alone cannot be drawn, and deriving the stack on the client
            // would re-resolve a recipe the server already resolved.
            var level = accessor.getLevel();
            if (level != null) {
                var registries = level.registryAccess();
                if (!targetStack.isEmpty()) {
                    tag.put(TAG_TARGET_STACK, targetStack.save(registries));
                }
                // Grid order, so the icons read left to right the way the recipe does. Only non-empty cells:
                // nine empty frames would be nine icons of nothing.
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
        // Sent as components, not as their English text: the server picks the reason but cannot know the
        // player's language. NbtOps carries the key and its arguments; the client resolves them.
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
