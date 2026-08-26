package titular.modid.model;

public enum PermissionLevel {
	PLAYER,
	ADMIN,
	SUPERADMIN;

	public boolean includes(PermissionLevel required) {
		return required != null && ordinal() >= required.ordinal();
	}
}
