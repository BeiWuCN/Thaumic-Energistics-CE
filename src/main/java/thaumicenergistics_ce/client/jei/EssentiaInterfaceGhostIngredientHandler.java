package thaumicenergistics_ce.client.jei;

import appeng.client.gui.implementations.InterfaceScreen;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.network.EssentiaInterfaceMarkPayload;
import thaumicenergistics_ce.util.ThELog;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 让玩家把要素拖到带本访问卡的 ME 接口的配置行上。两种宿主形态共用 AE2 的同一个接口界面，
 * 故注册一次同时服务方块和线缆部件。没有卡就一个目标都没有：拖拽不显示落点，
 * 不是显示一个会吞东西的槽；界面按原始类型 [InterfaceScreen] 取，
 * 因 JEI 拿一个 [Class] 配同类型的处理器。
 */
public class EssentiaInterfaceGhostIngredientHandler implements IGhostIngredientHandler<InterfaceScreen> {

    private static final int SLOT_PIXELS = 16;

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            InterfaceScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        // 只收要素：这张卡搬的是源质，拖来的物品无处可去。
        if (!(ingredient.getIngredient() instanceof AspectInstance)) {
            return targets;
        }
        if (!(screen.getMenu() instanceof InterfaceMenu menu)
                || !menu.getUpgrades().isInstalled(ModItems.ESSENTIA_ACCESS_CARD.get())) {
            return targets;
        }
        addRow(targets, menu, screen);
        return targets;
    }

    @Override
    public void onComplete() {
        // 没有东西可释放：接口槽从不收玩家手上的物品。
    }

    /** 给出配置行每一个活动槽位。 */
    private static <I> void addRow(List<Target<I>> targets, InterfaceMenu menu, InterfaceScreen screen) {
        for (Slot slot : menu.getSlots(SlotSemantics.CONFIG)) {
            MarkTarget<I> target = MarkTarget.of(menu, screen, slot);
            if (target != null) {
                targets.add(target);
            }
        }
    }

    /** 一个落点，发给服务端：客户端从不写接口自己的配置行。 */
    private record MarkTarget<I>(int index, int x, int y, int containerId) implements Target<I> {

        /**
         * 一个配置槽的目标，或 {@code null}：得是压在 {@link ConfigMenuInventory} 上的活动
         * {@link AppEngSlot}，被锁的行在面板之外，下标也不是偏移量。
         */
        static <I> MarkTarget<I> of(InterfaceMenu menu, InterfaceScreen screen, Slot slot) {
            if (!(slot instanceof AppEngSlot appEngSlot)
                    || !(appEngSlot.getInventory() instanceof ConfigMenuInventory)
                    || !appEngSlot.isActive()) {
                return null;
            }
            int index = menu.getSlots(SlotSemantics.CONFIG).indexOf(slot);
            if (index < 0) {
                return null;
            }
            return new MarkTarget<>(index, screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y,
                    menu.containerId);
        }

        /**
         * JEI 画这个目标的位置，屏幕像素：槽位的 x、y 相对 GUI 角点，JEI 填矩形时自己不平移。
         */
        @Override
        public Rect2i getArea() {
            return new Rect2i(x, y, SLOT_PIXELS, SLOT_PIXELS);
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof AspectInstance aspect)) {
                ThELog.LOG.warn(
                        "[essentia-interface] drag produced {} which is not an AspectInstance",
                        ingredient == null ? "null" : ingredient.getClass().getName());
                return;
            }
            Identifier id = aspect.aspect().unwrapKey().map(key -> key.identifier()).orElse(null);
            if (id == null) {
                // 没有 id 的要素，服务端也查不到。
                ThELog.LOG.warn("[essentia-interface] drag produced an aspect with no registry id");
                return;
            }
            ThELog.LOG.info("[essentia-interface] sending config slot {} <- {}", index, id);
            ClientPacketDistributor.sendToServer(new EssentiaInterfaceMarkPayload(containerId, index, id));
        }
    }
}
