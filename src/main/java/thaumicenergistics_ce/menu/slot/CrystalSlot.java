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

public class CrystalSlot extends AppEngSlot {
    private static long lastLogAt;
    private static String lastLogKey = "";

    private final ResourceKey<IAspect> required;

    public CrystalSlot(InternalInventory inventory, int slot, ResourceKey<IAspect> required) {
        super(inventory, slot);
        this.required = required;
        setEmptyTooltip(() -> shiftHeld()
                ? List.of(Component.translatable(
                        "gui.thaumicenergistics_ce.arcane_terminal.crystal_required", aspectName(required)))
                : List.of());
    }

    private static boolean shiftHeld() {
        return Minecraft.getInstance().hasShiftDown();
    }

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
