package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * 箱子的源质端口，在屏幕背后的那个面上。它既索取也被索取：箱子从贴着那个面
 * 的东西抽取，而它在一次转动缺少 cognitio 时报告的吸力
 * 就是让一条管道线动起来的东西。管道只会朝一个能应答这个能力的邻居长出手臂，
 * 所以端口也是让箱子对一条线可见的东西。
 */
final class GachaEssentiaPort implements IEssentiaTransport {

    /** 箱子在一次转动缺少 cognitio 时报告的吸力：Thaumaturge 自己的源质端口
     * 索取时用的强度，足以压过一个罐并让整条线动起来。 */
    private static final int SUCTION = 128;

    private final BlockEntityGachaBox box;
    private final CachedEssentiaNeighbours neighbours;

    GachaEssentiaPort(BlockEntityGachaBox box) {
        this.box = box;
        this.neighbours = new CachedEssentiaNeighbours(box);
    }

    /** 把箱子背后那个面递出来的东西存入，上限是下一次转动还需要的量。 */
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

    /** 从管道一次一调用地取：一根管道携带一点、每次调用交出一点，
     * 所以一条管道线会花掉它有多少点可给就有多少秒来填满储备。 */
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

    /** 现在一点是否会被接受：箱子能转动并且缺一次转动的燃料。 */
    private boolean hungry() {
        return box.cognitio().wants() && box.canTurnNow();
    }

    /** cognitio 按 level 给出的形式，用于那些在没有服务端时到达的能力回答。 */
    private static @Nullable Holder<IAspect> cognitio(@Nullable Level level) {
        return level == null ? null : AEssentiaKeyType.aspectOf(level, TCAspects.COGNITIO.location());
    }

    /** 源质只经过屏幕背后的那个面到达，不经任何其他面，那也是一条管道线
     * 必须结束、它的手臂才能形成的地方。 */
    @Override
    public boolean isConnectable(Direction face) {
        return face == box.backFace();
    }

    /** 背面是入口；其他任何位置的管道手臂都不归箱子取用。 */
    @Override
    public boolean canInputFrom(Direction face) {
        return face == box.backFace();
    }

    /** 绝不把任何东西再抽出去：储备是一次转动的燃料，不是可供分享的存货。 */
    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    /** 箱子用这次转动还需要的量自己造出吸力，所以管道设定不了它。 */
    @Override
    public void setSuction(@Nullable Holder<IAspect> aspect, int amount) {}

    /** 箱子在索要什么：cognitio，并且只在它还缺一次转动的量时。 */
    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction face) {
        return face == box.backFace() && hungry() ? cognitio(box.getLevel()) : null;
    }

    /** 让一条线动起来的吸力：管道跟随它最饿的邻居，所以一个转不动的箱子
     * 什么也不索要，而不是把它还用不上的一个罐抽空。 */
    @Override
    public int getSuctionAmount(Direction face) {
        return face == box.backFace() && hungry() ? SUCTION : 0;
    }

    /** 零：箱子持有的每一点都已经被它要支付的下一次转动预定了。 */
    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction face) {
        return 0;
    }

    /** 把经过背面推入的一点存入，用于一条把它携带的东西交出来的线。 */
    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction face) {
        int room = spaceFor(aspect, face);
        if (amount <= 0 || room <= 0) {
            return 0;
        }
        return box.cognitio().add(Math.min(amount, room));
    }

    /** 储备持有的量从不超一次转动的花费，所以余量就是还缺的那部分。 */
    @Override
    public int spaceFor(Holder<IAspect> aspect, Direction face) {
        if (face != box.backFace() || !hungry() || !aspect.equals(cognitio(box.getLevel()))) {
            return 0;
        }
        return box.cognitio().room();
    }

    /** 箱子里没有任何东西可供路由：已经存入的东西不是一个管道能读取的容器。 */
    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        return null;
    }

    /** 同样的原因，为零：管道读箱子时找不到它可以拿走的东西。 */
    @Override
    public int getEssentiaAmount(Direction face) {
        return 0;
    }

    /** 没有阈值：一条线可以按自己的条件交过来一点，而不必匹配某个强度。 */
    @Override
    public int getMinimumSuction() {
        return 0;
    }
}
