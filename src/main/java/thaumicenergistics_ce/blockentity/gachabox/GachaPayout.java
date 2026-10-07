package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import com.leclowndu93150.thaumaturge.content.research.ResearchGrants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a paying turn hands the bound player. The points go into the aspect pools the way
 * Thaumaturge's curios hand theirs over, so a payout arrives with the mod's own flying icons and
 * sounds, and the line reporting it rides above the hotbar: a line in the chat for every turn
 * would flood it.
 */
final class GachaPayout {

    private GachaPayout() {}

    /** Grants the drawn points and reports them; the box only calls this for a turn that pays. */
    static void grant(ServerPlayer player) {
        GachaOdds.Payout drawn = GachaOdds.payout(player.getRandom());
        ResearchGrants.grantConvertedKnowledge(player, KnowledgeType.OBSERVATION, drawn.observation());
        ResearchGrants.grantConvertedKnowledge(player, KnowledgeType.THEORY, drawn.theory());
        player.displayClientMessage(
                Component.translatable(
                        "block.thaumicenergistics_ce.gacha_box.payout",
                        drawn.points(),
                        drawn.observation(),
                        drawn.theory()),
                true);
    }
}
