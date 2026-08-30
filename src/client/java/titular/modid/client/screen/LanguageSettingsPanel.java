package titular.modid.client.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import titular.modid.client.ClientLanguageManager;
import titular.modid.client.ClientLocale;
import titular.modid.client.ClientNetworking;
import titular.modid.client.ClientText;

/** Client-only language selector. No locale value is sent to the server. */
public final class LanguageSettingsPanel {
    public void install(TitularScreen screen, int x, int y) {
        ClientLanguageManager manager = ClientLanguageManager.global();
        ClientLocale current = manager.locale();
        ButtonWidget english = ButtonWidget.builder(ClientText.text("titular.screen.language.en_us"),
                        ignored -> select(screen, ClientLocale.EN_US))
                .dimensions(x, y, 106, 22)
                .build();
        ButtonWidget chinese = ButtonWidget.builder(ClientText.text("titular.screen.language.zh_cn"),
                        ignored -> select(screen, ClientLocale.ZH_CN))
                .dimensions(x + 114, y, 106, 22)
                .build();
        english.active = current != ClientLocale.EN_US;
        chinese.active = current != ClientLocale.ZH_CN;
        screen.addWidget(english);
        screen.addWidget(chinese);
    }

    private static void select(TitularScreen screen, ClientLocale locale) {
        ClientLanguageManager.global().setLocale(locale);
        ClientNetworking.clearError();
        screen.rebuildScreen();
    }
}
