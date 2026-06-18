package com.fishlog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

class FishStatsTabTop {

    static void render(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        s.starHits.clear();
        int footerH = 11, hdrH = 11;
        int listH   = h - FishStatsScreen.SEARCH_H - hdrH - footerH;
        int sbW = 4, half = w / 2;
        int cx1 = x + 1,        cw1 = half - 2;
        int cx2 = x + half + 1, cw2 = w - half - 2 - sbW;
        int dotS = 6, rarW = 52;

        int maxCnt = s.filteredTopCount.isEmpty() ? 1 : s.filteredTopCount.get(0).getValue();
        double maxVal = s.filteredTopValue.isEmpty() ? 1.0 : s.filteredTopValue.get(0).getValue();

        // ── Barre de recherche ────────────────────────────────────────────────
        ctx.fill(x, y, x + w, y + FishStatsScreen.SEARCH_H, ModColors.UI_SEARCH_BG);
        ctx.fill(x, y + FishStatsScreen.SEARCH_H - 1, x + w, y + FishStatsScreen.SEARCH_H, ModColors.UI_FOOTER_LINE);
        int hdrY = y + FishStatsScreen.SEARCH_H;

        // ── En-têtes ─────────────────────────────────────────────────────────
        ctx.fill(x, hdrY, x + w, hdrY + hdrH, ModColors.UI_HEADER_BG);
        ctx.fill(x + half, hdrY, x + half + 1, y + h, ModColors.UI_FOOTER_LINE);
        ctx.drawCenteredString(s.getFont(), I18n.get("fishlog.top.by_count"), cx1 + cw1/2, hdrY + 2, ModColors.TEXT_WHITE);
        ctx.drawCenteredString(s.getFont(), I18n.get("fishlog.top.by_value"), cx2 + cw2/2, hdrY + 2, ModColors.TEXT_WHITE);
        ctx.fill(x, hdrY + hdrH - 1, x + w, hdrY + hdrH, ModColors.UI_HEADER_LINE);

        // ── Bouton reverse ────────────────────────────────────────────────────
        s.topRevBtnX = x + w - sbW - FishStatsScreen.REV_BTN_W - 2;
        s.topRevBtnY = hdrY + 1;
        ctx.fill(s.topRevBtnX, s.topRevBtnY, s.topRevBtnX + FishStatsScreen.REV_BTN_W, s.topRevBtnY + FishStatsScreen.REV_BTN_H,
            s.topReversed ? ModColors.BTN_REV_ACTIVE : ModColors.BTN_REV_NORMAL);
        ctx.fill(s.topRevBtnX, s.topRevBtnY, s.topRevBtnX + FishStatsScreen.REV_BTN_W, s.topRevBtnY + 1, ModColors.TOGGLE_BORDER);
        ctx.fill(s.topRevBtnX, s.topRevBtnY, s.topRevBtnX + 1, s.topRevBtnY + FishStatsScreen.REV_BTN_H, ModColors.TOGGLE_BORDER);
        ctx.drawCenteredString(s.getFont(), s.topReversed ? "▲" : "▼",
            s.topRevBtnX + FishStatsScreen.REV_BTN_W / 2, s.topRevBtnY + 1,
            s.topReversed ? ModColors.TEXT_PRICE : ModColors.BTN_TEXT_NORMAL);

        // ── Bouton Max/Mean/Min ───────────────────────────────────────────────
        s.valModeBtnX = s.topRevBtnX - FishStatsScreen.VAL_MODE_BTN_W - 2;
        s.valModeBtnY = hdrY + 1;
        ctx.fill(s.valModeBtnX, s.valModeBtnY, s.valModeBtnX + FishStatsScreen.VAL_MODE_BTN_W, s.valModeBtnY + FishStatsScreen.VAL_MODE_BTN_H, ModColors.BTN_NORMAL);
        ctx.fill(s.valModeBtnX, s.valModeBtnY, s.valModeBtnX + FishStatsScreen.VAL_MODE_BTN_W, s.valModeBtnY + 1, ModColors.TOGGLE_BORDER);
        ctx.fill(s.valModeBtnX, s.valModeBtnY, s.valModeBtnX + 1, s.valModeBtnY + FishStatsScreen.VAL_MODE_BTN_H, ModColors.TOGGLE_BORDER);
        String valModeLabel = switch (s.valueMode) { case MAX -> "MAX"; case MEAN -> "MEAN"; case MIN -> "MIN"; };
        ctx.drawCenteredString(s.getFont(), valModeLabel, s.valModeBtnX + FishStatsScreen.VAL_MODE_BTN_W / 2, s.valModeBtnY + 1, ModColors.BTN_TEXT_NORMAL);

        // ── Listes défilables ─────────────────────────────────────────────────
        int listY = hdrY + hdrH;
        ctx.enableScissor(x, listY, x + w, listY + listH);

        for (int i = 0; i < s.filteredTopCount.size(); i++) {
            int idx = s.topReversed ? s.filteredTopCount.size() - 1 - i : i;
            int ri  = i - s.topScrollOffset;
            int by  = listY + ri * FishStatsScreen.TOP_ROW_H;
            if (by + FishStatsScreen.TOP_ROW_H < listY || by > listY + listH) continue;
            var e   = s.filteredTopCount.get(idx);
            int col = FishStatsScreen.RARITY_COL.getOrDefault(s.fishRarity.getOrDefault(e.getKey(), ""), ModColors.RARITY_UNKNOWN);
            ctx.fill(cx1, by, cx1 + cw1, by + FishStatsScreen.TOP_ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD);
            String rar1 = s.fishRarity.getOrDefault(e.getKey(), "");
            ItemStack icon1 = FishTextureCache.getItemStack(e.getKey(), rar1);
            if (icon1 != null) FishStatsUtils.renderScaledItem(ctx, icon1, cx1 + 1, by + 1, 10);
            else ctx.fill(cx1 + 1, by + 2, cx1 + 1 + dotS, by + 2 + dotS, col);
            int barX = cx1 + dotS + 4, valW = 34;
            int barW = cw1 - dotS - 4 - valW - rarW - 2;
            int fill = (int)(barW * e.getValue() / (double) maxCnt);
            ctx.fill(barX, by + 2, barX + barW, by + 2 + dotS, ModColors.BAR_BG);
            ctx.fill(barX, by + 2, barX + fill, by + 2 + dotS, 0x883388CC | 0xFF000000);
            ctx.drawString(s.getFont(), e.getKey(), barX + 2, by + 2, ModColors.TEXT_WHITE);
            int rarCol1 = FishStatsScreen.RARITY_COL.getOrDefault(rar1, ModColors.TEXT_MUTED_ARGB);
            int rarTx1  = cx1 + cw1 - valW - rarW;
            ctx.drawString(s.getFont(), FishStatsScreen.rarityDisplay(rar1), rarTx1, by + 2, rarCol1);
            if (FishStatsScreen.EVENT_NAMES.containsKey(rar1))
                FishStatsUtils.checkRectTooltip(s, rarTx1, by + 2, s.getFont().width("EVENT"), 9, List.of(Component.literal(rar1)));
            String cntShort = FishStatsUtils.formatShort(e.getValue());
            int cntTx = cx1 + cw1 - valW;
            ctx.drawString(s.getFont(), cntShort, cntTx, by + 2, ModColors.TEXT_MEDIUM);
            FishStatsUtils.checkTooltip(s, cntTx, by + 2, cntShort, I18n.get("fishlog.top.catches", e.getValue()));
            boolean fav1 = FavoritesStore.INSTANCE.isFavorite(e.getKey());
            int sx1 = cx1 + cw1 - 9, sy1 = by + 2;
            ctx.drawString(s.getFont(), fav1 ? "★" : "☆", sx1, sy1, fav1 ? 0xFFFFDD00 : ModColors.TEXT_VERY_MUTED);
            s.starHits.add(new FishStatsScreen.StarHit(e.getKey(), sx1 - 1, sy1, 10, 9));
        }

        for (int i = 0; i < s.filteredTopValue.size(); i++) {
            int idx = s.topReversed ? s.filteredTopValue.size() - 1 - i : i;
            int ri  = i - s.topScrollOffset;
            int by  = listY + ri * FishStatsScreen.TOP_ROW_H;
            if (by + FishStatsScreen.TOP_ROW_H < listY || by > listY + listH) continue;
            var e   = s.filteredTopValue.get(idx);
            int col = FishStatsScreen.RARITY_COL.getOrDefault(s.fishRarity.getOrDefault(e.getKey(), ""), ModColors.RARITY_UNKNOWN);
            ctx.fill(cx2, by, cx2 + cw2, by + FishStatsScreen.TOP_ROW_H, (i % 2 == 0) ? ModColors.ROW_EVEN : ModColors.ROW_ODD);
            String rar2 = s.fishRarity.getOrDefault(e.getKey(), "");
            ItemStack icon2 = FishTextureCache.getItemStack(e.getKey(), rar2);
            if (icon2 != null) FishStatsUtils.renderScaledItem(ctx, icon2, cx2 + 1, by + 1, 10);
            else ctx.fill(cx2 + 1, by + 2, cx2 + 1 + dotS, by + 2 + dotS, col);
            int barX = cx2 + dotS + 4, valW = 40;
            int barW = cw2 - dotS - 4 - valW - rarW - 2;
            int fill = (int)(barW * e.getValue() / maxVal);
            ctx.fill(barX, by + 2, barX + barW, by + 2 + dotS, ModColors.BAR_BG);
            ctx.fill(barX, by + 2, barX + fill, by + 2 + dotS, 0x8844CC88 | 0xFF000000);
            ctx.drawString(s.getFont(), e.getKey(), barX + 2, by + 2, ModColors.TEXT_WHITE);
            int rarCol2 = FishStatsScreen.RARITY_COL.getOrDefault(rar2, ModColors.TEXT_MUTED_ARGB);
            int rarTx2  = cx2 + cw2 - valW - rarW;
            ctx.drawString(s.getFont(), FishStatsScreen.rarityDisplay(rar2), rarTx2, by + 2, rarCol2);
            if (FishStatsScreen.EVENT_NAMES.containsKey(rar2))
                FishStatsUtils.checkRectTooltip(s, rarTx2, by + 2, s.getFont().width("EVENT"), 9, List.of(Component.literal(rar2)));
            String priceShort = FishStatsUtils.formatShort(e.getValue()) + "$";
            int priceTx = cx2 + cw2 - valW;
            ctx.drawString(s.getFont(), priceShort, priceTx, by + 2, ModColors.TEXT_MEDIUM);
            FishStatsUtils.checkTooltip(s, priceTx, by + 2, priceShort, String.format("%.0f$", e.getValue()));
            boolean fav2 = FavoritesStore.INSTANCE.isFavorite(e.getKey());
            int sx2 = cx2 + cw2 - 9, sy2 = by + 2;
            ctx.drawString(s.getFont(), fav2 ? "★" : "☆", sx2, sy2, fav2 ? 0xFFFFDD00 : ModColors.TEXT_VERY_MUTED);
            s.starHits.add(new FishStatsScreen.StarHit(e.getKey(), sx2 - 1, sy2, 10, 9));
        }

        ctx.disableScissor();

        int topVisible = listH / FishStatsScreen.TOP_ROW_H;
        FishStatsUtils.renderScrollbar(s, ctx, FishStatsScreen.SB_TOP_R, cx2 + cw2 + 1, listY, listH, s.topScrollOffset, s.filteredTopValue.size(), topVisible);

        int fy = listY + listH;
        ctx.fill(x, fy, x + w, fy + footerH, ModColors.UI_FOOTER_BG);
        ctx.fill(x, fy, x + w, fy + 1, ModColors.UI_FOOTER_LINE);
        String topFooter = s.filteredTopCount.size() == s.topCount.size()
            ? I18n.get(s.topCount.size() > 1 ? "fishlog.footer.top.plural" : "fishlog.footer.top", s.topCount.size())
            : I18n.get("fishlog.footer.filtered", s.filteredTopCount.size(), s.topCount.size());
        ctx.drawString(s.getFont(), topFooter, x + 3, fy + 2, ModColors.TEXT_MUTED_ARGB);
    }
}
