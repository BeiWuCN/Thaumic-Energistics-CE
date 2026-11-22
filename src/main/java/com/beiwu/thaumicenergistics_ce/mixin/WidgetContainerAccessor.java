package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import java.util.Map;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WidgetContainer.class, remap = false)
public interface WidgetContainerAccessor {
    @Accessor("widgets")
    Map<String, AbstractWidget> tce$widgets();

    @Accessor("compositeWidgets")
    Map<String, ICompositeWidget> tce$compositeWidgets();
}
