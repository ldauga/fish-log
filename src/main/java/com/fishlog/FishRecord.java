package com.fishlog;

import java.time.LocalDateTime;
import java.util.Map;

public class FishRecord {

    public static final Map<String, Double> COEFFS = Map.of(
        "COMMUN",    0.5,
        "RARE",      0.5,
        "ÉPIQUE",    0.5,
        "ÉMISSAIRE", 0.5,
        "LÉGENDAIRE",1.0,
        "MYTHIQUE",  2.0,
        "",   6.0,
        "PERDU",     3.0,
        "ARTEFACT",  0.5
    );

    public final LocalDateTime timestamp;
    public final String player, rarity, fish;
    public final double sizeCm, price;

    public FishRecord(LocalDateTime timestamp, String player, String rarity, String fish, double sizeCm) {
        this.timestamp = timestamp;
        this.player    = player;
        this.rarity    = rarity;
        this.fish      = fish;
        this.sizeCm    = sizeCm;
        this.price     = sizeCm * COEFFS.getOrDefault(rarity, 0.5);
    }
}
