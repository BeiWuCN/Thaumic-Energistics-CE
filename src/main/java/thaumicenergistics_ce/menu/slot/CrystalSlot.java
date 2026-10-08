package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.arcane.EssentiaCrystals;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import thaumicenergistics_ce.util.ThELog;

/**
 * 奥术合成终端六个水晶槽之一，钉在一个元质上。拒绝交给 {@code TcWorkbench.isValidCrystal}，
 * 就是工作台自己的规则（{@code MenuArcaneWorkbench.addSlots}），终端收的东西和工作台一致。
 * {@code mayPlace} 是全部闸门：进槽的每条路都过它。
 */
public class CrystalSlot extends AppEngSlot {
    /** 同一条结论一秒最多写一行，免得拖动时刷屏。 */
    private static long lastLogAt;
    private static String lastLogKey = "";

    private final ResourceKey<IAspect> required;

    public CrystalSlot(InternalInventory inventory, int slot, ResourceKey<IAspect> required) {
        super(inventory, slot);
        this.required = required;
        // 每个槽钉死一个元质，玩家得知道是哪一个：按住 Shift 悬停空槽时写清它要什么。
        // 常驻会盖住旁边的槽，所以照 AE2 自己的习惯让 Shift 决定出不出。
        // 供货方（AE2 的 getCustomTooltip）是在画提示时才调这个 supplier 的，
        // 于是 Screen 这个客户端类只在客户端才被解析——服务端建菜单时不会碰它。
        setEmptyTooltip(() -> shiftHeld()
                ? List.of(Component.translatable(
                        "gui.thaumicenergistics_ce.arcane_terminal.crystal_required", aspectName(required)))
                : List.of());
    }

    /** 左 Shift（或右 Shift）按住时才出提示。 */
    private static boolean shiftHeld() {
        // 26.1.2 把 Screen.hasShiftDown() 拿掉了，改问客户端自己。
        return Minecraft.getInstance().hasShiftDown();
    }

    /** 要素的名字就是语言文件里的 {@code aspect.<命名空间>.<路径>}，跟 Thaumaturge 对齐。 */
    private static Component aspectName(ResourceKey<IAspect> aspect) {
        Identifier id = aspect.identifier();
        return Component.translatable("aspect." + id.getNamespace() + "." + id.getPath());
    }

    public ResourceKey<IAspect> requiredAspect() {
        return required;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        boolean door = super.mayPlace(stack);
        boolean fits = door && TcWorkbench.isValidCrystal(stack, required);
        if (!stack.isEmpty()) {
            trace(stack, door, fits);
        }
        return fits;
    }

    /**
     * 诊断：写清「哪个槽、来的什么东西、自带什么要素、哪一层放行或拦下」。
     * 拦下来只有两处——容器闸门（{@code isCrystal}，即物品不对）或者要素不匹配。
     * 客户端也会走 {@code mayPlace}，同一件事可能在日志里出现两份，看线程名分辨。
     */
    private void trace(ItemStack stack, boolean door, boolean fits) {
        long now = System.currentTimeMillis();
        String key = stack.getItem() + "|" + required.identifier() + "|" + door + "|" + fits;
        if (key.equals(lastLogKey) && now - lastLogAt < 1000L) {
            return;
        }
        lastLogKey = key;
        lastLogAt = now;
        Holder<IAspect> carried = EssentiaCrystals.aspectOf(stack);
        ThELog.LOG.info("[arcane] 水晶槽 {}：来的是 {}，自带要素 {} —— 容器闸门 {}，要素匹配 {}",
                required.identifier(),
                stack.getItem(),
                carried == null
                        ? "无（不是水晶物品，或者水晶没带要素）"
                        : carried.unwrapKey().map(k -> k.identifier().toString()).orElse("?"),
                door ? "过" : "拦",
                fits ? "过" : "拦");
    }
}
