package com.fishlog;

import java.util.*;
import java.util.stream.Collectors;

class FishStatsDataLoader {

    static void loadFish(FishStatsScreen s) {
        s.records = FishDataStore.INSTANCE.snapshot();

        // ── Raretés ──────────────────────────────────────────────────────────
        Map<String, Integer> rar = new LinkedHashMap<>();
        for (FishRecord r : s.records) rar.merge(r.rarity, 1, Integer::sum);
        s.rarityEntries = new ArrayList<>(rar.entrySet());
        s.rarityEntries.sort(Comparator.comparingInt(e -> {
            int idx = FishStatsScreen.RARITY_ORDER.indexOf(e.getKey());
            return idx < 0 ? FishStatsScreen.RARITY_ORDER.size() : idx;
        }));

        s.lastByRarity = new LinkedHashMap<>();
        for (FishRecord r : s.records)
            s.lastByRarity.merge(r.rarity, r.timestamp, (a, b) -> a.isAfter(b) ? a : b);

        s.lastByFish = new LinkedHashMap<>();
        for (FishRecord r : s.records)
            s.lastByFish.merge(r.fish, r.timestamp, (a, b) -> a.isAfter(b) ? a : b);

        // ── Top par nombre et valeur ──────────────────────────────────────────
        Map<String, Integer> cnt = new LinkedHashMap<>();
        Map<String, Double>  sum = new LinkedHashMap<>(), max = new LinkedHashMap<>(), min = new LinkedHashMap<>();
        for (FishRecord r : s.records) {
            cnt.merge(r.fish, 1, Integer::sum);
            sum.merge(r.fish, r.price, Double::sum);
            max.merge(r.fish, r.price, Math::max);
            min.merge(r.fish, r.price, Math::min);
        }
        s.fishCount = cnt; s.fishPriceSum = sum; s.fishPriceMax = max; s.fishPriceMin = min;
        s.topCount  = cnt.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).collect(Collectors.toList());
        s.topValue  = s.computeTopValue();

        // ── Rareté dominante par poisson ─────────────────────────────────────
        Map<String, Map<String, Integer>> fishRarCount = new LinkedHashMap<>();
        for (FishRecord r : s.records)
            fishRarCount.computeIfAbsent(r.fish, k -> new HashMap<>()).merge(r.rarity, 1, Integer::sum);
        s.fishRarity = new LinkedHashMap<>();
        for (var e : fishRarCount.entrySet()) {
            s.fishRarity.put(e.getKey(), e.getValue().entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(""));
        }

        // ── Horaire & journalier ──────────────────────────────────────────────
        Arrays.fill(s.hourly, 0);
        for (FishRecord r : s.records) s.hourly[r.timestamp.getHour()]++;
        TreeMap<java.time.LocalDate, Integer> dailyMap = new TreeMap<>();
        for (FishRecord r : s.records) dailyMap.merge(r.timestamp.toLocalDate(), 1, Integer::sum);
        s.dailyDates  = dailyMap.keySet().toArray(new java.time.LocalDate[0]);
        s.dailyCounts = dailyMap.values().stream().mapToInt(Integer::intValue).toArray();

        // ── Cumul ─────────────────────────────────────────────────────────────
        if (!s.records.isEmpty()) {
            List<FishRecord> sorted = new ArrayList<>(s.records);
            sorted.sort(Comparator.comparing(r -> r.timestamp));
            s.cumTimes  = new double[sorted.size()];
            s.cumValues = new double[sorted.size()];
            s.cumDates  = new java.time.LocalDateTime[sorted.size()];
            long t0 = sorted.get(0).timestamp.toEpochSecond(java.time.ZoneOffset.UTC);
            double cumSum = 0;
            for (int i = 0; i < sorted.size(); i++) {
                s.cumTimes[i]  = (sorted.get(i).timestamp.toEpochSecond(java.time.ZoneOffset.UTC) - t0) / 60.0;
                cumSum += sorted.get(i).price;
                s.cumValues[i] = cumSum;
                s.cumDates[i]  = sorted.get(i).timestamp;
            }
        }

        // ── Tailles & records ─────────────────────────────────────────────────
        s.sizesByRarity = new LinkedHashMap<>();
        s.fishRecordsByRarity = new LinkedHashMap<>();
        for (FishRecord r : s.records) {
            s.sizesByRarity.computeIfAbsent(r.rarity, k -> new ArrayList<>()).add(r.sizeCm);
            s.fishRecordsByRarity.computeIfAbsent(r.rarity, k -> new ArrayList<>()).add(r);
        }
        s.sizesByRarity.values().forEach(Collections::sort);
        s.allRecords = new ArrayList<>(s.records);
        s.allRecords.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
    }

    static void loadBait(FishStatsScreen s) {
        List<BaitRecord> baitRecs = BaitDataStore.INSTANCE.snapshot();

        Map<String, Integer> btypes = new LinkedHashMap<>();
        for (BaitRecord r : baitRecs) btypes.merge(r.bait, 1, Integer::sum);
        s.baitTypeEntries = new ArrayList<>(btypes.entrySet());
        s.baitTypeEntries.sort((a, b) -> b.getValue() - a.getValue());

        s.lastByBait = new LinkedHashMap<>();
        for (BaitRecord r : baitRecs)
            s.lastByBait.merge(r.bait, r.timestamp, (a, b) -> a.isAfter(b) ? a : b);
        s.baitLastEntries = new ArrayList<>(s.lastByBait.entrySet());
        s.baitLastEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        Arrays.fill(s.baitHourly, 0);
        for (BaitRecord r : baitRecs) s.baitHourly[r.timestamp.getHour()]++;

        TreeMap<java.time.LocalDate, Integer> baitDailyMap = new TreeMap<>();
        for (BaitRecord r : baitRecs) baitDailyMap.merge(r.timestamp.toLocalDate(), 1, Integer::sum);
        s.baitDailyDates  = baitDailyMap.keySet().toArray(new java.time.LocalDate[0]);
        s.baitDailyCounts = baitDailyMap.values().stream().mapToInt(Integer::intValue).toArray();

        if (baitRecs.size() >= 2) {
            List<BaitRecord> bSorted = new ArrayList<>(baitRecs);
            bSorted.sort(Comparator.comparing(r -> r.timestamp));
            s.baitCumTimes  = new double[bSorted.size()];
            s.baitCumValues = new double[bSorted.size()];
            s.baitCumDates  = new java.time.LocalDateTime[bSorted.size()];
            long bt0 = bSorted.get(0).timestamp.toEpochSecond(java.time.ZoneOffset.UTC);
            for (int i = 0; i < bSorted.size(); i++) {
                s.baitCumTimes[i]  = (bSorted.get(i).timestamp.toEpochSecond(java.time.ZoneOffset.UTC) - bt0) / 60.0;
                s.baitCumValues[i] = i + 1;
                s.baitCumDates[i]  = bSorted.get(i).timestamp;
            }
        } else { s.baitCumTimes = null; s.baitCumValues = null; s.baitCumDates = null; }

        s.allBaitRecords = new ArrayList<>(baitRecs);
        s.allBaitRecords.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
    }
}
