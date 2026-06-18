package com.fishlog;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FishLogFabric implements ClientModInitializer {

    private static final Pattern FISH_PATTERN     = LogParser.FISH_PATTERN;
    private static final Pattern BAIT_PATTERN     = LogParser.BAIT_PATTERN;
    private static final Pattern DONATION_PATTERN =
        Pattern.compile("Vous avez payé LeLeoOriginel ✧([\\d ]+(?:[.,]\\d+)?)");

    private KeyBinding statsKey;
    private FishCsvLogger csvLogger;
    private BaitCsvLogger baitCsvLogger;

    private String pendingHeadFish   = null;
    private String pendingHeadRarity = null;
    private int    headWatchTicks    = 0;
    private final List<ItemStack> headSnapshot = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        statsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.fishlog.stats",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category.fishlog"
        ));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Path csvPath  = client.runDirectory.toPath().resolve("fish_log.csv");
            Path baitPath = client.runDirectory.toPath().resolve("bait_log.csv");

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

            Path favPath = client.runDirectory.toPath().resolve("favorites.txt");
            FavoritesStore.INSTANCE.init(favPath);

            DonationStore.INSTANCE.init(client.runDirectory.toPath());
            DonationHeadCache.invalidate();

            if (client.player == null) return;
            Path logsDir = client.runDirectory.toPath().resolve("logs");
            String playerName = client.player.getGameProfile().getName();
            int[] imported = LogParser.parseLogsInto(logsDir, csvPath, baitPath, playerName);
            if (imported[0] > 0) FishDataStore.INSTANCE.reload();
            if (imported[1] > 0) BaitDataStore.INSTANCE.reload();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (statsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new FishStatsScreen());
                }
            }
            if (headWatchTicks > 0 && pendingHeadFish != null && client.player != null) {
                headWatchTicks--;
                for (ItemStack s : client.player.getInventory().main) {
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
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handleMessage(message.getString());
        });

        FishLogCommon.LOGGER.info("[FishLog] Mod chargé");
    }

    private void handleMessage(String raw) {
        String text = raw.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();

        Matcher m = FISH_PATTERN.matcher(text);
        if (m.matches()) {
            String player = m.group(1);
            String rarity = m.group(2);
            String fish   = m.group(3);
            String size   = m.group(4);

            MinecraftClient mc = MinecraftClient.getInstance();
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

            MinecraftClient mc = MinecraftClient.getInstance();
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
            MinecraftClient mc = MinecraftClient.getInstance();
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

    private void startWatchingForHead(MinecraftClient mc, String fish, String rarity) {
        pendingHeadFish   = fish;
        pendingHeadRarity = rarity;
        headWatchTicks    = 40;
        headSnapshot.clear();
        if (mc.player == null) return;
        for (ItemStack s : mc.player.getInventory().main) {
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
