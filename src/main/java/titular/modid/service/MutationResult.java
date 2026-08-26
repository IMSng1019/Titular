package titular.modid.service;

import titular.modid.model.TitularData;

import java.util.Objects;

/** Result of a validated service mutation. */
public record MutationResult(boolean success, String message, TitularData data) {
	public MutationResult {
		message = Objects.requireNonNullElse(message, "");
	}

	public boolean isSuccess() {
		return success;
	}

	public static MutationResult accepted(TitularData data) {
		return new MutationResult(true, "", data);
	}

	public static MutationResult rejected(String message, TitularData data) {
		return new MutationResult(false, message, data);
	}
}
