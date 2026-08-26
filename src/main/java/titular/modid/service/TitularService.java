package titular.modid.service;

import titular.modid.model.PlayerTitleState;
import titular.modid.model.DisplayMode;
import titular.modid.model.GroupDefinition;
import titular.modid.model.PermissionLevel;
import titular.modid.model.TitleDefinition;
import titular.modid.model.TitularData;
import titular.modid.model.TitularSettings;
import titular.modid.model.TitularLimits;
import titular.modid.permission.PermissionResolver;
import titular.modid.permission.PermissionContextProvider;
import titular.modid.permission.VanillaPermissionResolver;
import titular.modid.storage.TitularStorage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative title state and title activation operations. */
public final class TitularService {
	private final TitularStorage storage;
	private final TitlePoolResolver poolResolver;
	private final PermissionResolver permissionResolver;
	private final PermissionContextProvider permissionContextProvider;
	private TitularData data;
	private static final PermissionContextProvider DEFAULT_PERMISSION_CONTEXT_PROVIDER =
			actorId -> new PermissionResolver.PermissionContext(0, Set.of());

	public TitularService(TitularStorage storage) {
		this(storage, Objects.requireNonNull(storage, "storage").load(), new VanillaPermissionResolver(),
				DEFAULT_PERMISSION_CONTEXT_PROVIDER);
	}

	public TitularService(TitularStorage storage, TitularData initialData) {
		this(storage, initialData, new VanillaPermissionResolver(), DEFAULT_PERMISSION_CONTEXT_PROVIDER);
	}

	public TitularService(TitularStorage storage, PermissionResolver permissionResolver) {
		this(storage, Objects.requireNonNull(storage, "storage").load(), permissionResolver,
				DEFAULT_PERMISSION_CONTEXT_PROVIDER);
	}

	public TitularService(TitularStorage storage, PermissionResolver permissionResolver,
			PermissionContextProvider permissionContextProvider) {
		this(storage, Objects.requireNonNull(storage, "storage").load(), permissionResolver,
				permissionContextProvider);
	}

	public TitularService(TitularStorage storage, TitularData initialData, PermissionResolver permissionResolver) {
		this(storage, initialData, permissionResolver, DEFAULT_PERMISSION_CONTEXT_PROVIDER);
	}

	public TitularService(TitularStorage storage, TitularData initialData, PermissionResolver permissionResolver,
			PermissionContextProvider permissionContextProvider) {
		this.storage = Objects.requireNonNull(storage, "storage");
		this.data = Objects.requireNonNull(initialData, "initialData");
		this.poolResolver = new TitlePoolResolver();
		this.permissionResolver = Objects.requireNonNull(permissionResolver, "permissionResolver");
		this.permissionContextProvider = Objects.requireNonNull(permissionContextProvider, "permissionContextProvider");
	}

	public TitularService(TitularData initialData, TitularStorage storage) {
		this(storage, initialData);
	}

	public TitularService(TitularData initialData, TitularStorage storage, PermissionResolver permissionResolver) {
		this(storage, initialData, permissionResolver);
	}

	public TitularService(TitularData initialData, TitularStorage storage, PermissionResolver permissionResolver,
			PermissionContextProvider permissionContextProvider) {
		this(storage, initialData, permissionResolver, permissionContextProvider);
	}

	public synchronized TitularData data() {
		return data;
	}

	public synchronized TitularData snapshot() {
		return data;
	}

	public synchronized List<String> resolveAvailableTitleIds(UUID playerId) {
		return poolResolver.resolve(playerId, data);
	}

	public synchronized List<TitleDefinition> resolveAvailableTitles(UUID playerId) {
		List<TitleDefinition> result = new ArrayList<>();
		for (String titleId : resolveAvailableTitleIds(playerId)) {
			TitleDefinition title = data.titles().get(titleId);
			if (title != null) result.add(title);
		}
		return List.copyOf(result);
	}

	public synchronized Optional<TitleDefinition> resolveActiveTitle(UUID playerId) {
		PlayerTitleState state = data.players().get(playerId);
		if (state == null || state.activeTitle() == null) return Optional.empty();
		if (!resolveAvailableTitleIds(playerId).contains(state.activeTitle())) return Optional.empty();
		return Optional.ofNullable(data.titles().get(state.activeTitle()));
	}

