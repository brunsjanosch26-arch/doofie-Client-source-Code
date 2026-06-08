package de.doofie;

import de.doofie.hud.KeystrokesHud;
import de.doofie.screen.DoofieModScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DoofieClientMod implements ClientModInitializer {

    public static final String MOD_ID = "doofie_client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Doofie Client] geladen!");

        // Keystrokes HUD
        HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
            KeystrokesHud.render(drawContext)
        );

        // ESC-Menü: "Give Feedback" → "Doofie Client"
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GameMenuScreen)) return;

            List<ButtonWidget> toReplace = new ArrayList<>();
            for (var widget : Screens.getButtons(screen)) {
                if (widget instanceof ButtonWidget btn) {
                    String msg = btn.getMessage().getString();
                    if (msg.contains("Feedback") || msg.contains("feedback")) {
                        toReplace.add(btn);
                    }
                }
            }

            for (ButtonWidget old : toReplace) {
                ButtonWidget newBtn = ButtonWidget.builder(
                    Text.literal("✦ Doofie Client"),
                    b -> client.setScreen(new DoofieModScreen(screen))
                )
                .dimensions(old.getX(), old.getY(), old.getWidth(), old.getHeight())
                .build();

                Screens.getButtons(screen).remove(old);
                Screens.getButtons(screen).add(newBtn);
            }
        });
    }
}
