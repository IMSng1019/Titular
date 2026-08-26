package titular.modid.server;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import titular.modid.network.RequestCodec;
import titular.modid.network.TitularPackets;
import titular.modid.network.TitularRequest;

/** Fabric packet registration and server-thread request dispatch. */
public final class ServerNetworking {
    private static boolean registered;

    private ServerNetworking() { }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        ServerPlayNetworking.registerGlobalReceiver(TitularPackets.REQUEST,
                ServerNetworking::receiveRequest);
    }

    private static void receiveRequest(MinecraftServer server, ServerPlayerEntity player,
                                       ServerPlayNetworkHandler handler, PacketByteBuf buffer,
                                       PacketSender responseSender) {
        TitularServerRuntime runtime = TitularServerRuntime.active();
        if (runtime == null) return;
        final TitularRequest request;
        try {
            request = RequestCodec.decode(buffer);
        } catch (RuntimeException exception) {
            server.execute(() -> {
                TitularServerRuntime current = TitularServerRuntime.active();
                if (current == runtime && current.server() == server && current.ownsConnection(player)) {
                    current.errorResponder().error(player.getUuid(), "Malformed Titular request", true);
                }
            });
            return;
        }
        server.execute(() -> {
            TitularServerRuntime current = TitularServerRuntime.active();
            if (current == null || current != runtime || current.server() != server
                    || !current.ownsConnection(player) || current.requestHandler() == null) return;
            current.requestHandler().handle(player.getUuid(), request);
        });
    }
}
