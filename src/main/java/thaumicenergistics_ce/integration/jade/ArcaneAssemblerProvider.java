package thaumicenergistics_ce.integration.jade;

import appeng.api.networking.IGridNode;
import appeng.core.localization.InGameTooltip;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;

/**
 * The Arcane Assembler's Jade tooltip.
 * <ul>
 *   <li>Written against Jade's API, not AE2's tooltip abstraction: AE2 registers its grid-state line
 *       through the internal {@code appeng.integration.modules.igtooltip} package, which an addon
 *       cannot hook.
 *   <li>One provider covers both halves: the server writes the numbers into the data tag and the client
 *       reads them back. The state is genuinely server-side - the craft, the vis buffer and the grid
 *       node all change between block updates.
 * </ul>
 */
public class ArcaneAssemblerProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ArcaneAssemblerProvider INSTANCE = new ArcaneAssemblerProvider();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "arcane_assembler");

    // ---- Server side: what travels ----------------------------------------

    /** Which grid state the node is in. See {@link JadeGridState}. */
    private static final String TAG_GRID_STATE = JadeGridState.TAG;
    /** Vis buffered for the next craft. */
    private static final String TAG_VIS = "BufferedVis";
    /** The vis the machine's 3x3 can be drawn on right now. */
    private static final String TAG_AURA = "AuraAround";
    /** Whole-percent vis discount from the installed gear. */
    private static final String TAG_DISCOUNT = "GearDiscount";
    /** Acceleration cards installed. */
    private static final String TAG_SPEED = "SpeedUpgrades";
    /** Patterns advertised from the knowledge core. */
    private static final String TAG_PATTERNS = "Patterns";
    /** Whether a craft is running, and how far along it is. */
    private static final String TAG_CRAFTING = "Crafting";
    private static final String TAG_PROGRESS = "CraftProgress";
    /** What the running craft produces, by item id. */
    private static final String TAG_TARGET = "CraftTarget";
    /** The same product as a saved stack, for the icon the arrow row draws. */
    private static final String TAG_TARGET_STACK = "CraftTargetStack";
    /** The running craft's ingredients as saved stacks, in grid order. */
    private static final String TAG_INPUTS = "CraftInputs";
    /**
     * Plain sentences, not translation keys: they carry numbers, and they are deliberately the same
     * sentences the log gets.
     */
    private static final String TAG_WAIT = "WaitReason";
    private static final String TAG_REFUSAL = "RefusalReason";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * <b>Take the node from {@code getActionableNode}, never {@code getGridNode(null)}.</b> A null side is
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
        tag.putInt(TAG_DISCOUNT, assembler.getGearDiscount());
        tag.putInt(TAG_SPEED, assembler.getSpeedUpgrades());
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

    /** A component as NBT, or an empty tag when it cannot be written. See {@link #decode}. */
    private static Tag encode(Component component) {
        return ComponentSerialization.CODEC
                .encodeStart(NbtOps.INSTANCE, component)
                .result()
                .orElse(new CompoundTag());
    }

    /**
     * The component back, or {@code null} when the tag is absent or unreadable. An empty line under
     * "waiting" would claim the machine waits for nothing.
     */
    private static @Nullable Component decode(CompoundTag tag, String key) {
        Tag encoded = tag.get(key);
        if (encoded == null) {
            return null;
        }
        return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
    }

    // ---- Client side: what is drawn ---------------------------------------

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(TAG_GRID_STATE)) {
            // Only reachable if the block entity was not the assembler. Better nothing than a wrong line.
            return;
        }
        var helper = IElementHelper.get();

        JadeGridState state = JadeGridState.read(tag);
        tooltip.add(helper.text(state.label().copy().withStyle(state.colour())));

        if (tag.getBoolean(TAG_CRAFTING)) {
            tooltip.add(helper.text(
                    Component.translatable("jade.thaumicenergistics_ce.arcane_assembler.crafting")
                            .withStyle(ChatFormatting.WHITE)));
            // The arrow row: what goes in on the left and what comes out on the right, so "what is it
            // making" and "out of what" need no sentence.
            // Full-size icons because Jade's arrow sprite is 22x16 and its 10x10 smallItem reads as lesser.
            ListTag inputs = tag.getList(TAG_INPUTS, Tag.TAG_COMPOUND);
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
            row.add(helper.progress(tag.getFloat(TAG_PROGRESS)));
            if (level != null) {
                ItemStack product =
                        ItemStack.parseOptional(level.registryAccess(), tag.getCompound(TAG_TARGET_STACK));
                if (!product.isEmpty()) {
                    row.add(helper.item(product));
                }
            }
            tooltip.add(row);
            String target = tag.getString(TAG_TARGET);
            if (!target.isEmpty()) {
                // Already a translation key, worked out on the server side. See appendServerData.
                tooltip.add(helper.text(
                        Component.translatable("jade.thaumicenergistics_ce.arcane_assembler.produces",
                                        Component.translatable(target))
                                .withStyle(ChatFormatting.GRAY)));
            }
        }

        // One line, not two: banked and drawable answer the same question, and split they read as two
        // facts to compare. The first number is a cache, not plain vis.
        tooltip.add(helper.text(Component.translatable(
                        "jade.thaumicenergistics_ce.arcane_assembler.vis",
                        tag.getInt(TAG_VIS),
                        BlockEntityArcaneAssembler.visBufferTarget(),
                        tag.getInt(TAG_AURA))
                .withStyle(ChatFormatting.GRAY)));

        int discount = tag.getInt(TAG_DISCOUNT);
        if (discount > 0) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.discount", discount)
                    .withStyle(ChatFormatting.GRAY)));
        }

        int speed = tag.getInt(TAG_SPEED);
        if (speed > 0) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.speed", speed)
                    .withStyle(ChatFormatting.GRAY)));
        }

        tooltip.add(helper.text(Component.translatable(
                        "jade.thaumicenergistics_ce.arcane_assembler.patterns", tag.getInt(TAG_PATTERNS))
                .withStyle(ChatFormatting.GRAY)));

        // A waiting machine and an idle one look identical from outside; the CPU waiting on it shows
        // only a stopped timer.
        Component wait = decode(tag, TAG_WAIT);
        if (wait != null) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.waiting", wait)
                    .withStyle(ChatFormatting.GOLD)));
        }
        Component refusal = decode(tag, TAG_REFUSAL);
        if (refusal != null) {
            tooltip.add(helper.text(Component.translatable(
                            "jade.thaumicenergistics_ce.arcane_assembler.refused", refusal)
                    .withStyle(ChatFormatting.RED)));
        }
    }


}
