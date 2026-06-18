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
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FishStatsUtils {

    // ── Couleurs appâts ───────────────────────────────────────────────────────
    static final Map<String, Integer> BAIT_COL      = new java.util.LinkedHashMap<>();
    static final Map<String, Integer> BAIT_COL_NORM = new HashMap<>();
    static {
        BAIT_COL.put("Ver Luisant",       ModColors.BAIT_BLEU);
        BAIT_COL.put("Slime Royal",       ModColors.BAIT_BLEU);
        BAIT_COL.put("Aimant",            ModColors.BAIT_GRIS);
        BAIT_COL.put("Appat Runique",     ModColors.BAIT_VIOLET);
        BAIT_COL.put("Leurre Mecanique",  ModColors.BAIT_VIOLET);
        BAIT_COL.put("Companion cube",    ModColors.BAIT_ORANGE);
        BAIT_COL.put("Ruche de Nimeria",  ModColors.BAIT_ORANGE);
        BAIT_COL.put("Zekappat",          ModColors.BAIT_ROUGE);
        BAIT_COL.put("Lisappat",          ModColors.BAIT_ROUGE);
        BAIT_COL.put("Lanterne Abyssale", ModColors.BAIT_BLEU_FONCE);
        BAIT_COL.put("Rune Atlante",      ModColors.BAIT_BLEU_FONCE);
        BAIT_COL.put("Mouche Commune",    ModColors.BAIT_GRIS_CLAIR);
        BAIT_COL.forEach((k, v) -> BAIT_COL_NORM.put(FishTextureCache.normalize(k), v));
    }

    static int baitColor(String bait) {
        Integer c = BAIT_COL.get(bait);
        if (c != null) return c;
        c = BAIT_COL_NORM.get(FishTextureCache.normalize(bait));
        if (c != null) return c;
        int h = Math.abs(bait.hashCode());
        return 0xFF000000 | ((128 + (h & 0x7F)) << 16) | ((128 + ((h >> 8) & 0x7F)) << 8) | (128 + ((h >> 16) & 0x7F));
    }

    // ── Rendu item avec mise à l'échelle ──────────────────────────────────────
    static void renderScaledItem(GuiGraphics ctx, ItemStack stack, int x, int y, int size) {
        float scale = size / 16.0f;
        var poseStack = ctx.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        ctx.renderItem(stack, 0, 0);
        poseStack.popPose();
    }

    // ── Texte centré avec réduction d'échelle si trop large ──────────────────
    static void drawFittedText(FishStatsScreen s, GuiGraphics ctx, String text, int cx, int y, int maxW, int color) {
        int textW = s.getFont().width(text);
        if (textW <= maxW) {
            ctx.drawCenteredString(s.getFont(), text, cx, y, color);
        } else {
            float scale = Math.max(0.5f, (float) maxW / textW);
            var poseStack = ctx.pose();
            poseStack.pushPose();
            poseStack.scale(scale, scale, 1f);
            ctx.drawCenteredString(s.getFont(), text, Math.round(cx / scale), Math.round(y / scale), color);
            poseStack.popPose();
        }
    }

    // ── Segment de ligne épaisse via Tessellator ──────────────────────────────
    static void thickLine(int plotX, int plotY, int plotW, int plotH,
                          float t1, float v1, float t2, float v2,
                          float thickness, int argb) {
        float x1 = plotX + t1 * plotW, y1 = plotY + plotH - v1 * plotH;
        float x2 = plotX + t2 * plotW, y2 = plotY + plotH - v2 * plotH;
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * (thickness / 2), ny = dx / len * (thickness / 2);
        var tess = Tesselator.getInstance();
        var buf  = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(x1 + nx, y1 + ny, 0f).setColor(argb);
        buf.addVertex(x1 - nx, y1 - ny, 0f).setColor(argb);
        buf.addVertex(x2 + nx, y2 + ny, 0f).setColor(argb);
        buf.addVertex(x2 + nx, y2 + ny, 0f).setColor(argb);
        buf.addVertex(x1 - nx, y1 - ny, 0f).setColor(argb);
        buf.addVertex(x2 - nx, y2 - ny, 0f).setColor(argb);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ── Scrollbar dessinable (écrit aussi les métadonnées dans FishStatsScreen) ─
    static void renderScrollbar(FishStatsScreen s, GuiGraphics ctx,
                                int id, int x, int y, int trackH,
                                int offset, int total, int visible) {
        s.sbX[id] = x; s.sbY[id] = y; s.sbTrackH[id] = trackH;
        s.sbTotal[id] = total; s.sbVis[id] = visible;
        if (total <= visible) return;
        ctx.fill(x, y, x + 3, y + trackH, ModColors.PLOT_BG);
        int thumbH = Math.max(6, trackH * visible / total);
        int maxOff = total - visible;
        int thumbY = y + (maxOff > 0 ? (trackH - thumbH) * offset / maxOff : 0);
        ctx.fill(x, thumbY, x + 3, thumbY + thumbH,
            s.sbDragId == id ? ModColors.SB_THUMB_DRAG : ModColors.SB_THUMB);
    }

    // ── Tooltip au survol de texte abrégé ─────────────────────────────────────
    static void checkTooltip(FishStatsScreen s, int tx, int ty, String shortText, String fullText) {
        int tw = s.getFont().width(shortText);
        if (s.lastMx >= tx && s.lastMx <= tx + tw && s.lastMy >= ty && s.lastMy <= ty + 9) {
            s.pendingTooltip = List.of(Component.literal(fullText));
        }
    }

    static void checkRectTooltip(FishStatsScreen s, int rx, int ry, int rw, int rh, List<Component> lines) {
        if (s.lastMx >= rx && s.lastMx <= rx + rw && s.lastMy >= ry && s.lastMy <= ry + rh) {
            s.pendingTooltip = lines;
        }
    }

    // ── Formatage ─────────────────────────────────────────────────────────────
    static String formatShort(double val) {
        if (val >= 1_000_000) return String.format("%.1fM", val / 1_000_000);
        if (val >= 1_000)     return String.format("%.1fk", val / 1_000);
        return String.format("%.0f", val);
    }

    static String formatElapsed(java.time.LocalDateTime last) {
        long sec = java.time.Duration.between(last, java.time.LocalDateTime.now()).getSeconds();
        if (sec < 0) sec = 0;
        if (sec < 60)  return I18n.get("fishlog.time.s", sec);
        long min = sec / 60;
        if (min < 60)  return I18n.get("fishlog.time.ms", min, sec % 60);
        long h = min / 60;
        if (h < 24)    return I18n.get("fishlog.time.hm", h, min % 60);
        long d = h / 24;
        return I18n.get("fishlog.time.dh", d, h % 24);
    }

    static double percentile(List<Double> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        double idx = p / 100.0 * (sorted.size() - 1);
        int lo = (int) idx;
        if (lo >= sorted.size() - 1) return sorted.get(sorted.size() - 1);
        return sorted.get(lo) + (idx - lo) * (sorted.get(lo + 1) - sorted.get(lo));
    }

    static int hueToRgb(float hue, float brightness) {
        float h = hue * 6f, c = brightness;
        float x = c * (1f - Math.abs(h % 2f - 1f));
        float r, g, b;
        if      (h < 1f) { r=c; g=x; b=0; }
        else if (h < 2f) { r=x; g=c; b=0; }
        else if (h < 3f) { r=0; g=c; b=x; }
        else if (h < 4f) { r=0; g=x; b=c; }
        else if (h < 5f) { r=x; g=0; b=c; }
        else             { r=c; g=0; b=x; }
        return 0xFF000000 | ((int)(r*255) << 16) | ((int)(g*255) << 8) | (int)(b*255);
    }
}
