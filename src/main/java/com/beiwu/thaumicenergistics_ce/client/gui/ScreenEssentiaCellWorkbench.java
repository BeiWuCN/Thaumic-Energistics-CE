package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.api.config.ActionItems;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.GuiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import com.beiwu.thaumicenergistics_ce.network.PartitionWellPayload;
import com.beiwu.thaumicenergistics_ce.util.EssentiaContainers;

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
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Slot hovered = getSlotUnderMouse();
        int well = hovered == null ? -1 : menu.wellOf(hovered);
        if (well >= 0) {
            if (menu.isPartitionSlotEnabled(well)) {
                // 照 AE2 配置槽的规矩：手上那件东西装着什么就标什么，一桶水标成水。
                // 手上没有装着源质的东西（空手、或普通物品）就取下标记。
                ResourceLocation marked = markedAspect();
                PacketDistributor.sendToServer(new PartitionWellPayload(
                        menu.containerId, well, marked == null ? PartitionWellPayload.CLEAR : marked));
            }
            // 点击都在此止步，手上拿的物品落不进井里。
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 光标上那件容器物品装着哪一味源质；空手、普通物品、或混装的容器都返回 {@code null}。
     * 走 {@link EssentiaContainers}，和「一桶水标记成水」是同一条判断：
     * 认的是内容物，不是物品类型。
     */
    private @Nullable ResourceLocation markedAspect() {
        GenericStack contained = EssentiaContainers.contained(menu.getCarried());
        return contained != null && contained.what() instanceof AEssentiaKey key ? key.getId() : null;
    }
}