	public synchronized Optional<String> resolveActiveTitleId(UUID playerId) {
		return resolveActiveTitle(playerId).map(TitleDefinition::id);
	}

	/** Ensures a first-time online/offline player has a persisted empty state. */
	public synchronized PlayerTitleState ensurePlayer(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		PlayerTitleState existing = data.players().get(playerId);
		if (existing != null) return existing;
		if (data.players().size() >= TitularLimits.MAX_DEFINITIONS) {
			throw new IllegalStateException("Maximum Titular player count reached");
		}
		Map<UUID, PlayerTitleState> players = new LinkedHashMap<>(data.players());
		PlayerTitleState created = new PlayerTitleState(playerId);
		players.put(playerId, created);
		TitularData candidate = new TitularData(data.titles(), data.groups(), players, data.settings(), data.revision() + 1);
		storage.save(candidate);
		data = candidate;
		return created;
	}

	public synchronized MutationResult activateTitle(UUID playerId, String titleId) {
		return activateTitle(playerId, playerId, titleId);
	}

	public synchronized MutationResult activateTitle(UUID actorId, UUID targetId, String titleId) {
		return activateTitle(actorId, targetId, titleId, permissionLevel(actorId));
	}

	/** Internal/test adapter; callers must use the overload that resolves actor permissions. */
	synchronized MutationResult activateTitle(UUID actorId, UUID targetId, String titleId,
			PermissionLevel permission) {
		if (!allowsTarget(actorId, targetId, permission, PermissionLevel.PLAYER)) {
			return MutationResult.rejected("You cannot change this player's active title", data);
		}
		PlayerTitleState current = data.players().get(targetId);
		if (current == null) return MutationResult.rejected("Unknown player", data);
		if (titleId != null && !titleId.isBlank() && !resolveAvailableTitleIds(targetId).contains(titleId)) {
			return MutationResult.rejected("Title is not available to this player", data);
		}
		return replacePlayer(current, titleId == null || titleId.isBlank() ? null : titleId);
	}

	public synchronized MutationResult clearActiveTitle(UUID playerId) {
		return activateTitle(playerId, playerId, null);
	}

	public synchronized MutationResult clearActiveTitle(UUID actorId, UUID targetId) {
		return clearActiveTitle(actorId, targetId, permissionLevel(actorId));
	}

	/** Internal/test adapter; callers must use the overload that resolves actor permissions. */
	synchronized MutationResult clearActiveTitle(UUID actorId, UUID targetId, PermissionLevel permission) {
		return activateTitle(actorId, targetId, null, permission);
	}

	/** Resolves the trusted server-side permission context for an actor. */
	public PermissionLevel permissionLevel(UUID actorId) {
		return permissionResolver.resolve(actorId, permissionContextProvider.context(actorId));
	}

	/** Internal/test adapter for legacy callers that already resolved permission. */
	PermissionLevel resolvePermission(UUID actorId, int operatorLevel, java.util.Set<String> permissionNodes) {
		return permissionResolver.resolve(actorId, operatorLevel, permissionNodes);
	}

	public synchronized MutationResult setPrimaryGroup(UUID actorId, UUID targetId, String groupId) {
		return setPrimaryGroup(actorId, targetId, groupId, permissionLevel(actorId));
	}

	/** Internal/test adapter; callers must use the overload that resolves actor permissions. */
	synchronized MutationResult setPrimaryGroup(UUID actorId, UUID targetId, String groupId,
			PermissionLevel permission) {
		if (!allowsTarget(actorId, targetId, permission, PermissionLevel.ADMIN)) {
			return rejected("Only an administrator may change a player's primary group", data);
		}
		if (groupId != null && !groupId.isBlank() && !data.groups().containsKey(groupId)) {
			return rejected("Unknown group", data);
		}
		if (groupId != null && groupId.length() > TitularLimits.MAX_STRING_LENGTH) {
			return rejected("Group id is too long", data);
		}
		PlayerTitleState current = data.players().get(targetId);
		if (current == null && data.players().size() >= TitularLimits.MAX_DEFINITIONS) return rejected("Maximum player count reached", data);
		if (current == null && (isSuperadmin(actorId, permission)
				|| (actorId != null && actorId.equals(targetId) && permission != null
				&& permission.includes(PermissionLevel.ADMIN)))) {
			current = new PlayerTitleState(targetId);
		}
		if (current == null) return rejected("Unknown player", data);
		return replacePlayerState(new PlayerTitleState(current.playerId(), blankToNull(groupId), current.extraGroups(),
				current.extraTitles(), current.activeTitle(), current.luckPermsGroups()));
	}

