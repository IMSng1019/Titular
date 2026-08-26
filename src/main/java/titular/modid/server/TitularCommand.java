package titular.modid.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

/** Registers the server-authoritative /titular entry point. */
public final class TitularCommand {
    private TitularCommand() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("titular").executes(context -> execute(context.getSource())));
    }

    private static int execute(ServerCommandSource source) {
        final ServerPlayerEntity player;
        try {
            player = source.getPlayer();
        } catch (Exception exception) {
            source.sendFeedback(() -> Text.literal("/titular can only be opened by a player"), false);
            return 0;
        }
        TitularServerRuntime runtime = TitularServerRuntime.active();
        if (runtime == null) {
            source.sendError(Text.literal("Titular is not ready yet"));
            return 0;
        }
        runtime.openScreen(player);
        return 1;
    }
}
