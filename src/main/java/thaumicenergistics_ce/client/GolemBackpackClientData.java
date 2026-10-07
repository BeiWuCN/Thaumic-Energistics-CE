package thaumicenergistics_ce.client;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import thaumicenergistics_ce.golem.BackpackSkins;
import thaumicenergistics_ce.network.GolemBackpackPayload;

/**
 * 客户端持有的副本：哪些傀儡背着背包，以及它长什么样。
 * 以实体为弱键，所以离开世界的傀儡会带走它的条目，而未知的傀儡
 * 读作“没有背包”，渲染器从第一帧起就是安全的。
 */
public final class GolemBackpackClientData {

    private static final Map<EntityThaumaturgeGolem, Integer> STATUS = new WeakHashMap<>();
    private static final Map<EntityThaumaturgeGolem, BackpackSkins> SKINS = new WeakHashMap<>();

    private GolemBackpackClientData() {}

    public static void accept(GolemBackpackPayload payload) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());
        if (!(entity instanceof EntityThaumaturgeGolem golem)) {
            return;
        }
        if (payload.status() == GolemBackpackPayload.STATUS_NO_BACKPACK) {
            STATUS.remove(golem);
            SKINS.remove(golem);
        } else {
            STATUS.put(golem, payload.status());
            SKINS.put(golem, BackpackSkins.fromOrdinal(payload.skinOrdinal()));
        }
    }

    public static boolean hasBackpack(EntityThaumaturgeGolem golem) {
        return STATUS.containsKey(golem);
    }

    public static boolean isInRange(EntityThaumaturgeGolem golem) {
        return STATUS.getOrDefault(golem, GolemBackpackPayload.STATUS_NO_BACKPACK)
                == GolemBackpackPayload.STATUS_IN_RANGE;
    }

    public static BackpackSkins skinOf(EntityThaumaturgeGolem golem) {
        return SKINS.getOrDefault(golem, BackpackSkins.Thaumium);
    }
}
