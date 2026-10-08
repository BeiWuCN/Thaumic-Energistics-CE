package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.misc.TTActionBar;
import net.minecraft.world.entity.player.Player;

/** 法杖付不起一次施法时 Thaumaturge 显示的那行紫色动作栏提示。 */
public final class TcActionBar {
    private TcActionBar() {}

    public static void sendPurple(Player player, String key) {
        TTActionBar.sendPurple(player, key);
    }
}
