package titular.modid.permission;

import titular.modid.model.PermissionLevel;

import java.util.Set;
import java.util.UUID;

/** Resolves the effective Titular permission tier for a command actor. */
public interface PermissionResolver {
    PermissionLevel resolve(UUID actorId, int operatorLevel, Set<String> permissionNodes);

    default PermissionLevel resolve(UUID actorId, int operatorLevel, String... permissionNodes) {
        return resolve(actorId, operatorLevel,
                permissionNodes == null ? Set.of() : java.util.Arrays.stream(permissionNodes)
                        .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    default PermissionLevel resolve(UUID actorId, PermissionContext context) {
        if (context == null) return PermissionLevel.PLAYER;
        return resolve(actorId, context.operatorLevel(), context.permissionNodes());
    }

    record PermissionContext(int operatorLevel, Set<String> permissionNodes) {
        public PermissionContext {
            permissionNodes = permissionNodes == null ? Set.of() : Set.copyOf(permissionNodes);
        }
    }
}
