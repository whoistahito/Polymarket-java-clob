# Domain Language

## Account Signer

The externally owned account that controls account credentials and authorizes actions for a Trading Wallet.

## Trading Wallet

The account wallet that holds collateral and positions and is named as the maker of an order. It may be an EOA, Proxy Wallet, Safe Wallet, or Deposit Wallet.

## Signing Identity

The combination of Account Signer, Trading Wallet, and wallet type needed to determine who authorizes an order and which signature rules apply.

## API Credentials

The secret-bearing credentials that authenticate private API operations. They authorize requests but do not themselves sign orders.

## Market Rule Snapshot

The observed executable constraints for one market at one point in time, including price grid, minimum share quantity, and negative-risk state.

## Order Intent

A trader's requested action with explicit units, execution policy, price protection, and lifetime. An Order Intent is not yet signed or submitted.

## Protected Price

The worst price an immediate Order Intent permits after considering enough available depth to satisfy its execution policy.

## Signed Order

An immutable order authorization produced from an Order Intent, a Market Rule Snapshot, a Signing Identity, and explicit signing inputs.

## Submission Outcome

The delivery disposition of an order write: Accepted, Rejected, or Unknown. Unknown means delivery cannot be proved either way and must not be treated as rejection.

## Settlement Outcome

The observed execution disposition after following the durable identifiers returned by an accepted order: Confirmed, Failed, Pending, or an explicit inconsistency.

## RFQ

A request for an executable Combo quote. Its RFQ ID is the durable identity used to recover progress after local timeout or uncertain delivery.

## Quote

The executable terms returned for an RFQ, including its acceptance deadline, Combo position, amounts, and builder attribution.

## Authoritative Subscription

The complete set of stream subjects currently requested by a consumer. Reconnection restores this set rather than replaying historical subscription commands.

## Registration

A closeable association between a stream subject and a handler. Closing a Registration removes that handler without closing unrelated subscriptions.

## Heartbeat

An explicitly started dead-man signal whose absence causes the exchange to cancel open orders. A Heartbeat is an order-safety mechanism, not a connection keepalive.
