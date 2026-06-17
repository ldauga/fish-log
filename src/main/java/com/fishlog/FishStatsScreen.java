package com.fishlog;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.time.format.DateTimeFormatter;
import net.minecraft.client.resource.language.I18n;
import java.util.*;
import java.util.HashMap;
import java.util.stream.Collectors;

public class FishStatsScreen extends Screen {

    // ── Onglets ──────────────────────────────────────────────────────────────
    private enum Tab { RARITY, TOP, SIZES, HOURLY, CUMUL, RECORDS }
    private Tab activeTab = Tab.RARITY;

    // ── Toggle Pêche / Appâts ─────────────────────────────────────────────────
    private boolean baitMode = false;
    private int baitToggleBtnX, baitToggleBtnY;
    private static final int BAIT_TOGGLE_W = 48, BAIT_TOGGLE_H = 18;

    // ── Couleurs ARGB par rareté ──────────────────────────────────────────────
    private static final Map<String, Integer> RARITY_COL = new LinkedHashMap<>();
    static {
        RARITY_COL.put("COMMUN",     ModColors.RARITY_COMMUN);
        RARITY_COL.put("RARE",       ModColors.RARITY_RARE);
        RARITY_COL.put("ÉPIQUE",     ModColors.RARITY_EPIQUE);
        RARITY_COL.put("ÉMISSAIRE",  ModColors.RARITY_EMISSAIRE);
        RARITY_COL.put("LÉGENDAIRE", ModColors.RARITY_LEGENDAIRE);
        RARITY_COL.put("MYTHIQUE",   ModColors.RARITY_MYTHIQUE);
        RARITY_COL.put("",           ModColors.RARITY_COSMIQUE);
        RARITY_COL.put("PERDU",      ModColors.RARITY_PERDU);
        RARITY_COL.put("ARTEFACT",   ModColors.RARITY_ARTEFACT);
        RARITY_COL.put("",     ModColors.RARITY_EVENT);
        RARITY_COL.put("",     ModColors.RARITY_EVENT);
        RARITY_COL.put("",     ModColors.RARITY_EVENT);
        RARITY_COL.put("",     ModColors.RARITY_EVENT);
        RARITY_COL.put("",     ModColors.RARITY_EVENT);
    }

    // ── Ordre d'affichage des raretés dans le box plot ───────────────────────
    private static final List<String> RARITY_ORDER = List.of(
        "PERDU", "COMMUN", "RARE", "ÉPIQUE", "LÉGENDAIRE", "MYTHIQUE", "ABYSSAL", "ÉMISSAIRE", "ARTEFACT",
        "", "", "", "", ""
    );

    // ── Raretés événement : symbol → nom recherchable ────────────────────────
    private static final Map<String, String> EVENT_NAMES = new java.util.HashMap<>();
    static {
        EVENT_NAMES.put("", "badschool");
        EVENT_NAMES.put("", "badlloween");
        EVENT_NAMES.put("", "freliz");
        EVENT_NAMES.put("", "paques");
        EVENT_NAMES.put("", "love in the badlands");
    }

    // ── Couleurs ARGB par appât ───────────────────────────────────────────────
    private static final Map<String, Integer> BAIT_COL = new LinkedHashMap<>();
    private static final Map<String, Integer> BAIT_COL_NORM = new HashMap<>();
    static {
        BAIT_COL.put("Ver Luisant",       ModColors.BAIT_BLEU);
        BAIT_COL.put("Slime Royal",       ModColors.BAIT_BLEU);
        BAIT_COL.put("Aimant",            ModColors.BAIT_GRIS);
        BAIT_COL.put("Appat Runique",       ModColors.BAIT_VIOLET);
        BAIT_COL.put("Leurre Mecanique",  ModColors.BAIT_VIOLET);
        BAIT_COL.put("Companion cube",    ModColors.BAIT_ORANGE);
        BAIT_COL.put("Ruche de Nimeria",  ModColors.BAIT_ORANGE);
        BAIT_COL.put("Zekappat",          ModColors.BAIT_ROUGE);
        BAIT_COL.put("Lisappat",          ModColors.BAIT_ROUGE);
        BAIT_COL.put("Lanterne Abyssale", ModColors.BAIT_BLEU_FONCE);
        BAIT_COL.put("Rune Atlante",      ModColors.BAIT_BLEU_FONCE);
        BAIT_COL.put("Mouche Commune",    ModColors.BAIT_GRIS_CLAIR);
        // Index normalisé (sans accents, sans casse) pour matcher les noms du serveur
        BAIT_COL.forEach((k, v) -> BAIT_COL_NORM.put(FishTextureCache.normalize(k), v));
    }

    // ── Données pré-calculées à l'init ────────────────────────────────────────
    private List<FishRecord>               records;
    private List<Map.Entry<String,Integer>> rarityEntries;   // rareté → nb
    private List<Map.Entry<String,Integer>> topCount;        // poisson → nb (tous)
    private List<Map.Entry<String,Double>>  topValue;        // poisson → valeur selon ValueMode
    private Map<String, String>            fishRarity;       // poisson → rareté dominante
    private Map<String, Double>            fishPriceSum, fishPriceMax, fishPriceMin;
    private Map<String, Integer>           fishCount;
    private int[]    hourly   = new int[24];
    private java.time.LocalDate[] dailyDates;
    private int[]                 dailyCounts;
    private double[] cumTimes;   // temps en minutes depuis le 1er
    private double[] cumValues;  // cumul des prix
    private java.time.LocalDateTime[] cumDates;
    private Map<String, List<Double>>              sizesByRarity;
    private Map<String, List<FishRecord>>          fishRecordsByRarity;
    private List<FishRecord>                       allRecords;
    private Map<String, java.time.LocalDateTime>   lastByRarity;
    private Map<String, java.time.LocalDateTime>   lastByFish;
    private int rarityListH = 0;

    private final List<StarHit> starHits = new ArrayList<>();
    private static class StarHit {
        final String fish; final int x, y, w, h;
        StarHit(String fish, int x, int y, int w, int h) {
            this.fish = fish; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    // ── Données appâts ────────────────────────────────────────────────────────
    private List<Map.Entry<String, Integer>>                    baitTypeEntries;
    private List<Map.Entry<String, java.time.LocalDateTime>>    baitLastEntries;
    private Map<String, java.time.LocalDateTime>                lastByBait;
    private int[]    baitHourly  = new int[24];
    private java.time.LocalDate[] baitDailyDates;
    private int[]                 baitDailyCounts;
    private double[] baitCumTimes;
    private double[] baitCumValues;
    private List<BaitRecord> allBaitRecords;

    // ── Scroll pour la table RECORDS et le tab TOP ───────────────────────────
    private int scrollOffset          = 0;
    private int topScrollOffset       = 0;
    private int baitScrollOffset      = 0;
    private int baitTopScrollOffset   = 0;
    private int rarityScrollOffset    = 0;
    private int baitTypesScrollOffset = 0;
    private static final int ROW_H    = 10;
    private static final int TOP_ROW_H = 12;

    // ── Bouton reverse pour les listes TOP ────────────────────────────────────
    private boolean topReversed     = false;
    private boolean baitTopReversed = false;
    private int topRevBtnX, topRevBtnY;
    private int baitTopRevBtnX, baitTopRevBtnY;
    private static final int REV_BTN_W = 14, REV_BTN_H = 9;

    // ── Bouton Max / Mean / Min (top argent) ──────────────────────────────────
    private enum ValueMode { MAX, MEAN, MIN }
    private ValueMode valueMode = ValueMode.MEAN;
    private int valModeBtnX, valModeBtnY;
    private static final int VAL_MODE_BTN_W = 30, VAL_MODE_BTN_H = 9;

    // ── Scrollbars draggables ─────────────────────────────────────────────────
    private static final int SB_RECORDS    = 0;
    private static final int SB_TOP_R      = 1;
    private static final int SB_BAIT_REC   = 2;
    private static final int SB_BAIT_TOP_L = 3;
    private static final int SB_BAIT_TOP_R = 4;
    private static final int SB_BAIT_TYPES = 5;
    private static final int SB_COUNT      = 6;
    private final int[] sbX      = new int[SB_COUNT];
    private final int[] sbY      = new int[SB_COUNT];
    private final int[] sbTrackH = new int[SB_COUNT];
    private final int[] sbTotal  = new int[SB_COUNT];
    private final int[] sbVis    = new int[SB_COUNT];
    private int sbDragId       = -1;
    private int sbDragStartY   = 0;
    private int sbDragStartOff = 0;

    // ── Recherche (onglet RECORDS) ────────────────────────────────────────────
    private TextFieldWidget     searchField;
    private List<FishRecord>    filteredRecords     = List.of();
    private List<BaitRecord>    filteredBaitRecords = List.of();
    private static final int    SEARCH_H = 12;

    // ── Recherche (onglet TOP) ────────────────────────────────────────────────
    private TextFieldWidget                     topSearchField;
    private List<Map.Entry<String,Integer>>     filteredTopCount = List.of();
    private List<Map.Entry<String,Double>>      filteredTopValue  = List.of();

    // ── Tooltip hover ─────────────────────────────────────────────────────────
    private int         lastMx, lastMy;
    private List<Text>  pendingTooltip = null;

    // ── Échelle logarithmique pour SIZES ──────────────────────────────────────
    private boolean sizesLogScale = false;
    private int sizesLogBtnX, sizesLogBtnY;
    private static final int LOG_BTN_W = 38, LOG_BTN_H = 11;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public FishStatsScreen() {
        super(Text.literal("Fish Stats"));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Pas de blur shader — le monde reste visible derrière le panel
    }

    @Override
    protected void init() {
        FishTextureCache.invalidate();
        records = FishDataStore.INSTANCE.snapshot();

        // Comptage par rareté
        Map<String, Integer> rar = new LinkedHashMap<>();
        for (FishRecord r : records) rar.merge(r.rarity, 1, Integer::sum);
        rarityEntries = new ArrayList<>(rar.entrySet());
        rarityEntries.sort(Comparator.comparingInt(e -> {
            int idx = RARITY_ORDER.indexOf(e.getKey());
            return idx < 0 ? RARITY_ORDER.size() : idx;
        }));

        // Dernière prise par rareté
        lastByRarity = new LinkedHashMap<>();
        for (FishRecord r : records)
            lastByRarity.merge(r.rarity, r.timestamp, (a, b) -> a.isAfter(b) ? a : b);

        // Dernière prise par poisson
        lastByFish = new LinkedHashMap<>();
        for (FishRecord r : records)
            lastByFish.merge(r.fish, r.timestamp, (a, b) -> a.isAfter(b) ? a : b);

        // Top par nombre et par valeur (max / mean / min)
        Map<String, Integer> cnt = new LinkedHashMap<>();
        Map<String, Double>  sum = new LinkedHashMap<>();
        Map<String, Double>  max = new LinkedHashMap<>();
        Map<String, Double>  min = new LinkedHashMap<>();
        for (FishRecord r : records) {
            cnt.merge(r.fish, 1, Integer::sum);
            sum.merge(r.fish, r.price, Double::sum);
            max.merge(r.fish, r.price, Math::max);
            min.merge(r.fish, r.price, Math::min);
        }
        fishCount    = cnt;
        fishPriceSum = sum;
        fishPriceMax = max;
        fishPriceMin = min;
        topCount = cnt.entrySet().stream()
            .sorted((a,b) -> b.getValue()-a.getValue())
            .collect(Collectors.toList());
        topValue = computeTopValue();

        // Rareté dominante par poisson (pour pastille colorée dans TOP)
        Map<String, Map<String, Integer>> fishRarCount = new LinkedHashMap<>();
        for (FishRecord r : records)
            fishRarCount.computeIfAbsent(r.fish, k -> new HashMap<>()).merge(r.rarity, 1, Integer::sum);
        fishRarity = new LinkedHashMap<>();
        for (var e : fishRarCount.entrySet()) {
            String dom = e.getValue().entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
            fishRarity.put(e.getKey(), dom);
        }

        // Hourly
        Arrays.fill(hourly, 0);
        for (FishRecord r : records) hourly[r.timestamp.getHour()]++;

        // Daily
        TreeMap<java.time.LocalDate, Integer> dailyMap = new TreeMap<>();
        for (FishRecord r : records) dailyMap.merge(r.timestamp.toLocalDate(), 1, Integer::sum);
        dailyDates  = dailyMap.keySet().toArray(new java.time.LocalDate[0]);
        dailyCounts = dailyMap.values().stream().mapToInt(Integer::intValue).toArray();

        // Cumul
        if (!records.isEmpty()) {
            List<FishRecord> sorted = new ArrayList<>(records);
            sorted.sort(Comparator.comparing(r -> r.timestamp));
            cumTimes  = new double[sorted.size()];
            cumValues = new double[sorted.size()];
            cumDates  = new java.time.LocalDateTime[sorted.size()];
            long t0 = java.time.LocalDateTime.of(sorted.get(0).timestamp.toLocalDate(),
                sorted.get(0).timestamp.toLocalTime()).toEpochSecond(java.time.ZoneOffset.UTC);
            double cumSum = 0;
            for (int i = 0; i < sorted.size(); i++) {
                long ti = sorted.get(i).timestamp.toEpochSecond(java.time.ZoneOffset.UTC);
                cumTimes[i]  = (ti - t0) / 60.0;
                cumSum       += sorted.get(i).price;
                cumValues[i] = cumSum;
                cumDates[i]  = sorted.get(i).timestamp;
            }
        }

        // Tailles par rareté (pour box plot)
        sizesByRarity = new LinkedHashMap<>();
        fishRecordsByRarity = new LinkedHashMap<>();
        for (FishRecord r : records) {
            sizesByRarity.computeIfAbsent(r.rarity, k -> new ArrayList<>()).add(r.sizeCm);
            fishRecordsByRarity.computeIfAbsent(r.rarity, k -> new ArrayList<>()).add(r);
        }
        sizesByRarity.values().forEach(Collections::sort);

        // Toute la liste triée pour RECORDS
        allRecords = new ArrayList<>(records);
        allRecords.sort((a,b) -> b.timestamp.compareTo(a.timestamp));

        scrollOffset = 0;
        rarityScrollOffset = 0;

        // ── Données appâts ────────────────────────────────────────────────────
        List<BaitRecord> baitRecs = BaitDataStore.INSTANCE.snapshot();

        Map<String, Integer> btypes = new LinkedHashMap<>();
        for (BaitRecord r : baitRecs) btypes.merge(r.bait, 1, Integer::sum);
        baitTypeEntries = new ArrayList<>(btypes.entrySet());
        baitTypeEntries.sort((a, b) -> b.getValue() - a.getValue());

        lastByBait = new LinkedHashMap<>();
        for (BaitRecord r : baitRecs)
            lastByBait.merge(r.bait, r.timestamp, (a, b) -> a.isAfter(b) ? a : b);

        baitLastEntries = new ArrayList<>(lastByBait.entrySet());
        baitLastEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        Arrays.fill(baitHourly, 0);
        for (BaitRecord r : baitRecs) baitHourly[r.timestamp.getHour()]++;

        TreeMap<java.time.LocalDate, Integer> baitDailyMap = new TreeMap<>();
        for (BaitRecord r : baitRecs) baitDailyMap.merge(r.timestamp.toLocalDate(), 1, Integer::sum);
        baitDailyDates  = baitDailyMap.keySet().toArray(new java.time.LocalDate[0]);
        baitDailyCounts = baitDailyMap.values().stream().mapToInt(Integer::intValue).toArray();

        if (baitRecs.size() >= 2) {
            List<BaitRecord> bSorted = new ArrayList<>(baitRecs);
            bSorted.sort(Comparator.comparing(r -> r.timestamp));
            baitCumTimes  = new double[bSorted.size()];
            baitCumValues = new double[bSorted.size()];
            long bt0 = bSorted.get(0).timestamp.toEpochSecond(java.time.ZoneOffset.UTC);
            for (int i = 0; i < bSorted.size(); i++) {
                long ti = bSorted.get(i).timestamp.toEpochSecond(java.time.ZoneOffset.UTC);
                baitCumTimes[i]  = (ti - bt0) / 60.0;
                baitCumValues[i] = i + 1;
            }
        } else {
            baitCumTimes = null;
            baitCumValues = null;
        }

        allBaitRecords = new ArrayList<>(baitRecs);
        allBaitRecords.sort((a, b) -> b.timestamp.compareTo(a.timestamp));

        baitScrollOffset      = 0;
        baitTopScrollOffset   = 0;
        baitTypesScrollOffset = 0;

        // Barre de recherche (positionnée dans la zone records)
        int cX = 15, cY = 52, cW = width - 30;
        searchField = new TextFieldWidget(textRenderer, cX + 2, cY + 2, cW - 7, 9, Text.empty());
        searchField.setMaxLength(100);
        searchField.setChangedListener(t -> {
            scrollOffset     = 0;
            baitScrollOffset = 0;
            updateFiltered();
        });
        searchField.setVisible(false);
        addDrawableChild(searchField);
        updateFiltered();
        if (activeTab == Tab.RECORDS) setFocused(searchField);

        topSearchField = new TextFieldWidget(textRenderer, cX + 2, cY + 2, cW - 7, 9, Text.empty());
        topSearchField.setMaxLength(100);
        topSearchField.setChangedListener(t -> {
            topScrollOffset = 0;
            updateTopFiltered();
        });
        topSearchField.setVisible(false);
        addDrawableChild(topSearchField);
        updateTopFiltered();
        if (activeTab == Tab.TOP && !baitMode) setFocused(topSearchField);
    }

    private List<Map.Entry<String, Double>> computeTopValue() {
        Map<String, Double> vals = new LinkedHashMap<>();
        for (String fish : fishPriceSum.keySet()) {
            double v = switch (valueMode) {
                case MAX  -> fishPriceMax.get(fish);
                case MIN  -> fishPriceMin.get(fish);
                case MEAN -> fishPriceSum.get(fish) / fishCount.getOrDefault(fish, 1);
            };
            vals.put(fish, v);
        }
        return vals.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());
    }

