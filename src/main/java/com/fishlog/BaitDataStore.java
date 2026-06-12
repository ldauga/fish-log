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

public class BaitDataStore {

    public static final BaitDataStore INSTANCE = new BaitDataStore();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<BaitRecord> records = new ArrayList<>();
    private Path csvPath;

    private BaitDataStore() {}

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
                if (first) { first = false; continue; }
                String[] p = line.split(",", 3);
                if (p.length < 3) continue;
                try {
                    LocalDateTime ts = LocalDateTime.parse(p[0], FMT);
                    records.add(new BaitRecord(ts, p[1], p[2]));
                } catch (Exception e) {
                    FishLogMod.LOGGER.warn("[FishLog] Ligne CSV appât ignorée : {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Lecture CSV appâts : {}", e.getMessage());
        }
    }

    public synchronized void add(BaitRecord r) {
        records.add(r);
    }

    public synchronized List<BaitRecord> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }
}
