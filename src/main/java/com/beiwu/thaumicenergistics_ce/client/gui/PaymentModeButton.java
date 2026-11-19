package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.beiwu.thaumicenergistics_ce.ThEIds;

/**
 * 奥术合成终端的付费源切换按钮：法杖 6 要素（2 点顶一颗）或水晶槽里的整颗晶体。
 * 底图、层级、tooltip 全走 AE2 的 IconButton 通用入口，这里只叠加图标纹理：
 * states.png 第一行 u=0 画法杖，u=16 画水晶袋。
 * 图标表示当前生效的模式，点击切到另一个。
 */
public class PaymentModeButton extends IconButton {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "textures/gui/states.png");

    private static final int SIZE = 16;

    /** true 画水晶袋：当前从水晶槽整颗扣；false 画法杖：当前用法杖 2:1 顶。 */
    private boolean crystals;

    public PaymentModeButton(OnPress onPress) {
        super(onPress);
    }

    /**
     * 返回 null，让 AE2 只画通用底图和 hover/focus 覆盖层；图标由 renderWidget 自己叠，
     * 否则底图会被当成图标再画一次造成相对偏移。
     */
    @Override
    protected Icon getIcon() {
        return null;
    }

    public void setCrystals(boolean crystals) {
        this.crystals = crystals;
    }

    /**
     * 和 AE2 的 ToggleButton 一样返回多行独立 Component，走 AEBaseScreen 的 ITooltip 渲染，
     * 格式（字体/行距）才和「终端风格/最长」那些 AE2 按钮一致。
     * 用 MC 原生 setTooltip 会把几行拼进一个 Component，走另一套渲染，看起来就不一样。
     * 三行分别是：标题、当前优先消耗哪一边、换算比例。
     */
    @Override
    public List<Component> getTooltipMessage() {
        String prefix = crystals ? ".crystals" : ".wand";
        String base = "gui.thaumicenergistics_ce.arcane_terminal.payment";
        return List.of(
                Component.translatable(base + prefix + ".line1"),
                Component.translatable(base + prefix + ".line2"),
                Component.translatable(base + prefix + ".line3"));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // AE2 全尺寸按钮的图标画在 y+1，hover 时再下移 1（yOffset），底图用 hover 覆盖层。
        // 叠加图标必须跟同一套偏移，否则光标指向时图标和底图会错开一格。
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        int u = crystals ? SIZE : 0;
        int yOffset = isHovered() ? 1 : 0;
        Blitter.texture(TEXTURE, 256, 256)
                .src(u, 0, SIZE, SIZE)
                .dest(getX(), getY() + 1 + yOffset)
                .zOffset(3)
                .blit(graphics);
    }
}
