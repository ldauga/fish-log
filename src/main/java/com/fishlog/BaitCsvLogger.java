package com.fishlog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BaitCsvLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path csvPath;

    public BaitCsvLogger(Path csvPath) {
        this.csvPath = csvPath;
        if (!Files.exists(csvPath)) {
            try (BufferedWriter bw = Files.newBufferedWriter(csvPath)) {
                bw.write("timestamp,player,bait");
                bw.newLine();
            } catch (IOException e) {
                FishLogMod.LOGGER.error("[FishLog] Création CSV appâts : {}", e.getMessage());
            }
        }
    }

    public void log(String player, String bait) {
        LocalDateTime now = LocalDateTime.now();
        String line = String.join(",", now.format(FMT), player, bait);
        try (BufferedWriter bw = Files.newBufferedWriter(csvPath,
                StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Écriture CSV appâts : {}", e.getMessage());
            return;
        }
        BaitDataStore.INSTANCE.add(new BaitRecord(now, player, bait));
    }
}
