package thaumicenergistics_ce.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ThEConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue RESTRICT_ESSENTIA_VISIBILITY;
    public static final ModConfigSpec.BooleanValue QUICK_INSERT;
    public static final ModConfigSpec.BooleanValue ALLOW_AE_FOR_AURA_VIS;
    public static final ModConfigSpec.DoubleValue AE_PER_VIS;
    public static final ModConfigSpec.BooleanValue BUS_ACCESS;
    public static final ModConfigSpec.DoubleValue BUS_POWER;
    public static final ModConfigSpec.BooleanValue GOLEM_TRANSFER;
    public static final ModConfigSpec.IntValue GOLEM_COOLDOWN;
    public static final ModConfigSpec.BooleanValue ASSEMBLER_ENABLED;
    public static final ModConfigSpec.IntValue ASSEMBLER_TICKS;
    public static final ModConfigSpec.DoubleValue ASSEMBLER_POWER;
    public static final ModConfigSpec.BooleanValue VIBRATION_ENABLED;
    public static final ModConfigSpec.IntValue BURN_TICKS;
    public static final ModConfigSpec.BooleanValue GACHA_ENABLED;
    public static final ModConfigSpec.DoubleValue GACHA_POWER;
    public static final ModConfigSpec.DoubleValue GACHA_SHORT_CHANCE;
    public static final ModConfigSpec.DoubleValue GACHA_LONG_CHANCE;

    static {
        var b = new ModConfigSpec.Builder();
        b.push("terminals");
        RESTRICT_ESSENTIA_VISIBILITY = b.comment("仅源质终端和装有源质访问卡的奥术终端显示源质。包含无线终端。")
                .define("restrictEssentiaVisibility", true);
        QUICK_INSERT = b.comment("Shift 点击时优先把法杖和水晶放入奥术终端的专用槽位。")
                .define("quickInsertWandAndCrystals", true);
        ALLOW_AE_FOR_AURA_VIS = b.comment("允许未装魔力访问卡的奥术终端消耗 AE 取用环境 VIS。关闭时必须装卡；环境 VIS 不足时仍不能合成。")
                .define("allowAeForAuraVis", false);
        AE_PER_VIS = b.comment("终端支付未定型灵气时，每点 Vis 消耗的 AE。灵气连接卡仍直接取用环境灵气。")
                .defineInRange("aePerVis", 1000.0, 0.01, 1000000.0);
        b.pop().push("buses");
        BUS_ACCESS = b.comment("允许装有源质访问卡的 ME 输入、输出和存储总线访问源质。")
                .define("essentiaAccessEnabled", true);
        BUS_POWER = b.comment("输入和输出总线每传输一点源质消耗的 AE。存储总线沿用 AE2 的存储访问耗电。")
                .defineInRange("aePerEssentia", 10.0, 0.0, 1000000.0);
        b.pop().push("golemBackpack");
        GOLEM_TRANSFER = b.comment("允许傀儡无线背包向网络传输物品。关闭后仍可装备和取下。")
                .define("transferEnabled", true);
        GOLEM_COOLDOWN = b.comment("背包两次传输之间的间隔，单位 tick，20 tick 为一秒。")
                .defineInRange("transferIntervalTicks", 20, 1, 12000);
        b.pop().push("arcaneAssembler");
        ASSEMBLER_ENABLED = b.comment("允许奥术装配室执行合成。关闭时保留已有任务，重新开启后继续。")
                .define("enabled", true);
        ASSEMBLER_TICKS = b.comment("无加速卡时的合成时长，单位 tick。每张加速卡减少4 tick，最低4 tick。")
                .defineInRange("craftTicks", 20, 4, 12000);
        ASSEMBLER_POWER = b.comment("合成进行时每 tick 的 AE 消耗。")
                .defineInRange("aePerTick", 1.5, 0.0, 1000000.0);
        b.pop().push("essentiaVibrationChamber");
        VIBRATION_ENABLED = b.comment("允许源质谐振仓吸入和燃烧源质。关闭时保留燃料进度和储存的能量。")
                .define("enabled", true);
        BURN_TICKS = b.comment("燃烧时长计算的基础 tick 数。能量要素除以1.6，其余除以2；仅对新点燃的燃料生效。")
                .defineInRange("baseBurnTicks", 800, 2, 120000);
        b.pop().push("gachaBox");
        GACHA_ENABLED = b.comment("允许概率之箱运行。关闭时暂停已有轮次，不删除缸中之脑。")
                .define("enabled", true);
        GACHA_POWER = b.comment("每轮开始时消耗的 AE，不影响已开始的轮次。")
                .defineInRange("aePerTurn", 500.0, 0.0, 1000000.0);
        GACHA_SHORT_CHANCE = b.comment("30秒轮次的获奖概率，0到1。")
                .defineInRange("chanceAt30Seconds", 0.75, 0.0, 1.0);
        GACHA_LONG_CHANCE = b.comment("180秒轮次的获奖概率，0到1，其余时长线性插值。")
                .defineInRange("chanceAt180Seconds", 0.45, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private ThEConfig() {}
}
