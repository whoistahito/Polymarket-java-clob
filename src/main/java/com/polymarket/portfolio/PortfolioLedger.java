package com.polymarket.portfolio;

import com.polymarket.authentication.ApiCredentials;
import java.io.IOException;
import java.util.List;

/**
 * Port for account reconciliation reads. The domain declares it; an internal adapter
 * implements it, so no transport type reaches this package.
 */
public interface PortfolioLedger {

    PortfolioPage<PositionSnapshot> positions(PositionQuery query, PageCursor cursor)
            throws IOException;

    PortfolioPage<TradeRecord> trades(TradeQuery query, PageCursor cursor) throws IOException;

    PortfolioPage<ActivityRecord> activity(ActivityQuery query, PageCursor cursor)
            throws IOException;

    /** Unread notifications for the address the L2 credentials authenticate. */
    List<Notification> notifications(ApiCredentials credentials, String address, int signatureType)
            throws IOException;
}
