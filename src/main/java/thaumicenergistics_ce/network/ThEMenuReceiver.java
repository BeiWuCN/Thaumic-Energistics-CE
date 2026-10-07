package thaumicenergistics_ce.network;

/**
 * 每个发往服务端的载荷向接收者要的唯一一件事：它发给哪个菜单。
 * 屏幕关掉之后才到的包就此丢弃，不会落到接下来打开的东西上。
 */
public interface ThEMenuReceiver {

    int containerId();
}
