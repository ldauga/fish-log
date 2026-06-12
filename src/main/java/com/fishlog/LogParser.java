package com.fishlog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class LogParser {

    // Gère les deux formats :
    // Fabric : [HH:mm:ss] [thread/LEVEL]: ...
    // Forge  : [DDmmmYYYY HH:mm:ss.mmm] [thread/LEVEL] [class/cat]: ...
    private static final Pattern LOG_LINE = Pattern.compile(
        "^\\[(?:[^\\]]*? )?(\\d{2}:\\d{2}:\\d{2})(?:\\.\\d+)?\\] \\[.+?\\].*?(?:\\[System\\] )?\\[CHAT\\] (.+)$"
    );

    static final Pattern FISH_PATTERN = Pattern.compile(
        "^(.+?) a p[êe]ch[ée] un\\(e\\) (\\S+) (.+?) de (\\d+(?:[.,]\\d+)?)cm !$"
    );

    static final Pattern BAIT_PATTERN = Pattern.compile(
        "^(.+?) a attrapé un appât (.+?) !$"
    );

    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern           FILE_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");

    /**
     * Parcourt le dossier logs/, parse chaque .log et .log.gz non encore traité,
     * et ajoute les entrées manquantes dans les CSV (déjà créés avec leur en-tête).
     * Un registre fishlog_parsed.txt garde la liste des fichiers déjà parsés.
     * Une déduplication par clé évite les doublons avec les captures en temps réel.
     * Retourne [nbPoissons, nbAppâts] importés.
     */
    public static int[] parseLogsInto(Path logsDir, Path fishCsvPath, Path baitCsvPath, String playerName) {
        if (!Files.isDirectory(logsDir)) return new int[]{0, 0};

        Path registry = fishCsvPath.getParent().resolve("fishlog_parsed.txt");
        Set<String> alreadyParsed = loadRegistry(registry);
        Set<String> seenFish = loadLines(fishCsvPath);
        Set<String> seenBait = loadLines(baitCsvPath);

        List<String[]> newFish = new ArrayList<>();
        List<String[]> newBait = new ArrayList<>();
        List<String> justParsed = new ArrayList<>();

        List<Path> logFiles;
        try (var stream = Files.list(logsDir)) {
            logFiles = stream
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".log") || name.endsWith(".log.gz");
                })
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .collect(Collectors.toList());
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Erreur scan dossier logs : {}", e.getMessage());
            return new int[]{0, 0};
        }

        for (Path logFile : logFiles) {
            String name = logFile.getFileName().toString();
            boolean isLatest = name.equals("latest.log");
            if (!isLatest && alreadyParsed.contains(name)) continue;

            LocalDate date = extractDate(logFile);
            if (date == null) continue;

            parseFile(logFile, date, playerName, seenFish, seenBait, newFish, newBait);
            if (!isLatest) justParsed.add(name);
        }

        newFish.sort(Comparator.comparing(a -> a[0]));
        newBait.sort(Comparator.comparing(a -> a[0]));

        int fishCount = writeEntries(fishCsvPath, newFish);
        int baitCount = writeEntries(baitCsvPath, newBait);

        boolean fishOk = newFish.isEmpty() || fishCount > 0;
        boolean baitOk = newBait.isEmpty() || baitCount > 0;
        if (!justParsed.isEmpty() && fishOk && baitOk) appendRegistry(registry, justParsed);

        FishLogMod.LOGGER.info("[FishLog] Import terminé : {} poissons, {} appâts", fishCount, baitCount);
        return new int[]{fishCount, baitCount};
    }

    // ── Registre des fichiers déjà parsés ─────────────────────────────────────

    private static Set<String> loadRegistry(Path registry) {
        Set<String> set = new HashSet<>();
        if (!Files.exists(registry)) return set;
        try (BufferedReader br = Files.newBufferedReader(registry, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) set.add(line);
            }
        } catch (IOException ignored) {}
        return set;
    }

    private static void appendRegistry(Path registry, List<String> names) {
        try (BufferedWriter bw = Files.newBufferedWriter(registry,
                StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
            for (String name : names) {
                bw.write(name);
                bw.newLine();
            }
        } catch (IOException e) {
            FishLogMod.LOGGER.warn("[FishLog] Mise à jour registry : {}", e.getMessage());
        }
    }

    // ── Chargement des lignes existantes pour déduplication ──────────────────

    private static Set<String> loadLines(Path csvPath) {
        Set<String> set = new HashSet<>();
        if (!Files.exists(csvPath)) return set;
        try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; } // sauter l'en-tête
                line = line.trim();
                if (!line.isEmpty()) set.add(line);
            }
        } catch (IOException ignored) {}
        return set;
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private static LocalDate extractDate(Path logFile) {
        String name = logFile.getFileName().toString();
        Matcher m = FILE_DATE.matcher(name);
        if (m.find()) {
            try {
                return LocalDate.parse(m.group(1), DATE_FMT);
            } catch (DateTimeParseException ignored) {}
        }
        // Fallback pour latest.log, debug-*.log.gz, etc. : date de modification du fichier
        try {
            return Files.getLastModifiedTime(logFile).toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (IOException e) {
            return LocalDate.now();
        }
    }

    private static BufferedReader openReader(Path logFile) throws IOException {
        String name = logFile.getFileName().toString();
        if (name.endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(logFile)), StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(logFile, StandardCharsets.UTF_8);
    }

    private static void parseFile(Path logFile, LocalDate date, String playerName,
                                   Set<String> seenFish, Set<String> seenBait,
                                   List<String[]> fishEntries, List<String[]> baitEntries) {
        try (BufferedReader reader = openReader(logFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line, date, playerName, seenFish, seenBait, fishEntries, baitEntries);
            }
        } catch (IOException e) {
            FishLogMod.LOGGER.warn("[FishLog] Lecture {} : {}", logFile.getFileName(), e.getMessage());
        }
    }

    private static void processLine(String line, LocalDate date, String playerName,
                                     Set<String> seenFish, Set<String> seenBait,
                                     List<String[]> fishEntries, List<String[]> baitEntries) {
        Matcher lm = LOG_LINE.matcher(line);
        if (!lm.matches()) return;

        String timeStr = lm.group(1);
        String message = lm.group(2).replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();

        LocalTime time;
        try {
            time = LocalTime.parse(timeStr, TIME_FMT);
        } catch (DateTimeParseException e) {
            return;
        }

        String dateTimeStr = LocalDateTime.of(date, time).format(DT_FMT);

        Matcher fm = FISH_PATTERN.matcher(message);
        if (fm.matches()) {
            String player = fm.group(1);
            if (playerName != null && !player.equalsIgnoreCase(playerName)) return;
            String key = String.join(",", dateTimeStr, player, fm.group(2), fm.group(3), fm.group(4));
            if (seenFish.add(key)) {
                fishEntries.add(new String[]{dateTimeStr, player, fm.group(2), fm.group(3), fm.group(4)});
            }
            return;
        }

        Matcher bm = BAIT_PATTERN.matcher(message);
        if (bm.matches()) {
            String player = bm.group(1);
            if (playerName != null && !player.equalsIgnoreCase(playerName)) return;
            String key = String.join(",", dateTimeStr, player, bm.group(2));
            if (seenBait.add(key)) {
                baitEntries.add(new String[]{dateTimeStr, player, bm.group(2)});
            }
        }
    }

    private static int writeEntries(Path csvPath, List<String[]> entries) {
        if (entries.isEmpty()) return 0;
        try (BufferedWriter bw = Files.newBufferedWriter(csvPath,
                StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
            for (String[] entry : entries) {
                bw.write(String.join(",", entry));
                bw.newLine();
            }
            return entries.size();
        } catch (IOException e) {
            FishLogMod.LOGGER.error("[FishLog] Écriture import : {}", e.getMessage());
            return 0;
        }
    }
}
