package com.fishlog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DonationStore {

    public static final DonationStore INSTANCE = new DonationStore();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Path receiptsDir;
    private final List<DonationData> donations = new ArrayList<>();

    private DonationStore() {}

    /** Initialise le dossier de reçus (preuve de don) et charge le HOF depuis le JAR. */
    public void init(Path runDir) {
        receiptsDir = runDir.resolve("fishlog_receipts");
        try {
            Files.createDirectories(receiptsDir);
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Impossible de créer fishlog_receipts/", e);
        }
        loadHallOfFame();
    }

    /** Charge la liste du Hall of Fame depuis le JSON bundlé dans le JAR. */
    private void loadHallOfFame() {
        donations.clear();
        try (InputStream is = DonationStore.class.getResourceAsStream("/assets/fishlog/hall_of_fame.json")) {
            if (is == null) return;
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            parseJson(json);
        } catch (Exception e) {
            FishLogMod.LOGGER.error("[FishLog] Erreur lecture hall_of_fame.json", e);
        }
        donations.sort((a, b) -> {
            int c = Double.compare(b.amount, a.amount);
            return c != 0 ? c : a.date.compareTo(b.date); // égalité → premier donateur en premier
        });
    }

    /**
     * Parse minimal d'un tableau JSON de la forme :
     * [{"player":"X","amount":100.0,"date":"2026-06-18"}, ...]
     */
    private void parseJson(String json) {
        // Enlever crochets extérieurs
        int start = json.indexOf('[');
        int end   = json.lastIndexOf(']');
        if (start < 0 || end <= start) return;
        String inner = json.substring(start + 1, end).trim();
        if (inner.isEmpty()) return;

        // Découper les objets {} (pas de nesting, format simple)
        int depth = 0, objStart = -1;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '{') { if (depth == 0) objStart = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    parseEntry(inner.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }
    }

    private void parseEntry(String obj) {
        String player = extractString(obj, "player");
        String amtStr = extractString(obj, "amount");
        String dateStr = extractString(obj, "date");
        if (player == null || amtStr == null || dateStr == null) return;
        try {
            double amount = Double.parseDouble(amtStr);
            LocalDateTime date = LocalDate.parse(dateStr, DATE_FMT).atStartOfDay();
            donations.add(new DonationData(player, amount, date));
        } catch (Exception ignored) {}
    }

    private static String extractString(String obj, String field) {
        String key = "\"" + field + "\"";
        int ki = obj.indexOf(key);
        if (ki < 0) return null;
        int colon = obj.indexOf(':', ki + key.length());
        if (colon < 0) return null;
        // Valeur string (entre guillemets) ou nombre
        int vi = colon + 1;
        while (vi < obj.length() && (obj.charAt(vi) == ' ' || obj.charAt(vi) == '\t' || obj.charAt(vi) == '\n')) vi++;
        if (vi >= obj.length()) return null;
        if (obj.charAt(vi) == '"') {
            int close = obj.indexOf('"', vi + 1);
            if (close < 0) return null;
            return obj.substring(vi + 1, close);
        }
        // Nombre
        int vend = vi;
        while (vend < obj.length() && ",}\n\r ".indexOf(obj.charAt(vend)) < 0) vend++;
        return obj.substring(vi, vend).trim();
    }

    /** Sauvegarde un reçu chiffré (preuve de don). N'affecte pas le HOF. */
    public void saveReceipt(String encrypted) {
        if (receiptsDir == null || encrypted == null) return;
        String ts = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path file = receiptsDir.resolve("receipt_" + ts + ".txt");
        try {
            Files.writeString(file, encrypted, StandardCharsets.UTF_8);
            FishLogMod.LOGGER.info("[FishLog] Reçu sauvegardé : {}", file.getFileName());
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Impossible de sauvegarder le reçu", e);
        }
    }

    public List<DonationData> getDonations() {
        return Collections.unmodifiableList(donations);
    }

    /** Retourne les donateurs triés par date de première donation croissante. */
    public List<DonationData> getDonationsByDate() {
        List<DonationData> sorted = new ArrayList<>(donations);
        sorted.sort((a, b) -> a.date.compareTo(b.date));
        return Collections.unmodifiableList(sorted);
    }
}
