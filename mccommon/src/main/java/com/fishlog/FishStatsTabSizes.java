package com.fishlog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class FishStatsTabSizes {

    static void render(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        if (s.sizesByRarity.isEmpty()) return;

        var entries = s.sizesByRarity.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> {
                int idx = FishStatsScreen.RARITY_ORDER.indexOf(e.getKey());
                return idx < 0 ? FishStatsScreen.RARITY_ORDER.size() : idx;
            }))
            .collect(Collectors.toList());
        if (entries.isEmpty()) {
            ctx.drawCenteredString(s.getFont(), I18n.get("fishlog.empty.nodata"), x + w/2, y + h/2, ModColors.TEXT_WHITE);
            return;
        }
        var richEntries = entries.stream().filter(e -> e.getValue().size() >= 3).collect(Collectors.toList());

        // ── Bouton LIN / LOG ──────────────────────────────────────────────────
        s.sizesLogBtnX = x + w - FishStatsScreen.LOG_BTN_W - 2;
        s.sizesLogBtnY = y + 1;
        ctx.fill(s.sizesLogBtnX, s.sizesLogBtnY, s.sizesLogBtnX + FishStatsScreen.LOG_BTN_W, s.sizesLogBtnY + FishStatsScreen.LOG_BTN_H,
            s.sizesLogScale ? ModColors.BTN_ACTIVE : ModColors.BTN_NORMAL);
        ctx.fill(s.sizesLogBtnX, s.sizesLogBtnY, s.sizesLogBtnX + FishStatsScreen.LOG_BTN_W, s.sizesLogBtnY + 1, ModColors.TOGGLE_BORDER);
        ctx.fill(s.sizesLogBtnX, s.sizesLogBtnY, s.sizesLogBtnX + 1, s.sizesLogBtnY + FishStatsScreen.LOG_BTN_H, ModColors.TOGGLE_BORDER);
        ctx.drawCenteredString(s.getFont(), s.sizesLogScale ? "LOG" : "LIN",
            s.sizesLogBtnX + FishStatsScreen.LOG_BTN_W / 2, s.sizesLogBtnY + 2,
            s.sizesLogScale ? ModColors.TEXT_PRICE : ModColors.BTN_TEXT_NORMAL);

        // ── Calcul des bornes ─────────────────────────────────────────────────
        var scaleSource = richEntries.isEmpty() ? entries : richEntries;
        double rawMin = scaleSource.stream().flatMapToDouble(e -> e.getValue().stream().mapToDouble(d -> d)).min().orElse(1);
        double rawMax = scaleSource.stream().flatMapToDouble(e -> e.getValue().stream().mapToDouble(d -> d)).max().orElse(100);
        if (rawMax <= rawMin) rawMax = rawMin + 10;
        double scaleMin   = s.sizesLogScale ? Math.log10(Math.max(0.01, rawMin)) : rawMin;
        double scaleMax   = s.sizesLogScale ? Math.log10(Math.max(0.01, rawMax)) : rawMax;
        double scaleRange = scaleMax - scaleMin;
        if (scaleRange <= 0) scaleRange = 1;
        final double sm = scaleMin, sr = scaleRange;
        java.util.function.Function<Double, Double> toNorm = (v) -> {
            double sv = s.sizesLogScale ? Math.log10(Math.max(0.01, v)) : v;
            return (sv - sm) / sr;
        };

        int plotX = x + 20, plotY = y + 14, plotW = w - 40, plotH = h - 30;
        int bw    = plotW / entries.size();
        ctx.fill(plotX, plotY, plotX + plotW, plotY + plotH, ModColors.PLOT_BG);

        for (int i = 0; i < entries.size(); i++) {
            var e    = entries.get(i);
            var vals = e.getValue();
            int col  = FishStatsScreen.RARITY_COL.getOrDefault(e.getKey(), ModColors.RARITY_UNKNOWN);
            int bx   = plotX + i * bw + bw/5;
            int bwb  = bw * 3 / 5;
            boolean isEvent = FishStatsScreen.EVENT_NAMES.containsKey(e.getKey());
            String label    = isEvent ? "EVT" : e.getKey().substring(0, Math.min(3, e.getKey().length()));
            int labelCol    = isEvent ? 0xFFFFFF : (vals.size() < 3 ? col & 0x88FFFFFF : col);
            ctx.drawCenteredString(s.getFont(), label, bx + bwb/2, plotY + plotH + 2, labelCol);
            if (isEvent)
                FishStatsUtils.checkRectTooltip(s, bx, plotY + plotH + 2, bwb, 9, List.of(Component.literal(e.getKey())));

            if (vals.size() < 3) {
                ctx.fill(bx, plotY, bx + bwb, plotY + plotH, col & 0x22FFFFFF | 0x22000000);
                ctx.drawCenteredString(s.getFont(), I18n.get("fishlog.empty.nodata"), bx + bwb/2, plotY + plotH/2 - 4, ModColors.TEXT_VERY_MUTED);
                continue;
            }

            double q1  = FishStatsUtils.percentile(vals, 25);
            double med = FishStatsUtils.percentile(vals, 50);
            double q3  = FishStatsUtils.percentile(vals, 75);
            double lo  = vals.get(0);
            double hi  = vals.get(vals.size()-1);
            int yQ1  = plotY + plotH - (int)(plotH * toNorm.apply(q1));
            int yMed = plotY + plotH - (int)(plotH * toNorm.apply(med));
            int yQ3  = plotY + plotH - (int)(plotH * toNorm.apply(q3));
            int yLo  = plotY + plotH - (int)(plotH * toNorm.apply(lo));
            int yHi  = plotY + plotH - (int)(plotH * toNorm.apply(hi));

            ctx.fill(bx, yQ3, bx + bwb, yQ1, col & 0x55FFFFFF | 0x55000000);
            ctx.fill(bx, yQ3, bx + bwb, yQ3 + 1, col);
            ctx.fill(bx, yQ1, bx + bwb, yQ1 + 1, col);
            ctx.fill(bx, yMed, bx + bwb, yMed + 2, ModColors.CHART_MEDIAN);
            int mid = bx + bwb/2;
            ctx.fill(mid, yHi, mid + 1, yQ3, col);
            ctx.fill(mid, yQ1, mid + 1, yLo, col);

            List<FishRecord> rarityRecs = s.fishRecordsByRarity.getOrDefault(e.getKey(), List.of());
            if (!rarityRecs.isEmpty()) {
                FishRecord maxFish = rarityRecs.stream().max(Comparator.comparingDouble(r -> r.sizeCm)).orElse(null);
                FishRecord minFish = rarityRecs.stream().min(Comparator.comparingDouble(r -> r.sizeCm)).orElse(null);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(e.getKey()).withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(col & 0xFFFFFF))));
                if (maxFish != null) lines.add(Component.literal(I18n.get("fishlog.sizes.tooltip.max", maxFish.fish, maxFish.sizeCm)));
                if (minFish != null) lines.add(Component.literal(I18n.get("fishlog.sizes.tooltip.min", minFish.fish, minFish.sizeCm)));
                lines.add(Component.literal(I18n.get("fishlog.sizes.tooltip.q1", q1)));
                lines.add(Component.literal(I18n.get("fishlog.sizes.tooltip.median", med)));
                lines.add(Component.literal(I18n.get("fishlog.sizes.tooltip.q3", q3)));
                FishStatsUtils.checkRectTooltip(s, bx, plotY, bwb, plotH, lines);
            }
        }

        // ── Axe Y ────────────────────────────────────────────────────────────
        for (int tick = 0; tick <= 4; tick++) {
            double norm = tick / 4.0;
            double raw  = s.sizesLogScale ? Math.pow(10, scaleMin + norm * scaleRange) : rawMin + norm * scaleRange;
            int yt = plotY + plotH - (int)(plotH * norm);
            ctx.drawString(s.getFont(), String.format("%.0f", raw), x, yt - 4, ModColors.TEXT_MUTED);
            ctx.fill(plotX, yt, plotX + plotW, yt + 1, ModColors.CHART_GRID_DENSE);
        }
    }
}
