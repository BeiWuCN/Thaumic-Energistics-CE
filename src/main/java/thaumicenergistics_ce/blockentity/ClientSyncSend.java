package thaumicenergistics_ce.blockentity;

import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The project's one send exit for a block entity update: one packet built once and handed to the players
 * watching this chunk. Whether AE2's own markForClientUpdate would do the same on 1.21 is unverified. */
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
