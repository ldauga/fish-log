package com.fishlog;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FishDataStore {

    public static final FishDataStore INSTANCE = new FishDataStore();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<FishRecord> records = new ArrayList<>();
    private Path csvPath;

    private FishDataStore() {}

    public synchronized void init(Path path) {
        this.csvPath = path;
        reload();
    }

    public synchronized void reload() {
        records.clear();
        if (csvPath == null || !Files.exists(csvPath)) return;
        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; } // skip header
                String[] p = line.split(",", 5);
                if (p.length < 5) continue;
                try {
                    LocalDateTime ts = LocalDateTime.parse(p[0], FMT);
                    double size = Double.parseDouble(p[4].replace(',', '.'));
                    records.add(new FishRecord(ts, p[1], p[2], p[3], size));
                } catch (Exception e) {
                    FishLogMod.LOGGER.warn("[FishLog] Ligne CSV ignorée : {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Lecture CSV : {}", e.getMessage());
        }
    }

    public synchronized void add(FishRecord r) {
        records.add(r);
    }

    public synchronized List<FishRecord> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }
}
