package thaumicenergistics_ce.client;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import thaumicenergistics_ce.golem.BackpackSkins;
import thaumicenergistics_ce.net.GolemBackpackPayload;

/**
 * The client's copy of which golems are wearing a backpack, and what it looks like.
 * <ul>
 *   <li>Weakly keyed by the entity, so a golem that leaves the world takes its entry with it.
 *   <li>An unknown golem reads as "no backpack", which makes the renderer safe to run from the
 *       first frame.
 * </ul>
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
