package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import thaumicenergistics_ce.compat.thaumaturge.TcResearch;

final class GachaPayout {

    private GachaPayout() {}

    static void grant(ServerPlayer player) {
        GachaOdds.Payout drawn = GachaOdds.payout(player.getRandom());
        TcResearch.grantKnowledge(player, KnowledgeType.OBSERVATION, drawn.observation());
        TcResearch.grantKnowledge(player, KnowledgeType.THEORY, drawn.theory());
        player.sendOverlayMessage(
                Component.translatable(
                        "block.thaumicenergistics_ce.gacha_box.payout",
                        drawn.points(),
                        drawn.observation(),
                        drawn.theory()));
    }
}
