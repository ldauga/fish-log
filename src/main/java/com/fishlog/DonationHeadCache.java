package com.fishlog;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class DonationHeadCache {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    // null dans la map = fetch échoué (ne pas réessayer)
    private static final Map<String, Optional<ItemStack>> cache = new ConcurrentHashMap<>();
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    public static void prefetch(List<String> names) {
        for (String name : names) {
            if (!cache.containsKey(name) && pending.add(name)) {
                CompletableFuture.runAsync(() -> fetchHead(name))
                    .exceptionally(e -> { pending.remove(name); return null; });
            }
        }
    }

    /** Retourne l'ItemStack si chargé, empty() si échec, ou absent si en cours de chargement. */
    public static Optional<ItemStack> get(String name) {
        return cache.getOrDefault(name, null);
    }

    public static void invalidate() {
        cache.clear();
        pending.clear();
    }

    private static void fetchHead(String name) {
        try {
            // 1. UUID via API Mojang
            String uuidJson = httpGet("https://api.mojang.com/users/profiles/minecraft/" + name);
            String uuidRaw = extractJsonString(uuidJson, "id");
            if (uuidRaw == null || uuidRaw.isEmpty()) {
                cache.put(name, Optional.empty());
                return;
            }
            // Formater en UUID avec tirets
            String uuidStr = uuidRaw.replaceFirst(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                "$1-$2-$3-$4-$5"
            );
            UUID uuid = UUID.fromString(uuidStr);

            // 2. Profil avec textures
            String profileJson = httpGet(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr
            );
            String base64 = extractJsonString(profileJson, "value");
            if (base64 == null || base64.isEmpty()) {
                cache.put(name, Optional.empty());
                return;
            }

            // 3. Créer ItemStack PLAYER_HEAD avec le profil
            GameProfile profile = new GameProfile(uuid, name);
            profile.getProperties().put("textures", new Property("textures", base64));
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
            cache.put(name, Optional.of(head));

            FishLogMod.LOGGER.info("[FishLog] Tête chargée pour {}", name);
        } catch (Exception e) {
            FishLogMod.LOGGER.warn("[FishLog] Erreur chargement tête pour {}: {}", name, e.getMessage());
            cache.put(name, Optional.empty());
        } finally {
            pending.remove(name);
        }
    }

    private static String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Extraction minimaliste d'un champ string dans un JSON plat, sans dépendance externe. */
    private static String extractJsonString(String json, String field) {
        if (json == null || json.isEmpty()) return null;
        String key = "\"" + field + "\"";
        int ki = json.indexOf(key);
        if (ki < 0) return null;
        int colon = json.indexOf(':', ki + key.length());
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        int close = open + 1;
        while (close < json.length()) {
            char c = json.charAt(close);
            if (c == '\\') { close += 2; continue; }
            if (c == '"') break;
            close++;
        }
        if (close >= json.length()) return null;
        return json.substring(open + 1, close);
    }
}
