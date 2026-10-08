package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.menu.slot.AppEngSlot;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;

/**
 * 奥术合成终端六个水晶槽之一，钉在一个元质上。拒绝交给 {@code TcWorkbench.isValidCrystal}，
 * 就是工作台自己的规则（{@code MenuArcaneWorkbench.addSlots}），终端收的东西和工作台一致。
 * {@code mayPlace} 是全部闸门：进槽的每条路都过它。
 */
public class CrystalSlot extends AppEngSlot {
    private final ResourceKey<IAspect> required;

    public CrystalSlot(InternalInventory inventory, int slot, ResourceKey<IAspect> required) {
        super(inventory, slot);
        this.required = required;
        // 按住 Shift 悬停空槽才写清这个槽要哪个元质：常驻提示会盖住旁边的槽，照 AE2 自己的习惯用 Shift。
        // AE2 只在空槽被画时才调这个 supplier，所以 Screen 这个客户端类在服务端建菜单时不会被解析。
        setEmptyTooltip(() -> Screen.hasShiftDown()
                ? List.of(Component.translatable(
                        "gui.thaumicenergistics_ce.arcane_terminal.crystal_required", aspectName(required)))
                : List.of());
    }

    /** 要素的名字就是语言文件里的 {@code aspect.<命名空间>.<路径>}，跟 Thaumaturge 对齐。 */
    private static Component aspectName(ResourceKey<IAspect> aspect) {
        ResourceLocation id = aspect.location();
        return Component.translatable("aspect." + id.getNamespace() + "." + id.getPath());
    }

    public ResourceKey<IAspect> requiredAspect() {
        return required;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return super.mayPlace(stack) && TcWorkbench.isValidCrystal(stack, required);
    }
}
