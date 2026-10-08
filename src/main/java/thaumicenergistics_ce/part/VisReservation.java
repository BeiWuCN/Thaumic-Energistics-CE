package thaumicenergistics_ce.part;

/**
 * 对 Vis Interface 的 vis 与背后 [ME 网络] 能量的一次占用。
 * TECE 自己的类型：[Thaumaturge] 0.4.6 把预留模型换成拉取模型，
 * {@code IVisRelaySource} 现在索要一定数量的 centivis 并在一次调用里取走，
 * 双方过去共用的 {@code Reservation} 不复存在。
 * [Arcane Assembler] 仍要先问再付，它要比较多个来源并挑一个，
 * 因此这里保留两步形状，边界靠 TECE 这一侧。
 */
public interface VisReservation {

    int amount();

    int commit();

    void close();
}
