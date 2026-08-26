package titular.modid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmokeTest {
	@Test
	void junitIsAvailable() {
		assertEquals("titular", Titular.MOD_ID);
	}
}
