package thaumicenergistics_ce.integration.ae2.patternprovider;

import appeng.core.localization.GuiText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import thaumicenergistics_ce.registry.TECEItems;

public final class ProviderUpgradeTooltip {
    private ProviderUpgradeTooltip() {}

    public static List<Component> apply(List<? extends Component> original, boolean enabled) {
        var lines = new ArrayList<Component>(original);
        var card = GuiText.CompatibleUpgrade.text(
                TECEItems.ESSENTIA_ACCESS_CARD.get().getDefaultInstance().getItemName(), 1);
        if (enabled) {
            if (lines.stream().noneMatch(line -> line.getString().equals(card.getString()))) {
                if (lines.isEmpty()) lines.add(GuiText.CompatibleUpgrades.text());
                lines.add(card);
            }
        } else {
            lines.removeIf(line -> line.getString().equals(card.getString()));
            if (lines.size() == 1 && lines.getFirst().getString().equals(
                    GuiText.CompatibleUpgrades.text().getString())) lines.clear();
        }
        return lines;
    }
}
