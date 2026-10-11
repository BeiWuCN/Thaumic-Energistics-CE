package thaumicenergistics_ce.recipe.infusion;

public record InfusionRisk(int base, int altar, boolean shortages, float stability) {

    public static final InfusionRisk NONE = new InfusionRisk(0, 0, false, 25.0F);

    public static final int CAP = 25;

    public static final int SHORTAGE_TIER = 4;

    public static final int MAX_TIER = 5;

    public int instability() {
        return Math.min(CAP, Math.max(0, base) + Math.max(0, altar));
    }

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