    private void updateFiltered() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        if (q.isEmpty()) {
            filteredRecords     = allRecords;
            filteredBaitRecords = allBaitRecords;
        } else {
            filteredRecords = allRecords.stream()
                .filter(r -> r.fish.toLowerCase().contains(q) || rarityForSearch(r.rarity).contains(q))
                .collect(Collectors.toList());
            filteredBaitRecords = allBaitRecords.stream()
                .filter(r -> r.bait.toLowerCase().contains(q))
                .collect(Collectors.toList());
        }
    }

    private void updateTopFiltered() {
        String q = topSearchField != null ? topSearchField.getText().trim().toLowerCase() : "";
        if (q.isEmpty()) {
            filteredTopCount = topCount;
            filteredTopValue = topValue;
        } else {
            filteredTopCount = topCount.stream()
                .filter(e -> e.getKey().toLowerCase().contains(q)
                          || rarityForSearch(fishRarity.getOrDefault(e.getKey(), "")).contains(q))
                .collect(Collectors.toList());
            filteredTopValue = topValue.stream()
                .filter(e -> e.getKey().toLowerCase().contains(q)
                          || rarityForSearch(fishRarity.getOrDefault(e.getKey(), "")).contains(q))
                .collect(Collectors.toList());
        }
    }

    // Retourne un nom de rareté lisible pour la recherche.
    // "" (cosmique) → "cosmique" ; EVENT → "event <nom>" ; non-imprimable → "abyssale" ; sinon casse basse.
    private static String rarityForSearch(String rarity) {
        if (rarity == null || rarity.isEmpty()) return "cosmique";
        String eventName = EVENT_NAMES.get(rarity);
        if (eventName != null) return "event " + eventName;
        if (rarity.chars().noneMatch(Character::isLetterOrDigit)) return "abyssale";
        return rarity.toLowerCase();
    }

    private static String rarityDisplay(String rarity) {
        if (EVENT_NAMES.containsKey(rarity)) return "EVENT";
        if ("LÉGENDAIRE".equals(rarity)) return "LEGEND";
        return rarity;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Rendu principal
    // ═════════════════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        lastMx = mx; lastMy = my;
        pendingTooltip = null;
        java.util.Arrays.fill(sbTotal, 0);
        renderBackground(ctx, mx, my, delta);

        int W = width, H = height;
        int padL = 10, padT = 30, padR = 10, padB = 10;
        int areaX = padL, areaY = padT, areaW = W - padL - padR, areaH = H - padT - padB;

        // Fond du panel
        ctx.fill(areaX, areaY, areaX + areaW, areaY + areaH, ModColors.UI_PANEL_BG);

        // Onglets
        renderTabs(ctx, areaX, areaY, areaW);

        int cX = areaX + 5, cY = areaY + 22, cW = areaW - 10, cH = areaH - 27;

        if (baitMode) {
            if (allBaitRecords.isEmpty()) {
                ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.empty.bait"), cX + cW/2, cY + cH/2, ModColors.TEXT_WHITE);
                return;
            }
            switch (activeTab) {
                case RARITY  -> renderBaitTypes(ctx, cX, cY, cW, cH);
                case TOP     -> renderBaitTop(ctx, cX, cY, cW, cH);
                case SIZES   -> ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.empty.na_bait"), cX + cW/2, cY + cH/2, ModColors.RARITY_UNKNOWN);
                case HOURLY  -> renderBaitHourly(ctx, cX, cY, cW, cH);
                case CUMUL   -> renderBaitCumul(ctx, cX, cY, cW, cH);
                case RECORDS -> renderBaitRecords(ctx, cX, cY, cW, cH);
            }
        } else {
            if (records.isEmpty()) {
                ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.empty.fish"), cX + cW/2, cY + cH/2, ModColors.TEXT_WHITE);
                return;
            }
            switch (activeTab) {
                case RARITY  -> renderRarity(ctx, cX, cY, cW, cH);
                case TOP     -> renderTop(ctx, cX, cY, cW, cH);
                case SIZES   -> renderSizes(ctx, cX, cY, cW, cH);
                case HOURLY  -> renderHourly(ctx, cX, cY, cW, cH);
                case CUMUL   -> renderCumul(ctx, cX, cY, cW, cH);
                case RECORDS -> renderRecords(ctx, cX, cY, cW, cH);
            }
        }

        // ── Visibilité des barres de recherche ───────────────────────────────────
        boolean isRecordsTab = activeTab == Tab.RECORDS;
        if (searchField != null) {
            searchField.setVisible(isRecordsTab);
            searchField.setEditable(isRecordsTab);
        }
        boolean isTopFishTab = activeTab == Tab.TOP && !baitMode;
        if (topSearchField != null) {
            topSearchField.setVisible(isTopFishTab);
            topSearchField.setEditable(isTopFishTab);
        }

        // ── Bandeau global : noir sur tous les onglets + marque à droite ────────
        {
            int footerH = 12;
            int fy = cY + cH - footerH;
            // Onglets sans leur propre footer noir → on dessine le bandeau
            boolean hasOwnFooter = activeTab == Tab.RARITY || activeTab == Tab.TOP || activeTab == Tab.RECORDS;
            if (!hasOwnFooter) {
                ctx.fill(cX, fy, cX + cW, cY + cH, ModColors.UI_FOOTER_BG);
                ctx.fill(cX, fy, cX + cW, fy + 1, ModColors.UI_FOOTER_LINE);
            }
            String brand = "powered by @LeLeoOriginel";
            ctx.drawTextWithShadow(textRenderer, brand,
                cX + cW - textRenderer.getWidth(brand) - 3, fy + 2, ModColors.TEXT_BRAND);
        }

        if (pendingTooltip != null) {
            ctx.drawTooltip(textRenderer, pendingTooltip, mx, my);
        }
        super.render(ctx, mx, my, delta);
    }

