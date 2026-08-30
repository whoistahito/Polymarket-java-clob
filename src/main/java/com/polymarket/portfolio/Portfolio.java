package com.polymarket.portfolio;

import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** Account state: positions, history and notifications. One page or snapshot per call. */
public final class Portfolio {

    /** The Data API's own default page size. */
    public static final int DEFAULT_PAGE_SIZE = 100;

    private final SigningAuthority authority;
    private final PortfolioLedger ledger;

    public Portfolio(@NonNull SigningAuthority authority, @NonNull PortfolioLedger ledger) {
        this.authority = authority;
        this.ledger = ledger;
    }

    public PortfolioPage<PositionSnapshot> positions(@NonNull PositionQuery query)
            throws IOException {
        return positions(query, PageCursor.firstPage(DEFAULT_PAGE_SIZE));
    }

    public PortfolioPage<PositionSnapshot> positions(@NonNull PositionQuery query,
            @NonNull PageCursor cursor) throws IOException {
        return ledger.positions(query, cursor);
    }

    public PortfolioPage<TradeRecord> trades(@NonNull TradeQuery query) throws IOException {
        return trades(query, PageCursor.firstPage(DEFAULT_PAGE_SIZE));
    }

    public PortfolioPage<TradeRecord> trades(@NonNull TradeQuery query, @NonNull PageCursor cursor)
            throws IOException {
        return ledger.trades(query, cursor);
    }

    public PortfolioPage<ActivityRecord> activity(@NonNull ActivityQuery query) throws IOException {
        return activity(query, PageCursor.firstPage(DEFAULT_PAGE_SIZE));
    }

    public PortfolioPage<ActivityRecord> activity(@NonNull ActivityQuery query,
            @NonNull PageCursor cursor) throws IOException {
        return ledger.activity(query, cursor);
    }

    /**
     * One page of absolute Combo position snapshots — the holdings an accepted RFQ settles into,
     * keyed by Combo position id.
     */
    public PortfolioPage<ComboPositionSnapshot> comboPositions(@NonNull ComboPositionQuery query)
            throws IOException {
        return comboPositions(query, PageCursor.firstPage(DEFAULT_PAGE_SIZE));
    }

    public PortfolioPage<ComboPositionSnapshot> comboPositions(@NonNull ComboPositionQuery query,
            @NonNull PageCursor cursor) throws IOException {
        return ledger.comboPositions(query, cursor);
    }

    /** One page of resting orders. The CLOB pages this feed by cursor, not by offset. */
    public OpenOrderPage openOrders(@NonNull OpenOrderQuery query) throws IOException {
        return openOrders(query, OrderCursor.first());
    }

    public OpenOrderPage openOrders(@NonNull OpenOrderQuery query, @NonNull OrderCursor cursor)
            throws IOException {
        return ledger.openOrders(authority.requireApiCredentials("openOrders"),
                authority.requireAccountSigner("openOrders"), query, cursor);
    }

    /** The account's pUSD collateral balance and the allowances it has granted. */
    public BalanceSnapshot collateralBalance() throws IOException {
        return balance(AssetType.COLLATERAL, Optional.empty());
    }

    /** The account's holding of one conditional token, by its CLOB token id. */
    public BalanceSnapshot conditionalBalance(@NonNull String tokenId) throws IOException {
        if (tokenId.isBlank()) throw new IllegalArgumentException("tokenId must not be blank");
        return balance(AssetType.CONDITIONAL, Optional.of(tokenId));
    }

    private BalanceSnapshot balance(AssetType assetType, Optional<String> tokenId)
            throws IOException {
        return ledger.balance(authority.requireApiCredentials("balance"),
                authority.requireAccountSigner("balance"), assetType, tokenId,
                authority.signingIdentity().map(SigningIdentity::signatureType).orElse(0));
    }

    /** One snapshot of everything unread; the endpoint has no pagination to expose. */
    public List<Notification> notifications() throws IOException {
        return ledger.notifications(authority.requireApiCredentials("notifications"),
                authority.requireAccountSigner("notifications"),
                authority.signingIdentity().map(SigningIdentity::signatureType).orElse(0));
    }
}
