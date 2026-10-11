package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;

public final class TcGolem {
    private TcGolem() {}

    public static boolean hasActiveJob(EntityThaumaturgeGolem golem) {
        return golem.activeJob() != null;
    }
}
