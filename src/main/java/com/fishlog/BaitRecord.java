package com.fishlog;

import java.time.LocalDateTime;

public class BaitRecord {
    public final LocalDateTime timestamp;
    public final String player, bait;

    public BaitRecord(LocalDateTime timestamp, String player, String bait) {
        this.timestamp = timestamp;
        this.player    = player;
        this.bait      = bait;
    }
}