	public synchronized MutationResult setPlayerPrimaryGroup(UUID actorId, UUID targetId, String groupId) {
		return setPrimaryGroup(actorId, targetId, groupId);
	}

	synchronized MutationResult setPlayerPrimaryGroup(UUID actorId, UUID targetId, String groupId,
			PermissionLevel permission) {
		return setPrimaryGroup(actorId, targetId, groupId, permission);
	}

	public synchronized MutationResult setExtraGroups(UUID actorId, UUID targetId, List<String> groupIds) {
		return setExtraGroups(actorId, targetId, groupIds, permissionLevel(actorId));
	}

	synchronized MutationResult setExtraGroups(UUID actorId, UUID targetId, List<String> groupIds,
			PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		PlayerTitleState current = data.players().get(targetId);
		if (current == null && data.players().size() >= TitularLimits.MAX_DEFINITIONS) return rejected("Maximum player count reached", data);
		if (current == null) current = new PlayerTitleState(targetId);
		if (hasInvalidReference(groupIds)) return rejected("Invalid group reference", data);
		if (groupIds != null && groupIds.size() > TitularLimits.MAX_REFERENCE_LIST) return rejected("Too many group references", data);
		List<String> groups = normalizeReferences(groupIds);
		for (String groupId : groups) if (!data.groups().containsKey(groupId)) return rejected("Unknown group", data);
		return replacePlayerState(new PlayerTitleState(current.playerId(), current.primaryGroup(), groups,
				current.extraTitles(), current.activeTitle(), current.luckPermsGroups()));
	}

	public synchronized MutationResult setExtraTitles(UUID actorId, UUID targetId, List<String> titleIds) {
		return setExtraTitles(actorId, targetId, titleIds, permissionLevel(actorId));
	}

	synchronized MutationResult setExtraTitles(UUID actorId, UUID targetId, List<String> titleIds,
			PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		PlayerTitleState current = data.players().get(targetId);
		if (current == null && data.players().size() >= TitularLimits.MAX_DEFINITIONS) return rejected("Maximum player count reached", data);
		if (current == null) current = new PlayerTitleState(targetId);
		if (hasInvalidReference(titleIds)) return rejected("Invalid title reference", data);
		if (titleIds != null && titleIds.size() > TitularLimits.MAX_REFERENCE_LIST) return rejected("Too many title references", data);
		List<String> titles = normalizeReferences(titleIds);
		for (String titleId : titles) if (!data.titles().containsKey(titleId)) return rejected("Unknown title", data);
		return replacePlayerState(new PlayerTitleState(current.playerId(), current.primaryGroup(), current.extraGroups(),
				titles, current.activeTitle(), current.luckPermsGroups()));
	}

	public synchronized MutationResult addExtraGroup(UUID actorId, UUID targetId, String groupId) {
		return addExtraGroup(actorId, targetId, groupId, permissionLevel(actorId));
	}

	synchronized MutationResult addExtraGroup(UUID actorId, UUID targetId, String groupId,
			PermissionLevel permission) {
		if (hasInvalidReference(groupId)) return rejected("Invalid group reference", data);
		PlayerTitleState state = data.players().get(targetId);
		if (state == null && !isSuperadmin(actorId, permission)) return rejected("Unknown player", data);
		if (state == null) state = new PlayerTitleState(targetId);
		List<String> groups = new ArrayList<>(state.extraGroups());
		groups.add(groupId);
		return setExtraGroups(actorId, targetId, groups, permission);
	}

