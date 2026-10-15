package thaumicenergistics_ce.mixin;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WidgetContainer.class, remap = false)
public interface WidgetContainerAccessor {
    @Accessor("compositeWidgets")
    Map<String, ICompositeWidget> tce$compositeWidgets();
}