    // ── Onglets ───────────────────────────────────────────────────────────────
    private void renderTabs(DrawContext ctx, int x, int y, int w) {
        // Bouton toggle Pêche / Appâts (à droite)
        baitToggleBtnX = x + w - BAIT_TOGGLE_W - 1;
        baitToggleBtnY = y + 1;
        ctx.fill(baitToggleBtnX, baitToggleBtnY,
            baitToggleBtnX + BAIT_TOGGLE_W, baitToggleBtnY + BAIT_TOGGLE_H,
            baitMode ? ModColors.TOGGLE_BAIT_BG : ModColors.TOGGLE_FISH_BG);
        ctx.fill(baitToggleBtnX, baitToggleBtnY,
            baitToggleBtnX + BAIT_TOGGLE_W, baitToggleBtnY + 1, ModColors.TOGGLE_BORDER);
        ctx.fill(baitToggleBtnX, baitToggleBtnY,
            baitToggleBtnX + 1, baitToggleBtnY + BAIT_TOGGLE_H, ModColors.TOGGLE_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer,
            baitMode ? I18n.translate("fishlog.toggle.bait") : I18n.translate("fishlog.toggle.fish"),
            baitToggleBtnX + BAIT_TOGGLE_W / 2, baitToggleBtnY + 5,
            baitMode ? ModColors.TEXT_PRICE : ModColors.BTN_TEXT_NORMAL);

        Tab[] tabs = Tab.values();
        String[] lbl = baitMode
            ? new String[]{I18n.translate("fishlog.tab.types"), I18n.translate("fishlog.tab.top"), "—", I18n.translate("fishlog.tab.hourly"), I18n.translate("fishlog.tab.cumul"), I18n.translate("fishlog.tab.records")}
            : new String[]{I18n.translate("fishlog.tab.rarity"), I18n.translate("fishlog.tab.top"), I18n.translate("fishlog.tab.sizes"), I18n.translate("fishlog.tab.hourly"), I18n.translate("fishlog.tab.cumul"), I18n.translate("fishlog.tab.records")};
        int tabsW = w - BAIT_TOGGLE_W - 2;
        int tw = tabsW / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tx = x + i * tw;
            boolean active   = tabs[i] == activeTab;
            boolean disabled = baitMode && tabs[i] == Tab.SIZES;
            ctx.fill(tx, y, tx + tw, y + 20, disabled ? ModColors.TAB_DISABLED : active ? ModColors.TAB_ACTIVE : ModColors.TAB_INACTIVE);
            ctx.drawCenteredTextWithShadow(textRenderer, lbl[i], tx + tw/2, y + 6,
                disabled ? ModColors.TAB_TEXT_DISABLED : active ? ModColors.TEXT_WHITE : ModColors.TEXT_MUTED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RARITY – barres horizontales par rareté
    // ─────────────────────────────────────────────────────────────────────────
    private void renderRarity(DrawContext ctx, int x, int y, int w, int h) {
        if (rarityEntries.isEmpty()) return;
        int total  = rarityEntries.stream().mapToInt(Map.Entry::getValue).sum();
        int maxCnt = rarityEntries.get(0).getValue();

        // ── Bande cumulée ───────────────────────────────────────────────────
        int bandH = 14;
        int bx    = x + 2, bandW = w - 4;
        ctx.fill(bx, y + 2, bx + bandW, y + 2 + bandH, ModColors.CHART_BAND_BG);
        int cumX = bx;
        for (Map.Entry<String,Integer> e : rarityEntries) {
            int col  = RARITY_COL.getOrDefault(e.getKey(), ModColors.RARITY_UNKNOWN);
            int segW = bandW * e.getValue() / total;
            ctx.fill(cumX, y + 2, cumX + segW, y + 2 + bandH, col);
            cumX += segW;
        }

        // ── En-tête bandeau ─────────────────────────────────────────────────
        int hdrY = y + 2 + bandH + 2;
        int hdrH = 12;
        ctx.fill(x, hdrY, x + w, hdrY + hdrH, ModColors.UI_HEADER_BG);
        ctx.fill(x, hdrY + hdrH - 1, x + w, hdrY + hdrH, ModColors.UI_HEADER_LINE);
        int barAreaX = x + 2, barAreaW = w - 4;
        int labelW   = 80, barW = barAreaW - labelW - 72;
        ctx.drawTextWithShadow(textRenderer, I18n.translate("fishlog.col.rarity"),       barAreaX,                     hdrY + 2, ModColors.TEXT_HEADER_COL);
        ctx.drawTextWithShadow(textRenderer, I18n.translate("fishlog.col.distribution"), barAreaX + labelW,            hdrY + 2, ModColors.TEXT_HEADER_COL);
        ctx.drawTextWithShadow(textRenderer, "%",                                        barAreaX + labelW + barW + 4, hdrY + 2, ModColors.TEXT_HEADER_COL);

        // ── Liste défilable ─────────────────────────────────────────────────
        int footerH = 12;
        int listY   = hdrY + hdrH;
        int listH   = h - (listY - y) - footerH;
        rarityListH = listH;
        int rowH    = 21;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        int ry = listY - rarityScrollOffset;
        Set<String> rarFavSnap = FavoritesStore.INSTANCE.snapshot();
        for (Map.Entry<String,Integer> e : rarityEntries) {
            if (ry + rowH >= listY && ry <= listY + listH) {
                int col  = RARITY_COL.getOrDefault(e.getKey(), ModColors.RARITY_UNKNOWN);
                float pct = 100f * e.getValue() / total;
                int fill  = barW * e.getValue() / maxCnt;

                int nameCol = EVENT_NAMES.containsKey(e.getKey()) ? 0xFFFFFF : col;
                ctx.drawTextWithShadow(textRenderer, e.getKey(), barAreaX, ry + 1, nameCol);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + barW, ry + 9, ModColors.PLOT_BG);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + fill, ry + 9, col & 0x99FFFFFF | 0x99000000);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + fill, ry + 1, col);
                ctx.drawTextWithShadow(textRenderer,
                    String.format("%.2f%%", pct),
                    barAreaX + labelW + barW + 4, ry + 1, ModColors.TEXT_LIGHT);
                int cnt = e.getValue();
                String countStr = I18n.translate(cnt > 1 ? "fishlog.catch.plural" : "fishlog.catch", cnt);
                java.time.LocalDateTime last = lastByRarity.get(e.getKey());
                String secondLine = last != null
                    ? countStr + "  —  " + I18n.translate("fishlog.elapsed", formatElapsed(last))
                    : countStr;
                ctx.drawTextWithShadow(textRenderer, secondLine, barAreaX, ry + 11, ModColors.TEXT_VERY_MUTED);
            }
            ry += rowH;
            // Sous-entrées : poissons favoris de cette rareté
            for (String fish : rarFavSnap) {
                if (!e.getKey().equals(fishRarity.getOrDefault(fish, ""))) continue;
                if (ry + 9 >= listY && ry <= listY + listH) {
                    int fc = fishCount.getOrDefault(fish, 0);
                    float fpct = records.size() > 0 ? 100f * fc / records.size() : 0f;
                    java.time.LocalDateTime lastF = lastByFish != null ? lastByFish.get(fish) : null;
                    String fcStr = I18n.translate(fc > 1 ? "fishlog.catch.plural" : "fishlog.catch", fc);
                    String elap = lastF != null ? "  " + I18n.translate("fishlog.elapsed", formatElapsed(lastF)) : "";
                    String line = "★ " + fish + "   " + fcStr + String.format("   %.1f%%", fpct) + elap;
                    ctx.drawTextWithShadow(textRenderer, line, barAreaX + 8, ry + 1, 0xFFFFDD00);
                }
                ry += 10;
            }
        }
        ctx.disableScissor();

        // ── Footer ──────────────────────────────────────────────────────────
        int fy = y + h - footerH;
        ctx.fill(x, fy, x + w, y + h, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        ctx.drawTextWithShadow(textRenderer,
            I18n.translate(total > 1 ? "fishlog.footer.rarity.plural" : "fishlog.footer.rarity", total),
            x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }

    private void renderScaledItem(DrawContext ctx, ItemStack stack, int x, int y, int size) {
        float scale = size / 16.0f;
        var matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1.0f);
        ctx.drawItem(stack, 0, 0);
        matrices.pop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  TOP – barres horizontales (tous les poissons, défilable, pastille rareté)
    // ─────────────────────────────────────────────────────────────────────────
    private void renderTop(DrawContext ctx, int x, int y, int w, int h) {
        starHits.clear();
        int footerH = 11;
        int hdrH    = 11;
        int listH   = h - SEARCH_H - hdrH - footerH;

        int sbW  = 4;
        int half = w / 2;
        int cx1 = x + 1,        cw1 = half - 2;
        int cx2 = x + half + 1, cw2 = w - half - 2 - sbW;
        int dotS = 6;
        int rarW = 52;

        int maxCnt = filteredTopCount.isEmpty() ? 1 : filteredTopCount.get(0).getValue();
        double maxVal = filteredTopValue.isEmpty() ? 1.0 : filteredTopValue.get(0).getValue();

        // ── Barre de recherche ────────────────────────────────────────────────
        ctx.fill(x, y, x + w, y + SEARCH_H, ModColors.UI_SEARCH_BG);
        ctx.fill(x, y + SEARCH_H - 1, x + w, y + SEARCH_H, ModColors.UI_FOOTER_LINE);

        int hdrY = y + SEARCH_H;

        // ── En-têtes ─────────────────────────────────────────────────────────
        ctx.fill(x, hdrY, x + w, hdrY + hdrH, ModColors.UI_HEADER_BG);
        ctx.fill(x + half, hdrY, x + half + 1, y + h, ModColors.UI_FOOTER_LINE);
        ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.top.by_count"), cx1 + cw1/2, hdrY + 2, ModColors.TEXT_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.top.by_value"), cx2 + cw2/2, hdrY + 2, ModColors.TEXT_WHITE);
        ctx.fill(x, hdrY + hdrH - 1, x + w, hdrY + hdrH, ModColors.UI_HEADER_LINE);

        // ── Bouton reverse ────────────────────────────────────────────────────
        topRevBtnX = x + w - sbW - REV_BTN_W - 2;
        topRevBtnY = hdrY + 1;
        ctx.fill(topRevBtnX, topRevBtnY, topRevBtnX + REV_BTN_W, topRevBtnY + REV_BTN_H,
            topReversed ? ModColors.BTN_REV_ACTIVE : ModColors.BTN_REV_NORMAL);
        ctx.fill(topRevBtnX, topRevBtnY, topRevBtnX + REV_BTN_W, topRevBtnY + 1, ModColors.TOGGLE_BORDER);
        ctx.fill(topRevBtnX, topRevBtnY, topRevBtnX + 1, topRevBtnY + REV_BTN_H, ModColors.TOGGLE_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, topReversed ? "▲" : "▼",
            topRevBtnX + REV_BTN_W / 2, topRevBtnY + 1, topReversed ? ModColors.TEXT_PRICE : ModColors.BTN_TEXT_NORMAL);

        // ── Bouton Max / Mean / Min ───────────────────────────────────────────
        valModeBtnX = topRevBtnX - VAL_MODE_BTN_W - 2;
        valModeBtnY = hdrY + 1;
        ctx.fill(valModeBtnX, valModeBtnY, valModeBtnX + VAL_MODE_BTN_W, valModeBtnY + VAL_MODE_BTN_H,
            ModColors.BTN_NORMAL);
        ctx.fill(valModeBtnX, valModeBtnY, valModeBtnX + VAL_MODE_BTN_W, valModeBtnY + 1, ModColors.TOGGLE_BORDER);
        ctx.fill(valModeBtnX, valModeBtnY, valModeBtnX + 1, valModeBtnY + VAL_MODE_BTN_H, ModColors.TOGGLE_BORDER);
        String valModeLabel = switch (valueMode) { case MAX -> "MAX"; case MEAN -> "MEAN"; case MIN -> "MIN"; };
        ctx.drawCenteredTextWithShadow(textRenderer, valModeLabel,
            valModeBtnX + VAL_MODE_BTN_W / 2, valModeBtnY + 1, ModColors.BTN_TEXT_NORMAL);

