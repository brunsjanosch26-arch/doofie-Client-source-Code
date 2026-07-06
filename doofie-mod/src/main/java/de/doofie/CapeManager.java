package de.doofie;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CapeManager {
    private static final String API_BASE = "https://doofie-client-backend-production.up.railway.app";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    // Optional.empty() = confirmed no cape, Optional.of(id) = has cape, absent = not yet fetched
    private static final Map<UUID, Optional<Identifier>> cache = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> fetchTime = new ConcurrentHashMap<>();
    private static final long CACHE_MS = 60_000L;

    public static Identifier getCapeForPlayer(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = fetchTime.get(uuid);
        if (last == null || (now - last) > CACHE_MS) {
            fetchTime.put(uuid, now);
            Thread.ofVirtual().start(() -> fetchAndCache(uuid));
        }
        return cache.getOrDefault(uuid, Optional.empty()).orElse(null);
    }

    private static void fetchAndCache(UUID uuid) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/api/v1/cosmetics/cape/user/" + uuid))
                .GET()
                .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                cache.put(uuid, Optional.empty());
                return;
            }

            JsonArray arr = JsonParser.parseString(resp.body()).getAsJsonArray();
            if (arr.isEmpty()) {
                cache.put(uuid, Optional.empty());
                return;
            }

            String hash = arr.get(0).getAsJsonObject().get("_id").getAsString();
            String texUrl = API_BASE + "/uploads/capes/" + hash + ".png";

            HttpRequest texReq = HttpRequest.newBuilder()
                .uri(URI.create(texUrl))
                .GET()
                .build();
            HttpResponse<InputStream> texResp = HTTP.send(texReq, HttpResponse.BodyHandlers.ofInputStream());
            if (texResp.statusCode() != 200) {
                cache.put(uuid, Optional.empty());
                return;
            }

            NativeImage img = NativeImage.read(texResp.body());
            Identifier id = Identifier.of("doofie_client", "capes/" + hash);
            final String hashFinal = hash;

            MinecraftClient.getInstance().execute(() -> {
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "doofie_cape_" + hashFinal, img));
                cache.put(uuid, Optional.of(id));
            });
        } catch (Exception e) {
            DoofieClientMod.LOGGER.warn("[CapeManager] Failed for {}: {}", uuid, e.getMessage());
            cache.put(uuid, Optional.empty());
        }
    }
}
