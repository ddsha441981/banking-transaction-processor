# Banking Transaction Processor

A small, test-driven service that processes banking transactions across multiple accounts.

## Goals
- Accounts with unique IDs and balances
- Deposit, withdrawal and transfer
- Validation: no overdrafts, no invalid amounts
- Per-account ledger with timestamps
- Queries for balance and transaction history

## Status

| Capability | Status  |
|---|---------|
| `Money` value object | Done    |
| `Account` with id and balance | Done    |
| Deposit and withdrawal with validation | Done    |
| Per-account ledger with timestamps | Done    |
| In-memory repository and `BankingService` | Done    |
| Transfer between accounts | Done (single-threaded) |
| Balance and history queries | Done    |
| Thread-safety | Planned |
| Transaction history query | Done |
| Thread-safety | Done (single JVM) |
| Transfer between accounts | Done |

## How to run

Requires Java 17 and Maven.

```
mvn clean test
```

There is no `main` class yet. The behaviour is documented and verified by the tests.

## Assumptions
- Single currency
- In-memory storage only (no persistence)
- Amounts have at most two decimal places
- Account ids are unique strings supplied by the caller

## Design decisions and trade-offs

**Money is a value object, not a raw `BigDecimal`.**
`Money` is immutable, never negative and always has two decimal places. Over-precise input such as `10.005` is rejected instead of being silently rounded, because hidden rounding is a classic source of ledger discrepancies. Validation lives in one place, so `Account` never has to check for negative values.
This came out of a refactor: the first version of `Account` took a `BigDecimal` and validated it itself. Introducing `Money` removed that duplication.

**Business rules have their own exceptions.**
`InvalidAmountException` (zero amount) and `InsufficientFundsException` (overdraft) describe what went wrong in domain terms. `Money` still throws `IllegalArgumentException` for technical misuse such as negative input. All exceptions are unchecked, because they signal a caller error or a violated rule and checked exceptions would add noise to every signature. Checked exceptions were the alternative.

**Service layer and repository.**
`BankingService` is the single entry point and never hands out `Account` objects, so callers cannot bypass the validation rules. Storage sits behind an `AccountRepository` interface, so a database could replace the in-memory map without touching the service. Account id uniqueness is enforced with `ConcurrentHashMap.putIfAbsent`, so the check and the insert are one atomic step.

**A failed operation never changes state.**
Every rejection test also asserts that the balance and the ledger are unchanged. This is the foundation for atomic transfers.

**Withdrawing the full balance is allowed.**
There is a boundary test for it, plus a one-cent overdraft test on the other side of the line.

**Transfers validate first, then move money.**
`BankingService.transfer` looks up both accounts before touching either, then debits the source and credits the target. Everything that can fail (unknown account, zero amount, insufficient funds) fails before any state changes, and the credit cannot fail once the debit has succeeded, so a transfer is never applied halfway. A dedicated test covers the classic bug of debiting the source before discovering the target does not exist. Both accounts get a ledger entry (`TRANSFER_OUT` and `TRANSFER_IN`) that names the counterparty. The transfer methods on `Account` are package-private, so only the service can create transfer entries.

**History is a read-only snapshot.**
`getTransactionHistory` returns an immutable copy in the order operations happened. Callers cannot change the ledger through it, and a list obtained earlier does not change when later operations occur. Tests cover both. The service never exposes `Account` objects, so the ledger can only grow through validated operations.

**One clock for the whole service.**
`BankingService` passes its `Clock` to every account it opens, so all timestamps come from one source. Tests use a controllable clock to assert exact timestamps and ordering without sleeping.
**Concurrency: one lock per account, transfers lock both in id order.**
Each `Account` guards its balance and ledger with its own monitor, so the funds check, the balance update and the ledger append happen as one step. This prevents lost updates and overdrafts under contention, and keeps every ledger's last `balanceAfter` equal to the real balance. A transfer takes both account locks in a fixed order (by id), which makes it one unit and rules out the classic A->B / B->A deadlock. Locks are per account, so transfers between unrelated accounts do not block each other. The concurrency tests release many threads at once; the first version of them failed before the locking existed. Monitors were chosen over `ReentrantLock` for simplicity; `ReentrantLock` would allow timeouts and fair ordering if needed.

**Ledger design.**
- Each `Transaction` is an immutable record holding type, amount, `balanceAfter` and timestamp. `balanceAfter` lets a reader audit an account without replaying every earlier entry.
- The opening balance is not a ledger entry. The ledger records only operations performed after the account is opened.
- `getTransactions()` returns a copy, so callers cannot modify the ledger.
- Only successful operations are recorded.

**Time is injected.**
`Account` takes a `Clock` (default `Clock.systemUTC()`), and timestamps are `Instant` values. Tests use a fixed clock, so timestamps are deterministic and there is no sleeping or flakiness. `Instant` avoids any time zone ambiguity.

## Testing approach
- Developed test-first. The git history shows the red/green/refactor steps, and refactors are separate commits.
- Test names describe behaviour and read as documentation of the contract.
- Value-object rules are tested in `MoneyTest`. Account tests do not repeat them.

## Limitations
- `Account` is not thread-safe yet. Concurrency handling is planned as a later step.
- No persistence. State is lost when the process stops.
- Concurrency control works inside one JVM only. Multiple service instances would need database transactions or distributed locks.
- Reading two balances is not an atomic snapshot: a caller who reads account A then B can see a transfer half-way between the two reads.
- Accounts are never removed, so locks and ledgers grow for the life of the process.
- No idempotency keys, so a retried operation is applied twice.
- Account is mutable. This is simple and works for the current scope, but an immutable design with explicit state transitions could be easier to reason about under concurrency.
- Transfers are atomic only for a single caller. Under concurrent use, two threads could both pass the funds check and overdraw an account (check-then-act race). Planned fix: lock both accounts in a consistent order.

## What I would do next
1. Add the repository and `BankingService`, so callers use one entry point.
2. Implement atomic transfers that write a ledger entry on both sides.
3. Add balance and history queries.
4. Make concurrent transfers safe by locking accounts in a consistent order to avoid deadlock.
5. Add idempotency keys and a persistence layer.