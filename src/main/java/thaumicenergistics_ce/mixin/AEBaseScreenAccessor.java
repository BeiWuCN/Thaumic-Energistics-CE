package thaumicenergistics_ce.mixin;

import appeng.client.gui.WidgetContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取界面上那个控件容器。{@code widgets} 声明在 {@link appeng.client.gui.AEBaseScreen} 上，
 * 子类里 {@code @Shadow} 它属于跨类取成员，开在基类上最稳。
 */
@Mixin(appeng.client.gui.AEBaseScreen.class)
public interface AEBaseScreenAccessor {

    @Accessor("widgets")
    WidgetContainer tce$widgets();
}
