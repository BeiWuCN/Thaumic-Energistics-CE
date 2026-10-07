package thaumicenergistics_ce.blockentity;

import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 本项目对方块实体更新的唯一发送出口：构建一次数据包并交给
 * 观望这个区块的玩家。AE2 自己的 markForClientUpdate 在 1.21 上是否做同样的事，尚未验证。 */
public final class ClientSyncSend {

    private ClientSyncSend() {}

    public static void sendBlockEntityUpdate(BlockEntity owner) {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return;
        }
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(owner);
        for (ServerPlayer player :
                server.getChunkSource().chunkMap.getPlayers(new ChunkPos(owner.getBlockPos()), false)) {
            player.connection.send(packet);
        }
    }
}
