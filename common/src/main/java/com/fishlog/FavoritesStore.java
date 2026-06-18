package com.fishlog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FavoritesStore {

    public static final FavoritesStore INSTANCE = new FavoritesStore();

    private final Set<String> favorites = new LinkedHashSet<>();
    private Path filePath;

    private FavoritesStore() {}

    public synchronized void init(Path path) {
        this.filePath = path;
        reload();
    }

    public synchronized void reload() {
        favorites.clear();
        if (filePath == null || !Files.exists(filePath)) {
            favorites.add("Trousseau de clés");
            persist();
            return;
        }
        try {
            Files.lines(filePath)
                 .map(String::trim)
                 .filter(l -> !l.isEmpty())
                 .forEach(favorites::add);
        } catch (IOException e) {
            FishLogCommon.LOGGER.error("[FishLog] Lecture favorites.txt : {}", e.getMessage());
        }
    }

    public synchronized void toggle(String fish) {
        if (!favorites.remove(fish)) favorites.add(fish);
        persist();
    }

    public synchronized boolean isFavorite(String fish) {
        return favorites.contains(fish);
    }

    public synchronized Set<String> snapshot() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(favorites));
    }

    private void persist() {
        if (filePath == null) return;
        try (BufferedWriter bw = Files.newBufferedWriter(filePath)) {
            for (String f : favorites) {
                bw.write(f);
                bw.newLine();
            }
        } catch (IOException e) {
            FishLogCommon.LOGGER.error("[FishLog] Écriture favorites.txt : {}", e.getMessage());
        }
    }
}
