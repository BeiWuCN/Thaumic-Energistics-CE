package thaumicenergistics_ce.blockentity;

import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ClientSyncSend {

    private ClientSyncSend() {}

    public static void sendBlockEntityUpdate(BlockEntity owner) {
        if (!(owner.getLevel() instanceof ServerLevel server)) {
            return;
        }
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(owner);
        for (ServerPlayer player :
                server.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(owner.getBlockPos()), false)) {
            player.connection.send(packet);
        }
    }
}
