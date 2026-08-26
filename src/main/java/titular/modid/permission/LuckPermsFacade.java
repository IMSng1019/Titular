package titular.modid.permission;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * API-neutral boundary around LuckPerms.  Keeping this type free of LP
 * classes is important: it is safe to load when LuckPerms is not installed.
 */
public interface LuckPermsFacade {
    /** True when the optional provider is installed and usable. */
    default boolean isAvailable() { return true; }

    /** True when a user's data is currently loaded and safe to mirror. */
    default boolean hasUser(UUID playerId) { return true; }

    /** Returns all groups inherited by a user, including their primary group. */
    Set<String> inheritedGroups(UUID playerId);

    /** Returns trusted LuckPerms permission nodes for an actor. */
    Set<String> permissionNodes(UUID actorId);

    default PermissionResolver.PermissionContext permissionContext(UUID actorId) {
        return new PermissionResolver.PermissionContext(0, permissionNodes(actorId));
    }

    /** Installs a listener for user data recalculation events. */
    default void registerUserDataRecalculationListener(Consumer<UUID> listener) {
    }

    /** Removes a listener previously registered by this facade. */
    default void unregisterUserDataRecalculationListener(Consumer<UUID> listener) {
    }

    /** Lifecycle hook for an implementation that needs to initialize resources. */
    default void start() {
    }

    /** Lifecycle hook for an implementation that owns registrations/resources. */
    default void stop() {
    }

    default void close() {
        stop();
    }
}