	public synchronized MutationResult removeExtraGroup(UUID actorId, UUID targetId, String groupId) {
		return removeExtraGroup(actorId, targetId, groupId, permissionLevel(actorId));
	}

	synchronized MutationResult removeExtraGroup(UUID actorId, UUID targetId, String groupId,
			PermissionLevel permission) {
		if (hasInvalidReference(groupId)) return rejected("Invalid group reference", data);
		PlayerTitleState state = data.players().get(targetId);
		if (state == null) return rejected("Unknown player", data);
		List<String> groups = new ArrayList<>(state.extraGroups());
		groups.removeIf(groupId::equals);
		return setExtraGroups(actorId, targetId, groups, permission);
	}

	public synchronized MutationResult addExtraTitle(UUID actorId, UUID targetId, String titleId) {
		return addExtraTitle(actorId, targetId, titleId, permissionLevel(actorId));
	}

	synchronized MutationResult addExtraTitle(UUID actorId, UUID targetId, String titleId,
			PermissionLevel permission) {
		if (hasInvalidReference(titleId)) return rejected("Invalid title reference", data);
		PlayerTitleState state = data.players().get(targetId);
		if (state == null && !isSuperadmin(actorId, permission)) return rejected("Unknown player", data);
		if (state == null) state = new PlayerTitleState(targetId);
		List<String> titles = new ArrayList<>(state.extraTitles());
		titles.add(titleId);
		return setExtraTitles(actorId, targetId, titles, permission);
	}

	public synchronized MutationResult removeExtraTitle(UUID actorId, UUID targetId, String titleId) {
		return removeExtraTitle(actorId, targetId, titleId, permissionLevel(actorId));
	}

	synchronized MutationResult removeExtraTitle(UUID actorId, UUID targetId, String titleId,
			PermissionLevel permission) {
		if (hasInvalidReference(titleId)) return rejected("Invalid title reference", data);
		PlayerTitleState state = data.players().get(targetId);
		if (state == null) return rejected("Unknown player", data);
		List<String> titles = new ArrayList<>(state.extraTitles());
		titles.removeIf(titleId::equals);
		return setExtraTitles(actorId, targetId, titles, permission);
	}

	public synchronized MutationResult createGroup(UUID actorId, GroupDefinition group) {
		return createGroup(actorId, group, permissionLevel(actorId));
	}

	synchronized MutationResult createGroup(UUID actorId, GroupDefinition group, PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		if (!validGroupDefinition(group)) return rejected("Invalid group definition", data);
		if (data.groups().containsKey(group.id())) return rejected("Group already exists", data);
		if (data.groups().size() >= TitularLimits.MAX_DEFINITIONS) return rejected("Maximum group count reached", data);
		Map<String, GroupDefinition> groups = new LinkedHashMap<>(data.groups());
		groups.put(group.id(), group);
		return replaceData(new TitularData(data.titles(), groups, data.players(), data.settings(), data.revision() + 1));
	}

	public synchronized MutationResult updateGroup(UUID actorId, GroupDefinition group) {
		return updateGroup(actorId, group, permissionLevel(actorId));
	}

	synchronized MutationResult updateGroup(UUID actorId, GroupDefinition group, PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		if (!validGroupDefinition(group)) return rejected("Invalid group definition", data);
		Map<String, GroupDefinition> groups = new LinkedHashMap<>(data.groups());
		if (!groups.containsKey(group.id())) return rejected("Unknown group", data);
		groups.put(group.id(), group);
		if (hasGroupCycle(group.id(), groups)) return rejected("Group inheritance cycle", data);
		return replaceData(new TitularData(data.titles(), groups, data.players(), data.settings(), data.revision() + 1));
	}

	public synchronized MutationResult deleteGroup(UUID actorId, String groupId) {
		return deleteGroup(actorId, groupId, permissionLevel(actorId));
	}

