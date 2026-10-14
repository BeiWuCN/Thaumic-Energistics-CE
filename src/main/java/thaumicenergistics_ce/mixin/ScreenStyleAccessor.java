package thaumicenergistics_ce.mixin;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ScreenStyle.class, remap = false)
public interface ScreenStyleAccessor {
    @Accessor("widgets") Map<String, WidgetStyle> tce$widgets();
}
