package titular.modid.permission;

import org.junit.jupiter.api.Test;
import titular.modid.model.PermissionLevel;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionResolverTest {
    @Test
    void resolvesVanillaAndNodeBasedPermissionTiers() {
        VanillaPermissionResolver resolver = new VanillaPermissionResolver();
        UUID player = UUID.randomUUID();

        assertEquals(PermissionLevel.PLAYER, resolver.resolve(player, 1, Set.of()));
        assertEquals(PermissionLevel.ADMIN, resolver.resolve(player, 2, Set.of()));
        assertEquals(PermissionLevel.SUPERADMIN, resolver.resolve(player, 4, Set.of()));
        assertEquals(PermissionLevel.ADMIN, resolver.resolve(player, 0, Set.of("titular.admin")));
        assertEquals(PermissionLevel.SUPERADMIN, resolver.resolve(player, 0, Set.of("titular.superadmin")));
    }
}
