package com.fishlog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class DonationStore {

    public static final DonationStore INSTANCE = new DonationStore();

    private Path receiptsDir;
    private final List<DonationData> donations = new ArrayList<>();

    private DonationStore() {}

    public void init(Path runDir) {
        receiptsDir = runDir.resolve("fishlog_receipts");
        try {
            Files.createDirectories(receiptsDir);
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Impossible de créer fishlog_receipts/", e);
        }
        reload();
    }

    public void reload() {
        donations.clear();
        if (receiptsDir == null || !Files.isDirectory(receiptsDir)) return;
        try (Stream<Path> files = Files.list(receiptsDir)) {
            files.filter(p -> p.toString().endsWith(".txt")).forEach(p -> {
                try {
                    String content = Files.readString(p, StandardCharsets.UTF_8).trim();
                    DonationData data = DonationCrypto.decrypt(content);
                    if (data != null) donations.add(data);
                } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Erreur lecture fishlog_receipts/", e);
        }
        donations.sort((a, b) -> Double.compare(b.amount, a.amount));
    }

    public void saveReceipt(String encrypted) {
        if (receiptsDir == null || encrypted == null) return;
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path file = receiptsDir.resolve("receipt_" + ts + ".txt");
        try {
            Files.writeString(file, encrypted, StandardCharsets.UTF_8);
            // Recharger immédiatement pour l'affichage
            reload();
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Impossible de sauvegarder le reçu", e);
        }
    }

    public List<DonationData> getDonations() {
        return Collections.unmodifiableList(donations);
    }
}
