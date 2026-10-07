package thaumicenergistics_ce.menu;

import java.util.function.IntSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;

/**
 * 知识铭刻机的两项读数，由菜单交给屏幕：服务端上取自机器和它的核心槽，
 * 客户端上是服务端最后发来的值；{@code set} 保留收到的值，客户端上槽位同步会调它。
 */
final class KnowledgeInscriberReadings implements ContainerData {

    private static final int COUNT = 2;

    private final @Nullable BlockEntityKnowledgeInscriber inscriber;

    /** 状态为谁而读：配方能不能存要对照这名玩家检查。 */
    private final Player player;

    /** 核心槽里是否有核心。只有菜单能看到自己的槽位。 */
    private final IntSupplier coreInSlot;

    private final int[] mirrored = new int[COUNT];

    KnowledgeInscriberReadings(
            @Nullable BlockEntityKnowledgeInscriber inscriber, Player player, IntSupplier coreInSlot) {
        this.inscriber = inscriber;
        this.player = player;
        this.coreInSlot = coreInSlot;
    }

    @Override
    public int get(int index) {
        if (inscriber == null) {
            return index >= 0 && index < mirrored.length ? mirrored[index] : 0;
        }
        return switch (index) {
            case MenuKnowledgeInscriber.DATA_HAS_CORE -> coreInSlot.getAsInt();
            case MenuKnowledgeInscriber.DATA_STATE -> status();
            default -> 0;
        };
    }

    /**
     * 机器的状态，只是对不能存该配方的玩家，[ACTIONABLE] 变成 [RESEARCH_LOCKED]：
     * 按钮的 tooltip 读它；[ACTIONABLE] 是可存储，不是被允许。
     */
    private int status() {
        int status = inscriber.status();
        if (status == BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE && !inscriber.canStore(player)) {
            return BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED;
        }
        return status;
    }

    @Override
    public void set(int index, int value) {
        if (index >= 0 && index < mirrored.length) {
            mirrored[index] = value;
        }
    }

    @Override
    public int getCount() {
        return COUNT;
    }
}
