package thaumicenergistics_ce.network;

/**
 * 每个发往服务端的载荷向接收者索取的唯一一件事：它发给的是哪个菜单，这样屏幕关闭后
 * 才到的包会被丢弃，而不是落到接下来打开的东西上。
 */
public interface ThEMenuReceiver {

    int containerId();
}
