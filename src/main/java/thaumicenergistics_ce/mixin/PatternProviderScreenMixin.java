package thaumicenergistics_ce.mixin;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给样板供应器界面挂上升级面板。AE2 把这个面板做在 {@code UpgradeableScreen} 里，而供应器界面不是它，
 * 于是样式文档里那个 {@code upgrades} 部件（右对齐、贴窗口顶）一直没人消费。这里按同一做法补一句。
 * 卡片那行可用升级提示也来自这个面板：AE2 的机器物品并不在自身 tooltip 里列。
 */
@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin {

    @Inject(
            method = "<init>(Lappeng/menu/implementations/PatternProviderMenu;"
                    + "Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;"
                    + "Lappeng/client/gui/style/ScreenStyle;)V",
            at = @At("TAIL"))
    private void tce$addUpgradesPanel(
            PatternProviderMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style,
            CallbackInfo callback) {
        ((AEBaseScreenAccessor) (Object) this)
                .tce$widgets()
                .add(
                        "upgrades",
                        new UpgradesPanel(
                                menu.getSlots(SlotSemantics.UPGRADE),
                                ((PatternProviderUpgrades) (Object) menu).tce$upgradeHost()));
    }
}
