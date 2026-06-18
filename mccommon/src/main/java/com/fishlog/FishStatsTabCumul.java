package com.fishlog;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

class FishStatsTabCumul {

    private static final DateTimeFormatter CUMUL_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static void renderFish(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        if (s.cumTimes == null || s.cumTimes.length < 2) {
            ctx.drawCenteredString(s.getFont(), I18n.get("fishlog.empty.nodata"), x + w/2, y + h/2, ModColors.TEXT_WHITE);
            return;
        }
        double maxT = s.cumTimes[s.cumTimes.length - 1];
        double maxV = s.cumValues[s.cumValues.length - 1];
        if (maxT <= 0) maxT = 1;
        if (maxV <= 0) maxV = 1;
        int plotX = x + 30, plotY = y + 10, plotW = w - 40, plotH = h - 25;
        ctx.fill(plotX, plotY, plotX + plotW, plotY + plotH, ModColors.PLOT_BG);

        int[] colY = computeColY(s.cumTimes, s.cumValues, plotW, plotH, maxT, maxV, plotY);
        for (int px = 0; px < plotW; px++)
            ctx.fill(plotX + px, colY[px], plotX + px + 1, plotY + plotH, ModColors.CHART_FISH_FILL);
        drawThickCurve(colY, plotX, plotW, ModColors.CHART_FISH_LINE);

        for (int t = 0; t <= 4; t++) {
            int yt = plotY + plotH - t * plotH / 4;
            ctx.fill(plotX, yt, plotX + plotW, yt + 1, ModColors.CHART_GRID);
            double axisVal   = maxV * t / 4;
            String axisShort = FishStatsUtils.formatShort(axisVal) + "$";
            ctx.drawString(s.getFont(), axisShort, x, yt - 4, ModColors.TEXT_MUTED);
            FishStatsUtils.checkTooltip(s, x, yt - 4, axisShort, String.format("%.0f$", axisVal));
        }
        ctx.drawCenteredString(s.getFont(),
            I18n.get(s.cumTimes.length > 1 ? "fishlog.cumul.footer.plural" : "fishlog.cumul.footer",
                s.cumValues[s.cumValues.length-1], s.cumTimes.length),
            plotX + plotW/2, plotY + plotH + 6, ModColors.TEXT_WHITE);

        if (s.lastMx >= plotX && s.lastMx <= plotX + plotW && s.lastMy >= plotY && s.lastMy <= plotY + plotH) {
            double tCursor = maxT * (s.lastMx - plotX) / (double) plotW;
            int lo = 0, hi = s.cumTimes.length - 1;
            while (lo < hi - 1) { int mid = (lo + hi) >> 1; if (s.cumTimes[mid] <= tCursor) lo = mid; else hi = mid; }
            int hovI = Math.abs(s.cumTimes[lo] - tCursor) <= Math.abs(s.cumTimes[hi] - tCursor) ? lo : hi;
            s.pendingTooltip = List.of(Component.literal(
                I18n.get("fishlog.cumul.tooltip", s.cumDates[hovI].format(CUMUL_FMT), Math.round(s.cumValues[hovI]))));
        }
    }

