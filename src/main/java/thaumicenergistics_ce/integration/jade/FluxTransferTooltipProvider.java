package thaumicenergistics_ce.integration.jade;

import appeng.api.integrations.igtooltip.BaseClassRegistration;
import appeng.api.integrations.igtooltip.ClientRegistration;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.PartTooltips;
import appeng.api.integrations.igtooltip.TooltipProvider;
import thaumicenergistics_ce.client.jade.FluxTransferTooltip;
import thaumicenergistics_ce.part.PartFluxTransferInterface;

/**
 * 把通量接口的两半悬浮提示注册给 AE2。
 * <ul>
 *   <li>26.1.13 通过 {@code META-INF/services} 加载悬浮提示提供器，所以下面两个调用
 *       不能再从 mod 构造函数和客户端初始化里发出——改为在那个文件
 *       里写上这个类名。
 *   <li>部件不是方块实体，所以两半都走 {@link PartTooltips}，
 *       不走注册对象上那些 {@code addBlockEntity*} 方法。
 * </ul>
 */
public final class FluxTransferTooltipProvider implements TooltipProvider {

    @Override
    public void registerCommon(CommonRegistration registration) {
        PartTooltips.addServerData(PartFluxTransferInterface.class, FluxTransferStatusProvider.INSTANCE);
    }

    @Override
    public void registerClient(ClientRegistration registration) {
        PartTooltips.addBody(PartFluxTransferInterface.class, FluxTransferTooltip.INSTANCE);
    }

    @Override
    public void registerBlockEntityBaseClasses(BaseClassRegistration registration) {
        // 这里没有自带方块实体的部件宿主：接口住在一条线缆上。
    }
}