	synchronized MutationResult deleteGroup(UUID actorId, String groupId, PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		if (!data.groups().containsKey(groupId)) return rejected("Unknown group", data);
		for (GroupDefinition group : data.groups().values()) {
			if (groupId.equals(group.parent())) return rejected("Group is still referenced as a parent", data);
		}
		for (PlayerTitleState state : data.players().values()) {
			if (groupId.equals(state.primaryGroup()) || state.extraGroups().contains(groupId)
					|| state.luckPermsGroups().contains(groupId)) {
				return rejected("Group is still assigned to a player", data);
			}
		}
		Map<String, GroupDefinition> groups = new LinkedHashMap<>(data.groups());
		groups.remove(groupId);
		return replaceData(new TitularData(data.titles(), groups, data.players(), data.settings(), data.revision() + 1));
	}

	public synchronized MutationResult createTitle(UUID actorId, TitleDefinition title) {
		return createTitle(actorId, title, permissionLevel(actorId));
	}

	synchronized MutationResult createTitle(UUID actorId, TitleDefinition title, PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		if (title == null || title.id() == null || title.id().isBlank()) return rejected("Invalid title definition", data);
		if (data.titles().containsKey(title.id())) return rejected("Title already exists", data);
		if (!validTitleText(title)) return rejected("Title text is too large or unsupported", data);
		if (data.titles().size() >= TitularLimits.MAX_DEFINITIONS) return rejected("Maximum title count reached", data);
		Map<String, TitleDefinition> titles = new LinkedHashMap<>(data.titles());
		titles.put(title.id(), title);
		return replaceData(new TitularData(titles, data.groups(), data.players(), data.settings(), data.revision() + 1));
	}

	public synchronized MutationResult updateTitle(UUID actorId, TitleDefinition title) {
		return updateTitle(actorId, title, permissionLevel(actorId));
	}

	synchronized MutationResult updateTitle(UUID actorId, TitleDefinition title, PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		if (title == null || title.id() == null || title.id().isBlank() || !data.titles().containsKey(title.id())) {
			return rejected("Unknown title", data);
		}
		if (!validTitleText(title)) return rejected("Title text is too large or unsupported", data);
		Map<String, TitleDefinition> titles = new LinkedHashMap<>(data.titles());
		titles.put(title.id(), title);
		return replaceData(new TitularData(titles, data.groups(), data.players(), data.settings(), data.revision() + 1));
	}

	public synchronized MutationResult deleteTitle(UUID actorId, String titleId) {
		return deleteTitle(actorId, titleId, permissionLevel(actorId));
	}

	synchronized MutationResult deleteTitle(UUID actorId, String titleId, PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		if (!data.titles().containsKey(titleId)) return rejected("Unknown title", data);
		Map<String, TitleDefinition> titles = new LinkedHashMap<>(data.titles());
		titles.remove(titleId);
		Map<String, GroupDefinition> groups = new LinkedHashMap<>();
		for (GroupDefinition group : data.groups().values()) {
			groups.put(group.id(), new GroupDefinition(group.id(), group.parent(), without(group.titleIds(), titleId)));
		}
		Map<UUID, PlayerTitleState> players = new LinkedHashMap<>();
		for (PlayerTitleState state : data.players().values()) {
			String active = titleId.equals(state.activeTitle()) ? null : state.activeTitle();
			players.put(state.playerId(), new PlayerTitleState(state.playerId(), state.primaryGroup(), state.extraGroups(),
					without(state.extraTitles(), titleId), active, state.luckPermsGroups()));
		}
		return replaceData(new TitularData(titles, groups, players, data.settings(), data.revision() + 1));
	}

	public synchronized MutationResult setDisplayMode(UUID actorId, DisplayMode displayMode) {
		return setDisplayMode(actorId, displayMode, permissionLevel(actorId));
	}

