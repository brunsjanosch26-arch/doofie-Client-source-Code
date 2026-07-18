package de.doofie;

import de.doofie.screen.DoofieModScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Doofie Client fuer MC 26.2 (Mojang-Mappings): Kampf-FX + ESC-Menue. */
public class DoofieClientMod implements ClientModInitializer {

    public static final String MOD_ID = "doofie_client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Doofie Client] geladen (26.2 Edition)!");

        // Kampf-FX: Hitmarker + Kill-Flash
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && entity instanceof LivingEntity living) {
                DoofieCombatFx.onAttack(living);
            }
            return InteractionResult.PASS;
        });

        // B-Taste: getragenen Rucksack oeffnen (Server-Befehl /rucksackoeffnen)
        var backpackKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            new net.minecraft.client.KeyMapping(
                "key.doofie_client.rucksack",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_B,
                net.minecraft.client.KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DoofieCombatFx.tick();
            while (backpackKey.consumeClick()) {
                if (client.player != null) {
                    client.player.connection.sendCommand("rucksackoeffnen");
                }
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("doofie_client", "combat_fx"),
            (ctx, delta) -> DoofieCombatFx.render(ctx));

        // ESC-Menü: "Give Feedback" → "Doofie Client"
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PauseScreen)) return;

            List<Button> toReplace = new ArrayList<>();
            for (var widget : Screens.getWidgets(screen)) {
                if (widget instanceof Button btn) {
                    String msg = btn.getMessage().getString();
                    if (msg.contains("Feedback") || msg.contains("feedback")) {
                        toReplace.add(btn);
                    }
                }
            }

            for (Button old : toReplace) {
                Button newBtn = Button.builder(
                    Component.literal("✦ Doofie Client"),
                    b -> client.setScreenAndShow(new DoofieModScreen(screen))
                )
                .bounds(old.getX(), old.getY(), old.getWidth(), old.getHeight())
                .build();

                Screens.getWidgets(screen).remove(old);
                Screens.getWidgets(screen).add(newBtn);
            }
        });
    }
}
