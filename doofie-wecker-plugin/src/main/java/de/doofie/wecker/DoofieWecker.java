package de.doofie.wecker;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DOOFIE-WECKER — weckt die schlafende Lobby.
 *
 * Joint ein Spieler den Proxy, waehrend die Lobby offline ist (das
 * exaroton-Plugin entfernt offline Server aus dem Proxy), startet dieses
 * Plugin die Lobby per exaroton-API und schickt den Spieler mit einer
 * freundlichen Nachricht weg — beim naechsten Join ist die Lobby da.
 *
 * Konfiguration: plugins/doofiewecker/token.txt (exaroton-API-Token)
 * und optional lobby-id.txt (exaroton-Server-ID der Lobby).
 */
@Plugin(id = "doofiewecker", name = "DoofieWecker", version = "1.0.0",
        description = "Weckt die Lobby, wenn jemand joint", authors = {"Doofie"})
public class DoofieWecker {

    private static final String LOBBY_SERVERNAME = "lobby";
    private static final String LOBBY_ID_STANDARD = "Bx6yHYDahdOjeAji";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path datenordner;
    private volatile long letzterWeckversuch = 0;

    @Inject
    public DoofieWecker(ProxyServer proxy, Logger logger, @DataDirectory Path datenordner) {
        this.proxy = proxy;
        this.logger = logger;
        this.datenordner = datenordner;
    }

    private String lies(String datei, String standard) {
        try {
            Path p = datenordner.resolve(datei);
            if (Files.exists(p)) return Files.readString(p).trim();
        } catch (IOException ignored) { }
        return standard;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        // Lobby im Proxy registriert = online — alles gut
        if (proxy.getServer(LOBBY_SERVERNAME).isPresent()) return;

        String token = lies("token.txt", "");
        if (token.isEmpty()) {
            logger.warn("Lobby offline, aber kein token.txt — kann nicht wecken!");
            return;
        }

        // Hoechstens alle 30s einen Start ausloesen
        long jetzt = System.currentTimeMillis();
        if (jetzt - letzterWeckversuch > 30_000) {
            letzterWeckversuch = jetzt;
            String lobbyId = lies("lobby-id.txt", LOBBY_ID_STANDARD);
            proxy.getScheduler().buildTask(this, () -> weckeLobby(token, lobbyId)).schedule();
        }

        event.setResult(ResultedEvent.ComponentResult.denied(Component.text()
            .append(Component.text("💤 Die Lobby hat geschlafen — ", NamedTextColor.GOLD))
            .append(Component.text("ich habe sie GEWECKT!\n\n", NamedTextColor.GREEN))
            .append(Component.text("Versuch es in ~1 Minute nochmal.", NamedTextColor.AQUA))
            .build()));
    }

    private void weckeLobby(String token, String lobbyId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.exaroton.com/v1/servers/" + lobbyId + "/start/"))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "doofie-wecker")
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            logger.info("Lobby-Weckversuch: HTTP {}", resp.statusCode());
        } catch (Exception ex) {
            logger.warn("Lobby wecken fehlgeschlagen: {}", ex.getMessage());
        }
    }
}
