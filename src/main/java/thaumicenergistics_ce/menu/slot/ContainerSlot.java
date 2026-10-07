package thaumicenergistics_ce.menu.slot;

/**
 * 玩家正使用的源质容器所在之处，供必须告诉服务端是哪一个的终端使用。光标上的物品堆
 * 与主手是两个不同的位置，两者都不是菜单槽位。槽位 id 是 {@code Slot.index}，
 * 不是 {@code getSlotIndex()}——后者在槽位自己的容器内计数，一旦 AE2 的槽位加入列表
 * 指的便是另一个槽位。
 */
public final class ContainerSlot {

    public static final int CURSOR = -1;

    public static final int MAIN_HAND = -2;

    private ContainerSlot() {}
}
