package titular.modid.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.node.Node;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * LuckPerms API implementation. This class is only instantiated by
 * {@link LuckPermsFacadeFactory} after the loader confirms the optional mod is present.
 */
final class LuckPermsApiFacade implements LuckPermsFacade {
	private LuckPerms luckPerms;
	private EventSubscription<UserDataRecalculateEvent> subscription;
	private Consumer<UUID> listener;

	@Override
	public synchronized boolean isAvailable() {
		try {
			start();
			return luckPerms != null;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	@Override
	public synchronized boolean hasUser(UUID playerId) {
		return playerId != null && isAvailable() && luckPerms.getUserManager().getUser(playerId) != null;
	}

	@Override
	public synchronized void start() {
		if (luckPerms == null) luckPerms = LuckPermsProvider.get();
	}

	@Override
	public synchronized void stop() {
		if (subscription != null) {
			subscription.close();
			subscription = null;
		}
		listener = null;
		luckPerms = null;
	}

	@Override
	public synchronized Set<String> inheritedGroups(UUID playerId) {
		User user = user(playerId);
		if (user == null) return Set.of();
		Set<String> result = new LinkedHashSet<>();
		for (Group group : user.getInheritedGroups(QueryOptions.nonContextual())) {
			if (group != null && group.getName() != null) result.add(group.getName());
		}
		if (user.getPrimaryGroup() != null) result.add(user.getPrimaryGroup());
		return Collections.unmodifiableSet(new LinkedHashSet<>(result));
	}

	@Override
	public synchronized Set<String> permissionNodes(UUID actorId) {
		User user = user(actorId);
		if (user == null) return Set.of();
		Set<String> result = new LinkedHashSet<>();
		var permissions = user.getCachedData().getPermissionData(QueryOptions.nonContextual());
		for (String key : Set.of("titular.admin", "titular.superadmin")) {
			if (permissions.checkPermission(key) == Tristate.TRUE) result.add(key);
		}
		return Collections.unmodifiableSet(new LinkedHashSet<>(result));
	}

	@Override
	public synchronized void registerUserDataRecalculationListener(Consumer<UUID> callback) {
		start();
		if (subscription != null) subscription.close();
		listener = callback;
		subscription = luckPerms.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
			User user = event.getUser();
			if (listener != null && user != null) listener.accept(user.getUniqueId());
		});
	}

	@Override
	public synchronized void unregisterUserDataRecalculationListener(Consumer<UUID> callback) {
		if (listener == callback && subscription != null) {
			subscription.close();
			subscription = null;
			listener = null;
		}
	}

	private User user(UUID playerId) {
		if (playerId == null) return null;
		start();
		User user = luckPerms.getUserManager().getUser(playerId);
		if (user != null) return user;
		return null;
	}
}
