package titular.modid.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import titular.modid.Titular;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.RequestCodec;
import titular.modid.network.SnapshotCodec;
import titular.modid.network.TitularPackets;
import titular.modid.network.TitularRequest;
import titular.modid.model.DisplayMode;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;

/** Client packet registration and server-authoritative snapshot dispatch. */
public final class ClientNetworking {
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AtomicBoolean OPEN_REQUESTED = new AtomicBoolean();
    private static final AtomicReference<String> LAST_ERROR = new AtomicReference<>();

    private ClientNetworking() { }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        ClientPlayNetworking.registerGlobalReceiver(TitularPackets.OPEN, ClientNetworking::receiveOpen);
        ClientPlayNetworking.registerGlobalReceiver(TitularPackets.SNAPSHOT, ClientNetworking::receiveSnapshot);
        ClientPlayNetworking.registerGlobalReceiver(TitularPackets.ERROR, ClientNetworking::receiveError);
    }

    private static void receiveOpen(MinecraftClient client, ClientPlayNetworkHandler handler,
                                    PacketByteBuf buffer, PacketSender responseSender) {
        client.execute(() -> {
            if (client.getNetworkHandler() != handler) return;
            OPEN_REQUESTED.set(true);
            openIfReady(client);
        });
    }

    private static void receiveSnapshot(MinecraftClient client, ClientPlayNetworkHandler handler,
                                         PacketByteBuf buffer, PacketSender responseSender) {
        // The callback may run on the networking thread. Retain an independent
        // buffer so decoding itself happens on the Minecraft client thread.
        PacketByteBuf retained = new PacketByteBuf(buffer.copy());
        client.execute(() -> {
            try {
                if (client.getNetworkHandler() != handler) return;
                ClientSnapshot snapshot = SnapshotCodec.decode(retained);
                ClientTitularState.replace(snapshot);
                openIfReady(client);
            } catch (RuntimeException exception) {
                Titular.LOGGER.warn("Ignoring malformed Titular snapshot", exception);
            } finally {
                retained.release();
            }
        });
    }

    private static void receiveError(MinecraftClient client, ClientPlayNetworkHandler handler,
                                     PacketByteBuf buffer, PacketSender responseSender) {
        String message;
        try {
            message = buffer.readString(MAX_ERROR_LENGTH);
        } catch (RuntimeException exception) {
            message = "Malformed Titular server error";
        }
        String finalMessage = message;
        client.execute(() -> {
            if (client.getNetworkHandler() == handler) LAST_ERROR.set(finalMessage);
        });
    }

    private static void openIfReady(MinecraftClient client) {
        if (OPEN_REQUESTED.get() && ClientTitularState.current() != null) {
            OPEN_REQUESTED.set(false);
            client.setScreen(new TitularScreen());
        }
    }

    public static boolean hasPendingOpen() {
        return OPEN_REQUESTED.get();
    }

    public static String lastError() {
        return LAST_ERROR.get();
    }

    public static void clearError() {
        LAST_ERROR.set(null);
    }

    public static void clearConnectionState() {
        OPEN_REQUESTED.set(false);
        LAST_ERROR.set(null);
        ClientTitularState.clear();
    }

    /** Sends an already constructed request, preserving its expected revision. */
    public static boolean send(TitularRequest request) {
        if (request == null || !ClientPlayNetworking.canSend(TitularPackets.REQUEST)) return false;
        PacketByteBuf buffer = PacketByteBufs.create();
        try {
            RequestCodec.encode(buffer, request);
            ClientPlayNetworking.send(TitularPackets.REQUEST, buffer);
            return true;
        } catch (RuntimeException exception) {
            buffer.release();
            Titular.LOGGER.warn("Unable to encode Titular request", exception);
            return false;
        }
    }

    /** Creates a request with the latest snapshot revision and sends it. */
    public static boolean sendWithCurrentRevision(LongFunction<TitularRequest> factory) {
        if (factory == null) return false;
        return send(factory.apply(ClientTitularState.expectedRevision()));
    }

    public static boolean sendActivate(String titleId) {
        return sendWithCurrentRevision(revision -> TitularRequest.activate(titleId, revision));
    }

    public static boolean sendClear() {
        return sendWithCurrentRevision(TitularRequest::clear);
    }

    public static boolean sendPrimaryGroup(UUID target, String groupId) {
        return sendWithCurrentRevision(revision -> TitularRequest.setPrimaryGroup(target, groupId, revision));
    }

    public static boolean sendPlayerFields(UUID target, TitularRequest.PlayerFields fields) {
        return sendWithCurrentRevision(revision -> TitularRequest.setPlayerFields(target, fields, revision));
    }

    public static boolean sendDisplayMode(DisplayMode mode) {
        return sendWithCurrentRevision(revision -> TitularRequest.setDisplayMode(mode, revision));
    }

    public static boolean sendRefresh() {
        return sendWithCurrentRevision(TitularRequest::refresh);
    }

    public static boolean sendReload() {
        return sendWithCurrentRevision(TitularRequest::reload);
    }
}
