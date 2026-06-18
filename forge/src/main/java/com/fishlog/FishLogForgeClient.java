package com.fishlog;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Logique client Forge — uniquement chargée côté client via DistExecutor. */
class FishLogForgeClient {

    private static final Pattern FISH_PATTERN     = LogParser.FISH_PATTERN;
    private static final Pattern BAIT_PATTERN     = LogParser.BAIT_PATTERN;
    private static final Pattern DONATION_PATTERN =
        Pattern.compile("Vous avez payé LeLeoOriginel ✧([\\d ]+(?:[.,]\\d+)?)");

    private static KeyMapping statsKey;
    private FishCsvLogger csvLogger;
    private BaitCsvLogger baitCsvLogger;

    private String pendingHeadFish   = null;
    private String pendingHeadRarity = null;
    private int    headWatchTicks    = 0;
    private final List<ItemStack> headSnapshot = new ArrayList<>();

    static void init(IEventBus modEventBus) {
        FishLogForgeClient instance = new FishLogForgeClient();
        modEventBus.addListener(instance::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(instance::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(instance::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(instance::onChatReceived);
        FishLogCommon.LOGGER.info("[FishLog] Mod chargé (Forge)");
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        statsKey = new KeyMapping(
            "key.fishlog.stats",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category.fishlog"
        );
        event.register(statsKey);
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (statsKey != null) {
            while (statsKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new FishStatsScreen());
                }
            }
        }
        if (headWatchTicks > 0 && pendingHeadFish != null && mc.player != null) {
            headWatchTicks--;
            for (ItemStack s : mc.player.getInventory().items) {
                if (!s.isEmpty() && s.getItem() == Items.PLAYER_HEAD && isNewHead(s)) {
                    FishTextureCache.cacheHeadStack(pendingHeadFish, pendingHeadRarity, s);
                    FishLogCommon.LOGGER.info("[FishLog] Tête HDB capturée pour {}", pendingHeadFish);
                    headSnapshot.add(s.copy());
                    pendingHeadFish   = null;
                    pendingHeadRarity = null;
                    headWatchTicks    = 0;
                    break;
                }
            }
        }
    }

    private void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path csvPath  = gameDir.resolve("fish_log.csv");
        Path baitPath = gameDir.resolve("bait_log.csv");

        if (csvLogger == null) {
            csvLogger = new FishCsvLogger(csvPath);
            FishDataStore.INSTANCE.init(csvPath);
        } else {
            FishDataStore.INSTANCE.reload();
        }
        if (baitCsvLogger == null) {
            baitCsvLogger = new BaitCsvLogger(baitPath);
            BaitDataStore.INSTANCE.init(baitPath);
        } else {
            BaitDataStore.INSTANCE.reload();
        }

        Path favPath = gameDir.resolve("favorites.txt");
        FavoritesStore.INSTANCE.init(favPath);

        DonationStore.INSTANCE.init(gameDir);
        DonationHeadCache.invalidate();

        if (mc.player == null) return;
        Path logsDir = gameDir.resolve("logs");
        String playerName = mc.player.getGameProfile().getName();
        int[] imported = LogParser.parseLogsInto(logsDir, csvPath, baitPath, playerName);
        if (imported[0] > 0) FishDataStore.INSTANCE.reload();
        if (imported[1] > 0) BaitDataStore.INSTANCE.reload();
    }

    private void onChatReceived(ClientChatReceivedEvent event) {
        handleMessage(event.getMessage().getString());
    }

    private void handleMessage(String raw) {
        String text = raw.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();

        Matcher m = FISH_PATTERN.matcher(text);
        if (m.matches()) {
            String player = m.group(1);
            String rarity = m.group(2);
            String fish   = m.group(3);
            String size   = m.group(4);

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            String localName = mc.player.getGameProfile().getName();
            if (!player.equalsIgnoreCase(localName)) return;

            if (csvLogger == null) return;
            FishLogCommon.LOGGER.info("[FishLog] Prise : {} {} {} {}cm", rarity, fish, player, size);
            csvLogger.log(player, rarity, fish, size);
            if (FishTextureCache.isHdbFish(FishTextureCache.normalize(fish))) {
                startWatchingForHead(mc, fish, rarity);
            }
            return;
        }

        Matcher bm = BAIT_PATTERN.matcher(text);
        if (bm.matches()) {
            String player = bm.group(1);
            String bait   = bm.group(2);

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            String localName = mc.player.getGameProfile().getName();
            if (!player.equalsIgnoreCase(localName)) return;

            if (baitCsvLogger == null) return;
            FishLogCommon.LOGGER.info("[FishLog] Appât : {} {}", bait, player);
            baitCsvLogger.log(player, bait);
            return;
        }

        Matcher dm = DONATION_PATTERN.matcher(text);
        if (dm.find()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            String player = mc.player.getGameProfile().getName();
            double amount = Double.parseDouble(dm.group(1).replace(" ", "").replace(",", "."));
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String encrypted = DonationCrypto.encrypt(player, amount, now);
            if (encrypted != null) {
                DonationStore.INSTANCE.saveReceipt(encrypted);
                FishLogCommon.LOGGER.info("[FishLog] Don enregistré : {} → {}", player, amount);
            }
        }
    }

    private void startWatchingForHead(Minecraft mc, String fish, String rarity) {
        pendingHeadFish   = fish;
        pendingHeadRarity = rarity;
        headWatchTicks    = 40;
        headSnapshot.clear();
        if (mc.player == null) return;
        for (ItemStack s : mc.player.getInventory().items) {
            if (!s.isEmpty() && s.getItem() == Items.PLAYER_HEAD) {
                headSnapshot.add(s.copy());
            }
        }
    }

    private boolean isNewHead(ItemStack s) {
        for (ItemStack snap : headSnapshot) {
            if (snap.getItem() == s.getItem()
                    && snap.getComponents().equals(s.getComponents())) {
                return false;
            }
        }
        return true;
    }
}