        // ── Listes scrollables ────────────────────────────────────────────────
        int listY = hdrY + hdrH;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        for (int i = 0; i < filteredTopCount.size(); i++) {
            int idx = topReversed ? filteredTopCount.size() - 1 - i : i;
            int ri  = i - topScrollOffset;
            int by  = listY + ri * TOP_ROW_H;
            if (by + TOP_ROW_H < listY || by > listY + listH) continue;
            var e   = filteredTopCount.get(idx);
            int col = RARITY_COL.getOrDefault(fishRarity.getOrDefault(e.getKey(), ""), ModColors.RARITY_UNKNOWN);
            ctx.fill(cx1, by, cx1 + cw1, by + TOP_ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD);

            String rar1 = fishRarity.getOrDefault(e.getKey(), "");
            ItemStack icon1 = FishTextureCache.getItemStack(e.getKey(), rar1);
            if (icon1 != null) {
                renderScaledItem(ctx, icon1, cx1 + 1, by + 1, 10);
            } else {
                ctx.fill(cx1 + 1, by + 2, cx1 + 1 + dotS, by + 2 + dotS, col);
            }

            int barX  = cx1 + dotS + 4;
            int valW  = 34;
            int barW  = cw1 - dotS - 4 - valW - rarW - 2;
            int fill  = (int)(barW * e.getValue() / (double) maxCnt);
            ctx.fill(barX, by + 2, barX + barW, by + 2 + dotS, ModColors.BAR_BG);
            ctx.fill(barX, by + 2, barX + fill, by + 2 + dotS, 0x883388CC | 0xFF000000);
            ctx.drawTextWithShadow(textRenderer, e.getKey(), barX + 2, by + 2, ModColors.TEXT_WHITE);

            int rarCol1 = RARITY_COL.getOrDefault(rar1, ModColors.TEXT_MUTED_ARGB);
            int rarTx1  = cx1 + cw1 - valW - rarW;
            ctx.drawTextWithShadow(textRenderer, rarityDisplay(rar1), rarTx1, by + 2, rarCol1);
            if (EVENT_NAMES.containsKey(rar1))
                checkRectTooltip(rarTx1, by + 2, textRenderer.getWidth("EVENT"), 9,
                    List.of(Text.literal(rar1).withColor(0xFFFFFFFF)));

            String cntShort = formatShort(e.getValue());
            int cntTx = cx1 + cw1 - valW;
            ctx.drawTextWithShadow(textRenderer, cntShort, cntTx, by + 2, ModColors.TEXT_MEDIUM);
            checkTooltip(cntTx, by + 2, cntShort, I18n.translate("fishlog.top.catches", e.getValue()));

            boolean fav1 = FavoritesStore.INSTANCE.isFavorite(e.getKey());
            int sx1 = cx1 + cw1 - 9, sy1 = by + 2;
            ctx.drawTextWithShadow(textRenderer, fav1 ? "★" : "☆", sx1, sy1, fav1 ? 0xFFFFDD00 : ModColors.TEXT_VERY_MUTED);
            starHits.add(new StarHit(e.getKey(), sx1 - 1, sy1, 10, 9));
        }

        for (int i = 0; i < filteredTopValue.size(); i++) {
            int idx = topReversed ? filteredTopValue.size() - 1 - i : i;
            int ri  = i - topScrollOffset;
            int by  = listY + ri * TOP_ROW_H;
            if (by + TOP_ROW_H < listY || by > listY + listH) continue;
            var e   = filteredTopValue.get(idx);
            int col = RARITY_COL.getOrDefault(fishRarity.getOrDefault(e.getKey(), ""), ModColors.RARITY_UNKNOWN);
            ctx.fill(cx2, by, cx2 + cw2, by + TOP_ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD);

            String rar2 = fishRarity.getOrDefault(e.getKey(), "");
            ItemStack icon2 = FishTextureCache.getItemStack(e.getKey(), rar2);
            if (icon2 != null) {
                renderScaledItem(ctx, icon2, cx2 + 1, by + 1, 10);
            } else {
                ctx.fill(cx2 + 1, by + 2, cx2 + 1 + dotS, by + 2 + dotS, col);
            }

            int barX = cx2 + dotS + 4;
            int valW = 40;
            int barW = cw2 - dotS - 4 - valW - rarW - 2;
            int fill = (int)(barW * e.getValue() / maxVal);
            ctx.fill(barX, by + 2, barX + barW, by + 2 + dotS, ModColors.BAR_BG);
            ctx.fill(barX, by + 2, barX + fill, by + 2 + dotS, 0x8844CC88 | 0xFF000000);
            ctx.drawTextWithShadow(textRenderer, e.getKey(), barX + 2, by + 2, ModColors.TEXT_WHITE);

            int rarCol2 = RARITY_COL.getOrDefault(rar2, ModColors.TEXT_MUTED_ARGB);
            int rarTx2  = cx2 + cw2 - valW - rarW;
            ctx.drawTextWithShadow(textRenderer, rarityDisplay(rar2), rarTx2, by + 2, rarCol2);
            if (EVENT_NAMES.containsKey(rar2))
                checkRectTooltip(rarTx2, by + 2, textRenderer.getWidth("EVENT"), 9,
                    List.of(Text.literal(rar2).withColor(0xFFFFFFFF)));

            String priceShort = formatShort(e.getValue()) + "$";
            int priceTx = cx2 + cw2 - valW;
            ctx.drawTextWithShadow(textRenderer, priceShort, priceTx, by + 2, ModColors.TEXT_MEDIUM);
            checkTooltip(priceTx, by + 2, priceShort, String.format("%.0f$", e.getValue()));

            boolean fav2 = FavoritesStore.INSTANCE.isFavorite(e.getKey());
            int sx2 = cx2 + cw2 - 9, sy2 = by + 2;
            ctx.drawTextWithShadow(textRenderer, fav2 ? "★" : "☆", sx2, sy2, fav2 ? 0xFFFFDD00 : ModColors.TEXT_VERY_MUTED);
            starHits.add(new StarHit(e.getKey(), sx2 - 1, sy2, 10, 9));
        }

        ctx.disableScissor();

        // ── Scrollbar (côté valeur uniquement) ───────────────────────────────
        int topVisible = listH / TOP_ROW_H;
        renderScrollbar(ctx, SB_TOP_R, cx2 + cw2 + 1, listY, listH, topScrollOffset, filteredTopValue.size(), topVisible);

