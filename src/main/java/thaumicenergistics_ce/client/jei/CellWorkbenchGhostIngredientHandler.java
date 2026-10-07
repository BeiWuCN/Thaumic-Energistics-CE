package thaumicenergistics_ce.client.jei;

import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.client.gui.ScreenEssentiaCellWorkbench;
import thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench;
import thaumicenergistics_ce.network.PartitionWellPayload;

/**
 * 让玩家把 JEI 里的要素拖进源质元件工作台的分区格。
 * 拖拽是玩家对 AE2 里其他所有过滤网格的预期，而这些格属于同一类：
 * 只有要素会被提供落点，因为格中存的是 [AEKey]，物品无处可放。标记会发给
 * 服务端，而不是写进槽位，走的是 [PartitionWellPayload]。
 */
public class CellWorkbenchGhostIngredientHandler
        implements IGhostIngredientHandler<ScreenEssentiaCellWorkbench> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            ScreenEssentiaCellWorkbench screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof AspectInstance)) {
            return targets;
        }
        MenuEssentiaCellWorkbench menu = screen.getMenu();
        for (int well = 0; well < MenuEssentiaCellWorkbench.partitionSlotCount(); well++) {
            Target<I> target = WellTarget.of(menu, well, screen.getGuiLeft(), screen.getGuiTop());
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // 没有要释放的东西：分区格从不接收玩家手上的物品。
    }

    private record WellTarget<I>(MenuEssentiaCellWorkbench menu, int well, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * 为一个分区格构建目标，其他情况则返回 {@code null}：格背后是 AE2 的配置物品栏，
         * 而工作台里装着存储元件才使它可写。
         */
        static <I> WellTarget<I> of(MenuEssentiaCellWorkbench menu, int well, int guiLeft, int guiTop) {
            Slot slot = menu.slots.get(menu.partitionSlotIndex(well));
            if (slot instanceof AppEngSlot appEngSlot
                    && menu.isPartitionSlotEnabled(well)
                    && appEngSlot.getInventory() instanceof ConfigMenuInventory) {
                return new WellTarget<>(menu, well, guiLeft, guiTop);
            }
            return null;
        }

        /**
         * JEI 绘制该目标的位置，单位为屏幕像素：要加上 GUI 的偏移，因为 JEI 填充
         * 这个矩形时自己不做任何平移，而槽位的 x 和 y 是相对 GUI 角点的。
         */
        @Override
        public Rect2i getArea() {
            Slot slot = menu.slots.get(menu.partitionSlotIndex(well));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, 16, 16);
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof AspectInstance aspect)) {
                return;
            }
            ResourceLocation id = aspect.aspect().unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) {
                // 不由注册表支撑：没有 id 可发，服务端也无法存下它叫不出名字的
                // 标记。
                return;
            }
            // 发给服务端，因为这是本界面唯一会外发的一次写入：格自己会一直留着
            // 这个标记，直到服务端用它自己那个空分区作答。
            PacketDistributor.sendToServer(new PartitionWellPayload(menu.containerId, well, id));
        }
    }
}
