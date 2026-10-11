package thaumicenergistics_ce.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ThEConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue RESTRICT_ESSENTIA_VISIBILITY;
    public static final ModConfigSpec.BooleanValue ARCANE_ESSENTIA_ACCESS;
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
        b.translation("thaumicenergistics_ce.config.terminals").push("terminals");
        RESTRICT_ESSENTIA_VISIBILITY = b.comment("Hide essentia in ordinary ME terminals. Dedicated Essentia Terminals remain available. Arcane terminals use their own access setting.")
                .translation("thaumicenergistics_ce.config.terminals.restrictEssentiaVisibility")
                .define("restrictEssentiaVisibility", false);
        ARCANE_ESSENTIA_ACCESS = b.comment("Allow wired and wireless arcane crafting terminals to display and transfer essentia when an Essentia Access Card is installed. Disabled by default.")
                .translation("thaumicenergistics_ce.config.terminals.arcaneEssentiaAccess")
                .define("arcaneEssentiaAccess", false);
        QUICK_INSERT = b.comment("Shift-click wands and crystals into their dedicated arcane terminal slots before moving them to ordinary inventory slots.")
                .translation("thaumicenergistics_ce.config.terminals.quickInsertWandAndCrystals")
                .define("quickInsertWandAndCrystals", true);
        ALLOW_AE_FOR_AURA_VIS = b.comment("Allow wireless arcane terminals to spend AE when drawing environmental VIS. Placed terminals draw chunk VIS directly. Disabled by default.")
                .translation("thaumicenergistics_ce.config.terminals.allowAeForAuraVis")
                .define("allowAeForAuraVis", false);
        AE_PER_VIS = b.comment("AE cost per point of environmental VIS drawn by wireless arcane terminals. Placed terminals draw chunk VIS directly.")
                .translation("thaumicenergistics_ce.config.terminals.aePerVis")
                .defineInRange("aePerVis", 1000.0, 0.01, 1000000.0);
        b.pop().translation("thaumicenergistics_ce.config.buses").push("buses");
        BUS_ACCESS = b.comment("Allow ME Import, Export and Storage Buses with an Essentia Access Card to access essentia.")
                .translation("thaumicenergistics_ce.config.buses.essentiaAccessEnabled")
                .define("essentiaAccessEnabled", true);
        BUS_POWER = b.comment("AE cost per point of essentia transferred by import and export buses. Storage buses use AE2 storage access costs.")
                .translation("thaumicenergistics_ce.config.buses.aePerEssentia")
                .defineInRange("aePerEssentia", 10.0, 0.0, 1000000.0);
        b.pop().translation("thaumicenergistics_ce.config.golemBackpack").push("golemBackpack");
        GOLEM_TRANSFER = b.comment("Allow golem wireless backpacks to transfer items to the network. Disabling transfers does not prevent equipping or removing the backpack.")
                .translation("thaumicenergistics_ce.config.golemBackpack.transferEnabled")
                .define("transferEnabled", true);
        GOLEM_COOLDOWN = b.comment("Ticks between backpack transfers. 20 ticks equal one second.")
                .translation("thaumicenergistics_ce.config.golemBackpack.transferIntervalTicks")
                .defineInRange("transferIntervalTicks", 20, 1, 12000);
        b.pop().translation("thaumicenergistics_ce.config.arcaneAssembler").push("arcaneAssembler");
        ASSEMBLER_ENABLED = b.comment("Allow arcane assemblers to craft. Disabling pauses existing jobs; enabling resumes them.")
                .translation("thaumicenergistics_ce.config.arcaneAssembler.enabled")
                .define("enabled", true);
        ASSEMBLER_TICKS = b.comment("Crafting time in ticks without acceleration cards. Each card reduces it by 4 ticks, to a minimum of 4 ticks.")
                .translation("thaumicenergistics_ce.config.arcaneAssembler.craftTicks")
                .defineInRange("craftTicks", 20, 4, 12000);
        ASSEMBLER_POWER = b.comment("AE consumed per tick while crafting.")
                .translation("thaumicenergistics_ce.config.arcaneAssembler.aePerTick")
                .defineInRange("aePerTick", 1.5, 0.0, 1000000.0);
        b.pop().translation("thaumicenergistics_ce.config.essentiaVibrationChamber").push("essentiaVibrationChamber");
        VIBRATION_ENABLED = b.comment("Allow essentia vibration chambers to accept and burn essentia. Disabling preserves fuel progress and stored energy.")
                .translation("thaumicenergistics_ce.config.essentiaVibrationChamber.enabled")
                .define("enabled", true);
        BURN_TICKS = b.comment("Base burn time in ticks. Divide by 1.6 for Potentia and by 2 for other aspects. Applies only to newly ignited fuel.")
                .translation("thaumicenergistics_ce.config.essentiaVibrationChamber.baseBurnTicks")
                .defineInRange("baseBurnTicks", 800, 2, 120000);
        b.pop().translation("thaumicenergistics_ce.config.gachaBox").push("gachaBox");
        GACHA_ENABLED = b.comment("Allow gacha boxes to operate. Disabling pauses active rounds and preserves the Brain in a Jar.")
                .translation("thaumicenergistics_ce.config.gachaBox.enabled")
                .define("enabled", true);
        GACHA_POWER = b.comment("AE consumed when a round starts. Does not affect rounds already in progress.")
                .translation("thaumicenergistics_ce.config.gachaBox.aePerTurn")
                .defineInRange("aePerTurn", 500.0, 0.0, 1000000.0);
        GACHA_SHORT_CHANCE = b.comment("Payout chance for a 30-second round, from 0 to 1.")
                .translation("thaumicenergistics_ce.config.gachaBox.chanceAt30Seconds")
                .defineInRange("chanceAt30Seconds", 0.75, 0.0, 1.0);
        GACHA_LONG_CHANCE = b.comment("Payout chance for a 180-second round, from 0 to 1. Other round lengths use linear interpolation.")
                .translation("thaumicenergistics_ce.config.gachaBox.chanceAt180Seconds")
                .defineInRange("chanceAt180Seconds", 0.45, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private ThEConfig() {}
}
