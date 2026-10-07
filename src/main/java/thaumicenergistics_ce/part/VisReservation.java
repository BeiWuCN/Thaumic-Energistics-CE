package thaumicenergistics_ce.part;

/**
 * 对 Vis Interface 的 vis 以及其背后 [ME 网络] 能量的一次占用。TECE 自己的类型：[Thaumaturge]
 * 0.4.6 把它的预留模型换成了拉取模型，{@code IVisRelaySource} 现在索要一定数量的
 * centivis 并在一次调用里取走，于是双方过去共用的 {@code Reservation} 不复存在。
 * [Arcane Assembler] 仍然要先问再付——它要比较多个来源并挑一个——因此
 * 这个两步形状被保留在这里，即边界靠 TECE 的这一侧。
 */
public interface VisReservation {

    int amount();

    int commit();

    void close();
}