        // ── Footer ────────────────────────────────────────────────────────────
        int fy = listY + listH;
        ctx.fill(x, fy, x + w, fy + footerH, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        String topFooter = filteredTopCount.size() == topCount.size()
            ? I18n.translate(topCount.size() > 1 ? "fishlog.footer.top.plural" : "fishlog.footer.top", topCount.size())
            : I18n.translate("fishlog.footer.filtered", filteredTopCount.size(), topCount.size());
        ctx.drawTextWithShadow(textRenderer, topFooter, x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SIZES – boîtes à moustaches + toggle échelle log
    // ─────────────────────────────────────────────────────────────────────────
    private void renderSizes(DrawContext ctx, int x, int y, int w, int h) {
        if (sizesByRarity.isEmpty()) return;

        var entries = sizesByRarity.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> {
                int idx = RARITY_ORDER.indexOf(e.getKey());
                return idx < 0 ? RARITY_ORDER.size() : idx;
            }))
            .collect(Collectors.toList());
        if (entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.empty.nodata"), x + w/2, y + h/2, ModColors.TEXT_WHITE);
            return;
        }
        var richEntries = entries.stream().filter(e -> e.getValue().size() >= 3).collect(Collectors.toList());

        // ── Bouton LIN / LOG ─────────────────────────────────────────────────
        sizesLogBtnX = x + w - LOG_BTN_W - 2;
        sizesLogBtnY = y + 1;
        ctx.fill(sizesLogBtnX, sizesLogBtnY, sizesLogBtnX + LOG_BTN_W, sizesLogBtnY + LOG_BTN_H,
            sizesLogScale ? ModColors.BTN_ACTIVE : ModColors.BTN_NORMAL);
        ctx.fill(sizesLogBtnX, sizesLogBtnY, sizesLogBtnX + LOG_BTN_W, sizesLogBtnY + 1, ModColors.TOGGLE_BORDER);
        ctx.fill(sizesLogBtnX, sizesLogBtnY, sizesLogBtnX + 1, sizesLogBtnY + LOG_BTN_H, ModColors.TOGGLE_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer,
            sizesLogScale ? "LOG" : "LIN",
            sizesLogBtnX + LOG_BTN_W / 2, sizesLogBtnY + 2,
            sizesLogScale ? ModColors.TEXT_PRICE : ModColors.BTN_TEXT_NORMAL);

        // ── Calcul des bornes (uniquement les raretés avec assez de data) ────────
        var scaleSource = richEntries.isEmpty() ? entries : richEntries;
        double rawMin = scaleSource.stream().flatMapToDouble(e -> e.getValue().stream().mapToDouble(d -> d)).min().orElse(1);
        double rawMax = scaleSource.stream().flatMapToDouble(e -> e.getValue().stream().mapToDouble(d -> d)).max().orElse(100);
        if (rawMax <= rawMin) rawMax = rawMin + 10;

        // En échelle log, on travaille en log10(v) — floor à 0.01 pour éviter log(0)
        double scaleMin = sizesLogScale ? Math.log10(Math.max(0.01, rawMin)) : rawMin;
        double scaleMax = sizesLogScale ? Math.log10(Math.max(0.01, rawMax)) : rawMax;
        double scaleRange = scaleMax - scaleMin;
        if (scaleRange <= 0) scaleRange = 1;

        // Convertit une valeur brute en position Y dans la zone de plot
        final double sm = scaleMin, sr = scaleRange;
        java.util.function.Function<Double, Double> toNorm = (v) -> {
            double sv = sizesLogScale ? Math.log10(Math.max(0.01, v)) : v;
            return (sv - sm) / sr;
        };

        int plotX = x + 20, plotY = y + 14, plotW = w - 40, plotH = h - 30;
        int bw    = plotW / entries.size();
        ctx.fill(plotX, plotY, plotX + plotW, plotY + plotH, ModColors.PLOT_BG);

        for (int i = 0; i < entries.size(); i++) {
            var e    = entries.get(i);
            var vals = e.getValue();
            int col  = RARITY_COL.getOrDefault(e.getKey(), ModColors.RARITY_UNKNOWN);
            int bx   = plotX + i * bw + bw/5;
            int bwb  = bw * 3 / 5;

            // Label commun (sous le graphique)
            boolean isEvent = EVENT_NAMES.containsKey(e.getKey());
            String label = isEvent ? "EVT" : e.getKey().substring(0, Math.min(3, e.getKey().length()));
            int labelCol = isEvent ? 0xFFFFFF : (vals.size() < 3 ? col & 0x88FFFFFF : col);
            ctx.drawCenteredTextWithShadow(textRenderer, label, bx + bwb/2, plotY + plotH + 2, labelCol);
            if (isEvent)
                checkRectTooltip(bx, plotY + plotH + 2, bwb, 9,
                    List.of(Text.literal(e.getKey()).withColor(0xFFFFFFFF)));

            if (vals.size() < 3) {
                // Colonne grisée + message "pas assez de données"
                ctx.fill(bx, plotY, bx + bwb, plotY + plotH, col & 0x22FFFFFF | 0x22000000);
                ctx.drawCenteredTextWithShadow(textRenderer,
                    I18n.translate("fishlog.empty.nodata"),
                    bx + bwb/2, plotY + plotH/2 - 4, ModColors.TEXT_VERY_MUTED);
                continue;
            }

            double q1  = percentile(vals, 25);
            double med = percentile(vals, 50);
            double q3  = percentile(vals, 75);
            double lo  = vals.get(0);
            double hi  = vals.get(vals.size()-1);

            int yQ1  = plotY + plotH - (int)(plotH * toNorm.apply(q1));
            int yMed = plotY + plotH - (int)(plotH * toNorm.apply(med));
            int yQ3  = plotY + plotH - (int)(plotH * toNorm.apply(q3));
            int yLo  = plotY + plotH - (int)(plotH * toNorm.apply(lo));
            int yHi  = plotY + plotH - (int)(plotH * toNorm.apply(hi));

            // Boîte
            ctx.fill(bx, yQ3, bx + bwb, yQ1, col & 0x55FFFFFF | 0x55000000);
            ctx.fill(bx, yQ3, bx + bwb, yQ3 + 1, col);
            ctx.fill(bx, yQ1, bx + bwb, yQ1 + 1, col);
            // Médiane
            ctx.fill(bx, yMed, bx + bwb, yMed + 2, ModColors.CHART_MEDIAN);
            // Moustaches
            int mid = bx + bwb/2;
            ctx.fill(mid, yHi, mid + 1, yQ3, col);
            ctx.fill(mid, yQ1, mid + 1, yLo, col);

            // Hover tooltip
            List<FishRecord> rarityRecs = fishRecordsByRarity.getOrDefault(e.getKey(), List.of());
            if (!rarityRecs.isEmpty()) {
                FishRecord maxFish = rarityRecs.stream().max(Comparator.comparingDouble(r -> r.sizeCm)).orElse(null);
                FishRecord minFish = rarityRecs.stream().min(Comparator.comparingDouble(r -> r.sizeCm)).orElse(null);
                List<Text> lines = new ArrayList<>();
                lines.add(Text.literal(e.getKey()).withColor(col));
                if (maxFish != null)
                    lines.add(Text.literal(I18n.translate("fishlog.sizes.tooltip.max", maxFish.fish, maxFish.sizeCm)));
                if (minFish != null)
                    lines.add(Text.literal(I18n.translate("fishlog.sizes.tooltip.min", minFish.fish, minFish.sizeCm)));
                lines.add(Text.literal(I18n.translate("fishlog.sizes.tooltip.q1", q1)));
                lines.add(Text.literal(I18n.translate("fishlog.sizes.tooltip.median", med)));
                lines.add(Text.literal(I18n.translate("fishlog.sizes.tooltip.q3", q3)));
                checkRectTooltip(bx, plotY, bwb, plotH, lines);
            }
        }

        // Axe Y : ticks adaptés à l'échelle
        for (int tick = 0; tick <= 4; tick++) {
            double norm = tick / 4.0;
            double raw  = sizesLogScale
                ? Math.pow(10, scaleMin + norm * scaleRange)
                : rawMin + norm * scaleRange;
            int yt = plotY + plotH - (int)(plotH * norm);
            ctx.drawTextWithShadow(textRenderer, String.format("%.0f", raw), x, yt - 4, ModColors.TEXT_MUTED);
            ctx.fill(plotX, yt, plotX + plotW, yt + 1, ModColors.CHART_GRID_DENSE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HOURLY – barres par heure + courbe poissons/jour
    // ─────────────────────────────────────────────────────────────────────────
    private void renderHourly(DrawContext ctx, int x, int y, int w, int h) {
        int usableH     = h - 12;                 // réserve le pied de page global
        int hourlyAreaH = usableH * 48 / 100;     // ~48 % pour les barres horaires
        int sepY        = y + hourlyAreaH;

        // ── Titre section horaire ────────────────────────────────────────────
        ctx.drawCenteredTextWithShadow(textRenderer,
            I18n.translate("fishlog.hourly.label"), x + w / 2, y + 1, ModColors.TEXT_MUTED);

        // ── Barres horaires ──────────────────────────────────────────────────
        int maxH = Arrays.stream(hourly).max().orElse(1);
        if (maxH == 0) maxH = 1;
        int plotH  = hourlyAreaH - 20;
        int plotW  = w - 20;
        int bw     = plotW / 24;
        int topOff = 10;
        for (int i = 0; i < 24; i++) {
            int bh = hourly[i] * plotH / maxH;
            int bx = x + 10 + i * bw;
            int by = y + topOff + (plotH - bh);
            ctx.fill(bx, by, bx + bw - 1, y + topOff + plotH, ModColors.CHART_HOURLY_FISH);
            if (i % 4 == 0) {
                ctx.drawCenteredTextWithShadow(textRenderer,
                    String.valueOf(i), bx + bw / 2, y + topOff + plotH + 2, ModColors.TEXT_MUTED);
            }
            if (hourly[i] > 0) {
                String hShort = formatShort(hourly[i]);
                drawFittedText(ctx, hShort, bx + bw / 2, by - 9, bw - 1, ModColors.TEXT_WHITE);
                int barBottom = y + topOff + plotH;
                if (lastMx >= bx && lastMx < bx + bw && lastMy >= by && lastMy <= barBottom) {
                    pendingTooltip = List.of(Text.literal(I18n.translate("fishlog.hourly.tooltip", hourly[i], i)));
                }
            }
        }

        // ── Séparateur ───────────────────────────────────────────────────────
        ctx.fill(x, sepY, x + w, sepY + 1, ModColors.UI_DIVIDER);

        // ── Graphe poissons / jour ────────────────────────────────────────────
        int dailyY = sepY + 2;
        int dailyH = usableH - hourlyAreaH - 3;

        ctx.drawCenteredTextWithShadow(textRenderer,
            I18n.translate("fishlog.daily.title"), x + w / 2, dailyY + 1, ModColors.TEXT_MUTED);

        if (dailyCounts == null || dailyCounts.length < 2) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                I18n.translate("fishlog.empty.nodata"), x + w / 2, dailyY + dailyH / 2, ModColors.TEXT_WHITE);
            return;
        }

        int n    = dailyCounts.length;
        int maxV = Arrays.stream(dailyCounts).max().orElse(1);
        if (maxV == 0) maxV = 1;

        int plotX  = x + 28;
        int plotY2 = dailyY + 12;
        int plotW2 = w - 36;
        int plotH2 = dailyH - 24;
        if (plotH2 < 8) return;
        ctx.fill(plotX, plotY2, plotX + plotW2, plotY2 + plotH2, ModColors.PLOT_BG);

        // Aire + ligne
        for (int i = 0; i < n - 1; i++) {
            float t1 = (float) i / (n - 1);
            float t2 = (float) (i + 1) / (n - 1);
            float v1 = (float) dailyCounts[i] / maxV;
            float v2 = (float) dailyCounts[i + 1] / maxV;
            int x1 = plotX + (int)(plotW2 * t1);
            int x2 = plotX + (int)(plotW2 * t2);
            int y1 = plotY2 + plotH2 - (int)(plotH2 * v1);
            int y2 = plotY2 + plotH2 - (int)(plotH2 * v2);
            for (int px = x1; px <= x2 && px < plotX + plotW2; px++) {
                int yTop = px == x1 ? y1 : y1 + (y2 - y1) * (px - x1) / Math.max(1, x2 - x1);
                ctx.fill(px, yTop, px + 1, plotY2 + plotH2, ModColors.CHART_FISH_FILL);
            }
            thickLine(plotX, plotY2, plotW2, plotH2, t1, v1, t2, v2, 2f, ModColors.CHART_FISH_LINE);
        }

        // Grille Y + étiquettes
        for (int t = 0; t <= 4; t++) {
            int yt = plotY2 + plotH2 - t * plotH2 / 4;
            ctx.fill(plotX, yt, plotX + plotW2, yt + 1, ModColors.CHART_GRID);
            ctx.drawTextWithShadow(textRenderer,
                String.valueOf((int) Math.round(maxV * t / 4.0)), x, yt - 4, ModColors.TEXT_MUTED);
        }

        // Hover tooltip : jour le plus proche sous le curseur
        if (lastMx >= plotX && lastMx <= plotX + plotW2 && lastMy >= plotY2 && lastMy <= plotY2 + plotH2) {
            int hovI = Math.round((float)(lastMx - plotX) / plotW2 * (n - 1));
            hovI = Math.max(0, Math.min(n - 1, hovI));
            DateTimeFormatter fmtFull = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            pendingTooltip = List.of(Text.literal(I18n.translate("fishlog.daily.tooltip", dailyCounts[hovI], dailyDates[hovI].format(fmtFull))));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CUMUL – courbe cumulée
    // ─────────────────────────────────────────────────────────────────────────
    private static final DateTimeFormatter CUMUL_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private void renderCumul(DrawContext ctx, int x, int y, int w, int h) {
        if (cumTimes == null || cumTimes.length < 2) {
            ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.empty.nodata"), x + w/2, y + h/2, ModColors.TEXT_WHITE);
            return;
        }

        double maxT = cumTimes[cumTimes.length - 1];
        double maxV = cumValues[cumValues.length - 1];
        if (maxT <= 0) maxT = 1;
        if (maxV <= 0) maxV = 1;

        int plotX = x + 30, plotY = y + 10, plotW = w - 40, plotH = h - 25;
        ctx.fill(plotX, plotY, plotX + plotW, plotY + plotH, ModColors.PLOT_BG);

        // Précalcul : une valeur Y par colonne pixel (O(plotW) au lieu de O(N))
        int[] colY = new int[plotW];
        int dataIdx = 0;
        for (int px = 0; px < plotW; px++) {
            double t = maxT * px / (plotW - 1);
            while (dataIdx < cumTimes.length - 1 && cumTimes[dataIdx + 1] <= t) dataIdx++;
            double v;
            if (dataIdx >= cumTimes.length - 1) {
                v = cumValues[cumValues.length - 1];
            } else {
                double dt = cumTimes[dataIdx + 1] - cumTimes[dataIdx];
                double frac = dt > 0 ? (t - cumTimes[dataIdx]) / dt : 0;
                v = cumValues[dataIdx] + frac * (cumValues[dataIdx + 1] - cumValues[dataIdx]);
            }
            colY[px] = plotY + plotH - (int)(plotH * v / maxV);
        }

        // Aire sous la courbe (O(plotW) fill calls)
        for (int px = 0; px < plotW; px++) {
            ctx.fill(plotX + px, colY[px], plotX + px + 1, plotY + plotH, ModColors.CHART_FISH_FILL);
        }

        // Ligne épaisse en un seul draw call batchifié
        var tess = Tessellator.getInstance();
        var buf  = tess.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int px = 0; px < plotW - 1; px++) {
            float fx1 = plotX + px,     fy1 = colY[px];
            float fx2 = plotX + px + 1, fy2 = colY[px + 1];
            float ddx = fx2 - fx1, ddy = fy2 - fy1;
            float len = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (len < 0.001f) continue;
            float nx = -ddy / len, ny = ddx / len;
            buf.vertex(fx1 + nx, fy1 + ny, 0f).color(ModColors.CHART_FISH_LINE);
            buf.vertex(fx1 - nx, fy1 - ny, 0f).color(ModColors.CHART_FISH_LINE);
            buf.vertex(fx2 + nx, fy2 + ny, 0f).color(ModColors.CHART_FISH_LINE);
            buf.vertex(fx2 + nx, fy2 + ny, 0f).color(ModColors.CHART_FISH_LINE);
            buf.vertex(fx1 - nx, fy1 - ny, 0f).color(ModColors.CHART_FISH_LINE);
            buf.vertex(fx2 - nx, fy2 - ny, 0f).color(ModColors.CHART_FISH_LINE);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();

        // Axes
        for (int t = 0; t <= 4; t++) {
            int yt = plotY + plotH - t * plotH / 4;
            ctx.fill(plotX, yt, plotX + plotW, yt + 1, ModColors.CHART_GRID);
            double axisVal = maxV * t / 4;
            String axisShort = formatShort(axisVal) + "$";
            ctx.drawTextWithShadow(textRenderer, axisShort, x, yt - 4, ModColors.TEXT_MUTED);
            checkTooltip(x, yt - 4, axisShort, String.format("%.0f$", axisVal));
        }
        int cumulCnt = cumTimes.length;
        ctx.drawCenteredTextWithShadow(textRenderer,
            I18n.translate(cumulCnt > 1 ? "fishlog.cumul.footer.plural" : "fishlog.cumul.footer", cumValues[cumValues.length-1], cumulCnt),
            plotX + plotW/2, plotY + plotH + 6, ModColors.TEXT_WHITE);

        // Hover tooltip : recherche binaire sur cumTimes
        if (lastMx >= plotX && lastMx <= plotX + plotW && lastMy >= plotY && lastMy <= plotY + plotH) {
            double tCursor = maxT * (lastMx - plotX) / (double) plotW;
            int lo = 0, hi = cumTimes.length - 1;
            while (lo < hi - 1) {
                int mid = (lo + hi) >> 1;
                if (cumTimes[mid] <= tCursor) lo = mid; else hi = mid;
            }
            int hovI = Math.abs(cumTimes[lo] - tCursor) <= Math.abs(cumTimes[hi] - tCursor) ? lo : hi;
            pendingTooltip = List.of(Text.literal(I18n.translate("fishlog.cumul.tooltip", cumDates[hovI].format(CUMUL_FMT), Math.round(cumValues[hovI]))));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RECORDS – table défilable
    // ─────────────────────────────────────────────────────────────────────────
    private void renderRecords(DrawContext ctx, int x, int y, int w, int h) {
        int sbW    = 4;
        int dateW  = 95, rarW = 62, sizeW = 54, priceW = 38;
        int fishW  = w - dateW - rarW - sizeW - priceW - sbW - 4;
        int[] widths = {dateW, rarW, fishW, sizeW, priceW};
        String[] hdrs = {I18n.translate("fishlog.col.date"), I18n.translate("fishlog.col.rarity"), I18n.translate("fishlog.col.fish"), I18n.translate("fishlog.col.size"), I18n.translate("fishlog.col.price")};

        // Fond barre de recherche
        ctx.fill(x, y, x + w, y + SEARCH_H, ModColors.UI_SEARCH_BG);
        ctx.fill(x, y + SEARCH_H - 1, x + w, y + SEARCH_H, ModColors.UI_FOOTER_LINE);

        // En-tête colonnes (sous la barre de recherche)
        int footerH = 12;
        int hdrH    = 12;
        int hY = y + SEARCH_H;
        ctx.fill(x, hY, x + w, hY + hdrH, ModColors.UI_HEADER_BG);
        int cx = x;
        for (int i = 0; i < hdrs.length; i++) {
            ctx.drawTextWithShadow(textRenderer, hdrs[i], cx + 2, hY + 2, ModColors.TEXT_HEADER_COL);
            cx += widths[i];
        }
        ctx.fill(x, hY + hdrH - 1, x + w, hY + hdrH, ModColors.UI_HEADER_LINE);

        // Zone scrollable
        int listY = hY + hdrH;
        int listH = h - SEARCH_H - hdrH - footerH;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        int rowY = listY - scrollOffset * ROW_H;
        for (int i = 0; i < filteredRecords.size(); i++) {
            if (rowY + ROW_H < listY || rowY > listY + listH) { rowY += ROW_H; continue; }
            FishRecord r = filteredRecords.get(i);
            ctx.fill(x, rowY, x + w, rowY + ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD_ALT);

            int rCol = RARITY_COL.getOrDefault(r.rarity, ModColors.TEXT_MUTED_ARGB);
            cx = x;
            ctx.drawTextWithShadow(textRenderer, r.timestamp.format(DT_FMT),         cx + 2, rowY + 1, ModColors.TEXT_LIGHT);  cx += widths[0];
            ctx.drawTextWithShadow(textRenderer, rarityDisplay(r.rarity), cx + 2, rowY + 1, rCol);
            if (EVENT_NAMES.containsKey(r.rarity))
                checkRectTooltip(cx + 2, rowY + 1, textRenderer.getWidth("EVENT"), 9,
                    List.of(Text.literal(r.rarity).withColor(0xFFFFFFFF)));
            cx += widths[1];
            ItemStack fishIcon = FishTextureCache.getItemStack(r.fish, r.rarity);
            if (fishIcon != null) renderScaledItem(ctx, fishIcon, cx + 1, rowY + 1, 8);
            ctx.drawTextWithShadow(textRenderer, r.fish, cx + (fishIcon != null ? 10 : 2), rowY + 1, ModColors.TEXT_WHITE);     cx += widths[2];
            ctx.drawTextWithShadow(textRenderer, String.format("%.1fcm", r.sizeCm),  cx + 2, rowY + 1, ModColors.TEXT_MEDIUM);  cx += widths[3];
            ctx.drawTextWithShadow(textRenderer, String.format("%.0f$", r.price),    cx + 2, rowY + 1, ModColors.TEXT_PRICE);
            rowY += ROW_H;
        }
        ctx.disableScissor();

        // Scrollbar
        int recVisible = listH / ROW_H;
        renderScrollbar(ctx, SB_RECORDS, x + w - sbW, listY, listH, scrollOffset, filteredRecords.size(), recVisible);

        // Footer avec fond noir
        int fy = y + h - footerH;
        ctx.fill(x, fy, x + w, y + h, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        String footerTxt = filteredRecords.size() == allRecords.size()
            ? I18n.translate(allRecords.size() > 1 ? "fishlog.footer.records.plural" : "fishlog.footer.records", allRecords.size())
            : I18n.translate("fishlog.footer.filtered", filteredRecords.size(), allRecords.size());
        ctx.drawTextWithShadow(textRenderer, footerTxt, x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BAIT TYPES – distribution par type d'appât (défilable)
    // ─────────────────────────────────────────────────────────────────────────
    private void renderBaitTypes(DrawContext ctx, int x, int y, int w, int h) {
        if (baitTypeEntries.isEmpty()) return;
        int total  = baitTypeEntries.stream().mapToInt(Map.Entry::getValue).sum();
        int maxCnt = baitTypeEntries.get(0).getValue();

        // ── Bande cumulée ────────────────────────────────────────────────────
        int bandH = 14, bx = x + 2, bandW = w - 4;
        ctx.fill(bx, y + 2, bx + bandW, y + 2 + bandH, ModColors.CHART_BAND_BG);
        int cumX = bx;
        for (var e : baitTypeEntries) {
            int col  = baitColor(e.getKey());
            int segW = bandW * e.getValue() / total;
            ctx.fill(cumX, y + 2, cumX + segW, y + 2 + bandH, col);
            cumX += segW;
        }

        // ── Liste défilable ──────────────────────────────────────────────────
        int sbW      = 4;
        int barAreaX = x + 2, barAreaW = w - 4;
        int labelW   = 90, barW = barAreaW - labelW - 55 - sbW;
        int rowH     = 21;
        int footerH  = 12;
        int listY    = y + 2 + bandH + 2;
        int listH    = h - (listY - y) - footerH;

        ctx.enableScissor(x, listY, x + w, listY + listH);
        int ry = listY - baitTypesScrollOffset * rowH;
        for (var e : baitTypeEntries) {
            if (ry + rowH >= listY && ry <= listY + listH) {
                int col   = baitColor(e.getKey());
                float pct = 100f * e.getValue() / total;
                int fill  = barW * e.getValue() / maxCnt;

                ctx.drawTextWithShadow(textRenderer, e.getKey(), barAreaX, ry + 1, col);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + barW, ry + 9, ModColors.PLOT_BG);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + fill, ry + 9, col & 0x99FFFFFF | 0x99000000);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + fill, ry + 1, col);
                ctx.drawTextWithShadow(textRenderer,
                    String.format("%d  (%.1f%%)", e.getValue(), pct),
                    barAreaX + labelW + barW + 4, ry + 1, ModColors.TEXT_LIGHT);
                java.time.LocalDateTime last = lastByBait.get(e.getKey());
                if (last != null) {
                    ctx.drawTextWithShadow(textRenderer,
                        I18n.translate("fishlog.elapsed", formatElapsed(last)),
                        barAreaX, ry + 11, ModColors.TEXT_VERY_MUTED);
                }
            }
            ry += rowH;
        }
        ctx.disableScissor();

        // ── Scrollbar ────────────────────────────────────────────────────────
        int visRows = listH / rowH;
        renderScrollbar(ctx, SB_BAIT_TYPES, x + w - sbW, listY, listH, baitTypesScrollOffset, baitTypeEntries.size(), visRows);

        // ── Footer ───────────────────────────────────────────────────────────
        int fy = y + h - footerH;
        ctx.fill(x, fy, x + w, y + h, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        ctx.drawTextWithShadow(textRenderer,
            I18n.translate(total > 1 ? "fishlog.bait.total.plural" : "fishlog.bait.total", total),
            x + 2, fy + 2, ModColors.TEXT_BAIT_FOOTER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BAIT TOP – par quantité (gauche) et par récence (droite)
    // ─────────────────────────────────────────────────────────────────────────
    private void renderBaitTop(DrawContext ctx, int x, int y, int w, int h) {
        int footerH = 11, hdrH = 11;
        int listH   = h - hdrH - footerH;
        int sbW     = 4;
        int half    = w / 2;
        int cx1 = x + 1,        cw1 = half - 2 - sbW;
        int cx2 = x + half + 1, cw2 = w - half - 2 - sbW;
        int dotS = 6;

        int maxCnt = baitTypeEntries.isEmpty() ? 1 : baitTypeEntries.get(0).getValue();

        ctx.fill(x, y, x + w, y + hdrH, ModColors.UI_HEADER_BG);
        ctx.fill(x + half, y, x + half + 1, y + h, ModColors.UI_FOOTER_LINE);
        ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.top.by_count"),       cx1 + cw1/2, y + 2, ModColors.TEXT_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.bait.top.by_recency"), cx2 + cw2/2, y + 2, ModColors.TEXT_WHITE);
        ctx.fill(x, y + hdrH - 1, x + w, y + hdrH, ModColors.UI_HEADER_LINE);

        // ── Bouton reverse ────────────────────────────────────────────────────
        baitTopRevBtnX = x + w - sbW - REV_BTN_W - 2;
        baitTopRevBtnY = y + 1;
        ctx.fill(baitTopRevBtnX, baitTopRevBtnY, baitTopRevBtnX + REV_BTN_W, baitTopRevBtnY + REV_BTN_H,
            baitTopReversed ? ModColors.BTN_REV_ACTIVE : ModColors.BTN_REV_NORMAL);
        ctx.fill(baitTopRevBtnX, baitTopRevBtnY, baitTopRevBtnX + REV_BTN_W, baitTopRevBtnY + 1, ModColors.TOGGLE_BORDER);
        ctx.fill(baitTopRevBtnX, baitTopRevBtnY, baitTopRevBtnX + 1, baitTopRevBtnY + REV_BTN_H, ModColors.TOGGLE_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, baitTopReversed ? "▲" : "▼",
            baitTopRevBtnX + REV_BTN_W / 2, baitTopRevBtnY + 1, baitTopReversed ? ModColors.TEXT_PRICE : ModColors.BTN_TEXT_NORMAL);

        int listY = y + hdrH;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        for (int i = 0; i < baitTypeEntries.size(); i++) {
            int idx = baitTopReversed ? baitTypeEntries.size() - 1 - i : i;
            int ri = i - baitTopScrollOffset;
            int by = listY + ri * TOP_ROW_H;
            if (by + TOP_ROW_H < listY || by > listY + listH) continue;
            var e   = baitTypeEntries.get(idx);
            int col = baitColor(e.getKey());
            ctx.fill(cx1, by, cx1 + cw1, by + TOP_ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD);
            ctx.fill(cx1 + 1, by + 2, cx1 + 1 + dotS, by + 2 + dotS, col);
            int barX = cx1 + dotS + 4, valW = 24;
            int barW = cw1 - dotS - 4 - valW - 2;
            int fill = (int)(barW * e.getValue() / (double) maxCnt);
            ctx.fill(barX, by + 2, barX + barW, by + 2 + dotS, ModColors.BAR_BG);
            ctx.fill(barX, by + 2, barX + fill,  by + 2 + dotS, ModColors.CHART_HOURLY_FISH);
            ctx.drawTextWithShadow(textRenderer, e.getKey(), barX + 2, by + 2, ModColors.TEXT_WHITE);
            ctx.drawTextWithShadow(textRenderer, String.valueOf(e.getValue()), cx1 + cw1 - valW, by + 2, ModColors.TEXT_MEDIUM);
        }

        for (int i = 0; i < baitLastEntries.size(); i++) {
            int idx = baitTopReversed ? baitLastEntries.size() - 1 - i : i;
            int ri = i - baitTopScrollOffset;
            int by = listY + ri * TOP_ROW_H;
            if (by + TOP_ROW_H < listY || by > listY + listH) continue;
            var e   = baitLastEntries.get(idx);
            int col = baitColor(e.getKey());
            ctx.fill(cx2, by, cx2 + cw2, by + TOP_ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD);
            ctx.fill(cx2 + 1, by + 2, cx2 + 1 + dotS, by + 2 + dotS, col);
            int tx2  = cx2 + dotS + 4;
            int elaW = 56;
            ctx.drawTextWithShadow(textRenderer, e.getKey(), tx2, by + 2, ModColors.TEXT_WHITE);
            ctx.drawTextWithShadow(textRenderer, I18n.translate("fishlog.elapsed", formatElapsed(e.getValue())),
                cx2 + cw2 - elaW, by + 2, ModColors.TEXT_RECENCY);
        }

        ctx.disableScissor();

        int baitTopVis = listH / TOP_ROW_H;
        renderScrollbar(ctx, SB_BAIT_TOP_L, cx1 + cw1 + 1, listY, listH, baitTopScrollOffset, baitTypeEntries.size(), baitTopVis);
        renderScrollbar(ctx, SB_BAIT_TOP_R, cx2 + cw2 + 1, listY, listH, baitTopScrollOffset, baitLastEntries.size(), baitTopVis);

        int fy = listY + listH;
        ctx.fill(x, fy, x + w, fy + footerH, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        ctx.drawTextWithShadow(textRenderer,
            I18n.translate(baitTypeEntries.size() > 1 ? "fishlog.footer.bait_top.plural" : "fishlog.footer.bait_top", baitTypeEntries.size()),
            x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BAIT HOURLY – répartition horaire des appâts
    // ─────────────────────────────────────────────────────────────────────────
    private void renderBaitHourly(DrawContext ctx, int x, int y, int w, int h) {
        int usableH     = h - 12;
        int hourlyAreaH = usableH * 48 / 100;
        int sepY        = y + hourlyAreaH;

        ctx.drawCenteredTextWithShadow(textRenderer,
            I18n.translate("fishlog.hourly.label"), x + w / 2, y + 1, ModColors.TEXT_MUTED);

        int maxH = Arrays.stream(baitHourly).max().orElse(1);
        if (maxH == 0) maxH = 1;
        int plotH  = hourlyAreaH - 20;
        int plotW  = w - 20;
        int bw     = plotW / 24;
        int topOff = 10;
        for (int i = 0; i < 24; i++) {
            int bh  = baitHourly[i] * plotH / maxH;
            int bxi = x + 10 + i * bw;
            int byi = y + topOff + (plotH - bh);
            ctx.fill(bxi, byi, bxi + bw - 1, y + topOff + plotH, ModColors.CHART_HOURLY_BAIT);
            if (i % 4 == 0) {
                ctx.drawCenteredTextWithShadow(textRenderer,
                    String.valueOf(i), bxi + bw / 2, y + topOff + plotH + 2, ModColors.TEXT_MUTED);
            }
            if (baitHourly[i] > 0) {
                String hShort = formatShort(baitHourly[i]);
                drawFittedText(ctx, hShort, bxi + bw / 2, byi - 9, bw - 1, ModColors.TEXT_WHITE);
                int barBottom = y + topOff + plotH;
                if (lastMx >= bxi && lastMx < bxi + bw && lastMy >= byi && lastMy <= barBottom) {
                    pendingTooltip = List.of(Text.literal(I18n.translate("fishlog.bait.hourly.tooltip", baitHourly[i], i)));
                }
            }
        }

        ctx.fill(x, sepY, x + w, sepY + 1, ModColors.UI_DIVIDER);

        int dailyY = sepY + 2;
        int dailyH = usableH - hourlyAreaH - 3;

        ctx.drawCenteredTextWithShadow(textRenderer,
            I18n.translate("fishlog.bait.daily.title"), x + w / 2, dailyY + 1, ModColors.TEXT_MUTED);

        if (baitDailyCounts == null || baitDailyCounts.length < 2) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                I18n.translate("fishlog.empty.nodata"), x + w / 2, dailyY + dailyH / 2, ModColors.TEXT_WHITE);
            return;
        }

        int n    = baitDailyCounts.length;
        int maxV = Arrays.stream(baitDailyCounts).max().orElse(1);
        if (maxV == 0) maxV = 1;

        int plotX  = x + 28;
        int plotY2 = dailyY + 12;
        int plotW2 = w - 36;
        int plotH2 = dailyH - 24;
        if (plotH2 < 8) return;
        ctx.fill(plotX, plotY2, plotX + plotW2, plotY2 + plotH2, ModColors.PLOT_BG);

        for (int i = 0; i < n - 1; i++) {
            float t1 = (float) i / (n - 1);
            float t2 = (float) (i + 1) / (n - 1);
            float v1 = (float) baitDailyCounts[i] / maxV;
            float v2 = (float) baitDailyCounts[i + 1] / maxV;
            int x1 = plotX + (int)(plotW2 * t1);
            int x2 = plotX + (int)(plotW2 * t2);
            int y1 = plotY2 + plotH2 - (int)(plotH2 * v1);
            int y2 = plotY2 + plotH2 - (int)(plotH2 * v2);
            for (int px = x1; px <= x2 && px < plotX + plotW2; px++) {
                int yTop = px == x1 ? y1 : y1 + (y2 - y1) * (px - x1) / Math.max(1, x2 - x1);
                ctx.fill(px, yTop, px + 1, plotY2 + plotH2, ModColors.CHART_BAIT_FILL);
            }
            thickLine(plotX, plotY2, plotW2, plotH2, t1, v1, t2, v2, 2f, ModColors.CHART_BAIT_LINE);
        }

        for (int t = 0; t <= 4; t++) {
            int yt = plotY2 + plotH2 - t * plotH2 / 4;
            ctx.fill(plotX, yt, plotX + plotW2, yt + 1, ModColors.CHART_GRID);
            ctx.drawTextWithShadow(textRenderer,
                String.valueOf((int) Math.round(maxV * t / 4.0)), x, yt - 4, ModColors.TEXT_MUTED);
        }

        if (lastMx >= plotX && lastMx <= plotX + plotW2 && lastMy >= plotY2 && lastMy <= plotY2 + plotH2) {
            int hovI = Math.round((float)(lastMx - plotX) / plotW2 * (n - 1));
            hovI = Math.max(0, Math.min(n - 1, hovI));
            DateTimeFormatter fmtFull = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            pendingTooltip = List.of(Text.literal(I18n.translate("fishlog.bait.daily.tooltip", baitDailyCounts[hovI], baitDailyDates[hovI].format(fmtFull))));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BAIT CUMUL – courbe cumulée des appâts capturés
    // ─────────────────────────────────────────────────────────────────────────
    private void renderBaitCumul(DrawContext ctx, int x, int y, int w, int h) {
        if (baitCumTimes == null || baitCumTimes.length < 2) {
            ctx.drawCenteredTextWithShadow(textRenderer, I18n.translate("fishlog.empty.nodata"), x + w/2, y + h/2, ModColors.TEXT_WHITE);
            return;
        }
        double maxT = baitCumTimes[baitCumTimes.length - 1];
        double maxV = baitCumValues[baitCumValues.length - 1];
        if (maxT <= 0) maxT = 1;
        if (maxV <= 0) maxV = 1;

        int plotX = x + 30, plotY = y + 10, plotW = w - 40, plotH = h - 25;
        ctx.fill(plotX, plotY, plotX + plotW, plotY + plotH, ModColors.PLOT_BG);

        for (int i = 0; i < baitCumTimes.length - 1; i++) {
            int x1 = plotX + (int)(plotW * baitCumTimes[i]   / maxT);
            int x2 = plotX + (int)(plotW * baitCumTimes[i+1] / maxT);
            int y1 = plotY + plotH - (int)(plotH * baitCumValues[i]   / maxV);
            int y2 = plotY + plotH - (int)(plotH * baitCumValues[i+1] / maxV);
            for (int px = x1; px <= x2 && px < plotX + plotW; px++) {
                int yTop = px == x1 ? y1 : y1 + (y2 - y1) * (px - x1) / Math.max(1, x2 - x1);
                ctx.fill(px, yTop, px + 1, plotY + plotH, ModColors.CHART_BAIT_FILL);
            }
            thickLine(plotX, plotY, plotW, plotH,
                (float)(baitCumTimes[i]  /maxT), (float)(baitCumValues[i]  /maxV),
                (float)(baitCumTimes[i+1]/maxT), (float)(baitCumValues[i+1]/maxV),
                2f, ModColors.CHART_BAIT_LINE);
        }

        for (int t = 0; t <= 4; t++) {
            int yt = plotY + plotH - t * plotH / 4;
            ctx.fill(plotX, yt, plotX + plotW, yt + 1, ModColors.CHART_GRID);
            ctx.drawTextWithShadow(textRenderer, String.format("%.0f", maxV * t / 4), x, yt - 4, ModColors.TEXT_MUTED);
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
            I18n.translate(maxV > 1 ? "fishlog.bait.cumul.footer.plural" : "fishlog.bait.cumul.footer", maxV),
            plotX + plotW/2, plotY + plotH + 6, ModColors.TEXT_WHITE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BAIT RECORDS – historique des captures d'appât
    // ─────────────────────────────────────────────────────────────────────────
    private void renderBaitRecords(DrawContext ctx, int x, int y, int w, int h) {
        int sbW = 4;
        int dateW = 95, baitW = w - dateW - sbW - 4;
        int[] widths = {dateW, baitW};
        String[] hdrs = {I18n.translate("fishlog.col.date"), I18n.translate("fishlog.col.bait")};

        // Fond barre de recherche
        ctx.fill(x, y, x + w, y + SEARCH_H, ModColors.UI_SEARCH_BG);
        ctx.fill(x, y + SEARCH_H - 1, x + w, y + SEARCH_H, ModColors.UI_FOOTER_LINE);

        int footerH = 12, hdrH = 12;
        int hY = y + SEARCH_H;
        ctx.fill(x, hY, x + w, hY + hdrH, ModColors.UI_HEADER_BG);
        int cx = x;
        for (int i = 0; i < hdrs.length; i++) {
            ctx.drawTextWithShadow(textRenderer, hdrs[i], cx + 2, hY + 2, ModColors.TEXT_HEADER_COL);
            cx += widths[i];
        }
        ctx.fill(x, hY + hdrH - 1, x + w, hY + hdrH, ModColors.UI_HEADER_LINE);

        int listY = hY + hdrH;
        int listH = h - SEARCH_H - hdrH - footerH;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        int rowY = listY - baitScrollOffset * ROW_H;
        for (int i = 0; i < filteredBaitRecords.size(); i++) {
            if (rowY + ROW_H < listY || rowY > listY + listH) { rowY += ROW_H; continue; }
            BaitRecord r = filteredBaitRecords.get(i);
            ctx.fill(x, rowY, x + w, rowY + ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD_ALT);
            int col = baitColor(r.bait);
            cx = x;
            ctx.drawTextWithShadow(textRenderer, r.timestamp.format(DT_FMT), cx + 2, rowY + 1, ModColors.TEXT_LIGHT); cx += widths[0];
            ctx.drawTextWithShadow(textRenderer, r.bait,                     cx + 2, rowY + 1, col);
            rowY += ROW_H;
        }
        ctx.disableScissor();

        int baitRecVis = listH / ROW_H;
        renderScrollbar(ctx, SB_BAIT_REC, x + w - sbW, listY, listH, baitScrollOffset, filteredBaitRecords.size(), baitRecVis);

        int fy = y + h - footerH;
        ctx.fill(x, fy, x + w, y + h, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        String footerTxt = filteredBaitRecords.size() == allBaitRecords.size()
            ? I18n.translate(allBaitRecords.size() > 1 ? "fishlog.footer.records.plural" : "fishlog.footer.records", allBaitRecords.size())
            : I18n.translate("fishlog.footer.filtered", filteredBaitRecords.size(), allBaitRecords.size());
        ctx.drawTextWithShadow(textRenderer, footerTxt, x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }

    private static int baitColor(String bait) {
        Integer c = BAIT_COL.get(bait);
        if (c != null) return c;
        // Fallback normalisé : supprime accents + casse (ex: "Lisappât" → "lisappat")
        c = BAIT_COL_NORM.get(FishTextureCache.normalize(bait));
        if (c != null) return c;
        int h = Math.abs(bait.hashCode());
        int r = 128 + (h & 0x7F);
        int g = 128 + ((h >> 8) & 0x7F);
        int b = 128 + ((h >> 16) & 0x7F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Primitives de rendu
    // ═════════════════════════════════════════════════════════════════════════

    /** Dessine du texte centré en réduisant l'échelle si le texte est plus large que maxW. */
    private void drawFittedText(DrawContext ctx, String text, int cx, int y, int maxW, int color) {
        int textW = textRenderer.getWidth(text);
        if (textW <= maxW) {
            ctx.drawCenteredTextWithShadow(textRenderer, text, cx, y, color);
        } else {
            float scale = Math.max(0.5f, (float) maxW / textW);
            var ms = ctx.getMatrices();
            ms.push();
            ms.scale(scale, scale, 1f);
            ctx.drawCenteredTextWithShadow(textRenderer, text,
                Math.round(cx / scale), Math.round(y / scale), color);
            ms.pop();
        }
    }

    /** Dessine un segment épais entre deux points en coordonnées relatives [0,1] dans la zone plot. */
    private void thickLine(int plotX, int plotY, int plotW, int plotH,
                           float t1, float v1, float t2, float v2,
                           float thickness, int argb) {
        float x1 = plotX + t1 * plotW, y1 = plotY + plotH - v1 * plotH;
        float x2 = plotX + t2 * plotW, y2 = plotY + plotH - v2 * plotH;

        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len < 0.001f) return;
        float nx = -dy / len * (thickness / 2), ny = dx / len * (thickness / 2);

        var tess = Tessellator.getInstance();
        var buf  = tess.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        buf.vertex(x1 + nx, y1 + ny, 0f).color(argb);
        buf.vertex(x1 - nx, y1 - ny, 0f).color(argb);
        buf.vertex(x2 + nx, y2 + ny, 0f).color(argb);
        buf.vertex(x2 + nx, y2 + ny, 0f).color(argb);
        buf.vertex(x1 - nx, y1 - ny, 0f).color(argb);
        buf.vertex(x2 - nx, y2 - ny, 0f).color(argb);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    private void renderScrollbar(DrawContext ctx, int id, int x, int y, int trackH, int offset, int total, int visible) {
        sbX[id] = x; sbY[id] = y; sbTrackH[id] = trackH; sbTotal[id] = total; sbVis[id] = visible;
        if (total <= visible) return;
        ctx.fill(x, y, x + 3, y + trackH, ModColors.PLOT_BG);
        int thumbH = Math.max(6, trackH * visible / total);
        int maxOff = total - visible;
        int thumbY = y + (maxOff > 0 ? (trackH - thumbH) * offset / maxOff : 0);
        ctx.fill(x, thumbY, x + 3, thumbY + thumbH, sbDragId == id ? ModColors.SB_THUMB_DRAG : ModColors.SB_THUMB);
    }

    private static String formatShort(double val) {
        if (val >= 1_000_000) return String.format("%.1fM", val / 1_000_000);
        if (val >= 1_000)     return String.format("%.1fk", val / 1_000);
        return String.format("%.0f", val);
    }

    private void checkTooltip(int tx, int ty, String shortText, String fullText) {
        int tw = textRenderer.getWidth(shortText);
        if (lastMx >= tx && lastMx <= tx + tw && lastMy >= ty && lastMy <= ty + 9) {
            pendingTooltip = List.of(Text.literal(fullText));
        }
    }

    private void checkRectTooltip(int rx, int ry, int rw, int rh, List<Text> lines) {
        if (lastMx >= rx && lastMx <= rx + rw && lastMy >= ry && lastMy <= ry + rh) {
            pendingTooltip = lines;
        }
    }

    private static String formatElapsed(java.time.LocalDateTime last) {
        long sec = java.time.Duration.between(last, java.time.LocalDateTime.now()).getSeconds();
        if (sec < 0) sec = 0;
        if (sec < 60) return I18n.translate("fishlog.time.s", sec);
        long min = sec / 60;
        if (min < 60) return I18n.translate("fishlog.time.ms", min, sec % 60);
        long h = min / 60;
        if (h < 24) return I18n.translate("fishlog.time.hm", h, min % 60);
        long d = h / 24;
        return I18n.translate("fishlog.time.dh", d, h % 24);
    }

    private static double percentile(List<Double> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        double idx = p / 100.0 * (sorted.size() - 1);
        int lo = (int) idx;
        if (lo >= sorted.size() - 1) return sorted.get(sorted.size() - 1);
        return sorted.get(lo) + (idx - lo) * (sorted.get(lo + 1) - sorted.get(lo));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Interactions
    // ═════════════════════════════════════════════════════════════════════════

    private int getScrollOffset(int id) {
        return switch (id) {
            case SB_RECORDS                    -> scrollOffset;
            case SB_TOP_R                      -> topScrollOffset;
            case SB_BAIT_REC                   -> baitScrollOffset;
            case SB_BAIT_TOP_L, SB_BAIT_TOP_R -> baitTopScrollOffset;
            case SB_BAIT_TYPES                 -> baitTypesScrollOffset;
            default -> 0;
        };
    }

    private void setScrollOffset(int id, int val) {
        int clamped = Math.max(0, Math.min(Math.max(0, sbTotal[id] - sbVis[id]), val));
        switch (id) {
            case SB_RECORDS                    -> scrollOffset           = clamped;
            case SB_TOP_R                      -> topScrollOffset        = clamped;
            case SB_BAIT_REC                   -> baitScrollOffset       = clamped;
            case SB_BAIT_TOP_L, SB_BAIT_TOP_R -> baitTopScrollOffset    = clamped;
            case SB_BAIT_TYPES                 -> baitTypesScrollOffset  = clamped;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Scrollbars cliquables
        if (button == 0) {
            for (int id = 0; id < SB_COUNT; id++) {
                if (sbTotal[id] <= sbVis[id]) continue;
                int sx = sbX[id], sy = sbY[id], th = sbTrackH[id];
                if (mx >= sx && mx <= sx + 3 && my >= sy && my <= sy + th) {
                    int thumbH = Math.max(6, th * sbVis[id] / sbTotal[id]);
                    int maxOff = sbTotal[id] - sbVis[id];
                    int newOff = maxOff > 0 ? (int)((my - sy - thumbH / 2.0) * maxOff / (th - thumbH)) : 0;
                    setScrollOffset(id, newOff);
                    sbDragId = id;
                    sbDragStartY   = (int) my;
                    sbDragStartOff = getScrollOffset(id);
                    return true;
                }
            }
        }
        // Toggle Pêche / Appâts
        if (my >= baitToggleBtnY && my < baitToggleBtnY + BAIT_TOGGLE_H
                && mx >= baitToggleBtnX && mx < baitToggleBtnX + BAIT_TOGGLE_W) {
            baitMode = !baitMode;
            scrollOffset = 0; topScrollOffset = 0;
            baitScrollOffset = 0; baitTopScrollOffset = 0; rarityScrollOffset = 0; baitTypesScrollOffset = 0;
            if (searchField != null) { searchField.setText(""); updateFiltered(); }
            if (topSearchField != null) { topSearchField.setText(""); updateTopFiltered(); }
            if (baitMode && activeTab == Tab.SIZES) activeTab = Tab.RARITY;
            return true;
        }

        // Boutons reverse du tab TOP
        if (activeTab == Tab.TOP) {
            if (!baitMode
                    && mx >= topRevBtnX && mx < topRevBtnX + REV_BTN_W
                    && my >= topRevBtnY && my < topRevBtnY + REV_BTN_H) {
                topReversed = !topReversed;
                topScrollOffset = 0;
                return true;
            }
            if (!baitMode
                    && mx >= valModeBtnX && mx < valModeBtnX + VAL_MODE_BTN_W
                    && my >= valModeBtnY && my < valModeBtnY + VAL_MODE_BTN_H) {
                valueMode = switch (valueMode) { case MAX -> ValueMode.MEAN; case MEAN -> ValueMode.MIN; case MIN -> ValueMode.MAX; };
                topValue = computeTopValue();
                updateTopFiltered();
                topScrollOffset = 0;
                return true;
            }
            if (baitMode
                    && mx >= baitTopRevBtnX && mx < baitTopRevBtnX + REV_BTN_W
                    && my >= baitTopRevBtnY && my < baitTopRevBtnY + REV_BTN_H) {
                baitTopReversed = !baitTopReversed;
                baitTopScrollOffset = 0;
                return true;
            }
        }

        // Bouton LIN/LOG du tab SIZES (pêche uniquement)
        if (!baitMode && activeTab == Tab.SIZES
                && mx >= sizesLogBtnX && mx < sizesLogBtnX + LOG_BTN_W
                && my >= sizesLogBtnY && my < sizesLogBtnY + LOG_BTN_H) {
            sizesLogScale = !sizesLogScale;
            return true;
        }

        // Boutons favoris (onglet TOP)
        if (!baitMode && activeTab == Tab.TOP) {
            for (StarHit h : starHits) {
                if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                    FavoritesStore.INSTANCE.toggle(h.fish);
                    return true;
                }
            }
        }

        Tab[] tabs = Tab.values();
        int areaX  = 10;
        int tabsW  = (width - 20) - BAIT_TOGGLE_W - 2;
        int tw     = tabsW / tabs.length;
        int tabY   = 30;
        if (my >= tabY && my < tabY + 20) {
            int idx = (int)((mx - areaX) / tw);
            if (idx >= 0 && idx < tabs.length) {
                if (!(baitMode && tabs[idx] == Tab.SIZES)) {
                    activeTab = tabs[idx];
                    scrollOffset = 0; topScrollOffset = 0;
                    baitScrollOffset = 0; baitTopScrollOffset = 0; baitTypesScrollOffset = 0;
                    if (searchField != null) { searchField.setText(""); updateFiltered(); }
                    if (topSearchField != null) { topSearchField.setText(""); updateTopFiltered(); }
                    if (activeTab == Tab.RECORDS && searchField != null) setFocused(searchField);
                    if (activeTab == Tab.TOP && !baitMode && topSearchField != null) setFocused(topSearchField);
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && sbDragId >= 0) {
            int th     = sbTrackH[sbDragId];
            int thumbH = Math.max(6, th * sbVis[sbDragId] / sbTotal[sbDragId]);
            int maxOff = sbTotal[sbDragId] - sbVis[sbDragId];
            int range  = th - thumbH;
            if (range > 0) {
                int delta = (int)((my - sbDragStartY) * maxOff / (double) range);
                setScrollOffset(sbDragId, sbDragStartOff + delta);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && sbDragId >= 0) {
            sbDragId = -1;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        int delta = (int) vAmount;
        if (baitMode) {
            switch (activeTab) {
                case RECORDS -> setScrollOffset(SB_BAIT_REC,   baitScrollOffset     - delta);
                case TOP     -> setScrollOffset(SB_BAIT_TOP_R, baitTopScrollOffset  - delta);
                case RARITY  -> setScrollOffset(SB_BAIT_TYPES, baitTypesScrollOffset - delta);
                default -> { }
            }
        } else {
            switch (activeTab) {
                case RECORDS -> setScrollOffset(SB_RECORDS, scrollOffset    - delta);
                case TOP     -> setScrollOffset(SB_TOP_R,   topScrollOffset - delta);
                case RARITY  -> {
                    Set<String> favs = FavoritesStore.INSTANCE.snapshot();
                    int subEntries = (int) favs.stream().filter(f -> fishRarity.containsKey(f)).count();
                    int totalH = rarityEntries.size() * 21 + subEntries * 10;
                    int maxScroll = Math.max(0, totalH - rarityListH);
                    rarityScrollOffset = Math.max(0, Math.min(maxScroll, rarityScrollOffset - delta * 21));
                }
                default -> { }
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (searchField != null && searchField.isFocused() && !searchField.getText().isEmpty()) {
                searchField.setText("");
                return true;
            }
            if (topSearchField != null && topSearchField.isFocused() && !topSearchField.getText().isEmpty()) {
                topSearchField.setText("");
                return true;
            }
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

