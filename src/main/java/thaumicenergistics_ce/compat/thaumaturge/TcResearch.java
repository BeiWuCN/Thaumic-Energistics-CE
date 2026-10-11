package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import com.leclowndu93150.thaumaturge.content.research.ResearchGrants;
import net.minecraft.server.level.ServerPlayer;

public final class TcResearch {
    private TcResearch() {}

    public static void grantKnowledge(ServerPlayer player, KnowledgeType type, int amount) {
        ResearchGrants.grantConvertedKnowledge(player, type, amount);
    }
}
