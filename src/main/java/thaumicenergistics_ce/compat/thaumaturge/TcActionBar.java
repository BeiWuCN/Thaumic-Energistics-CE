package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.misc.TTActionBar;
import net.minecraft.world.entity.player.Player;

public final class TcActionBar {
    private TcActionBar() {}

    public static void sendPurple(Player player, String key) {
        TTActionBar.sendPurple(player, key);
    }
}
