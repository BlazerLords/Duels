package com.meteordevelopments.duels.tournament;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public final class TournamentRewardDelivery {
    private final int place;
    private final UUID deliveryId;
    private UUID winnerId;
    private String winnerName;
    private BigDecimal amount = BigDecimal.ZERO;
    private final List<ItemStack> items = new ArrayList<>();
    private TournamentRewardStatus status = TournamentRewardStatus.NOT_PAID;

    public TournamentRewardDelivery(final int place) {
        this(place, UUID.randomUUID());
    }

    public TournamentRewardDelivery(final int place, final UUID deliveryId) {
        this.place = place;
        this.deliveryId = deliveryId;
    }
}
