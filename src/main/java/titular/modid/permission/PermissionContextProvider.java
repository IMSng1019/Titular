package titular.modid.permission;

import java.util.UUID;

/** Supplies trusted server-side permission context for an actor. */
@FunctionalInterface
public interface PermissionContextProvider {
    PermissionResolver.PermissionContext context(UUID actorId);
}
