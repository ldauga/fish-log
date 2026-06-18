package com.fishlog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class FishStatsScreen extends Screen {

    // ── Enums ─────────────────────────────────────────────────────────────────
    enum Tab { RARITY, TOP, SIZES, HOURLY, CUMUL, RECORDS, HALL_OF_FAME }
    enum ValueMode { MAX, MEAN, MIN }

    static class StarHit {
        final String fish; final int x, y, w, h;
        StarHit(String fish, int x, int y, int w, int h) { this.fish=fish; this.x=x; this.y=y; this.w=w; this.h=h; }
    }

    // ── Constantes UI ─────────────────────────────────────────────────────────
    static final int SEARCH_H = 12, ROW_H = 10, TOP_ROW_H = 12;
    static final int REV_BTN_W = 14, REV_BTN_H = 9, VAL_MODE_BTN_W = 30, VAL_MODE_BTN_H = 9;
    static final int LOG_BTN_W = 38, LOG_BTN_H = 11;
    static final int BAIT_TOGGLE_W = 48, BAIT_TOGGLE_H = 18;
    static final int HOF_COFFEE_SIZE = 14;
    static final int SB_RECORDS = 0, SB_TOP_R = 1, SB_BAIT_REC = 2;
    static final int SB_BAIT_TOP_L = 3, SB_BAIT_TOP_R = 4, SB_BAIT_TYPES = 5, SB_HOF = 6, SB_COUNT = 7;

    // ── Couleurs par rareté (package-private pour les tabs) ───────────────────
    static final Map<String, Integer> RARITY_COL = new LinkedHashMap<>();
    static final List<String>         RARITY_ORDER;
    static final Map<String, String>  EVENT_NAMES = new java.util.HashMap<>();
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
        EVENT_NAMES.put("", "badschool");
        EVENT_NAMES.put("", "badlloween");
        EVENT_NAMES.put("", "freliz");
        EVENT_NAMES.put("", "paques");
        EVENT_NAMES.put("", "love in the badlands");
        RARITY_ORDER = List.of("PERDU","COMMUN","RARE","ÉPIQUE","LÉGENDAIRE","MYTHIQUE","ABYSSAL","ÉMISSAIRE","ARTEFACT","","","","","");
    }

    // ── Données poissons ──────────────────────────────────────────────────────
    List<FishRecord>               records;
    List<Map.Entry<String,Integer>> rarityEntries;
    List<Map.Entry<String,Integer>> topCount;
    List<Map.Entry<String,Double>>  topValue;
    Map<String, String>            fishRarity;
    Map<String, Double>            fishPriceSum, fishPriceMax, fishPriceMin;
    Map<String, Integer>           fishCount;
    int[]                          hourly    = new int[24];
    java.time.LocalDate[]          dailyDates;
    int[]                          dailyCounts;
    double[]                       cumTimes, cumValues;
    java.time.LocalDateTime[]      cumDates;
    Map<String, List<Double>>             sizesByRarity;
    Map<String, List<FishRecord>>         fishRecordsByRarity;
    List<FishRecord>                      allRecords;
    Map<String, java.time.LocalDateTime>  lastByRarity, lastByFish;

    // ── Données appâts ────────────────────────────────────────────────────────
    List<Map.Entry<String,Integer>>                 baitTypeEntries;
    List<Map.Entry<String,java.time.LocalDateTime>> baitLastEntries;
    Map<String, java.time.LocalDateTime>            lastByBait;
    int[]                     baitHourly  = new int[24];
    java.time.LocalDate[]     baitDailyDates;
    int[]                     baitDailyCounts;
    double[]                  baitCumTimes, baitCumValues;
    java.time.LocalDateTime[] baitCumDates;
    List<BaitRecord>          allBaitRecords;

    // ── Filtres et scrolls ────────────────────────────────────────────────────
    List<FishRecord>              filteredRecords     = List.of();
    List<BaitRecord>              filteredBaitRecords = List.of();
    List<Map.Entry<String,Integer>> filteredTopCount  = List.of();
    List<Map.Entry<String,Double>>  filteredTopValue  = List.of();
    int scrollOffset, topScrollOffset, baitScrollOffset, baitTopScrollOffset;
    int rarityScrollOffset, baitTypesScrollOffset, hofScrollOffset, rarityListH;
    EditBox searchField, topSearchField;

    // ── State UI ──────────────────────────────────────────────────────────────
    Tab       activeTab  = Tab.RARITY;
    boolean   baitMode   = false;
    boolean   topReversed = false, baitTopReversed = false;
    ValueMode valueMode  = ValueMode.MEAN;
    boolean   sizesLogScale = false;
    int baitToggleBtnX, baitToggleBtnY;
    int topRevBtnX, topRevBtnY, baitTopRevBtnX, baitTopRevBtnY;
    int valModeBtnX, valModeBtnY, sizesLogBtnX, sizesLogBtnY;
    int brandX, brandY, brandW, hofCoffeeX, hofCoffeeY;
    int[] hofSlotCmdX = new int[6], hofSlotCmdY = new int[6];
    boolean[] hofSlotIsPlaceholder = new boolean[6];
    final List<StarHit> starHits = new ArrayList<>();
    int lastMx, lastMy;
    List<Component> pendingTooltip = null;

    // ── Scrollbar drag ────────────────────────────────────────────────────────
    final int[] sbX = new int[SB_COUNT], sbY = new int[SB_COUNT];
    final int[] sbTrackH = new int[SB_COUNT], sbTotal = new int[SB_COUNT], sbVis = new int[SB_COUNT];
    int sbDragId = -1, sbDragStartY, sbDragStartOff;

    // ══════════════════════════════════════════════════════════════════════════
    public FishStatsScreen() { super(Component.literal("Fish Stats")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {}

    net.minecraft.client.gui.Font getFont() { return this.font; }

    @Override
    protected void init() {
        FishTextureCache.invalidate();
        FishStatsDataLoader.loadFish(this);
        FishStatsDataLoader.loadBait(this);
        scrollOffset = rarityScrollOffset = baitScrollOffset = baitTopScrollOffset = baitTypesScrollOffset = 0;

        int cX = 15, cY = 52, cW = width - 30;
        searchField = new EditBox(font, cX + 2, cY + 2, cW - 7, 9, Component.empty());
        searchField.setMaxLength(100);
        searchField.setResponder(t -> { scrollOffset = baitScrollOffset = 0; updateFiltered(); });
        searchField.setVisible(false);
        addRenderableWidget(searchField);
        updateFiltered();
        if (activeTab == Tab.RECORDS) setFocused(searchField);

        topSearchField = new EditBox(font, cX + 2, cY + 2, cW - 7, 9, Component.empty());
        topSearchField.setMaxLength(100);
        topSearchField.setResponder(t -> { topScrollOffset = 0; updateTopFiltered(); });
        topSearchField.setVisible(false);
        addRenderableWidget(topSearchField);
        updateTopFiltered();
        if (activeTab == Tab.TOP && !baitMode) setFocused(topSearchField);
    }

    List<Map.Entry<String,Double>> computeTopValue() {
        Map<String, Double> vals = new LinkedHashMap<>();
        for (String fish : fishPriceSum.keySet()) {
            vals.put(fish, switch (valueMode) {
                case MAX  -> fishPriceMax.get(fish);
                case MIN  -> fishPriceMin.get(fish);
                case MEAN -> fishPriceSum.get(fish) / fishCount.getOrDefault(fish, 1);
            });
        }
        return vals.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());
    }

    void updateFiltered() {
        String q = searchField != null ? stripAccents(searchField.getValue().trim()) : "";
        if (q.isEmpty()) { filteredRecords = allRecords; filteredBaitRecords = allBaitRecords; return; }
        filteredRecords = allRecords.stream()
            .filter(r -> stripAccents(r.fish).contains(q) || stripAccents(rarityForSearch(r.rarity)).contains(q))
            .collect(Collectors.toList());
        filteredBaitRecords = allBaitRecords.stream()
            .filter(r -> stripAccents(r.bait).contains(q))
            .collect(Collectors.toList());
    }

    void updateTopFiltered() {
        String q = topSearchField != null ? stripAccents(topSearchField.getValue().trim()) : "";
        if (q.isEmpty()) { filteredTopCount = topCount; filteredTopValue = topValue; return; }
        filteredTopCount = topCount.stream()
            .filter(e -> stripAccents(e.getKey()).contains(q) || stripAccents(rarityForSearch(fishRarity.getOrDefault(e.getKey(),""))).contains(q))
            .collect(Collectors.toList());
        filteredTopValue = topValue.stream()
            .filter(e -> stripAccents(e.getKey()).contains(q) || stripAccents(rarityForSearch(fishRarity.getOrDefault(e.getKey(),""))).contains(q))
            .collect(Collectors.toList());
    }

    static String rarityForSearch(String rarity) {
        if (rarity == null || rarity.isEmpty()) return "cosmique";
        String ev = EVENT_NAMES.get(rarity);
        if (ev != null) return "event " + ev;
        if (rarity.chars().noneMatch(Character::isLetterOrDigit)) return "abyssale";
        return rarity.toLowerCase();
    }

    static String stripAccents(String s) {
        if (s == null) return "";
        s = s.toLowerCase()
            .replace("é","e").replace("è","e").replace("ê","e").replace("ë","e")
            .replace("à","a").replace("â","a").replace("á","a")
            .replace("ô","o").replace("ö","o").replace("ó","o")
            .replace("î","i").replace("ï","i")
            .replace("ù","u").replace("û","u").replace("ü","u")
            .replace("ç","c").replace("œ","oe").replace("æ","ae");
        return s;
    }

    static String rarityDisplay(String rarity) {
        if (EVENT_NAMES.containsKey(rarity)) return "EVENT";
        if ("LÉGENDAIRE".equals(rarity)) return "LEGEND.";
        return rarity;
    }

    // ═════════════════════════════════════════════════════════════════════════
    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        lastMx = mx; lastMy = my;
        pendingTooltip = null;
        java.util.Arrays.fill(sbTotal, 0);
        renderBackground(ctx, mx, my, delta);

        int W = width, H = height, padL = 10, padT = 30, padR = 10, padB = 10;
        int areaX = padL, areaY = padT, areaW = W - padL - padR, areaH = H - padT - padB;
        ctx.fill(areaX, areaY, areaX + areaW, areaY + areaH, ModColors.UI_PANEL_BG);
        renderTabs(ctx, areaX, areaY, areaW);

        int cX = areaX + 5, cY = areaY + 22, cW = areaW - 10, cH = areaH - 27;

        if (activeTab == Tab.HALL_OF_FAME) {
            FishStatsHallOfFame.render(this, ctx, cX, cY, cW, cH);
        } else if (baitMode) {
            if (allBaitRecords.isEmpty()) { ctx.drawCenteredString(font, I18n.get("fishlog.empty.bait"), cX + cW/2, cY + cH/2, ModColors.TEXT_WHITE); return; }
            switch (activeTab) {
                case RARITY  -> FishStatsTabRecords.renderBaitTypes(this, ctx, cX, cY, cW, cH);
                case TOP     -> FishStatsTabRecords.renderBaitTop(this, ctx, cX, cY, cW, cH);
                case HOURLY  -> FishStatsTabHourly.renderBait(this, ctx, cX, cY, cW, cH);
                case CUMUL   -> FishStatsTabCumul.renderBait(this, ctx, cX, cY, cW, cH);
                case RECORDS -> FishStatsTabRecords.renderBaitRecords(this, ctx, cX, cY, cW, cH);
                case SIZES   -> ctx.drawCenteredString(font, I18n.get("fishlog.empty.na_bait"), cX+cW/2, cY+cH/2, ModColors.RARITY_UNKNOWN);
                default      -> {}
            }
        } else {
            if (records.isEmpty()) { ctx.drawCenteredString(font, I18n.get("fishlog.empty.fish"), cX + cW/2, cY + cH/2, ModColors.TEXT_WHITE); return; }
            switch (activeTab) {
                case RARITY  -> FishStatsTabRarity.render(this, ctx, cX, cY, cW, cH);
                case TOP     -> FishStatsTabTop.render(this, ctx, cX, cY, cW, cH);
                case SIZES   -> FishStatsTabSizes.render(this, ctx, cX, cY, cW, cH);
                case HOURLY  -> FishStatsTabHourly.renderFish(this, ctx, cX, cY, cW, cH);
                case CUMUL   -> FishStatsTabCumul.renderFish(this, ctx, cX, cY, cW, cH);
                case RECORDS -> FishStatsTabRecords.renderFish(this, ctx, cX, cY, cW, cH);
                default      -> {}
            }
        }

        boolean isRecordsTab = activeTab == Tab.RECORDS;
        if (searchField != null) { searchField.setVisible(isRecordsTab); searchField.setEditable(isRecordsTab); }
        boolean isTopFishTab = activeTab == Tab.TOP && !baitMode;
        if (topSearchField != null) { topSearchField.setVisible(isTopFishTab); topSearchField.setEditable(isTopFishTab); }

        // ── Bandeau global ────────────────────────────────────────────────────
        int footerH = 12, fy = cY + cH - footerH;
        boolean hasOwnFooter = activeTab == Tab.RARITY || activeTab == Tab.TOP || activeTab == Tab.RECORDS || activeTab == Tab.HALL_OF_FAME;
        if (!hasOwnFooter) { ctx.fill(cX, fy, cX+cW, cY+cH, ModColors.UI_FOOTER_BG); ctx.fill(cX, fy, cX+cW, fy+1, ModColors.UI_FOOTER_LINE); }
        String brand = "powered by @LeLeoOriginel";
        brandW = font.width(brand); brandX = cX + cW - brandW - 3; brandY = fy + 2;
        long now2 = System.currentTimeMillis();
        int charX = brandX;
        for (int ci = 0; ci < brand.length(); ci++) {
            String ch = String.valueOf(brand.charAt(ci));
            float hue = ((now2/40 + ci*18) % 360) / 360f;
            float bri = 0.65f + 0.35f * (float) Math.sin(now2/250.0 + ci*0.4);
            ctx.drawString(font, ch, charX, brandY, FishStatsUtils.hueToRgb(hue, bri));
            charX += font.width(ch);
        }
        if (lastMx >= brandX && lastMx < brandX+brandW && lastMy >= brandY && lastMy < brandY+9)
            pendingTooltip = List.of(Component.literal("Buy me a coffee !"));

        if (pendingTooltip != null) ctx.renderComponentTooltip(font, pendingTooltip, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void renderTabs(GuiGraphics ctx, int x, int y, int w) {
        baitToggleBtnX = x + w - BAIT_TOGGLE_W - 1; baitToggleBtnY = y + 1;
        ctx.fill(baitToggleBtnX, baitToggleBtnY, baitToggleBtnX+BAIT_TOGGLE_W, baitToggleBtnY+BAIT_TOGGLE_H, baitMode ? ModColors.TOGGLE_BAIT_BG : ModColors.TOGGLE_FISH_BG);
        ctx.drawCenteredString(font, baitMode ? I18n.get("fishlog.toggle.bait") : I18n.get("fishlog.toggle.fish"), baitToggleBtnX+BAIT_TOGGLE_W/2, baitToggleBtnY+5, baitMode ? ModColors.TEXT_PRICE : ModColors.BTN_TEXT_NORMAL);
        Tab[] tabs = Arrays.stream(Tab.values()).filter(t -> t != Tab.HALL_OF_FAME).toArray(Tab[]::new);
        String[] lbl = baitMode
            ? new String[]{I18n.get("fishlog.tab.types"),I18n.get("fishlog.tab.top"),"—",I18n.get("fishlog.tab.hourly"),I18n.get("fishlog.tab.cumul"),I18n.get("fishlog.tab.records")}
            : new String[]{I18n.get("fishlog.tab.rarity"),I18n.get("fishlog.tab.top"),I18n.get("fishlog.tab.sizes"),I18n.get("fishlog.tab.hourly"),I18n.get("fishlog.tab.cumul"),I18n.get("fishlog.tab.records")};
        int tabsW = w - BAIT_TOGGLE_W - 2, tw = tabsW / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tx = x + i * tw;
            boolean active = tabs[i] == activeTab, disabled = baitMode && tabs[i] == Tab.SIZES;
            ctx.fill(tx, y, tx+tw, y+20, disabled ? ModColors.TAB_DISABLED : active ? ModColors.TAB_ACTIVE : ModColors.TAB_INACTIVE);
            ctx.drawCenteredString(font, lbl[i], tx+tw/2, y+6, disabled ? ModColors.TAB_TEXT_DISABLED : active ? ModColors.TEXT_WHITE : ModColors.TEXT_MUTED);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= brandX && mx < brandX+brandW && my >= brandY && my < brandY+9) {
            minecraft.setScreen(new ChatScreen("/pay LeLeoOriginel 1000")); return true;
        }
        if (button == 0 && activeTab == Tab.HALL_OF_FAME && mx >= hofCoffeeX && mx < hofCoffeeX+HOF_COFFEE_SIZE && my >= hofCoffeeY && my < hofCoffeeY+HOF_COFFEE_SIZE) {
            minecraft.setScreen(new ChatScreen("/pay LeLeoOriginel 1000")); return true;
        }
        if (button == 0 && activeTab == Tab.HALL_OF_FAME) {
            for (int i = 0; i < 6; i++) {
                if (hofSlotIsPlaceholder[i] && mx >= hofSlotCmdX[i] && mx < hofSlotCmdX[i]+HOF_COFFEE_SIZE && my >= hofSlotCmdY[i] && my < hofSlotCmdY[i]+HOF_COFFEE_SIZE) {
                    minecraft.setScreen(new ChatScreen("/and choose a command")); return true;
                }
            }
        }
        if (button == 0) {
            for (int id = 0; id < SB_COUNT; id++) {
                if (sbTotal[id] <= sbVis[id]) continue;
                int sx = sbX[id], sy = sbY[id], th = sbTrackH[id];
                if (mx >= sx && mx <= sx+3 && my >= sy && my <= sy+th) {
                    int thumbH = Math.max(6, th*sbVis[id]/sbTotal[id]);
                    int maxOff = sbTotal[id]-sbVis[id];
                    setScrollOffset(id, maxOff > 0 ? (int)((my-sy-thumbH/2.0)*maxOff/(th-thumbH)) : 0);
                    sbDragId = id; sbDragStartY = (int)my; sbDragStartOff = getScrollOffset(id); return true;
                }
            }
        }
        if (my >= baitToggleBtnY && my < baitToggleBtnY+BAIT_TOGGLE_H && mx >= baitToggleBtnX && mx < baitToggleBtnX+BAIT_TOGGLE_W) {
            baitMode = !baitMode;
            scrollOffset = topScrollOffset = baitScrollOffset = baitTopScrollOffset = rarityScrollOffset = baitTypesScrollOffset = 0;
            if (searchField != null) { searchField.setValue(""); updateFiltered(); }
            if (topSearchField != null) { topSearchField.setValue(""); updateTopFiltered(); }
            if (baitMode && activeTab == Tab.SIZES) activeTab = Tab.RARITY;
            return true;
        }
        if (activeTab == Tab.TOP && !baitMode) {
            if (mx >= topRevBtnX && mx < topRevBtnX+REV_BTN_W && my >= topRevBtnY && my < topRevBtnY+REV_BTN_H) { topReversed = !topReversed; topScrollOffset = 0; return true; }
            if (mx >= valModeBtnX && mx < valModeBtnX+VAL_MODE_BTN_W && my >= valModeBtnY && my < valModeBtnY+VAL_MODE_BTN_H) {
                valueMode = switch (valueMode) { case MAX -> ValueMode.MEAN; case MEAN -> ValueMode.MIN; case MIN -> ValueMode.MAX; };
                topValue = computeTopValue(); updateTopFiltered(); topScrollOffset = 0; return true;
            }
        }
        if (activeTab == Tab.TOP && baitMode && mx >= baitTopRevBtnX && mx < baitTopRevBtnX+REV_BTN_W && my >= baitTopRevBtnY && my < baitTopRevBtnY+REV_BTN_H) { baitTopReversed = !baitTopReversed; baitTopScrollOffset = 0; return true; }
        if (!baitMode && activeTab == Tab.SIZES && mx >= sizesLogBtnX && mx < sizesLogBtnX+LOG_BTN_W && my >= sizesLogBtnY && my < sizesLogBtnY+LOG_BTN_H) { sizesLogScale = !sizesLogScale; return true; }
        if (!baitMode && activeTab == Tab.TOP) { for (StarHit h : starHits) { if (mx >= h.x && mx < h.x+h.w && my >= h.y && my < h.y+h.h) { FavoritesStore.INSTANCE.toggle(h.fish); return true; } } }
        Tab[] tabs = Arrays.stream(Tab.values()).filter(t -> t != Tab.HALL_OF_FAME).toArray(Tab[]::new);
        int tabsW = (width-20)-BAIT_TOGGLE_W-2, tw = tabsW/tabs.length;
        if (my >= 30 && my < 50) {
            int idx = (int)((mx-10)/tw);
            if (idx >= 0 && idx < tabs.length && !(baitMode && tabs[idx] == Tab.SIZES)) {
                activeTab = tabs[idx];
                scrollOffset = topScrollOffset = baitScrollOffset = baitTopScrollOffset = baitTypesScrollOffset = hofScrollOffset = 0;
                if (searchField != null) { searchField.setValue(""); updateFiltered(); }
                if (topSearchField != null) { topSearchField.setValue(""); updateTopFiltered(); }
                if (activeTab == Tab.RECORDS && searchField != null) setFocused(searchField);
                if (activeTab == Tab.TOP && !baitMode && topSearchField != null) setFocused(topSearchField);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && sbDragId >= 0) {
            int th = sbTrackH[sbDragId], thumbH = Math.max(6, th*sbVis[sbDragId]/sbTotal[sbDragId]);
            int maxOff = sbTotal[sbDragId]-sbVis[sbDragId], range = th-thumbH;
            if (range > 0) setScrollOffset(sbDragId, sbDragStartOff + (int)((my-sbDragStartY)*maxOff/(double)range));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && sbDragId >= 0) { sbDragId = -1; return true; }
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
                default -> {}
            }
        } else {
            switch (activeTab) {
                case RECORDS -> setScrollOffset(SB_RECORDS, scrollOffset    - delta);
                case TOP     -> setScrollOffset(SB_TOP_R,   topScrollOffset - delta);
                case HALL_OF_FAME -> setScrollOffset(SB_HOF, hofScrollOffset - delta);
                case RARITY -> {
                    Set<String> favs = FavoritesStore.INSTANCE.snapshot();
                    int subEntries = (int) favs.stream().filter(f -> fishRarity.containsKey(f)).count();
                    int totalH = rarityEntries.size() * 21 + subEntries * 10;
                    rarityScrollOffset = Math.max(0, Math.min(Math.max(0, totalH - rarityListH), rarityScrollOffset - delta * 21));
                }
                default -> {}
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (searchField != null && searchField.isFocused() && !searchField.getValue().isEmpty()) { searchField.setValue(""); return true; }
            if (topSearchField != null && topSearchField.isFocused() && !topSearchField.getValue().isEmpty()) { topSearchField.setValue(""); return true; }
            this.onClose(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    int getScrollOffset(int id) {
        return switch (id) {
            case SB_RECORDS                    -> scrollOffset;
            case SB_TOP_R                      -> topScrollOffset;
            case SB_BAIT_REC                   -> baitScrollOffset;
            case SB_BAIT_TOP_L, SB_BAIT_TOP_R -> baitTopScrollOffset;
            case SB_BAIT_TYPES                 -> baitTypesScrollOffset;
            case SB_HOF                        -> hofScrollOffset;
            default -> 0;
        };
    }

    void setScrollOffset(int id, int val) {
        int clamped = Math.max(0, Math.min(Math.max(0, sbTotal[id] - sbVis[id]), val));
        switch (id) {
            case SB_RECORDS                    -> scrollOffset          = clamped;
            case SB_TOP_R                      -> topScrollOffset       = clamped;
            case SB_BAIT_REC                   -> baitScrollOffset      = clamped;
            case SB_BAIT_TOP_L, SB_BAIT_TOP_R -> baitTopScrollOffset   = clamped;
            case SB_BAIT_TYPES                 -> baitTypesScrollOffset = clamped;
            case SB_HOF                        -> hofScrollOffset       = clamped;
        }
    }
}
