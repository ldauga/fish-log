package com.fishlog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.Map;
import java.util.Set;

class FishStatsTabRarity {

    static void render(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        if (s.rarityEntries.isEmpty()) return;
        int total  = s.rarityEntries.stream().mapToInt(Map.Entry::getValue).sum();
        int maxCnt = s.rarityEntries.stream().mapToInt(Map.Entry::getValue).max().orElse(1);

        // ── Bande cumulée ────────────────────────────────────────────────────
        int bandH = 14, bx = x + 2, bandW = w - 4;
        ctx.fill(bx, y + 2, bx + bandW, y + 2 + bandH, ModColors.CHART_BAND_BG);
        int cumX = bx;
        for (Map.Entry<String,Integer> e : s.rarityEntries) {
            int col  = FishStatsScreen.RARITY_COL.getOrDefault(e.getKey(), ModColors.RARITY_UNKNOWN);
            int segW = bandW * e.getValue() / total;
            ctx.fill(cumX, y + 2, cumX + segW, y + 2 + bandH, col);
            cumX += segW;
        }

        // ── En-tête ──────────────────────────────────────────────────────────
        int hdrY = y + 2 + bandH + 2, hdrH = 12;
        ctx.fill(x, hdrY, x + w, hdrY + hdrH, ModColors.UI_HEADER_BG);
        ctx.fill(x, hdrY + hdrH - 1, x + w, hdrY + hdrH, ModColors.UI_HEADER_LINE);
        int barAreaX = x + 2, barAreaW = w - 4;
        int labelW   = 80, barW = barAreaW - labelW - 72;
        ctx.drawString(s.getFont(), I18n.get("fishlog.col.rarity"),       barAreaX,                     hdrY + 2, ModColors.TEXT_HEADER_COL);
        ctx.drawString(s.getFont(), I18n.get("fishlog.col.distribution"), barAreaX + labelW,            hdrY + 2, ModColors.TEXT_HEADER_COL);
        ctx.drawString(s.getFont(), "%",                                   barAreaX + labelW + barW + 4, hdrY + 2, ModColors.TEXT_HEADER_COL);

        // ── Liste défilable ──────────────────────────────────────────────────
        int footerH = 12, listY = hdrY + hdrH;
        int listH   = h - (listY - y) - footerH;
        int rowH    = 21;
        s.rarityListH = listH;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        int ry = listY - s.rarityScrollOffset;
        Set<String> rarFavSnap = FavoritesStore.INSTANCE.snapshot();
        for (Map.Entry<String,Integer> e : s.rarityEntries) {
            if (ry + rowH >= listY && ry <= listY + listH) {
                int col  = FishStatsScreen.RARITY_COL.getOrDefault(e.getKey(), ModColors.RARITY_UNKNOWN);
                float pct = 100f * e.getValue() / total;
                int fill  = barW * e.getValue() / maxCnt;

                int nameCol = FishStatsScreen.EVENT_NAMES.containsKey(e.getKey()) ? 0xFFFFFF : col;
                ctx.drawString(s.getFont(), e.getKey(), barAreaX, ry + 1, nameCol);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + barW, ry + 9, ModColors.PLOT_BG);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + fill, ry + 9, col & 0x99FFFFFF | 0x99000000);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + fill, ry + 1, col);
                ctx.drawString(s.getFont(), String.format("%.2f%%", pct), barAreaX + labelW + barW + 4, ry + 1, ModColors.TEXT_LIGHT);

                int cnt = e.getValue();
                String countStr = I18n.get(cnt > 1 ? "fishlog.catch.plural" : "fishlog.catch", cnt);
                java.time.LocalDateTime last = s.lastByRarity.get(e.getKey());
                String secondLine = last != null
                    ? countStr + "  —  " + I18n.get("fishlog.elapsed", FishStatsUtils.formatElapsed(last))
                    : countStr;
                ctx.drawString(s.getFont(), secondLine, barAreaX, ry + 11, ModColors.TEXT_VERY_MUTED);
            }
            ry += rowH;

            for (String fish : rarFavSnap) {
                if (!e.getKey().equals(s.fishRarity.getOrDefault(fish, ""))) continue;
                if (ry + 9 >= listY && ry <= listY + listH) {
                    int fc   = s.fishCount.getOrDefault(fish, 0);
                    float fpct = s.records.size() > 0 ? 100f * fc / s.records.size() : 0f;
                    java.time.LocalDateTime lastF = s.lastByFish != null ? s.lastByFish.get(fish) : null;
                    String fcStr = I18n.get(fc > 1 ? "fishlog.catch.plural" : "fishlog.catch", fc);
                    String elap  = lastF != null ? "  " + I18n.get("fishlog.elapsed", FishStatsUtils.formatElapsed(lastF)) : "";
                    ctx.drawString(s.getFont(),
                        "★ " + fish + "   " + fcStr + String.format("   %.1f%%", fpct) + elap,
                        barAreaX + 8, ry + 1, 0xFFFFDD00);
                }
                ry += 10;
            }
        }
        ctx.disableScissor();

        // ── Footer ───────────────────────────────────────────────────────────
        int fy = y + h - footerH;
        ctx.fill(x, fy, x + w, y + h, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        ctx.drawString(s.getFont(),
            I18n.get(total > 1 ? "fishlog.footer.rarity.plural" : "fishlog.footer.rarity", total),
            x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }
}
