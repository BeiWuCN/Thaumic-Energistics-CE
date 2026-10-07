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
 * 让玩家把 JEI 里的要素拖进源质元件工作台的分区井。拖是玩家对 AE2 它它过滤网格的预期，
 * 这些井同一类：只有要素给落点，井里存的是 [AEKey]，物品无处可放。
 * 标记发给服务端，不写进槽位，走 [PartitionWellPayload]。
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
        // 没有东西可释放：分区井从不收玩家手上的物品。
    }

    private record WellTarget<I>(MenuEssentiaCellWorkbench menu, int well, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * 为一个分区井建目标，它它情况为 {@code null}：井背后是 AE2 的配置物品栏，
         * 工作台里装着元件才使它可写。
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
         * JEI 画这个目标的位置，屏幕像素：要加上 GUI 偏移，JEI 填这个矩形时自己不平移，
         * 而槽位的 x、y 相对角点。
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
                // 不由注册表支撑：没有 id 可发，服务端也存不下它叫不出名字的标记。
                return;
            }
            // 发给服务端：这是本界面唯一一次外发写入。井自己会留着这个标记，
            // 直到服务端用它那份空分区作答。
            PacketDistributor.sendToServer(new PartitionWellPayload(menu.containerId, well, id));
        }
    }
}
