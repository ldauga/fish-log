package com.fishlog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;

import java.time.format.DateTimeFormatter;
import java.util.Map;

class FishStatsTabRecords {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ─────────────────────────────────────────────────────────────────────────
    //  RECORDS – table défilable
    // ─────────────────────────────────────────────────────────────────────────
    static void renderFish(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        int sbW = 4;
        int dateW = 95, rarW = 62, sizeW = 54, priceW = 38;
        int fishW = w - dateW - rarW - sizeW - priceW - sbW - 4;
        int[] widths = {dateW, rarW, fishW, sizeW, priceW};
        String[] hdrs = {I18n.get("fishlog.col.date"), I18n.get("fishlog.col.rarity"),
                         I18n.get("fishlog.col.fish"), I18n.get("fishlog.col.size"), I18n.get("fishlog.col.price")};

        ctx.fill(x, y, x + w, y + FishStatsScreen.SEARCH_H, ModColors.UI_SEARCH_BG);
        ctx.fill(x, y + FishStatsScreen.SEARCH_H - 1, x + w, y + FishStatsScreen.SEARCH_H, ModColors.UI_FOOTER_LINE);

        int footerH = 12, hdrH = 12, hY = y + FishStatsScreen.SEARCH_H;
        ctx.fill(x, hY, x + w, hY + hdrH, ModColors.UI_HEADER_BG);
        int cx = x;
        for (int i = 0; i < hdrs.length; i++) {
            ctx.drawString(s.getFont(), hdrs[i], cx + 2, hY + 2, ModColors.TEXT_HEADER_COL);
            cx += widths[i];
        }
        ctx.fill(x, hY + hdrH - 1, x + w, hY + hdrH, ModColors.UI_HEADER_LINE);

        int listY = hY + hdrH, listH = h - FishStatsScreen.SEARCH_H - hdrH - footerH;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        int rowY = listY - s.scrollOffset * FishStatsScreen.ROW_H;
        for (int i = 0; i < s.filteredRecords.size(); i++) {
            if (rowY + FishStatsScreen.ROW_H < listY || rowY > listY + listH) { rowY += FishStatsScreen.ROW_H; continue; }
            FishRecord r = s.filteredRecords.get(i);
            ctx.fill(x, rowY, x + w, rowY + FishStatsScreen.ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD_ALT);
            int rCol = FishStatsScreen.RARITY_COL.getOrDefault(r.rarity, ModColors.TEXT_MUTED_ARGB);
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate rDate = r.timestamp.toLocalDate();
            int dateCol = rDate.equals(today) ? 0xFF55FF55 : rDate.equals(today.minusDays(1)) ? 0xFFFFAA00 : ModColors.TEXT_MUTED_ARGB;
            cx = x;
            ctx.drawString(s.getFont(), r.timestamp.format(DT_FMT), cx + 2, rowY + 1, dateCol); cx += widths[0];
            ctx.drawString(s.getFont(), FishStatsScreen.rarityDisplay(r.rarity), cx + 2, rowY + 1, rCol);
            cx += widths[1];
            ItemStack fishIcon = FishTextureCache.getItemStack(r.fish, r.rarity);
            if (fishIcon != null) FishStatsUtils.renderScaledItem(ctx, fishIcon, cx + 1, rowY + 1, 8);
            ctx.drawString(s.getFont(), r.fish, cx + (fishIcon != null ? 10 : 2), rowY + 1, ModColors.TEXT_WHITE); cx += widths[2];
            ctx.drawString(s.getFont(), String.format("%.1fcm", r.sizeCm), cx + 2, rowY + 1, ModColors.TEXT_MEDIUM); cx += widths[3];
            ctx.drawString(s.getFont(), String.format("%.0f$", r.price), cx + 2, rowY + 1, ModColors.TEXT_PRICE);
            rowY += FishStatsScreen.ROW_H;
        }
        ctx.disableScissor();

        FishStatsUtils.renderScrollbar(s, ctx, FishStatsScreen.SB_RECORDS, x + w - sbW, listY, listH, s.scrollOffset, s.filteredRecords.size(), listH / FishStatsScreen.ROW_H);

        int fy = y + h - footerH;
        ctx.fill(x, fy, x + w, y + h, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        String footerTxt = s.filteredRecords.size() == s.allRecords.size()
            ? I18n.get(s.allRecords.size() > 1 ? "fishlog.footer.records.plural" : "fishlog.footer.records", s.allRecords.size())
            : I18n.get("fishlog.footer.filtered", s.filteredRecords.size(), s.allRecords.size());
        ctx.drawString(s.getFont(), footerTxt, x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BAIT TYPES – distribution par type d'appât
    // ─────────────────────────────────────────────────────────────────────────
    static void renderBaitTypes(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        if (s.baitTypeEntries.isEmpty()) return;
        int total  = s.baitTypeEntries.stream().mapToInt(Map.Entry::getValue).sum();
        int maxCnt = s.baitTypeEntries.get(0).getValue();

        int bandH = 14, bx = x + 2, bandW = w - 4;
        ctx.fill(bx, y + 2, bx + bandW, y + 2 + bandH, ModColors.CHART_BAND_BG);
        int cumX = bx;
        for (var e : s.baitTypeEntries) {
            int segW = bandW * e.getValue() / total;
            ctx.fill(cumX, y + 2, cumX + segW, y + 2 + bandH, FishStatsUtils.baitColor(e.getKey()));
            cumX += segW;
        }

        int sbW = 4, barAreaX = x + 2, barAreaW = w - 4;
        int labelW = 90, barW = barAreaW - labelW - 55 - sbW;
        int rowH = 21, footerH = 12;
        int listY = y + 2 + bandH + 2, listH = h - (listY - y) - footerH;

        ctx.enableScissor(x, listY, x + w, listY + listH);
        int ry = listY - s.baitTypesScrollOffset * rowH;
        for (var e : s.baitTypeEntries) {
            if (ry + rowH >= listY && ry <= listY + listH) {
                int col   = FishStatsUtils.baitColor(e.getKey());
                float pct = 100f * e.getValue() / total;
                int fill  = barW * e.getValue() / maxCnt;
                ctx.drawString(s.getFont(), e.getKey(), barAreaX, ry + 1, col);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + barW, ry + 9, ModColors.PLOT_BG);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + fill, ry + 9, col & 0x99FFFFFF | 0x99000000);
                ctx.fill(barAreaX + labelW, ry, barAreaX + labelW + fill, ry + 1, col);
                ctx.drawString(s.getFont(), String.format("%d  (%.1f%%)", e.getValue(), pct), barAreaX + labelW + barW + 4, ry + 1, ModColors.TEXT_LIGHT);
                java.time.LocalDateTime last = s.lastByBait.get(e.getKey());
                if (last != null)
                    ctx.drawString(s.getFont(), I18n.get("fishlog.elapsed", FishStatsUtils.formatElapsed(last)), barAreaX, ry + 11, ModColors.TEXT_VERY_MUTED);
            }
            ry += rowH;
        }
        ctx.disableScissor();

        FishStatsUtils.renderScrollbar(s, ctx, FishStatsScreen.SB_BAIT_TYPES, x + w - sbW, listY, listH, s.baitTypesScrollOffset, s.baitTypeEntries.size(), listH / rowH);

        int fy = y + h - footerH;
        ctx.fill(x, fy, x + w, y + h, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        ctx.drawString(s.getFont(), I18n.get(total > 1 ? "fishlog.bait.total.plural" : "fishlog.bait.total", total), x + 2, fy + 2, ModColors.TEXT_BAIT_FOOTER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BAIT TOP – par quantité (gauche) et par récence (droite)
    // ─────────────────────────────────────────────────────────────────────────
    static void renderBaitTop(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        int footerH = 11, hdrH = 11, listH = h - hdrH - footerH, sbW = 4;
        int half = w / 2;
        int cx1 = x + 1, cw1 = half - 2 - sbW;
        int cx2 = x + half + 1, cw2 = w - half - 2 - sbW;
        int dotS = 6, maxCnt = s.baitTypeEntries.isEmpty() ? 1 : s.baitTypeEntries.get(0).getValue();

        ctx.fill(x, y, x + w, y + hdrH, ModColors.UI_HEADER_BG);
        ctx.fill(x + half, y, x + half + 1, y + h, ModColors.UI_FOOTER_LINE);
        ctx.drawCenteredString(s.getFont(), I18n.get("fishlog.top.by_count"),       cx1 + cw1/2, y + 2, ModColors.TEXT_WHITE);
        ctx.drawCenteredString(s.getFont(), I18n.get("fishlog.bait.top.by_recency"), cx2 + cw2/2, y + 2, ModColors.TEXT_WHITE);
        ctx.fill(x, y + hdrH - 1, x + w, y + hdrH, ModColors.UI_HEADER_LINE);

        s.baitTopRevBtnX = x + w - sbW - FishStatsScreen.REV_BTN_W - 2;
        s.baitTopRevBtnY = y + 1;
        ctx.fill(s.baitTopRevBtnX, s.baitTopRevBtnY, s.baitTopRevBtnX + FishStatsScreen.REV_BTN_W, s.baitTopRevBtnY + FishStatsScreen.REV_BTN_H,
            s.baitTopReversed ? ModColors.BTN_REV_ACTIVE : ModColors.BTN_REV_NORMAL);
        ctx.drawCenteredString(s.getFont(), s.baitTopReversed ? "▲" : "▼",
            s.baitTopRevBtnX + FishStatsScreen.REV_BTN_W / 2, s.baitTopRevBtnY + 1,
            s.baitTopReversed ? ModColors.TEXT_PRICE : ModColors.BTN_TEXT_NORMAL);

        int listY = y + hdrH;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        for (int i = 0; i < s.baitTypeEntries.size(); i++) {
            int idx = s.baitTopReversed ? s.baitTypeEntries.size() - 1 - i : i;
            int ri = i - s.baitTopScrollOffset, by = listY + ri * FishStatsScreen.TOP_ROW_H;
            if (by + FishStatsScreen.TOP_ROW_H < listY || by > listY + listH) continue;
            var e   = s.baitTypeEntries.get(idx);
            int col = FishStatsUtils.baitColor(e.getKey());
            ctx.fill(cx1, by, cx1 + cw1, by + FishStatsScreen.TOP_ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD);
            ctx.fill(cx1 + 1, by + 2, cx1 + 1 + dotS, by + 2 + dotS, col);
            int barX = cx1 + dotS + 4, valW = 24, barW = cw1 - dotS - 4 - valW - 2;
            int fill = (int)(barW * e.getValue() / (double) maxCnt);
            ctx.fill(barX, by + 2, barX + barW, by + 2 + dotS, ModColors.BAR_BG);
            ctx.fill(barX, by + 2, barX + fill, by + 2 + dotS, ModColors.CHART_HOURLY_FISH);
            ctx.drawString(s.getFont(), e.getKey(), barX + 2, by + 2, ModColors.TEXT_WHITE);
            ctx.drawString(s.getFont(), String.valueOf(e.getValue()), cx1 + cw1 - valW, by + 2, ModColors.TEXT_MEDIUM);
        }

        for (int i = 0; i < s.baitLastEntries.size(); i++) {
            int idx = s.baitTopReversed ? s.baitLastEntries.size() - 1 - i : i;
            int ri = i - s.baitTopScrollOffset, by = listY + ri * FishStatsScreen.TOP_ROW_H;
            if (by + FishStatsScreen.TOP_ROW_H < listY || by > listY + listH) continue;
            var e   = s.baitLastEntries.get(idx);
            int col = FishStatsUtils.baitColor(e.getKey());
            ctx.fill(cx2, by, cx2 + cw2, by + FishStatsScreen.TOP_ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD);
            ctx.fill(cx2 + 1, by + 2, cx2 + 1 + dotS, by + 2 + dotS, col);
            ctx.drawString(s.getFont(), e.getKey(), cx2 + dotS + 4, by + 2, ModColors.TEXT_WHITE);
            ctx.drawString(s.getFont(), I18n.get("fishlog.elapsed", FishStatsUtils.formatElapsed(e.getValue())), cx2 + cw2 - 56, by + 2, ModColors.TEXT_RECENCY);
        }
        ctx.disableScissor();

        int baitTopVis = listH / FishStatsScreen.TOP_ROW_H;
        FishStatsUtils.renderScrollbar(s, ctx, FishStatsScreen.SB_BAIT_TOP_L, cx1 + cw1 + 1, listY, listH, s.baitTopScrollOffset, s.baitTypeEntries.size(), baitTopVis);
        FishStatsUtils.renderScrollbar(s, ctx, FishStatsScreen.SB_BAIT_TOP_R, cx2 + cw2 + 1, listY, listH, s.baitTopScrollOffset, s.baitLastEntries.size(), baitTopVis);

        int fy = listY + listH;
        ctx.fill(x, fy, x + w, fy + footerH, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        ctx.drawString(s.getFont(),
            I18n.get(s.baitTypeEntries.size() > 1 ? "fishlog.footer.bait_top.plural" : "fishlog.footer.bait_top", s.baitTypeEntries.size()),
            x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BAIT RECORDS – historique des appâts
    // ─────────────────────────────────────────────────────────────────────────
    static void renderBaitRecords(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        int sbW = 4, dateW = 95, baitW = w - dateW - sbW - 4;
        int[] widths = {dateW, baitW};
        String[] hdrs = {I18n.get("fishlog.col.date"), I18n.get("fishlog.col.bait")};

        ctx.fill(x, y, x + w, y + FishStatsScreen.SEARCH_H, ModColors.UI_SEARCH_BG);
        ctx.fill(x, y + FishStatsScreen.SEARCH_H - 1, x + w, y + FishStatsScreen.SEARCH_H, ModColors.UI_FOOTER_LINE);

        int footerH = 12, hdrH = 12, hY = y + FishStatsScreen.SEARCH_H;
        ctx.fill(x, hY, x + w, hY + hdrH, ModColors.UI_HEADER_BG);
        int cx = x;
        for (int i = 0; i < hdrs.length; i++) { ctx.drawString(s.getFont(), hdrs[i], cx + 2, hY + 2, ModColors.TEXT_HEADER_COL); cx += widths[i]; }
        ctx.fill(x, hY + hdrH - 1, x + w, hY + hdrH, ModColors.UI_HEADER_LINE);

        int listY = hY + hdrH, listH = h - FishStatsScreen.SEARCH_H - hdrH - footerH;
        ctx.enableScissor(x, listY, x + w, listY + listH);
        int rowY = listY - s.baitScrollOffset * FishStatsScreen.ROW_H;
        for (int i = 0; i < s.filteredBaitRecords.size(); i++) {
            if (rowY + FishStatsScreen.ROW_H < listY || rowY > listY + listH) { rowY += FishStatsScreen.ROW_H; continue; }
            BaitRecord r = s.filteredBaitRecords.get(i);
            ctx.fill(x, rowY, x + w, rowY + FishStatsScreen.ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD_ALT);
            cx = x;
            ctx.drawString(s.getFont(), r.timestamp.format(DT_FMT), cx + 2, rowY + 1, ModColors.TEXT_LIGHT); cx += widths[0];
            ctx.drawString(s.getFont(), r.bait, cx + 2, rowY + 1, FishStatsUtils.baitColor(r.bait));
            rowY += FishStatsScreen.ROW_H;
        }
        ctx.disableScissor();

        FishStatsUtils.renderScrollbar(s, ctx, FishStatsScreen.SB_BAIT_REC, x + w - sbW, listY, listH, s.baitScrollOffset, s.filteredBaitRecords.size(), listH / FishStatsScreen.ROW_H);

        int fy = y + h - footerH;
        ctx.fill(x, fy, x + w, y + h, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        String footerTxt = s.filteredBaitRecords.size() == s.allBaitRecords.size()
            ? I18n.get(s.allBaitRecords.size() > 1 ? "fishlog.footer.records.plural" : "fishlog.footer.records", s.allBaitRecords.size())
            : I18n.get("fishlog.footer.filtered", s.filteredBaitRecords.size(), s.allBaitRecords.size());
        ctx.drawString(s.getFont(), footerTxt, x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }
}
