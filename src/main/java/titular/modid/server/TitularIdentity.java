package titular.modid.server;

import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side identity projection used by snapshots. */
public final class TitularIdentity {
    private TitularIdentity() { }

    /** Returns the unmodified Minecraft profile name as display text. */
    public static Text rawName(ServerPlayerEntity player) {
        if (player == null || player.getGameProfile() == null) return Text.empty();
        return Text.literal(player.getGameProfile().getName());
    }
}
