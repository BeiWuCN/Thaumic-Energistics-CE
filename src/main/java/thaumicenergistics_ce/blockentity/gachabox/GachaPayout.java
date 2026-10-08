package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import com.leclowndu93150.thaumaturge.content.research.ResearchGrants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 一次给奖的转动交给已绑定玩家什么。
 * 点数按 Thaumaturge 饰品的交付方式注入要素池，奖励带着该 mod 自己的飞行动画与音效，
 * 报告的那行文字飘在快捷栏上方：每次转动都往聊天栏写一行会把它刷爆。
 * {@code OBSERVATION} 落进「原始」要素池（随机一个元始要素），{@code THEORY} 落进「复合」要素池
 * （随机一个已发现的复合要素，一个都没发现时退回元始）—— 提示里的两个数就按这个叫法写，
 * 免得玩家把 Thaumaturge 的知识类型名（观测/理论）当成别的东西。
 */
final class GachaPayout {

    private GachaPayout() {}

    /** 发放掷出的点数并报告它们；箱子只对给奖的转动调用。 */
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
