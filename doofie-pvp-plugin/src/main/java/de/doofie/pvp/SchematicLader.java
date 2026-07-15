package de.doofie.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Laedt .dschem-Dateien (gebackene Schematics): gzip-JSON mit
 * {w,h,l,palette:[blockdata-strings],data:base64(varint-palette-indizes)}.
 * Die Indizes liegen in YZX-Reihenfolge (wie Sponge-Schematics).
 */
public class SchematicLader {

    public final int breite, hoehe, laenge;
    private final BlockData[] palette;
    private final int[] indizes;
    private final boolean[] istLuft;

    public SchematicLader(File datei) throws IOException {
        JsonObject obj;
        try (InputStreamReader r = new InputStreamReader(
                new GZIPInputStream(new FileInputStream(datei)), StandardCharsets.UTF_8)) {
            obj = JsonParser.parseReader(r).getAsJsonObject();
        }
        breite = obj.get("w").getAsInt();
        hoehe = obj.get("h").getAsInt();
        laenge = obj.get("l").getAsInt();

        JsonArray pal = obj.getAsJsonArray("palette");
        palette = new BlockData[pal.size()];
        istLuft = new boolean[pal.size()];
        for (int i = 0; i < pal.size(); i++) {
            String eintrag = pal.get(i).getAsString();
            try {
                palette[i] = Bukkit.createBlockData(eintrag);
            } catch (IllegalArgumentException ex) {
                palette[i] = Material.AIR.createBlockData();
            }
            istLuft[i] = palette[i].getMaterial().isAir();
        }

        byte[] roh = Base64.getDecoder().decode(obj.get("data").getAsString());
        indizes = new int[breite * hoehe * laenge];
        int pos = 0, wert = 0, shift = 0, ziel = 0;
        while (pos < roh.length) {
            byte b = roh[pos++];
            wert |= (b & 0x7F) << shift;
            shift += 7;
            if ((b & 0x80) == 0) {
                indizes[ziel++] = wert;
                wert = 0;
                shift = 0;
            }
        }
    }

    /** BlockData an (x,y,z) — null, wenn Luft. */
    public BlockData blockAn(int x, int y, int z) {
        int idx = indizes[(y * laenge + z) * breite + x];
        return istLuft[idx] ? null : palette[idx];
    }
}
