package titular.modid.server;

import net.fabricmc.loader.api.FabricLoader;
import titular.modid.Titular;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import titular.modid.model.PermissionLevel;
import titular.modid.model.TitularData;
import titular.modid.network.ClientSnapshot;
import titular.modid.network.SnapshotCodec;
import titular.modid.network.SnapshotProjector;
import titular.modid.network.TitularPackets;
import titular.modid.permission.PermissionResolver;
import titular.modid.permission.VanillaPermissionResolver;
import titular.modid.permission.LuckPermsFacade;
import titular.modid.permission.LuckPermsFacadeFactory;
import titular.modid.permission.LuckPermsIntegration;
import titular.modid.service.MutationResult;
import titular.modid.service.TitularService;
import titular.modid.storage.JsonTitularStorage;
import titular.modid.storage.TitularStorage;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fabric-facing server adapter. The service and storage remain independent of
 * Fabric; this class owns their lifecycle and projects data to online clients.
 */
public final class TitularServerRuntime {
    private static volatile TitularServerRuntime active;

    private final MinecraftServer server;
    private final Path configDirectory;
    private final Map<UUID, ServerPlayerEntity> onlinePlayers = new LinkedHashMap<>();
    private final SnapshotBroadcaster snapshotBroadcaster = this::broadcastSnapshot;
    private final ErrorResponder errorResponder = this::sendError;
    private final PermissionResolver permissionResolver = new VanillaPermissionResolver();

    private JsonTitularStorage storage;
    private TitularService service;
    private ServerRequestHandler requestHandler;
    private LuckPermsFacade luckPermsFacade;
    private LuckPermsIntegration luckPermsIntegration;

    public TitularServerRuntime(MinecraftServer server) {
        this(server, FabricLoader.getInstance().getConfigDir().resolve("titular"));
    }

    public TitularServerRuntime(MinecraftServer server, Path configDirectory) {
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.configDirectory = java.util.Objects.requireNonNull(configDirectory, "configDirectory");
    }

    public static TitularServerRuntime active() {
        return active;
    }

    public static void setActive(TitularServerRuntime runtime) {
        active = runtime;
    }

    public synchronized void start() {
        luckPermsFacade = LuckPermsFacadeFactory.create();
        reloadStorage();
    }

    public synchronized void stop() {
        if (luckPermsIntegration != null) {
            luckPermsIntegration.stop();
            luckPermsIntegration = null;
        }
        onlinePlayers.clear();
        if (active == this) active = null;
    }

    public MinecraftServer server() { return server; }
    public Path configDirectory() { return configDirectory; }
    public synchronized JsonTitularStorage storage() { return storage; }
    public synchronized TitularService service() { return service; }
    public synchronized ServerRequestHandler requestHandler() { return requestHandler; }
    public synchronized LuckPermsFacade luckPermsFacade() { return luckPermsFacade; }
    public synchronized LuckPermsIntegration luckPermsIntegration() { return luckPermsIntegration; }
    public SnapshotBroadcaster snapshotBroadcaster() { return snapshotBroadcaster; }
    public ErrorResponder errorResponder() { return errorResponder; }

    public synchronized List<ServerPlayerEntity> onlinePlayers() {
        return List.copyOf(onlinePlayers.values());
    }

    public synchronized void playerJoined(ServerPlayerEntity player) {
        if (player == null) return;
        onlinePlayers.put(player.getUuid(), player);
        if (service == null) return;
        try {
            service.ensurePlayer(player.getUuid());
        } catch (RuntimeException exception) {
            Titular.LOGGER.warn("Could not persist initial Titular state for {}", player.getUuid(), exception);
        }
        boolean changed = luckPermsIntegration != null && luckPermsIntegration.syncUser(player.getUuid());
        if (!changed) broadcastSnapshot(service.data());
    }

    public synchronized void playerDisconnected(ServerPlayerEntity player) {
        if (player == null) return;
        if (onlinePlayers.get(player.getUuid()) != player) return;
        onlinePlayers.remove(player.getUuid());
        if (service != null) broadcastSnapshot(service.data());
    }

    /** Sends the packet which the future client entrypoint will use to open UI. */
    public void openScreen(ServerPlayerEntity player) {
        if (player == null) return;
        sendOpen(player);
        sendSnapshot(player);
    }

    /** Explicit lifecycle reload, used by server integration and tests. */
    public synchronized void reloadStorage() {
        reloadStorage(true);
    }

    private synchronized void reloadStorage(boolean broadcast) {
        JsonTitularStorage nextStorage = new JsonTitularStorage(configDirectory);
        TitularData loaded = nextStorage.load();
        TitularService nextService = new TitularService(nextStorage, loaded, permissionResolver, this::permissionContext);
        this.storage = nextStorage;
        this.service = nextService;
        this.requestHandler = createRequestHandler(nextService);
        if (luckPermsFacade != null) wireLuckPermsIntegration();
        if (broadcast && !onlinePlayers.isEmpty()) broadcastSnapshot(loaded);
    }

