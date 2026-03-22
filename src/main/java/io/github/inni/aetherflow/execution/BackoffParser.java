package io.github.inni.aetherflow.execution;

/**
 * Parses human-friendly backoff duration strings into seconds.
 * Supported formats: "5s" (seconds), "2m" (minutes), "1h" (hours).
 * Empty string returns 0 (no backoff).
 */
public final class BackoffParser {

	private BackoffParser() {
	}

	public static int parseSeconds(String backoff) {
		if (backoff == null || backoff.isBlank()) {
			return 0;
		}
		String trimmed = backoff.trim().toLowerCase();
		try {
			if (trimmed.endsWith("h")) {
				return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) * 3600;
			}
			if (trimmed.endsWith("m")) {
				return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) * 60;
			}
			if (trimmed.endsWith("s")) {
				return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1));
			}
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid backoff value: '" + backoff + "'", e);
		}
		throw new IllegalArgumentException("Invalid backoff value: '" + backoff + "'. Use format like '5s', '2m', or '1h'.");
	}
}
