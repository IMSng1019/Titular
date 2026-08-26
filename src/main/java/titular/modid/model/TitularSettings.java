package titular.modid.model;

public record TitularSettings(DisplayMode displayMode) {
	public TitularSettings {
		displayMode = displayMode == null ? DisplayMode.PREFIX : displayMode;
	}

	public TitularSettings() {
		this(DisplayMode.PREFIX);
	}
}
