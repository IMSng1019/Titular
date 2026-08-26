package titular.modid.permission;

import java.util.Set;
import java.util.UUID;

/** No-op soft-dependency implementation used when LuckPerms is unavailable. */
public final class NoLuckPermsFacade implements LuckPermsFacade {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Set<String> inheritedGroups(UUID playerId) {
        return Set.of();
    }

    @Override
    public Set<String> permissionNodes(UUID actorId) {
        return Set.of();
    }
}
