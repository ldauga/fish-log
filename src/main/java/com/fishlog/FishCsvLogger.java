package com.fishlog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FishCsvLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path csvPath;

    public FishCsvLogger(Path csvPath) {
        this.csvPath = csvPath;
        if (!Files.exists(csvPath)) {
            try (BufferedWriter bw = Files.newBufferedWriter(csvPath)) {
                bw.write("timestamp,player,rarity,fish,size_cm");
                bw.newLine();
            } catch (IOException e) {
                FishLogMod.LOGGER.error("[FishLog] Création CSV : {}", e.getMessage());
            }
        }
    }

    public void log(String player, String rarity, String fish, String sizeCm) {
        LocalDateTime now  = LocalDateTime.now();
        String line = String.join(",", now.format(FMT), player, rarity, fish, sizeCm);
        try (BufferedWriter bw = Files.newBufferedWriter(csvPath,
                StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Écriture CSV : {}", e.getMessage());
            return;
        }
        double size = 0;
        try { size = Double.parseDouble(sizeCm.replace(',', '.')); } catch (NumberFormatException ignored) {}
        FishDataStore.INSTANCE.add(new FishRecord(now, player, rarity, fish, size));
    }
}
