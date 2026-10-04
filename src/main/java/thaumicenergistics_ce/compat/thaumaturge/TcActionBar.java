package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.misc.TCActionBar;
import net.minecraft.world.entity.player.Player;

/** The purple action-bar line Thaumaturge shows when a wand cannot pay for a cast. */
public final class TcActionBar {
    private TcActionBar() {}

    /** Shows {@code key} - a translation key - to {@code player} in Thaumaturge's casting colour. */
    public static void sendPurple(Player player, String key) {
        TCActionBar.sendPurple(player, key);
    }
}
