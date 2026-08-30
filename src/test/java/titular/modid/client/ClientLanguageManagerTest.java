package titular.modid.client;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ClientLanguageManagerTest {
    @Test
    void defaultsToEnglishWhenConfigIsAbsent() throws Exception {
        Path config = Files.createTempDirectory("titular-locale").resolve("client.json");
        ClientLanguageManager manager = new ClientLanguageManager(config);

        assertEquals(ClientLocale.EN_US, manager.locale());
        assertEquals("Titular", manager.text("titular.screen.title"));
    }

    @Test
    void persistsChineseAndLoadsItAgain() throws Exception {
        Path config = Files.createTempDirectory("titular-locale").resolve("client.json");
        ClientLanguageManager manager = new ClientLanguageManager(config);
        manager.setLocale(ClientLocale.ZH_CN);

        ClientLanguageManager reloaded = new ClientLanguageManager(config);
        assertEquals(ClientLocale.ZH_CN, reloaded.locale());
        assertEquals("称号", reloaded.text("titular.screen.tab.titles"));
    }

    @Test
    void unknownOrMalformedConfigFallsBackToEnglish() throws Exception {
        Path config = Files.createTempDirectory("titular-locale").resolve("client.json");
        Files.writeString(config, "{\"locale\":\"fr_fr\"}");
        assertEquals(ClientLocale.EN_US, new ClientLanguageManager(config).locale());
        Files.writeString(config, "not-json");
        assertEquals(ClientLocale.EN_US, new ClientLanguageManager(config).locale());
    }

    @Test
    void formatsArgumentsAndUsesEnglishForMissingKeys() throws Exception {
        Path config = Files.createTempDirectory("titular-locale").resolve("client.json");
        ClientLanguageManager manager = new ClientLanguageManager(config);
        assertEquals("Active: wizard", manager.text("titular.screen.active", "wizard"));
        assertEquals("missing.key", manager.text("missing.key"));

        Text translated = ClientText.text(manager, "titular.screen.active", "wizard");
        assertEquals("Active: wizard", translated.getString());
    }

}