    private synchronized void wireLuckPermsIntegration() {
        if (luckPermsFacade == null || service == null) return;
        if (luckPermsIntegration != null) luckPermsIntegration.stop();
        luckPermsIntegration = new LuckPermsIntegration(luckPermsFacade, service,
                this::broadcastSnapshot, task -> server.execute(task));
        luckPermsIntegration.start();
    }

    /** Alias used by lifecycle integrations that expose a reload operation. */
    public synchronized void reload() {
        reloadStorage();
    }

    /** Authorized request callback: reload and atomically replace the service. */
    public synchronized MutationResult reloadFor(UUID actor) {
        if (service == null || service.permissionLevel(actor) != PermissionLevel.SUPERADMIN) {
            return MutationResult.rejected("Superadmin permission required", service == null ? new TitularData() : service.data());
        }
        reloadStorage(false);
        return MutationResult.accepted(service.data());
    }

    public synchronized MutationResult refreshFor(UUID actor) {
        return MutationResult.accepted(service == null ? new TitularData() : service.data());
    }

    public synchronized PermissionResolver.PermissionContext permissionContext(UUID actor) {
        ServerPlayerEntity player = onlinePlayers.get(actor);
        int operatorLevel = player == null ? 0 : server.getPermissionLevel(player.getGameProfile());
        Set<String> nodes = luckPermsIntegration == null
                ? Set.of()
                : luckPermsIntegration.context(actor).permissionNodes();
        return new PermissionResolver.PermissionContext(operatorLevel, nodes);
    }

    public synchronized boolean ownsConnection(UUID actor) {
        return actor != null && onlinePlayers.containsKey(actor);
    }

    public synchronized boolean ownsConnection(ServerPlayerEntity player) {
        return player != null && onlinePlayers.get(player.getUuid()) == player;
    }

    private ServerRequestHandler createRequestHandler(TitularService targetService) {
        return new ServerRequestHandler(targetService, snapshotBroadcaster, errorResponder,
                new ServerRequestHandler.ControlCallbacks() {
                    @Override public MutationResult refresh(UUID actor) { return refreshFor(actor); }
                    @Override public MutationResult reload(UUID actor) { return reloadFor(actor); }
                });
    }

    private synchronized List<SnapshotProjector.OnlinePlayer> projectedOnlinePlayers() {
        List<SnapshotProjector.OnlinePlayer> result = new ArrayList<>();
        for (ServerPlayerEntity player : onlinePlayers.values()) {
            result.add(new SnapshotProjector.OnlinePlayer(player.getUuid(), TitularIdentity.rawName(player)));
        }
        return result;
    }

    private synchronized void broadcastSnapshot(TitularData snapshot) {
        if (snapshot == null) return;
        List<SnapshotProjector.OnlinePlayer> online = projectedOnlinePlayers();
        for (ServerPlayerEntity recipient : onlinePlayers.values()) {
            ClientSnapshot projection = SnapshotProjector.project(snapshot, recipient.getUuid(),
                    permissionLevel(recipient.getUuid()), online);
            sendSnapshot(recipient, projection);
        }
    }

    public synchronized void sendSnapshot(ServerPlayerEntity recipient) {
        if (service == null || recipient == null) return;
        ClientSnapshot projection = SnapshotProjector.project(service.data(), recipient.getUuid(),
                permissionLevel(recipient.getUuid()), projectedOnlinePlayers());
        sendSnapshot(recipient, projection);
    }

    private void sendSnapshot(ServerPlayerEntity recipient, ClientSnapshot snapshot) {
        if (!ServerPlayNetworking.canSend(recipient, TitularPackets.SNAPSHOT)) return;
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            SnapshotCodec.write(buffer, snapshot);
            ServerPlayNetworking.send(recipient, TitularPackets.SNAPSHOT, buffer);
        } catch (RuntimeException exception) {
            Titular.LOGGER.warn("Could not send Titular snapshot to {}", recipient.getUuid(), exception);
            buffer.release();
        }
    }

    private void sendOpen(ServerPlayerEntity recipient) {
        if (!ServerPlayNetworking.canSend(recipient, TitularPackets.OPEN)) return;
        ServerPlayNetworking.send(recipient, TitularPackets.OPEN, new PacketByteBuf(Unpooled.buffer()));
    }

    private synchronized void sendError(UUID actor, String message, boolean refresh) {
        ServerPlayerEntity recipient = onlinePlayers.get(actor);
        if (recipient == null) return;
        if (ServerPlayNetworking.canSend(recipient, TitularPackets.ERROR)) {
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
            try {
                String safe = message == null ? "Titular request rejected" : message;
                if (safe.length() > 1024) safe = safe.substring(0, 1024);
                buffer.writeString(safe, 1024);
                ServerPlayNetworking.send(recipient, TitularPackets.ERROR, buffer);
            } catch (RuntimeException exception) {
                buffer.release();
                Titular.LOGGER.warn("Could not send Titular error to {}", actor, exception);
            }
        }
        if (refresh) sendSnapshot(recipient);
    }

    private PermissionLevel permissionLevel(UUID actor) {
        return service == null ? PermissionLevel.PLAYER : service.permissionLevel(actor);
    }
}