	/** Atomically replaces an offline or online player's complete editable state. */
	public synchronized MutationResult setPlayerFields(UUID actorId, UUID targetId, String primaryGroup,
			List<String> extraGroups, List<String> extraTitles, String activeTitle) {
		if (!isSuperadmin(actorId, permissionLevel(actorId))) {
			return rejected("Superadmin permission required", data);
		}
		PlayerTitleState current = data.players().get(targetId);
		if (current == null && data.players().size() >= TitularLimits.MAX_DEFINITIONS) return rejected("Maximum player count reached", data);
		if (current == null) current = new PlayerTitleState(targetId);
		if (primaryGroup != null && primaryGroup.length() > TitularLimits.MAX_STRING_LENGTH) {
			return rejected("Primary group id is too long", data);
		}
		if (primaryGroup != null && !primaryGroup.isBlank() && !data.groups().containsKey(primaryGroup)) {
			return rejected("Unknown group", data);
		}
		if (hasInvalidReference(extraGroups) || hasInvalidReference(extraTitles)) {
			return rejected("Invalid player fields", data);
		}
		if ((extraGroups != null && extraGroups.size() > TitularLimits.MAX_REFERENCE_LIST)
				|| (extraTitles != null && extraTitles.size() > TitularLimits.MAX_REFERENCE_LIST)) {
			return rejected("Too many player references", data);
		}
		List<String> groups = normalizeReferences(extraGroups);
		for (String groupId : groups) if (!data.groups().containsKey(groupId)) return rejected("Unknown group", data);
		List<String> titles = normalizeReferences(extraTitles);
		for (String titleId : titles) if (!data.titles().containsKey(titleId)) return rejected("Unknown title", data);
		String normalizedActive = blankToNull(activeTitle);
		PlayerTitleState replacement = new PlayerTitleState(targetId, blankToNull(primaryGroup), groups, titles,
				normalizedActive, current.luckPermsGroups());
		if (normalizedActive != null && !new TitlePoolResolver().resolve(replacement, data).contains(normalizedActive)) {
			return rejected("Title is not available to this player", data);
		}
		return replacePlayerState(replacement);
	}

	/**
	 * Mirrors LuckPerms inherited groups without touching any manually managed
	 * player fields. Unknown groups are intentionally ignored because only
	 * groups defined in Titular can contribute titles.
	 */
	public synchronized MutationResult syncLuckPermsGroups(UUID playerId, Collection<String> inheritedGroups) {
		if (playerId == null) return rejected("Unknown player", data);
		PlayerTitleState current = data.players().get(playerId);
		List<String> mirrored = normalizeKnownGroups(inheritedGroups);
		// LP can recalculate data for an offline UUID that has not yet opened a
		// Titular screen. Create only when there is a meaningful mirror to save.
		if (current == null) {
			if (mirrored.isEmpty()) return MutationResult.accepted(data);
			if (data.players().size() >= TitularLimits.MAX_DEFINITIONS) return rejected("Maximum player count reached", data);
			current = new PlayerTitleState(playerId);
		}
		if (current.luckPermsGroups().equals(mirrored)) {
			return MutationResult.accepted(data);
		}
		PlayerTitleState replacement = new PlayerTitleState(current.playerId(), current.primaryGroup(),
				current.extraGroups(), current.extraTitles(), current.activeTitle(), mirrored);
		return replacePlayerState(replacement);
	}

	/** Convenience boolean for integrations that only need to know if a write occurred. */
	public synchronized boolean updateLuckPermsGroups(UUID playerId, Collection<String> inheritedGroups) {
		TitularData before = data;
		MutationResult result = syncLuckPermsGroups(playerId, inheritedGroups);
		return result.success() && result.data().revision() != before.revision();
	}

	/** Reloads the backing storage and returns a new authoritative snapshot. */
	public synchronized MutationResult reload(UUID actorId) {
		if (!isSuperadmin(actorId, permissionLevel(actorId))) {
			return rejected("Superadmin permission required", data);
		}
		TitularData loaded = storage.load();
		data = loaded;
		return MutationResult.accepted(loaded);
	}

	synchronized MutationResult setDisplayMode(UUID actorId, DisplayMode displayMode, PermissionLevel permission) {
		if (!isSuperadmin(actorId, permission)) return rejected("Superadmin permission required", data);
		if (displayMode == null) return rejected("Display mode is required", data);
		return replaceData(new TitularData(data.titles(), data.groups(), data.players(),
				new TitularSettings(displayMode), data.revision() + 1));
	}

	private MutationResult replacePlayer(PlayerTitleState current, String activeTitle) {
		PlayerTitleState replacement = new PlayerTitleState(current.playerId(), current.primaryGroup(),
				current.extraGroups(), current.extraTitles(), activeTitle, current.luckPermsGroups());
		return replacePlayerState(replacement);
	}

