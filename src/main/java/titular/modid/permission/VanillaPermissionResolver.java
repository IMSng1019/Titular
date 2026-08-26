package titular.modid.permission;

import titular.modid.model.PermissionLevel;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Default resolver using vanilla operator level and optional permission nodes. */
public final class VanillaPermissionResolver implements PermissionResolver {
    public static final String ADMIN_NODE = "titular.admin";
    public static final String SUPERADMIN_NODE = "titular.superadmin";

    @Override
    public PermissionLevel resolve(UUID actorId, int operatorLevel, Set<String> permissionNodes) {
        if (operatorLevel >= 4 || contains(permissionNodes, SUPERADMIN_NODE)) return PermissionLevel.SUPERADMIN;
        if (operatorLevel >= 2 || contains(permissionNodes, ADMIN_NODE)) return PermissionLevel.ADMIN;
        return PermissionLevel.PLAYER;
    }

    private static boolean contains(Set<String> nodes, String expected) {
        if (nodes == null) return false;
        for (String node : nodes) {
            if (node != null && expected.equals(node.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
