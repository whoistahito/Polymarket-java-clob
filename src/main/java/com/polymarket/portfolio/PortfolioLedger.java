package com.polymarket.portfolio;

import com.polymarket.authentication.ApiCredentials;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

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

    /** One page of absolute Combo position snapshots. */
    PortfolioPage<ComboPositionSnapshot> comboPositions(ComboPositionQuery query, PageCursor cursor)
            throws IOException;

    /** One page of resting orders for the account the L2 credentials authenticate. */
    OpenOrderPage openOrders(ApiCredentials credentials, String address, OpenOrderQuery query,
            OrderCursor cursor) throws IOException;

    /** The balance and allowances for the account the L2 credentials authenticate. */
    BalanceSnapshot balance(ApiCredentials credentials, String address, AssetType assetType,
            Optional<String> tokenId, int signatureType) throws IOException;

    /** Unread notifications for the address the L2 credentials authenticate. */
    List<Notification> notifications(ApiCredentials credentials, String address, int signatureType)
            throws IOException;
}
