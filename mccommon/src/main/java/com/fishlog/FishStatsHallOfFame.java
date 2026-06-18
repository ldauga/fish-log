package com.fishlog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class FishStatsHallOfFame {

    private static final DateTimeFormatter HOF_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int HOF_ROW_H   = 13;
    private static final int HOF_BORDER  = 2;
    private static final int HOF_OWNER_H = 62;
    private static final int HOF_PODIUM_H = 72;
    private static final int HOF_GOLD    = 0xFFFFD700;
    private static final int HOF_SILVER  = 0xFFC0C0C0;
    private static final int HOF_BRONZE  = 0xFFCD7F32;
    private static final ItemStack STEVE_HEAD = new ItemStack(Items.PLAYER_HEAD);

    static void triggerPrefetch() {
        List<String> names = Stream.concat(
            Stream.of("LeLeoOriginel"),
            Stream.concat(
                DonationStore.INSTANCE.getDonations().stream().limit(3).map(d -> d.player),
                DonationStore.INSTANCE.getDonationsByDate().stream().limit(3).map(d -> d.player)
            )
        ).distinct().collect(Collectors.toList());
        DonationHeadCache.prefetch(names);
    }

    static void render(FishStatsScreen s, GuiGraphics ctx, int x, int y, int w, int h) {
        List<DonationData> donations = DonationStore.INSTANCE.getDonations();
        triggerPrefetch();

        int footerH = 12, innerX = x + HOF_BORDER, innerY = y + HOF_BORDER;
        int innerW  = w - 2 * HOF_BORDER, innerH = h - 2 * HOF_BORDER;
        int cx      = x + w / 2;
        long now    = System.currentTimeMillis();

        renderRainbowBorder(ctx, x, y, w, h, HOF_BORDER);

        // ── Owner ─────────────────────────────────────────────────────────────
        int ownerY = innerY + 4;
        ctx.drawCenteredString(s.getFont(), "Owner :", cx, ownerY, 0xFFFF4444);
        ownerY += 10;
        int headSize = 24, headX = cx - headSize / 2;
        Optional<ItemStack> ownerHead = DonationHeadCache.get("LeLeoOriginel");
        ItemStack ownerStack = (ownerHead != null && ownerHead.isPresent()) ? ownerHead.get() : STEVE_HEAD;
        FishStatsUtils.renderScaledItem(ctx, ownerStack, headX, ownerY, headSize);
        ownerY += headSize + 4;

        String ownerName = "LeLeoOriginel";
        int nameW     = s.getFont().width(ownerName);
        int totalW    = nameW + 2 + FishStatsScreen.HOF_COFFEE_SIZE;
        int nameStartX = cx - totalW / 2;
        int charX     = nameStartX;
        for (int ci = 0; ci < ownerName.length(); ci++) {
            String ch  = String.valueOf(ownerName.charAt(ci));
            float hue  = ((now / 40 + ci * 18) % 360) / 360f;
            float bri  = 0.65f + 0.35f * (float) Math.sin(now / 250.0 + ci * 0.4);
            ctx.drawString(s.getFont(), ch, charX, ownerY, FishStatsUtils.hueToRgb(hue, bri));
            charX += s.getFont().width(ch);
        }

        s.hofCoffeeX = nameStartX + nameW + 2;
        s.hofCoffeeY = ownerY - 2;
        FishStatsUtils.renderScaledItem(ctx, new ItemStack(Blocks.COMMAND_BLOCK.asItem()), s.hofCoffeeX, s.hofCoffeeY, FishStatsScreen.HOF_COFFEE_SIZE);
        if (s.lastMx >= s.hofCoffeeX && s.lastMx < s.hofCoffeeX + FishStatsScreen.HOF_COFFEE_SIZE
                && s.lastMy >= s.hofCoffeeY && s.lastMy < s.hofCoffeeY + FishStatsScreen.HOF_COFFEE_SIZE)
            s.pendingTooltip = List.of(Component.literal("Buy me a coffee !"));

        // ── Podiums ───────────────────────────────────────────────────────────
        int sepY = innerY + HOF_OWNER_H;
        ctx.fill(innerX + 10, sepY, innerX + innerW - 10, sepY + 1, ModColors.UI_DIVIDER);

        int halfW   = innerW / 2, lcx = innerX + halfW / 2, rcx = innerX + halfW + halfW / 2;
        int slotW   = Math.min(36, Math.max(20, (halfW - 12) / 3));
        int gap     = Math.max(2, (halfW - 3 * slotW) / 4);
        int slot1stH = 22, headSizeLg = Math.min(18, slotW - 2), headSizeSm = Math.min(14, slotW - 6);
        int podiumTop = sepY + 5, podiumBase = podiumTop + HOF_PODIUM_H - 20;
        ctx.fill(innerX + halfW, sepY + 2, innerX + halfW + 1, podiumTop + HOF_PODIUM_H - 8, ModColors.UI_DIVIDER);

        ctx.drawCenteredString(s.getFont(), "✧ " + I18n.get("fishlog.hof.podium.amount"), lcx, sepY + 3, HOF_GOLD);
        ctx.drawCenteredString(s.getFont(), "⏱ " + I18n.get("fishlog.hof.podium.time"),  rcx, sepY + 3, 0xFF88CCFF);

        DonationData d1 = donations.size() >= 1 ? donations.get(0) : null;
        DonationData d2 = donations.size() >= 2 ? donations.get(1) : null;
        DonationData d3 = donations.size() >= 3 ? donations.get(2) : null;
        int lPos1X = lcx - slotW/2, lPos2X = lPos1X - slotW - gap, lPos3X = lPos1X + slotW + gap;
        renderPodiumSlot(s, ctx, d1, 0, 1, lPos1X, podiumBase - slot1stH, slotW, HOF_GOLD,   headSizeLg, now, false);
        renderPodiumSlot(s, ctx, d2, 1, 2, lPos2X, podiumBase,            slotW, HOF_SILVER, headSizeSm, now, false);
        renderPodiumSlot(s, ctx, d3, 2, 3, lPos3X, podiumBase,            slotW, HOF_BRONZE, headSizeSm, now, false);

        List<DonationData> byDate = DonationStore.INSTANCE.getDonationsByDate();
        DonationData t1 = byDate.size() >= 1 ? byDate.get(0) : null;
        DonationData t2 = byDate.size() >= 2 ? byDate.get(1) : null;
        DonationData t3 = byDate.size() >= 3 ? byDate.get(2) : null;
        int rPos1X = rcx - slotW/2, rPos2X = rPos1X - slotW - gap, rPos3X = rPos1X + slotW + gap;
        renderPodiumSlot(s, ctx, t1, 3, 1, rPos1X, podiumBase - slot1stH, slotW, HOF_GOLD,   headSizeLg, now, true);
        renderPodiumSlot(s, ctx, t2, 4, 2, rPos2X, podiumBase,            slotW, HOF_SILVER, headSizeSm, now, true);
        renderPodiumSlot(s, ctx, t3, 5, 3, rPos3X, podiumBase,            slotW, HOF_BRONZE, headSizeSm, now, true);

        // ── Liste donateurs 4+ ────────────────────────────────────────────────
        int listTop = sepY + HOF_PODIUM_H + 32, listH = h - (listTop - y) - footerH - 2;
        int colDate = innerX + innerW - 8 - 50, colAmt = colDate - 4 - 52;
        if (listH > 0) {
            ctx.fill(innerX, listTop, innerX + innerW, listTop + HOF_ROW_H, ModColors.UI_HEADER_BG);
            ctx.drawString(s.getFont(), "#",                                      innerX + 3,  listTop + 3, ModColors.TEXT_HEADER_COL);
            ctx.drawString(s.getFont(), I18n.get("fishlog.hof.col.player"), innerX + 18, listTop + 3, ModColors.TEXT_HEADER_COL);
            ctx.drawString(s.getFont(), I18n.get("fishlog.hof.col.amount"), colAmt,      listTop + 3, ModColors.TEXT_HEADER_COL);
            ctx.drawString(s.getFont(), I18n.get("fishlog.hof.col.date"),   colDate,     listTop + 3, ModColors.TEXT_HEADER_COL);
            listTop += HOF_ROW_H;
            int listCount = Math.max(0, donations.size() - 3);
            int totalPx   = listCount * HOF_ROW_H;
            int rowY      = listTop - s.hofScrollOffset;
            for (int i = 3; i < donations.size(); i++) {
                if (rowY + HOF_ROW_H <= listTop) { rowY += HOF_ROW_H; continue; }
                if (rowY >= listTop + listH) break;
                DonationData d = donations.get(i);
                ctx.fill(innerX, rowY, innerX + innerW - 6, rowY + HOF_ROW_H, i % 2 == 0 ? ModColors.ROW_EVEN : ModColors.ROW_ODD);
                ctx.drawString(s.getFont(), String.valueOf(i + 1), innerX + 3,  rowY + 3, ModColors.TEXT_MUTED);
                ctx.drawString(s.getFont(), d.player,             innerX + 18, rowY + 3, ModColors.TEXT_WHITE);
                ctx.drawString(s.getFont(), String.format("✧%.2f", d.amount), colAmt,  rowY + 3, ModColors.TEXT_PRICE);
                ctx.drawString(s.getFont(), d.date.format(HOF_DATE_FMT),      colDate, rowY + 3, ModColors.TEXT_MUTED);
                rowY += HOF_ROW_H;
            }
            if (listCount > 0)
                FishStatsUtils.renderScrollbar(s, ctx, FishStatsScreen.SB_HOF, innerX + innerW - 5, listTop, listH, s.hofScrollOffset, totalPx, listH);
        }

        // ── Footer ────────────────────────────────────────────────────────────
        int fy = y + h - footerH;
        ctx.fill(innerX, fy, innerX + innerW, y + h - HOF_BORDER, ModColors.UI_FOOTER_BG);
        ctx.fill(innerX, fy, innerX + innerW, fy + 1, ModColors.UI_FOOTER_LINE);
        int n = donations.size();
        ctx.drawCenteredString(s.getFont(),
            n <= 1 ? I18n.get("fishlog.hof.footer", n) : I18n.get("fishlog.hof.footer.plural", n),
            cx, fy + 2, ModColors.TEXT_MUTED);
    }

    private static void renderPodiumSlot(FishStatsScreen s, GuiGraphics ctx, DonationData d,
                                         int slotIdx, int rank, int x, int y, int slotW,
                                         int nameColor, int headSize, long now, boolean showDate) {
        int cx = x + slotW / 2;
        if (d == null) {
            s.hofSlotIsPlaceholder[slotIdx] = true;
            FishStatsUtils.renderScaledItem(ctx, STEVE_HEAD, cx - headSize / 2, y, headSize);
            String medal = rank == 1 ? "★" : rank == 2 ? "✦" : "✧";
            ctx.drawString(s.getFont(), medal, cx + headSize / 2 - 4, y - 1, 0xFF555555);
            ctx.drawCenteredString(s.getFont(), "Be The Next", cx, y + headSize + 2, ModColors.TEXT_MUTED);
            int iconX = cx - FishStatsScreen.HOF_COFFEE_SIZE / 2, iconY = y + headSize + 12;
            s.hofSlotCmdX[slotIdx] = iconX; s.hofSlotCmdY[slotIdx] = iconY;
            FishStatsUtils.renderScaledItem(ctx, new ItemStack(Blocks.COMMAND_BLOCK.asItem()), iconX, iconY, FishStatsScreen.HOF_COFFEE_SIZE);
            if (s.lastMx >= iconX && s.lastMx < iconX + FishStatsScreen.HOF_COFFEE_SIZE
                    && s.lastMy >= iconY && s.lastMy < iconY + FishStatsScreen.HOF_COFFEE_SIZE)
                s.pendingTooltip = List.of(Component.literal("and choose an hover text"));
        } else {
            s.hofSlotIsPlaceholder[slotIdx] = false;
            Optional<ItemStack> head = DonationHeadCache.get(d.player);
            ItemStack stack = (head != null && head.isPresent()) ? head.get() : STEVE_HEAD;
            FishStatsUtils.renderScaledItem(ctx, stack, cx - headSize / 2, y, headSize);
            String medal = rank == 1 ? "★" : rank == 2 ? "✦" : "✧";
            ctx.drawString(s.getFont(), medal, cx + headSize / 2 - 4, y - 1, nameColor);
            String name = d.player.length() > 9 ? d.player.substring(0, 8) + "." : d.player;
            ctx.drawCenteredString(s.getFont(), name, cx, y + headSize + 2, nameColor);
            String subline = showDate ? d.date.format(HOF_DATE_FMT) : String.format("✧%.2f", d.amount);
            ctx.drawCenteredString(s.getFont(), subline, cx, y + headSize + 11, showDate ? ModColors.TEXT_MUTED : ModColors.TEXT_PRICE);
        }
    }

    private static void renderRainbowBorder(GuiGraphics ctx, int x, int y, int w, int h, int thick) {
        long now = System.currentTimeMillis();
        for (int t = 0; t < thick; t++) {
            int bx = x + t, by = y + t, bw = w - 2 * t, bh = h - 2 * t;
            for (int i = 0; i < bw; i++) {
                int pos = i + t * (w + h) / thick;
                ctx.fill(bx + i, by, bx + i + 1, by + 1, FishStatsUtils.hueToRgb(((now / 40 + pos * 2) % 360) / 360f, 1f));
            }
            for (int i = 0; i < bh; i++) {
                int pos = bw + i + t * (w + h) / thick;
                ctx.fill(bx + bw - 1, by + i, bx + bw, by + i + 1, FishStatsUtils.hueToRgb(((now / 40 + pos * 2) % 360) / 360f, 1f));
            }
            for (int i = bw - 1; i >= 0; i--) {
                int pos = bw + bh + (bw - 1 - i) + t * (w + h) / thick;
                ctx.fill(bx + i, by + bh - 1, bx + i + 1, by + bh, FishStatsUtils.hueToRgb(((now / 40 + pos * 2) % 360) / 360f, 1f));
            }
            for (int i = bh - 1; i >= 0; i--) {
                int pos = 2 * bw + bh + (bh - 1 - i) + t * (w + h) / thick;
                ctx.fill(bx, by + i, bx + 1, by + i + 1, FishStatsUtils.hueToRgb(((now / 40 + pos * 2) % 360) / 360f, 1f));
            }
        }
    }
}
