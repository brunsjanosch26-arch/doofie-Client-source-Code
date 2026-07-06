package de.doofie;

import de.doofie.hud.ArmorHud;
import de.doofie.hud.ChunkHud;
import de.doofie.hud.CompassHud;
import de.doofie.hud.CoordinatesHud;
import de.doofie.hud.EntityCountHud;
import de.doofie.hud.FpsHud;
import de.doofie.hud.HealthHud;
import de.doofie.hud.HeldItemHud;
import de.doofie.hud.KeystrokesHud;
import de.doofie.hud.LightLevelHud;
import de.doofie.hud.MemoryHud;
import de.doofie.hud.PingHud;
import de.doofie.hud.ReachHud;
import de.doofie.hud.SpeedHud;
import de.doofie.hud.StatusEffectsHud;
import de.doofie.hud.TargetHud;
import de.doofie.hud.TimeHud;
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

        // Alle HUD-Elemente in einem Callback registrieren
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            // Oben-links: FPS, Speed, Ping, Zeit, RAM, Licht, Reach, Chunk, Entities
            FpsHud.render(drawContext);
            SpeedHud.render(drawContext);
            PingHud.render(drawContext);
            TimeHud.render(drawContext);
            MemoryHud.render(drawContext);
            LightLevelHud.render(drawContext);
            ReachHud.render(drawContext);
            ChunkHud.render(drawContext);
            EntityCountHud.render(drawContext);

            // Oben-rechts: Armor + Status-Effekte
            ArmorHud.render(drawContext);
            StatusEffectsHud.render(drawContext);

            // Unten-links: Gesundheit + Koordinaten
            HealthHud.render(drawContext);
            CoordinatesHud.render(drawContext);

            // Unten-Mitte: Gehaltenes Item + Ziel-Info
            HeldItemHud.render(drawContext);
            TargetHud.render(drawContext);

            // Unten-rechts: Keystrokes + CPS
            KeystrokesHud.render(drawContext);

            // Oben-Mitte: Kompass
            CompassHud.render(drawContext);
        });

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
