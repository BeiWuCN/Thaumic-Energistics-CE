package thaumicenergistics_ce.infusion;

/**
 * 一次注魔有多危险：一个介于 0 与 {@link #CAP} 之间的数，以及它所落入的五个档位之一。
 *
 * @param base 配方本身带有的不稳定度
 * @param altar 祭坛周边额外增加的量，以摆错位置的方块计
 * @param shortages 仪式是否正在等待它够不到的源质
 * @param stability 祭坛此刻自己报告出的稳定度——也就是护目镜显示的值
 */
public record InfusionRisk(int base, int altar, boolean shortages, float stability) {

    public static final InfusionRisk NONE = new InfusionRisk(0, 0, false, 25.0F);

    public static final int CAP = 25;

    public static final int SHORTAGE_TIER = 4;

    public static final int MAX_TIER = 5;

    public int instability() {
        return Math.min(CAP, Math.max(0, base) + Math.max(0, altar));
    }

    /**
     * 该风险属于五个档位中的哪一档，从 1（几乎没什么）到 {@link #MAX_TIER}：按 Thaumaturge
     * 自己的阈值（12.5、0、-25）读取祭坛稳定度，再以 20 为间隔切成五档。
     */
    public int tier() {
        int tier;
        if (stability > 20.0F) {
            tier = 1;
        } else if (stability > 12.5F) {
            tier = 2;
        } else if (stability >= 0.0F) {
            tier = 3;
        } else if (stability > -25.0F) {
            tier = 4;
        } else {
            tier = 5;
        }
        return shortages ? Math.max(tier, SHORTAGE_TIER) : tier;
    }
}
