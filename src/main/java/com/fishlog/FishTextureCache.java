package com.fishlog;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FishTextureCache {

    private static Map<String, Integer> modelToCmd;
    private static final Map<String, ItemStack> cache     = new HashMap<>();
    // headCache survit à invalidate() — textures récupérées automatiquement à la pêche
    private static final Map<String, ItemStack> headCache = new HashMap<>();

    // Poissons dont l'icône est une tête HDB (pas de CMD) — texture capturée à la pêche
    static final Set<String> HDB_FISH = new HashSet<>(Arrays.asList(
        "squid", "crabe", "crabe_ermite", "crane_d_or", "miel_de_dieux", "nautile"
    ));

    // "Truite arc-en-ciel" (normale) → CMD selon rareté
    private static final Map<String, Integer> TRUITE_BY_RARITY = new HashMap<>();
    static {
        TRUITE_BY_RARITY.put("COMMUN",     10088);
        TRUITE_BY_RARITY.put("RARE",       10065);
        TRUITE_BY_RARITY.put("ÉPIQUE",     10115);
        TRUITE_BY_RARITY.put("LÉGENDAIRE", 10086);
    }

    // Nom CSV normalisé → item vanilla (pas de CMD)
    private static final Map<String, Item> VANILLA_ITEMS = new HashMap<>();
    static {
        VANILLA_ITEMS.put("encre_de_poulpe",    Items.INK_SAC);
        VANILLA_ITEMS.put("enclume",            Items.ANVIL);
        VANILLA_ITEMS.put("cornichon_de_mer",   Items.SEA_PICKLE);
        VANILLA_ITEMS.put("trident",            Items.TRIDENT);
        // "écaille d'amétiste" / "écaille d'améthyste" → les deux formes
        VANILLA_ITEMS.put("ecaille_d_ametiste",  Items.AMETHYST_SHARD);
        VANILLA_ITEMS.put("ecaille_d_amethyste", Items.AMETHYST_SHARD);
        // Têtes joueur (HDB) — affichées en Steve tant que la vraie texture n'est pas renseignée
        // Remplacer null par makeHeadStack("BASE64") une fois les valeurs récupérées en jeu
        // squid          → /hdb give 123263
        // crabe          → /hdb give 53086
        // crabe_ermite   → /hdb give 54474  (nom ingame : Michel l'hermite)
        // crane_d_or     → /hdb give 54790
        // miel_de_dieux  → /hdb give 68898
        VANILLA_ITEMS.put("squid",              Items.PLAYER_HEAD);
        VANILLA_ITEMS.put("crabe",              Items.PLAYER_HEAD);
        VANILLA_ITEMS.put("crabe_ermite",       Items.PLAYER_HEAD);
        VANILLA_ITEMS.put("crane_d_or",         Items.PLAYER_HEAD);
        VANILLA_ITEMS.put("miel_de_dieux",      Items.PLAYER_HEAD);
        VANILLA_ITEMS.put("nautile",            Items.PLAYER_HEAD);
        // Poisson métamorphe = tête Steve volontairement générique
        VANILLA_ITEMS.put("poisson_metamorphe", Items.PLAYER_HEAD);
    }

    // Nom CSV normalisé → nom de modèle quand la normalisation ne suffit pas
    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        ALIASES.put("bob_l_eponge_carree",           "bob_eponge");
        ALIASES.put("patrick_l_etoile_de_mer",        "patrick");
        ALIASES.put("le_poisson_steve",               "steve_le_poisson_orange");
        ALIASES.put("meduse_qui_brille",              "meduse_de_la_croisee");
        ALIASES.put("roi_maquereau",                  "roi_macquereau");
        ALIASES.put("barque_cassee",                  "barque");
        ALIASES.put("vieille_canne_a_peche",          "canne_a_peche");
        ALIASES.put("oo_poisson_de_donjon_tier_1_oo", "poisson_de_donjon_tier_1");
        // cuillère → modèle orthographié différemment
        ALIASES.put("cuillere_rouillee",              "cuilliere_rouillee");
        // Lysaé d'Obsydienne → modèle sans "d_" et avec "obsidienne"
        ALIASES.put("lysae_d_obsydienne",             "lysae_obsidienne");
        ALIASES.put("lysae_d_obsidienne",             "lysae_obsidienne");
    }

    public static boolean isHdbFish(String normalizedName) {
        return HDB_FISH.contains(normalizedName);
    }

    public static void cacheHeadStack(String fishName, String rarity, ItemStack head) {
        if (head == null || head.isEmpty()) return;
        String key = fishName + "|" + rarity;
        headCache.put(key, head.copy());
        cache.remove(key); // force re-lecture depuis headCache au prochain appel
    }

    public static ItemStack getItemStack(String fishName, String rarity) {
        String key = fishName + "|" + rarity;
        // La tête avec vraie texture prime sur tout
        if (headCache.containsKey(key)) return headCache.get(key);
        if (cache.containsKey(key)) return cache.get(key);

        if (modelToCmd == null) init();

        ItemStack result = build(fishName, rarity);
        cache.put(key, result);
        return result;
    }

    private static ItemStack build(String fishName, String rarity) {
        if (modelToCmd == null || modelToCmd.isEmpty()) return null;

        String norm = normalize(fishName);

        // Items vanilla (pas de CMD)
        Item vanilla = VANILLA_ITEMS.get(norm);
        if (vanilla != null) return new ItemStack(vanilla);

        if (norm.equals("truite_arc_en_ciel_albinos")) return makeStack(10043);

        if (norm.equals("truite_arc_en_ciel")) {
            Integer cmd = TRUITE_BY_RARITY.get(rarity);
            return cmd != null ? makeStack(cmd) : null;
        }

        String modelName = ALIASES.getOrDefault(norm, norm);
        Integer cmd = modelToCmd.get(modelName);
        return cmd != null ? makeStack(cmd) : null;
    }

    private static ItemStack makeStack(int cmd) {
        ItemStack stack = new ItemStack(Items.COOKED_COD);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(cmd));
        return stack;
    }

    private static void init() {
        try {
            var rm = MinecraftClient.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(Identifier.of("minecraft", "models/item/cooked_cod.json"));
            if (res.isEmpty()) return;

            try (InputStream is = res.get().getInputStream()) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray overrides = json.getAsJsonArray("overrides");
                if (overrides == null) return;

                modelToCmd = new HashMap<>();
                for (JsonElement el : overrides) {
                    JsonObject entry = el.getAsJsonObject();
                    JsonObject predicate = entry.getAsJsonObject("predicate");
                    if (predicate == null) continue;
                    JsonElement cmdEl = predicate.get("custom_model_data");
                    if (cmdEl == null) continue;
                    int cmd = cmdEl.getAsInt();
                    String model = entry.get("model").getAsString();
                    // "fish:ia_auto_gen/anchois" → "anchois"
                    String name = model.substring(model.lastIndexOf('/') + 1);
                    modelToCmd.put(name, cmd);
                }
            }
        } catch (Exception ignored) {
            // Pas de pack serveur chargé → pas d'icônes
        }
    }

    public static void invalidate() {
        modelToCmd = null;
        cache.clear();
        // headCache intentionnellement conservé : les textures capturées restent valides
    }

    static String normalize(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        s = s.replace("é","e").replace("è","e").replace("ê","e").replace("ë","e");
        s = s.replace("à","a").replace("â","a").replace("á","a");
        s = s.replace("ô","o").replace("ö","o").replace("ó","o");
        s = s.replace("î","i").replace("ï","i");
        s = s.replace("ù","u").replace("û","u").replace("ü","u");
        s = s.replace("ç","c");
        s = s.replace("œ","oe").replace("æ","ae");
        s = s.replace("å","a").replace("ø","o");
        return s.replaceAll("[^a-z0-9]","_").replaceAll("_+","_").replaceAll("^_|_$","");
    }
}
