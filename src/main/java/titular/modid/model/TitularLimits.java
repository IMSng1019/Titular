package titular.modid.model;

/** Shared limits used by the authoritative service, storage, and wire codecs. */
public final class TitularLimits {
	public static final int MAX_STRING_LENGTH = 256;
	public static final int MAX_REFERENCE_LIST = 512;
	public static final int MAX_DEFINITIONS = 512;
	public static final int MAX_TEXT_JSON_LENGTH = 16 * 1024;

	private TitularLimits() {
	}
}
