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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.network.EssentiaInterfaceMarkPayload;
import thaumicenergistics_ce.util.ThELog;

/**
 * 让玩家把要素拖到带有本访问卡的 ME 接口的配置行上。
 * 两种宿主形态共用 AE2 的同一个接口界面，所以一次注册同时服务方块与
 * 线缆部件。没有卡时一个目标都没有：拖拽不会显示落点，而不是显示一个会吞掉
 * 物品的槽位；界面按原始类型 [InterfaceScreen] 取用，因为 JEI 是把一个 [Class]
 * 与同类型的处理器配对的。
 */
public class EssentiaInterfaceGhostIngredientHandler implements IGhostIngredientHandler<InterfaceScreen> {

    private static final int SLOT_PIXELS = 16;

    @Override
    public <I> List<Target<I>> getTargetsTyped(
            InterfaceScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        // 只收要素：这张卡搬运的是源质，拖来的物品无处可去。
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
        // 没有要释放的东西：接口槽从不接收玩家手上的物品。
    }

    /** 提供配置行的每一个活动槽位。 */
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
         * 一个配置槽的目标，或 {@code null}：它必须是位于 {@link ConfigMenuInventory} 之上的
         * 活动 {@link AppEngSlot}，因为被锁定的行位于面板之外，下标也不是偏移量。
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
         * JEI 绘制该目标的位置，单位为屏幕像素：槽位的 x 和 y 是相对 GUI 角点的，
         * 而 JEI 填充这个矩形时自己不做任何平移。
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
            ResourceLocation id = aspect.aspect().unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) {
                // 没有 id 的要素，服务端同样查不到。
                ThELog.LOG.warn("[essentia-interface] drag produced an aspect with no registry id");
                return;
            }
            ThELog.LOG.info("[essentia-interface] sending config slot {} <- {}", index, id);
            PacketDistributor.sendToServer(new EssentiaInterfaceMarkPayload(containerId, index, id));
        }
    }
}
