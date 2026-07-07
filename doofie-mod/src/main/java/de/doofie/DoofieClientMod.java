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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
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

    /** Zeitpunkt des Weltbeitritts fuer das gestaffelte HUD-Intro. */
    private static long joinTime = 0;

    private static boolean hudVisible(int slot) {
        // Jedes HUD-Element erscheint 120ms nach dem vorherigen
        return System.currentTimeMillis() - joinTime > 400 + slot * 120L;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Doofie Client] geladen!");

        // HUD-Intro: Beitrittszeit merken
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joinTime = System.currentTimeMillis());

        // Kampf-FX: Hitmarker + Kill-Flash
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && entity instanceof LivingEntity living) {
                DoofieCombatFx.onAttack(living);
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> DoofieCombatFx.tick());

        // Alle HUD-Elemente in einem Callback registrieren (gestaffeltes Intro)
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            // Oben-links: FPS, Speed, Ping, Zeit, RAM, Licht, Reach, Chunk, Entities
            if (hudVisible(0)) FpsHud.render(drawContext);
            if (hudVisible(1)) SpeedHud.render(drawContext);
            if (hudVisible(2)) PingHud.render(drawContext);
            if (hudVisible(3)) TimeHud.render(drawContext);
            if (hudVisible(4)) MemoryHud.render(drawContext);
            if (hudVisible(5)) LightLevelHud.render(drawContext);
            if (hudVisible(6)) ReachHud.render(drawContext);
            if (hudVisible(7)) ChunkHud.render(drawContext);
            if (hudVisible(8)) EntityCountHud.render(drawContext);

            // Oben-rechts: Armor + Status-Effekte
            if (hudVisible(2)) ArmorHud.render(drawContext);
            if (hudVisible(3)) StatusEffectsHud.render(drawContext);

            // Unten-links: Gesundheit + Koordinaten
            if (hudVisible(0)) HealthHud.render(drawContext);
            if (hudVisible(1)) CoordinatesHud.render(drawContext);

            // Unten-Mitte: Gehaltenes Item + Ziel-Info
            if (hudVisible(4)) HeldItemHud.render(drawContext);
            if (hudVisible(5)) TargetHud.render(drawContext);

            // Unten-rechts: Keystrokes + CPS
            if (hudVisible(0)) KeystrokesHud.render(drawContext);

            // Oben-Mitte: Kompass
            if (hudVisible(6)) CompassHud.render(drawContext);

            // Kampf-Effekte immer zuletzt (liegen ueber allem)
            DoofieCombatFx.render(drawContext);
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
