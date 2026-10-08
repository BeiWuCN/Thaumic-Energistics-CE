package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAspects;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThETransaction;

/**
 * 箱子的源质端口，在屏幕背后那一面。它既抽也被抽：箱子从贴着那面的东西抽，
 * 转动缺 cognitio 时报的吸力就是让管道线动起来的东西。管子只朝能应答这个 [能力] 的邻居伸臂，
 * 一条线能不能看见箱子也看它。
 */
final class GachaEssentiaPort implements IEssentiaTransport {

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
            got += ThETransaction.apply(transaction -> storage.extract(aspect, need, transaction));
        }
        if (got < need) {
            IEssentiaTransport tube = neighbours.transport(back);
            if (tube != null) {
                got += drainTube(tube, back, aspect, need - got);
            }
        }
        box.cognitio().add(got);
    }

    /** 一次一个调用地从管道取：一根管子带一点，每次调用交一点，一条线有多少点可给就花多少秒填满储备。 */
    private static int drainTube(IEssentiaTransport tube, Direction face, Holder<IAspect> aspect, int want) {
        Direction into = face.getOpposite();
        if (!tube.canOutputTo(into) || !aspect.equals(tube.getEssentiaType(into))) {
            return 0;
        }
        int got = 0;
        while (got < want) {
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
        return level == null ? null : AEssentiaKeyType.aspectOf(level, TcAspects.COGNITIO.identifier());
    }

    /** 源质只从屏幕背后那一面进，别处都不进；管道线也只有在那一面才能伸出手臂。 */
    @Override
    public boolean isConnectable(Direction face) {
        return face == box.backFace();
    }

    /** 背面是入口，别处的管道臂不归箱子取。 */
    @Override
    public boolean canInputFrom(Direction face) {
        return face == box.backFace();
    }

    /** 从不抽回去：储备是转动的燃料，不是拿来分的存货。 */
    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    /** 吸力由箱子按转动还缺的量自己定，管道设不了。 */
    @Override
    public void setSuction(@Nullable Holder<IAspect> aspect, int amount) {}

    /** 箱子要的是 cognitio，只在还缺一次转动的量时才要。 */
    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction face) {
        return face == box.backFace() && hungry() ? cognitio(box.getLevel()) : null;
    }

    /** 带得动一条线的吸力：管子跟着最饿的邻居走；转不动的箱子什么都不索要，省得抽空它还用不上的罐子。 */
    @Override
    public int getSuctionAmount(Direction face) {
        return face == box.backFace() && hungry() ? SUCTION : 0;
    }

    /** 零：箱子持有的每一点都已许给下一次转动。 */
    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction face) {
        return 0;
    }

    /** 把背面推进来的一点存下，给那种交出自己携带物的管道线用。 */
    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction face) {
        int room = spaceFor(aspect, face);
        if (amount <= 0 || room <= 0) {
            return 0;
        }
        return box.cognitio().add(Math.min(amount, room));
    }

    /** 储备从不超过一次转动的花费，余量就是还缺的那部分。 */
    @Override
    public int spaceFor(Holder<IAspect> aspect, Direction face) {
        if (face != box.backFace() || !hungry() || !aspect.equals(cognitio(box.getLevel()))) {
            return 0;
        }
        return box.cognitio().room();
    }

    /** 箱子里没有可路由的东西：存下的源质不是管道能读的容器。 */
    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        return null;
    }

    /** 同样的原因，0：管道读箱子时找不到可拿走的东西。 */
    @Override
    public int getEssentiaAmount(Direction face) {
        return 0;
    }

    /** 没有阈值：管道线交一点过来时按自己的条件，不比对强度。 */
    @Override
    public int getMinimumSuction() {
        return 0;
    }
}
