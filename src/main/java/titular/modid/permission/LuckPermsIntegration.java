package titular.modid.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import titular.modid.model.TitularData;
import titular.modid.service.MutationResult;
import titular.modid.service.TitularService;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bridges the API-neutral facade to the server-authoritative service.  The
 * scheduler is injected so event callbacks can safely hop to the server
 * thread; unit tests can use a queue or direct execution.
 */
public final class LuckPermsIntegration implements PermissionContextProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("titular/luckperms");

    private final LuckPermsFacade facade;
    private final TitularService service;
    private final Consumer<TitularData> broadcaster;
    private final Consumer<Runnable> serverExecutor;
    private final Consumer<UUID> listener = this::onUserDataRecalculated;
    private volatile boolean started;
    private long lifecycleGeneration;

    public LuckPermsIntegration(LuckPermsFacade facade, TitularService service,
            Consumer<TitularData> broadcaster) {
        this(facade, service, broadcaster, Runnable::run);
    }

    public LuckPermsIntegration(LuckPermsFacade facade, TitularService service,
            Consumer<TitularData> broadcaster, Consumer<Runnable> serverExecutor) {
        this.facade = Objects.requireNonNull(facade, "facade");
        this.service = Objects.requireNonNull(service, "service");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.serverExecutor = Objects.requireNonNull(serverExecutor, "serverExecutor");
    }

    public synchronized void start() {
        if (started) return;
        try {
            facade.start();
            started = true;
            lifecycleGeneration++;
            facade.registerUserDataRecalculationListener(listener);
        } catch (RuntimeException exception) {
            started = false;
            LOGGER.warn("LuckPerms integration unavailable; continuing with JSON groups", exception);
            try { facade.stop(); } catch (RuntimeException ignored) { }
        }
    }

    public synchronized void stop() {
        if (!started) return;
        try {
            facade.unregisterUserDataRecalculationListener(listener);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to unregister LuckPerms listener", exception);
        } finally {
            try { facade.stop(); } catch (RuntimeException exception) {
                LOGGER.warn("Unable to stop LuckPerms integration", exception);
            }
            started = false;
            lifecycleGeneration++;
        }
    }

    public boolean isStarted() {
        return started;
    }

    /** Synchronizes one user immediately on the current (server) thread. */
    public boolean syncUser(UUID playerId) {
        if (playerId == null) return false;
        if (!facade.isAvailable() || !facade.hasUser(playerId)) return false;
        try {
            TitularData before = service.data();
            MutationResult result = service.syncLuckPermsGroups(playerId, facade.inheritedGroups(playerId));
            if (!result.success() || result.data().revision() == before.revision()) return false;
            broadcaster.accept(result.data());
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to synchronize LuckPerms groups for {}", playerId, exception);
            return false;
        }
    }

    @Override
    public PermissionResolver.PermissionContext context(UUID actorId) {
        try {
            return facade.permissionContext(actorId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to read LuckPerms permission context for {}", actorId, exception);
            return new PermissionResolver.PermissionContext(0, java.util.Set.of());
        }
    }

    public PermissionResolver.PermissionContext permissionContext(UUID actorId) {
        return context(actorId);
    }

    private void onUserDataRecalculated(UUID playerId) {
        if (playerId == null) return;
        final long generation;
        synchronized (this) {
            if (!started) return;
            generation = lifecycleGeneration;
        }
        try {
            serverExecutor.accept(() -> {
                synchronized (LuckPermsIntegration.this) {
                    if (!started || generation != lifecycleGeneration) return;
                }
                syncUser(playerId);
            });
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to schedule LuckPerms group synchronization", exception);
        }
    }
}
