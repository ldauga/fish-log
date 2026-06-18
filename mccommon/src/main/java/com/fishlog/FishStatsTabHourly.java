package com.fishlog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

class FishStatsTabHourly {

    static void renderFish(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        renderHourly(s, ctx, x, y, w, h, s.hourly, s.dailyCounts, s.dailyDates,
            "fishlog.hourly.label", "fishlog.daily.title", "fishlog.hourly.tooltip",
            "fishlog.daily.tooltip", "fishlog.empty.nodata",
            ModColors.CHART_HOURLY_FISH, ModColors.CHART_FISH_FILL, ModColors.CHART_FISH_LINE);
    }

    static void renderBait(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        renderHourly(s, ctx, x, y, w, h, s.baitHourly, s.baitDailyCounts, s.baitDailyDates,
            "fishlog.hourly.label", "fishlog.bait.daily.title", "fishlog.bait.hourly.tooltip",
            "fishlog.bait.daily.tooltip", "fishlog.empty.nodata",
            ModColors.CHART_HOURLY_BAIT, ModColors.CHART_BAIT_FILL, ModColors.CHART_BAIT_LINE);
    }

    private static void renderHourly(FishStatsScreen s, GuiGraphics ctx,
                                     int x, int y, int w, int h,
                                     int[] hourly, int[] dailyCounts, java.time.LocalDate[] dailyDates,
                                     String hourlyLbl, String dailyLbl,
                                     String hourlyTip, String dailyTip, String noDataKey,
                                     int barColor, int fillColor, int lineColor) {
        int usableH     = h - 12;
        int hourlyAreaH = usableH * 48 / 100;
        int sepY        = y + hourlyAreaH;

        ctx.drawCenteredString(s.getFont(), I18n.get(hourlyLbl), x + w / 2, y + 1, ModColors.TEXT_MUTED);

        int maxH = Arrays.stream(hourly).max().orElse(1);
        if (maxH == 0) maxH = 1;
        int plotH  = hourlyAreaH - 20;
        int plotW  = w - 20;
        int bw     = plotW / 24;
        int topOff = 10;
        for (int i = 0; i < 24; i++) {
            int bh  = hourly[i] * plotH / maxH;
            int bxi = x + 10 + i * bw;
            int byi = y + topOff + (plotH - bh);
            ctx.fill(bxi, byi, bxi + bw - 1, y + topOff + plotH, barColor);
            if (i % 4 == 0)
                ctx.drawCenteredString(s.getFont(), String.valueOf(i), bxi + bw / 2, y + topOff + plotH + 2, ModColors.TEXT_MUTED);
            if (hourly[i] > 0) {
                String hShort = FishStatsUtils.formatShort(hourly[i]);
                FishStatsUtils.drawFittedText(s, ctx, hShort, bxi + bw / 2, byi - 9, bw - 1, ModColors.TEXT_WHITE);
                int barBottom = y + topOff + plotH;
                if (s.lastMx >= bxi && s.lastMx < bxi + bw && s.lastMy >= byi && s.lastMy <= barBottom)
                    s.pendingTooltip = List.of(Component.literal(I18n.get(hourlyTip, hourly[i], i)));
            }
        }

        ctx.fill(x, sepY, x + w, sepY + 1, ModColors.UI_DIVIDER);

        int dailyY = sepY + 2;
        int dailyH = usableH - hourlyAreaH - 3;
        ctx.drawCenteredString(s.getFont(), I18n.get(dailyLbl), x + w / 2, dailyY + 1, ModColors.TEXT_MUTED);

        if (dailyCounts == null || dailyCounts.length < 2) {
            ctx.drawCenteredString(s.getFont(), I18n.get(noDataKey), x + w / 2, dailyY + dailyH / 2, ModColors.TEXT_WHITE);
            return;
        }

        int n    = dailyCounts.length;
        int maxV = Arrays.stream(dailyCounts).max().orElse(1);
        if (maxV == 0) maxV = 1;
        int plotX  = x + 28, plotY2 = dailyY + 12, plotW2 = w - 36, plotH2 = dailyH - 24;
        if (plotH2 < 8) return;
        ctx.fill(plotX, plotY2, plotX + plotW2, plotY2 + plotH2, ModColors.PLOT_BG);

        for (int i = 0; i < n - 1; i++) {
            float t1 = (float) i / (n - 1), t2 = (float) (i + 1) / (n - 1);
            float v1 = (float) dailyCounts[i] / maxV, v2 = (float) dailyCounts[i + 1] / maxV;
            int x1 = plotX + (int)(plotW2 * t1), x2 = plotX + (int)(plotW2 * t2);
            int y1 = plotY2 + plotH2 - (int)(plotH2 * v1), y2 = plotY2 + plotH2 - (int)(plotH2 * v2);
            for (int px = x1; px <= x2 && px < plotX + plotW2; px++) {
                int yTop = px == x1 ? y1 : y1 + (y2 - y1) * (px - x1) / Math.max(1, x2 - x1);
                ctx.fill(px, yTop, px + 1, plotY2 + plotH2, fillColor);
            }
            FishStatsUtils.thickLine(plotX, plotY2, plotW2, plotH2, t1, v1, t2, v2, 2f, lineColor);
        }

        for (int t = 0; t <= 4; t++) {
            int yt = plotY2 + plotH2 - t * plotH2 / 4;
            ctx.fill(plotX, yt, plotX + plotW2, yt + 1, ModColors.CHART_GRID);
            ctx.drawString(s.getFont(), String.valueOf((int) Math.round(maxV * t / 4.0)), x, yt - 4, ModColors.TEXT_MUTED);
        }

        if (s.lastMx >= plotX && s.lastMx <= plotX + plotW2 && s.lastMy >= plotY2 && s.lastMy <= plotY2 + plotH2) {
            int hovI = Math.round((float)(s.lastMx - plotX) / plotW2 * (n - 1));
            hovI = Math.max(0, Math.min(n - 1, hovI));
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            s.pendingTooltip = List.of(Component.literal(I18n.get(dailyTip, dailyCounts[hovI], dailyDates[hovI].format(fmt))));
        }
    }
}
