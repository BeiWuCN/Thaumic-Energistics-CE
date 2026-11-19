package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.GuiText;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import com.leclowndu93150.thaumaturge.content.item.PhialItem;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import com.beiwu.thaumicenergistics_ce.network.PartitionWellPayload;

/**
 * 源质元件工作台的界面。AE2 可升级界面，美术、槽位、标题和升级面板都来自样式；
 * 齿轮从元件填井，X 清空，点一个标记就把它取回。AE2 的模糊和复制模式开关没留，
 * 源质 [AEKey] 没有耐久或 NBT 可匹配。
 */
public class ScreenEssentiaCellWorkbench extends UpgradeableScreen<MenuEssentiaCellWorkbench> {

    private final ToggleButton copyMode;

    public ScreenEssentiaCellWorkbench(
            MenuEssentiaCellWorkbench menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        addToLeftToolbar(new ActionButton(ActionItems.COG, items -> menu.partitionToContents()));
        addToLeftToolbar(new ActionButton(ActionItems.CLOSE, items -> menu.clearPartition()));
        copyMode = addToLeftToolbar(new ToggleButton(
                Icon.COPY_MODE_ON, Icon.COPY_MODE_OFF,
                GuiText.CopyMode.text(), GuiText.CopyModeDesc.text(),
                act -> menu.toggleCopyMode()));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        copyMode.setState(menu.keepOnRemove);
    }

    /** AE2 的 help 按钮指向本 mod 的 ME 指南页。 */
    @Override
    protected guideme.PageAnchor getHelpTopic() {
        return guideme.PageAnchor.page(net.minecraft.resources.ResourceLocation.parse(ModGuide.PAGE_CELL_WORKBENCH));
    }

    @Override
    protected void openHelp() {
        ModGuide.open(menu.getPlayer(), ModGuide.PAGE_CELL_WORKBENCH);
    }

    /**
     * AE2 的元件工作台用 RestrictedInputSlot 在元件槽下画 BACKGROUND_STORAGE_CELL 图标。
     * 本 mod 的元件槽是普通 Slot（宿主不是 InternalInventory），这里补画同一张原生图标，
     * 让空槽看起来和 AE2 的一致。井的禁用态交给 CellPartitionSlot.isSlotEnabled 自己画。
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Slot cell = menu.cellSlot();
        if (cell != null && cell.getItem().isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos, topPos, 0.0f);
            Icon.BACKGROUND_STORAGE_CELL.getBlitter().dest(cell.x, cell.y).blit(graphics);
            graphics.pose().popPose();
        }
        drawEssentiaWells(graphics);
    }

    /**
     * 井里存的是源质时，补画 Thaumaturge 注册的要素纹理。
     * AE2 画槽位只会把 key 折回物品堆，源质折出来是那颗魔力水晶——有自己的模型和颜色，
     * 和 JEI、手册里的要素字形不是一个东西。这里绕开物品那一层直接画字形。
     */
    private void drawEssentiaWells(GuiGraphics graphics) {
        for (int well = 0; well < MenuEssentiaCellWorkbench.partitionSlotCount(); well++) {
            if (!(menu.keyInWell(well) instanceof AEssentiaKey key)) {
                continue;
            }
            Holder<IAspect> aspect = key.resolveAspect();
            if (aspect == null) {
                continue;
            }
            Slot slot = menu.getSlot(menu.partitionSlotIndex(well));
            // 数量写 0：井是标记，不是存货，画数字会被读成「要几颗」。
            AspectRendering.renderGui(graphics, font, leftPos + slot.x, topPos + slot.y, aspect, 0.0F);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Slot hovered = getSlotUnderMouse();
        int well = hovered == null ? -1 : menu.wellOf(hovered);
        if (well >= 0) {
            if (menu.isPartitionSlotEnabled(well)) {
                // 右键、且光标上是一瓶只装一味源质的安瓿：把那一味标进井里。
                // 其余情况（左键、空手、装了好几味的安瓿）仍旧是取回标记。
                ResourceLocation marked = button == 1 ? aspectOfCarriedPhial() : null;
                PacketDistributor.sendToServer(new PartitionWellPayload(
                        menu.containerId, well, marked == null ? PartitionWellPayload.CLEAR : marked));
            }
            // 两种走法点击都在此止步，手上拿的物品落不进井里。
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 光标上那瓶安瓿装的是哪一味；不是安瓿、或是混装的，返回 {@code null}。
     * 安瓿把内容存在 {@code ASPECTS} 组件里，只有一条时才算「单一源质」。
     */
    private @Nullable ResourceLocation aspectOfCarriedPhial() {
        ItemStack carried = menu.getCarried();
        if (!(carried.getItem() instanceof PhialItem)) {
            return null;
        }
        AspectList aspects = carried.get(TTDataComponents.ASPECTS.get());
        if (aspects == null || aspects.entries().size() != 1) {
            return null;
        }
        AEssentiaKey key = AEssentiaKey.of(aspects.entries().get(0).aspect());
        return key == null ? null : key.getId();
    }
}
