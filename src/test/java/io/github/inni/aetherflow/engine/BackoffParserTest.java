package io.github.inni.aetherflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.inni.aetherflow.execution.BackoffParser;
import org.junit.jupiter.api.Test;

class BackoffParserTest {

	@Test
	void parsesSeconds() {
		assertEquals(5, BackoffParser.parseSeconds("5s"));
		assertEquals(30, BackoffParser.parseSeconds("30s"));
		assertEquals(1, BackoffParser.parseSeconds("1s"));
	}

	@Test
	void parsesMinutes() {
		assertEquals(120, BackoffParser.parseSeconds("2m"));
		assertEquals(60, BackoffParser.parseSeconds("1m"));
	}

	@Test
	void parsesHours() {
		assertEquals(3600, BackoffParser.parseSeconds("1h"));
		assertEquals(7200, BackoffParser.parseSeconds("2h"));
	}

	@Test
	void emptyStringReturnsZero() {
		assertEquals(0, BackoffParser.parseSeconds(""));
		assertEquals(0, BackoffParser.parseSeconds(null));
		assertEquals(0, BackoffParser.parseSeconds("  "));
	}

	@Test
	void invalidFormatThrows() {
		assertThrows(IllegalArgumentException.class, () -> BackoffParser.parseSeconds("5x"));
		assertThrows(IllegalArgumentException.class, () -> BackoffParser.parseSeconds("abc"));
		assertThrows(IllegalArgumentException.class, () -> BackoffParser.parseSeconds("10"));
	}
}
