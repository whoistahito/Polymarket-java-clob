package com.polymarket.portfolio;

import com.polymarket.authentication.AuthenticationRequiredException;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Account state: positions, history and notifications. One page or snapshot per call. */
public final class Portfolio {

    /** The Data API's own default page size. */
    public static final int DEFAULT_PAGE_SIZE = 100;

    private final SigningAuthority authority;
    private final PortfolioLedger ledger;

    public Portfolio(SigningAuthority authority, PortfolioLedger ledger) {
        this.authority = Objects.requireNonNull(authority, "authority");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public PortfolioPage<PositionSnapshot> positions(PositionQuery query) throws IOException {
        return positions(query, PageCursor.firstPage(DEFAULT_PAGE_SIZE));
    }

    public PortfolioPage<PositionSnapshot> positions(PositionQuery query, PageCursor cursor)
            throws IOException {
        return ledger.positions(Objects.requireNonNull(query, "query"),
                Objects.requireNonNull(cursor, "cursor"));
    }

    public PortfolioPage<TradeRecord> trades(TradeQuery query) throws IOException {
        return trades(query, PageCursor.firstPage(DEFAULT_PAGE_SIZE));
    }

    public PortfolioPage<TradeRecord> trades(TradeQuery query, PageCursor cursor)
            throws IOException {
        return ledger.trades(Objects.requireNonNull(query, "query"),
                Objects.requireNonNull(cursor, "cursor"));
    }

    public PortfolioPage<ActivityRecord> activity(ActivityQuery query) throws IOException {
        return activity(query, PageCursor.firstPage(DEFAULT_PAGE_SIZE));
    }

    public PortfolioPage<ActivityRecord> activity(ActivityQuery query, PageCursor cursor)
            throws IOException {
        return ledger.activity(Objects.requireNonNull(query, "query"),
                Objects.requireNonNull(cursor, "cursor"));
    }

    /** One snapshot of everything unread; the endpoint has no pagination to expose. */
    public List<Notification> notifications() throws IOException {
        return ledger.notifications(
                authority.apiCredentials().orElseThrow(() -> new AuthenticationRequiredException(
                        "notifications needs L2 API credentials; supply them with withApiCredentials(...)")),
                authority.requireAccountSigner("notifications"),
                authority.signingIdentity().map(SigningIdentity::signatureType).orElse(0));
    }
}
