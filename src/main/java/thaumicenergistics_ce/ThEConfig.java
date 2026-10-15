package thaumicenergistics_ce;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ThEConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue RESTRICT_ESSENTIA_VISIBILITY;
    public static final ModConfigSpec.BooleanValue ARCANE_ESSENTIA_ACCESS;
    public static final ModConfigSpec.BooleanValue PATTERN_PROVIDER_AE2;
    public static final ModConfigSpec.BooleanValue PATTERN_PROVIDER_ADVANCED_AE;
    public static final ModConfigSpec.BooleanValue PATTERN_PROVIDER_LIGHTNING;
    public static final ModConfigSpec.BooleanValue PATTERN_PROVIDER_EXTENDED_AE;
    public static final ModConfigSpec.BooleanValue PATTERN_PROVIDER_EXTENDED_AE_PLUS;
    public static final ModConfigSpec.BooleanValue BUS_ACCESS;
    public static final ModConfigSpec.DoubleValue BUS_POWER;
    public static final ModConfigSpec.IntValue GOLEM_COOLDOWN;
    public static final ModConfigSpec.IntValue ASSEMBLER_TICKS;
    public static final ModConfigSpec.DoubleValue ASSEMBLER_POWER;
    public static final ModConfigSpec.IntValue BURN_TICKS;
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
        b.pop().translation("thaumicenergistics_ce.config.patternProviders").push("patternProviders");
        PATTERN_PROVIDER_AE2 = b.comment("Allow AE2 pattern providers to install Essentia Access Cards and send/receive essentia. Disabling preserves installed cards and buffered essentia; cards remain removable. Does not affect other mods\u2019 providers.")
                .translation("thaumicenergistics_ce.config.patternProviders.ae2Enabled")
                .define("ae2Enabled", true);
        PATTERN_PROVIDER_EXTENDED_AE = b.comment("Allow ExtendedAE pattern providers to install Essentia Access Cards and send/receive essentia. Disabling preserves installed cards and buffered essentia; cards remain removable. Does not affect other mods\u2019 providers.")
                .translation("thaumicenergistics_ce.config.patternProviders.extendedAeEnabled")
                .define("extendedAeEnabled", true);
        PATTERN_PROVIDER_EXTENDED_AE_PLUS = b.comment("Allow ExtendedAE Plus pattern providers to install Essentia Access Cards and send/receive essentia. Disabling preserves installed cards and buffered essentia; cards remain removable. Does not affect other mods\u2019 providers.")
                .translation("thaumicenergistics_ce.config.patternProviders.extendedAePlusEnabled")
                .define("extendedAePlusEnabled", true);
        PATTERN_PROVIDER_ADVANCED_AE = b.comment("Allow AdvancedAE pattern providers to install Essentia Access Cards and send/receive essentia. Disabling preserves installed cards and buffered essentia; cards remain removable.")
                .translation("thaumicenergistics_ce.config.patternProviders.advancedAeEnabled")
                .define("advancedAeEnabled", true);
        PATTERN_PROVIDER_LIGHTNING = b.comment("Allow AE2 Lightning Tech Reborn pattern providers to install Essentia Access Cards and send/receive essentia. Disabling preserves installed cards and buffered essentia; cards remain removable.")
                .translation("thaumicenergistics_ce.config.patternProviders.lightningTechEnabled")
                .define("lightningTechEnabled", true);
        b.pop().translation("thaumicenergistics_ce.config.buses").push("buses");
        BUS_ACCESS = b.comment("Allow ME Import, Export and Storage Buses with an Essentia Access Card to access essentia.")
                .translation("thaumicenergistics_ce.config.buses.essentiaAccessEnabled")
                .define("essentiaAccessEnabled", true);
        BUS_POWER = b.comment("AE cost per point of essentia transferred by import and export buses. Storage buses use AE2 storage access costs.")
                .translation("thaumicenergistics_ce.config.buses.aePerEssentia")
                .defineInRange("aePerEssentia", 10.0, 0.0, 1000000.0);
        b.pop().translation("thaumicenergistics_ce.config.golemBackpack").push("golemBackpack");
        GOLEM_COOLDOWN = b.comment("Ticks between backpack transfers. 20 ticks equal one second.")
                .translation("thaumicenergistics_ce.config.golemBackpack.transferIntervalTicks")
                .defineInRange("transferIntervalTicks", 20, 1, 12000);
        b.pop().translation("thaumicenergistics_ce.config.arcaneAssembler").push("arcaneAssembler");
        ASSEMBLER_TICKS = b.comment("Crafting time in ticks without acceleration cards. Each card reduces it by 4 ticks, to a minimum of 4 ticks.")
                .translation("thaumicenergistics_ce.config.arcaneAssembler.craftTicks")
                .defineInRange("craftTicks", 20, 4, 12000);
        ASSEMBLER_POWER = b.comment("AE consumed per tick while crafting.")
                .translation("thaumicenergistics_ce.config.arcaneAssembler.aePerTick")
                .defineInRange("aePerTick", 1.5, 0.0, 1000000.0);
        b.pop().translation("thaumicenergistics_ce.config.essentiaVibrationChamber").push("essentiaVibrationChamber");
        BURN_TICKS = b.comment("Base burn time in ticks. Divide by 1.6 for Potentia and by 2 for other aspects. Applies only to newly ignited fuel.")
                .translation("thaumicenergistics_ce.config.essentiaVibrationChamber.baseBurnTicks")
                .defineInRange("baseBurnTicks", 800, 2, 120000);
        b.pop().translation("thaumicenergistics_ce.config.gachaBox").push("gachaBox");
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