    static void renderBait(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        if (s.baitCumTimes == null || s.baitCumTimes.length < 2) {
            ctx.drawCenteredString(s.getFont(), I18n.get("fishlog.empty.nodata"), x + w/2, y + h/2, ModColors.TEXT_WHITE);
            return;
        }
        double maxT = s.baitCumTimes[s.baitCumTimes.length - 1];
        double maxV = s.baitCumValues[s.baitCumValues.length - 1];
        if (maxT <= 0) maxT = 1;
        if (maxV <= 0) maxV = 1;
        int plotX = x + 30, plotY = y + 10, plotW = w - 40, plotH = h - 25;
        ctx.fill(plotX, plotY, plotX + plotW, plotY + plotH, ModColors.PLOT_BG);

        int[] colY = computeColY(s.baitCumTimes, s.baitCumValues, plotW, plotH, maxT, maxV, plotY);
        for (int px = 0; px < plotW; px++)
            ctx.fill(plotX + px, colY[px], plotX + px + 1, plotY + plotH, ModColors.CHART_BAIT_FILL);
        drawThickCurve(colY, plotX, plotW, ModColors.CHART_BAIT_LINE);

        for (int t = 0; t <= 4; t++) {
            int yt = plotY + plotH - t * plotH / 4;
            ctx.fill(plotX, yt, plotX + plotW, yt + 1, ModColors.CHART_GRID);
            ctx.drawString(s.getFont(), String.format("%.0f", maxV * t / 4), x, yt - 4, ModColors.TEXT_MUTED);
        }
        ctx.drawCenteredString(s.getFont(),
            I18n.get(maxV > 1 ? "fishlog.bait.cumul.footer.plural" : "fishlog.bait.cumul.footer", (int) maxV),
            plotX + plotW/2, plotY + plotH + 6, ModColors.TEXT_WHITE);

        if (s.lastMx >= plotX && s.lastMx <= plotX + plotW && s.lastMy >= plotY && s.lastMy <= plotY + plotH) {
            double tCursor = maxT * (s.lastMx - plotX) / (double) plotW;
            int lo = 0, hi = s.baitCumTimes.length - 1;
            while (lo < hi - 1) { int mid = (lo + hi) >> 1; if (s.baitCumTimes[mid] <= tCursor) lo = mid; else hi = mid; }
            int hovI = Math.abs(s.baitCumTimes[lo] - tCursor) <= Math.abs(s.baitCumTimes[hi] - tCursor) ? lo : hi;
            s.pendingTooltip = List.of(Component.literal(
                I18n.get("fishlog.cumul.tooltip", s.baitCumDates[hovI].format(CUMUL_FMT), Math.round(s.baitCumValues[hovI]))));
        }
    }

    private static int[] computeColY(double[] cumTimes, double[] cumValues,
                                     int plotW, int plotH, double maxT, double maxV, int plotY) {
        int[] colY = new int[plotW];
        int dataIdx = 0;
        for (int px = 0; px < plotW; px++) {
            double t = maxT * px / (plotW - 1);
            while (dataIdx < cumTimes.length - 1 && cumTimes[dataIdx + 1] <= t) dataIdx++;
            double v;
            if (dataIdx >= cumTimes.length - 1) {
                v = cumValues[cumValues.length - 1];
            } else {
                double dt   = cumTimes[dataIdx + 1] - cumTimes[dataIdx];
                double frac = dt > 0 ? (t - cumTimes[dataIdx]) / dt : 0;
                v = cumValues[dataIdx] + frac * (cumValues[dataIdx + 1] - cumValues[dataIdx]);
            }
            colY[px] = plotY + plotH - (int)(plotH * v / maxV);
        }
        return colY;
    }

    private static void drawThickCurve(int[] colY, int plotX, int plotW, int argb) {
        var tess = Tesselator.getInstance();
        var buf  = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int px = 0; px < plotW - 1; px++) {
            float fx1 = plotX + px, fy1 = colY[px];
            float fx2 = plotX + px + 1, fy2 = colY[px + 1];
            float ddx = fx2 - fx1, ddy = fy2 - fy1;
            float len = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (len < 0.001f) continue;
            float nx = -ddy / len, ny = ddx / len;
            buf.addVertex(fx1 + nx, fy1 + ny, 0f).setColor(argb);
            buf.addVertex(fx1 - nx, fy1 - ny, 0f).setColor(argb);
            buf.addVertex(fx2 + nx, fy2 + ny, 0f).setColor(argb);
            buf.addVertex(fx2 + nx, fy2 + ny, 0f).setColor(argb);
            buf.addVertex(fx1 - nx, fy1 - ny, 0f).setColor(argb);
            buf.addVertex(fx2 - nx, fy2 - ny, 0f).setColor(argb);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }
}