	private MutationResult replacePlayerState(PlayerTitleState replacement) {
		PlayerTitleState existing = data.players().get(replacement.playerId());
		if (replacement.equals(existing)) return MutationResult.accepted(data);
		Map<UUID, PlayerTitleState> players = new LinkedHashMap<>(data.players());
		players.put(replacement.playerId(), replacement);
		return replaceData(new TitularData(data.titles(), data.groups(), players, data.settings(), data.revision() + 1));
	}

	private MutationResult replaceData(TitularData candidate) {
		if (candidate.equals(data)) return MutationResult.accepted(data);
		storage.save(candidate);
		data = candidate;
		return MutationResult.accepted(candidate);
	}

	private MutationResult rejected(String message, TitularData current) {
		return MutationResult.rejected(message, current);
	}

	private static boolean isSuperadmin(UUID actorId, PermissionLevel permission) {
		return actorId != null && permission != null && permission.includes(PermissionLevel.SUPERADMIN);
	}

	private static boolean allowsTarget(UUID actorId, UUID targetId, PermissionLevel permission, PermissionLevel required) {
		if (permission == null || actorId == null || targetId == null) return false;
		return permission.includes(PermissionLevel.SUPERADMIN)
				|| (permission.includes(required) && actorId.equals(targetId));
	}

	private boolean validGroupDefinition(GroupDefinition group) {
		if (group == null || group.id() == null || group.id().isBlank()) return false;
		if (group.id().length() > TitularLimits.MAX_STRING_LENGTH) return false;
		if (group.titleIds().size() > TitularLimits.MAX_REFERENCE_LIST) return false;
		if (group.parent() != null && (group.parent().isBlank() || !data.groups().containsKey(group.parent())
				|| group.id().equals(group.parent()))) return false;
		if (group.parent() != null && group.parent().length() > TitularLimits.MAX_STRING_LENGTH) return false;
		for (String titleId : group.titleIds()) {
			if (titleId == null || titleId.length() > TitularLimits.MAX_STRING_LENGTH || !data.titles().containsKey(titleId)) return false;
		}
		return true;
	}

	private static boolean hasGroupCycle(String start, Map<String, GroupDefinition> groups) {
		Set<String> visited = new LinkedHashSet<>();
		String id = start;
		while (id != null) {
			if (!visited.add(id)) return true;
			GroupDefinition group = groups.get(id);
			if (group == null) return false;
			id = group.parent();
		}
		return false;
	}

	private static List<String> normalizeReferences(Collection<String> values) {
		if (values == null) return List.of();
		if (values.size() > TitularLimits.MAX_REFERENCE_LIST) throw new IllegalArgumentException("Too many references");
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) if (value != null && !value.isBlank()) result.add(value);
		return List.copyOf(result);
	}

	private List<String> normalizeKnownGroups(Collection<String> values) {
		if (values == null) return List.of();
		if (values.size() > TitularLimits.MAX_REFERENCE_LIST) {
			throw new IllegalArgumentException("Too many LuckPerms groups");
		}
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String value : values) {
			if (value != null && !value.isBlank() && data.groups().containsKey(value)) result.add(value);
		}
		return List.copyOf(result);
	}

	private static boolean hasInvalidReference(Collection<String> values) {
		if (values == null) return false;
		for (String value : values) if (hasInvalidReference(value)) return true;
		return false;
	}

	private static boolean hasInvalidReference(String value) {
		return value == null || value.isBlank();
	}

	private static List<String> without(Collection<String> values, String removed) {
		List<String> result = new ArrayList<>();
		for (String value : values) if (!Objects.equals(value, removed)) result.add(value);
		return List.copyOf(result);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static boolean validTitleText(TitleDefinition title) {
		if (title.id().length() > TitularLimits.MAX_STRING_LENGTH) return false;
		try {
			return titular.modid.codec.TextJsonCodec.encode(title.prefix()).length() <= TitularLimits.MAX_TEXT_JSON_LENGTH
				&& titular.modid.codec.TextJsonCodec.encode(title.suffix()).length() <= TitularLimits.MAX_TEXT_JSON_LENGTH;
		} catch (RuntimeException exception) {
			return false;
		}
	}
}
