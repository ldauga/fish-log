package com.fishlog;

import java.time.LocalDateTime;

public class DonationData {
    public final String        player;
    public final double        amount;
    public final LocalDateTime date;

    public DonationData(String player, double amount, LocalDateTime date) {
        this.player = player;
        this.amount = amount;
        this.date   = date;
    }
}
