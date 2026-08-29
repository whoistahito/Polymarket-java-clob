package com.polymarket.portfolio;

import java.util.List;
import lombok.NonNull;

/** Immutable Combo position filter. Unset fields are never sent, so the server's defaults apply. */
public final class ComboPositionQuery {

    private final String user;
    private final List<ComboStatus.Known> statuses;
    private final List<String> comboConditionIds;

    private ComboPositionQuery(String user, List<ComboStatus.Known> statuses,
            List<String> comboConditionIds) {
        this.user = user;
        this.statuses = statuses;
        this.comboConditionIds = comboConditionIds;
    }

    /** The Data API rejects a Combo read without a user, so it is required here. */
    public static ComboPositionQuery forUser(@NonNull String user) {
        return new ComboPositionQuery(QueryBoundaries.address(user, "user"), List.of(), List.of());
    }

    public ComboPositionQuery statuses(@NonNull List<ComboStatus.Known> statuses) {
        return new ComboPositionQuery(user, List.copyOf(statuses), comboConditionIds);
    }

    public ComboPositionQuery combos(@NonNull List<String> comboConditionIds) {
        return new ComboPositionQuery(user, statuses,
                QueryBoundaries.comboConditionIds(comboConditionIds));
    }

    public String user() {
        return user;
    }

    public List<ComboStatus.Known> statuses() {
        return statuses;
    }

    public List<String> comboConditionIds() {
        return comboConditionIds;
    }
}
