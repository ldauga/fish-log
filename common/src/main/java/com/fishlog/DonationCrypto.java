package com.fishlog;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;

public class DonationCrypto {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PREFIX = "FISHLOG_V1:";

    // Clé AES-256 hardcodée (32 octets). Empêche la création manuelle de reçus.
    private static final byte[] KEY = {
        (byte)0x4C, (byte)0x65, (byte)0x6F, (byte)0x52, (byte)0x69, (byte)0x76, (byte)0x65, (byte)0x72,
        (byte)0x46, (byte)0x69, (byte)0x73, (byte)0x68, (byte)0x4C, (byte)0x6F, (byte)0x67, (byte)0x4D,
        (byte)0x6F, (byte)0x64, (byte)0x32, (byte)0x30, (byte)0x32, (byte)0x36, (byte)0x21, (byte)0x40,
        (byte)0x23, (byte)0x24, (byte)0x25, (byte)0x5E, (byte)0x26, (byte)0x2A, (byte)0x28, (byte)0x29
    };

    public static String encrypt(String player, double amount, LocalDateTime date) {
        try {
            String payload = player + "|" + String.format("%.2f", amount) + "|" + date.format(FMT);
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);

            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(data);

            byte[] combined = new byte[16 + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, 16);
            System.arraycopy(encrypted, 0, combined, 16, encrypted.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            FishLogCommon.LOGGER.error("[FishLog] Erreur chiffrement reçu", e);
            return null;
        }
    }

    public static DonationData decrypt(String receipt) {
        if (receipt == null || !receipt.startsWith(PREFIX)) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(receipt.substring(PREFIX.length()));
            if (combined.length < 17) return null;

            byte[] iv        = Arrays.copyOfRange(combined, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(combined, 16, combined.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(iv));
            String payload = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);

            String[] parts = payload.split("\\|", 3);
            if (parts.length != 3) return null;

            String player = parts[0];
            double amount = Double.parseDouble(parts[1].replace(",", "."));
            LocalDateTime date = LocalDateTime.parse(parts[2], FMT);
            return new DonationData(player, amount, date);
        } catch (Exception e) {
            return null;
        }
    }
}
