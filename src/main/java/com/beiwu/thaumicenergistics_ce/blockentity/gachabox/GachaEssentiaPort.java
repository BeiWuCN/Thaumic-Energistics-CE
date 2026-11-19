package com.beiwu.thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcAspects;
import com.beiwu.thaumicenergistics_ce.registry.capability.CachedEssentiaNeighbours;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

final class GachaEssentiaPort implements IEssentiaTransport {

    /** 转动缺 cognitio 时箱子报的吸力，用 Thaumaturge 自己源质端口的索取强度，压得过罐子，带得动整条线。 */
    private static final int SUCTION = 128;

    private final BlockEntityGachaBox box;
    private final CachedEssentiaNeighbours neighbours;

    GachaEssentiaPort(BlockEntityGachaBox box) {
        this.box = box;
        this.neighbours = new CachedEssentiaNeighbours(box);
    }

    /** 把箱子背后那面递出来的东西存进来，上限是下一次转动还缺的量。 */
    void sip(ServerLevel server) {
        int need = box.cognitio().room();
        if (need <= 0) {
            return;
        }
        Holder<IAspect> aspect = cognitio(server);
        if (aspect == null) {
            return;
        }
        Direction back = box.backFace();
        int got = 0;
        IEssentiaStorage storage = neighbours.storage(back);
        if (storage != null) {
            got += storage.extract(aspect, need, false);
        }
        if (got < need) {
            IEssentiaTransport tube = neighbours.transport(back);
            if (tube != null) {
                got += drainTube(tube, back, aspect, need - got);
            }
        }
        box.cognitio().add(got);
    }

    private static int drainTube(IEssentiaTransport tube, Direction face, Holder<IAspect> aspect, int want) {
        Direction into = face.getOpposite();
        if (!tube.canOutputTo(into)) {
            return 0;
        }
        int got = 0;
        while (got < want) {
            Holder<IAspect> held = tube.getEssentiaType(into);
            if (held == null || !held.equals(aspect)) {
                break;
            }
            int taken = tube.takeEssentia(aspect, want - got, into);
            if (taken <= 0) {
                break;
            }
            got += taken;
        }
        return got;
    }

    /** 这一刻收不收一点：箱子转得动，且缺一次转动的燃料。 */
    private boolean hungry() {
        return box.cognitio().wants() && box.canTurnNow();
    }

    /** cognitio 按 level 交出来的形式，给没有服务端就到的能力应答用。 */
    private static @Nullable Holder<IAspect> cognitio(@Nullable Level level) {
        return level == null ? null : AEssentiaKeyType.aspectOf(level, TcAspects.COGNITIO.location());
    }

    /** 源质只从屏幕背后那一面进，别处都不进；管道线也只有在那一面才能伸出手臂。 */
    @Override
    public boolean isConnectable(Direction face) {
        return face == box.backFace();
    }

    /** 从不抽回去：储备是转动的燃料，不是拿来分的存货。 */
    @Override
    public boolean canInputFrom(Direction face) {
        return face == box.backFace();
    }

    /** 箱子要的是 cognitio，只在还缺一次转动的量时才要。 */
    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    /** 带得动一条线的吸力：管子跟着最饿的邻居走；转不动的箱子什么都不索要，省得抽空它还用不上的罐子。 */
    @Override
    public @Nullable Holder<IAspect> getSuctionType(@Nullable Direction face) {
        return hungry() ? cognitio(box.getLevel()) : null;
    }

    /** 零：箱子持有的每一点都已许给下一次转动。 */
    @Override
    public int getSuctionAmount(@Nullable Direction face) {
        return hungry() ? SUCTION : 0;
    }

    /** 把背面推进来的一点存下，给那种交出自己携带物的管道线用。 */
    @Override
    public int getMinimumSuction() {
        return 0;
    }

    /** 储备从不超过一次转动的花费，余量就是还缺的那部分。 */
    @Override
    public void setSuction(@Nullable Holder<IAspect> type, int count) {}

    @Override
    public int addEssentia(Holder<IAspect> type, int count, Direction face) {
        if (count <= 0) {
            return 0;
        }
        int accepted = Math.min(count, spaceFor(type, face));
        return accepted <= 0 ? 0 : box.cognitio().add(accepted);
    }

    /** 箱子里没有可路由的东西：存下的源质不是管道能读的容器。 */
    @Override
    public int addEssentia(Holder<IAspect> type, int count, Direction face, boolean simulate) {
        int accepted = Math.min(Math.max(0, count), spaceFor(type, face));
        return simulate ? accepted : accepted <= 0 ? 0 : box.cognitio().add(accepted);
    }

    /** 同样的原因，0：管道读箱子时找不到可拿走的东西。 */
    @Override
    public int takeEssentia(Holder<IAspect> type, int count, Direction face) {
        return 0;
    }

    @Override
    public int spaceFor(Holder<IAspect> type, Direction face) {
        return face == box.backFace() && hungry() && type.equals(cognitio(box.getLevel())) ? box.cognitio().room() : 0;
    }

    /** 没有阈值：管道线交一点过来时按自己的条件，不比对强度。 */
    @Override
    public @Nullable Holder<IAspect> getEssentiaType(@Nullable Direction face) {
        return null;
    }

    @Override
    public int getEssentiaAmount(@Nullable Direction face) {
        return 0;
    }
}
