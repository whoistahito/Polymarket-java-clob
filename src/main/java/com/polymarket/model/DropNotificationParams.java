package com.polymarket.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Parameters for deleting notifications by id. Mirrors TS {@code DropNotificationParams}. */
@Value
@Builder
public class DropNotificationParams {

    List<String> ids;
}
