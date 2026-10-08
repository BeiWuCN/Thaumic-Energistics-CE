package thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.core.definitions.AEItems;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.compat.jei.ingredient.AspectIngredientType;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.menu.MenuEssentiaBusBase;
import thaumicenergistics_ce.network.EssentiaBusConfigPayload;
import thaumicenergistics_ce.util.ThELog;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 让玩家把 JEI 里的要素拖进源质总线的配置槽位。
 * <ul>
 *   <li>Thaumaturge 注册了 {@link AspectIngredientType}；JEI 只往声明过的目标里放。
 *   <li>针对两种总线共用的菜单写的，一次注册就够两个方向用。
 *   <li>用带 key 数据组件的物品堆调 {@link Slot#set}，这是 AE2 的非物品路径。
 * </ul>
 */
public class EssentiaBusGhostIngredientHandler<T extends UpgradeableScreen<? extends MenuEssentiaBusBase<?>>>
        implements IGhostIngredientHandler<T> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            T screen,
            ITypedIngredient<I> ingredient,
            boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        // 只收要素：总线搬的是源质，拖进来的物品没有去处。
        if (!(ingredient.getIngredient() instanceof AspectInstance)) {
            return targets;
        }
        MenuEssentiaBusBase<?> menu = screen.getMenu();
        for (int slot = 0; slot < menu.getConfigSlotCount(); slot++) {
            Target<I> target = ConfigTarget.of(menu, slot, screen.getGuiLeft(), screen.getGuiTop());
            if (target != null) {
                targets.add(target);
            }
        }
        // 客户端和服务端各按自己的升级物品栏判断，两边不一致会静静地
        // 表现为一个吃掉拖放的槽位。
        if (!targets.isEmpty()) {
            ThELog.LOG.info(
                    "[bus-config] offering {} target(s) of {} config slot(s); {} capacity card(s) installed",
                    targets.size(), menu.getConfigSlotCount(),
                    menu.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD));
        }
        return targets;
    }

    @Override
    public void onComplete() {
        // 没有要释放的：配置槽位从不从玩家那里拿走物品。
    }

    private record ConfigTarget<I>(MenuEssentiaBusBase<?> menu, int index, int guiLeft, int guiTop)
            implements Target<I> {

        /**
         * 一个配置槽位对应的目标，否则 {@code null}：它必须是活的 {@link AppEngSlot}，挂在
         * {@link ConfigMenuInventory} 上：锁住的行不在面板上，索引也不是固定偏移。
         */
        static <I> ConfigTarget<I> of(MenuEssentiaBusBase<?> menu, int index, int guiLeft, int guiTop) {
            Slot slot = menu.slots.get(menu.configSlotIndex(index));
            if (slot instanceof AppEngSlot appEngSlot
                    && appEngSlot.getInventory() instanceof ConfigMenuInventory
                    && appEngSlot.isActive()) {
                return new ConfigTarget<>(menu, index, guiLeft, guiTop);
            }
            return null;
        }

        /**
         * JEI 在哪里画这个目标，单位是<em>屏幕</em>像素：槽位的 x 和 y 相对 GUI
         * 的角，JEI 照原样填这个矩形，不再做自己的平移。
         */
        @Override
        public Rect2i getArea() {
            Slot slot = menu.slots.get(menu.configSlotIndex(index));
            return new Rect2i(guiLeft + slot.x, guiTop + slot.y, 16, 16);
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof AspectInstance aspect)) {
                ThELog.LOG.warn(
                        "[bus-config] drag produced {} which is not an AspectInstance",
                        ingredient == null ? "null" : ingredient.getClass().getName());
                return;
            }
            // 送到服务端而不是写进槽位：AE2 的配置槽位会把物品堆按
            // AEItemKey 拆开，而源质键不是物品，条目会消失。
            Identifier id = aspect.aspect().unwrapKey()
                    .map(key -> key.identifier())
                    .orElse(null);
            if (id == null) {
                // 没有 id 的要素，服务端也查不到。
                ThELog.LOG.warn("[bus-config] drag produced an aspect with no registry id");
                return;
            }
            ThELog.LOG.info(
                    "[bus-config] sending slot {} <- {} for menu {}", index, id, menu.containerId);
            ClientPacketDistributor.sendToServer(new EssentiaBusConfigPayload(menu.containerId, index, id));
        }
    }
}
