# Banking Transaction Processor

A small, test-driven service that processes banking transactions across multiple accounts.

## Goals
- Accounts with unique IDs and balances
- Deposit, withdrawal and transfer
- Validation: no overdrafts, no invalid amounts
- Per-account ledger with timestamps
- Queries for balance and transaction history

## Status

| Capability | Status |
|---|---|
| `Money` value object | Done |
| `Account` with id and balance | Done |
| Deposit and withdrawal with validation | Done |
| Per-account ledger with timestamps | Done |
| In-memory repository and `BankingService` | Done |
| Transfer between accounts (atomic) | Done |
| Balance and history queries | Done |
| Thread-safety | Done (single JVM) |
| Interactive console for manual testing | Done |
| REST API | Not built (see Scope decisions) |

## How to run

Requires Java 17 and Maven.



Run the tests:

```
mvn clean test
```

Start the REST API (port 8080):

```
mvn spring-boot:run
```

| Method | URL | Purpose | Success |
|---|---|---|---|
| POST | `/api/accounts` | open an account `{"id","openingBalance"}` | 201 |
| GET | `/api/accounts/{id}/balance` | current balance | 200 |
| POST | `/api/accounts/{id}/deposits` | deposit `{"amount"}` | 200 + balance |
| POST | `/api/accounts/{id}/withdrawals` | withdraw `{"amount"}` | 200 + balance |
| POST | `/api/transfers` | transfer `{"fromAccountId","toAccountId","amount"}` | 204 |
| GET | `/api/accounts/{id}/transactions` | ledger, oldest first | 200 |

Errors use RFC 7807 problem details: 404 unknown account, 409 duplicate account, 422 insufficient funds, 400 invalid amount, same-account transfer, missing field or malformed JSON.

Example:

```
curl -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' -d '{"id":"ALICE","openingBalance":100.00}'
curl -X POST localhost:8080/api/accounts/ALICE/deposits -H 'Content-Type: application/json' -d '{"amount":25.00}'
curl localhost:8080/api/accounts/ALICE/transactions
```
## Requirements coverage

| Requirement from the brief | Where it is tested |
|---|---|
| Accounts with unique ids and balances | `AccountTest`, `InMemoryAccountRepositoryTest`, `BankingServiceTest` |
| Deposit | `AccountTest`, `BankingServiceTest` |
| Withdrawal | `AccountTest`, `BankingServiceTest` |
| Transfer | `TransferTest` |
| No overdrafts, no invalid amounts | `MoneyTest`, `AccountTest`, `TransferTest` |
| Per-account ledger with timestamps | `AccountLedgerTest`, `BankingServiceTest` |
| Balance and history queries | `BankingServiceTest`, `TransferTest` |
| Behaviour under concurrent use (beyond the brief) | `ConcurrencyTest` |
| All of the above through the public API only | `RequirementsAcceptanceTest` |

## Scope decisions
- The service is exposed through a thin Spring Boot REST layer (`BankingController`). It only translates HTTP into `BankingService` calls; the domain classes have no Spring imports and stayed unchanged. I first built and tested the domain on its own, then added the HTTP layer on top.
- A small interactive console (`BankingConsole`) is included only so the service can be exercised by hand. It is not the REST API and has a few tests of its own.
- Account ids are case-sensitive and used as given. No trimming or normalisation.

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
`InvalidAmountException` (zero amount), `InsufficientFundsException` (overdraft), `InvalidTransferException` (transfer to the same account), `AccountNotFoundException` and `DuplicateAccountException` describe what went wrong in domain terms. `Money` still throws `IllegalArgumentException` for technical misuse such as negative input. All exceptions are unchecked, because they signal a caller error or a violated rule and checked exceptions would add noise to every signature. Checked exceptions were the alternative.

**A failed operation never changes state.**
Every rejection test also asserts that the balance and the ledger are unchanged. This is the foundation for atomic transfers.

**Withdrawing the full balance is allowed.**
There is a boundary test for it, plus a one-cent overdraft test on the other side of the line.

**Service layer and repository.**
`BankingService` is the single entry point and never hands out `Account` objects, so callers cannot bypass the validation rules. Storage sits behind an `AccountRepository` interface, so a database could replace the in-memory map without touching the service. Account id uniqueness is enforced with `ConcurrentHashMap.putIfAbsent`, so the check and the insert are one atomic step.

**Ledger design.**
- Each `Transaction` is an immutable record holding type, amount, `balanceAfter`, timestamp and, for transfers, the counterparty account id. `balanceAfter` lets a reader audit an account without replaying every earlier entry.
- The opening balance is not a ledger entry. The ledger records only operations performed after the account is opened.
- `getTransactions()` returns a copy, so callers cannot modify the ledger.
- Only successful operations are recorded.

**Transfers validate first, then move money.**
`BankingService.transfer` looks up both accounts before touching either, then debits the source and credits the target. Everything that can fail (unknown account, zero amount, insufficient funds) fails before any state changes, and the credit cannot fail once the debit has succeeded, so a transfer is never applied halfway. A dedicated test covers the classic bug of debiting the source before discovering the target does not exist. Both accounts get a ledger entry (`TRANSFER_OUT` and `TRANSFER_IN`) that names the counterparty. The transfer methods on `Account` are package-private, so only the service can create transfer entries.

**Time is injected.**
`Account` and `BankingService` take a `Clock` (default `Clock.systemUTC()`), and timestamps are `Instant` values. `BankingService` passes its clock to every account it opens, so all timestamps come from one source. Tests use a controllable clock to assert exact timestamps and ordering without sleeping or flakiness. `Instant` avoids any time zone ambiguity.

**History is a read-only snapshot.**
`getTransactionHistory` returns an immutable copy in the order operations happened. Callers cannot change the ledger through it, and a list obtained earlier does not change when later operations occur. Tests cover both.

**REST layer.**
`BankingConfig` wires the plain domain classes into Spring. `ApiExceptionHandler` maps domain exceptions to statuses: 404 for an unknown account, 409 for a duplicate id, 422 for insufficient funds (the request is well-formed but cannot be processed in the current state), and 400 for invalid input. Amounts are JSON numbers read as `BigDecimal`, so `10.005` reaches `Money` unrounded and is rejected. Deposit and withdrawal return the balance read right after the operation; under concurrent requests it may already include another request's change. API tests use MockMvc against the full application context and check both the status and that failed requests leave state unchanged. With a multi-threaded web server, the per-account locking from the concurrency work now matters in practice.

**Concurrency: one lock per account, transfers lock both in id order.**
Each `Account` guards its balance and ledger with its own monitor, so the funds check, the balance update and the ledger append happen as one step. This prevents lost updates and overdrafts under contention, and keeps every ledger's last `balanceAfter` equal to the real balance. A transfer takes both account locks in a fixed order (by id), which makes it one unit and rules out the classic A->B / B->A deadlock. Locks are per account, so transfers between unrelated accounts do not block each other. The concurrency tests release many threads at once; the first version of them failed before the locking existed. Monitors were chosen over `ReentrantLock` for simplicity; `ReentrantLock` would allow timeouts and fair ordering if needed.

## Testing approach
- Developed test-first. The git history shows the red/green/refactor steps, and refactors are separate commits.
- Commits prefixed `test:` are intentionally committed before the code they describe, so they may not compile on their own. Every `feat:` and `refactor:` commit, and the final `main`, builds and passes.
- Test names describe behaviour and read as documentation of the contract.
- Value-object rules are tested in `MoneyTest`. Account tests do not repeat them.
- Concurrency tests start many threads at the same instant and run opposite transfers under a timeout, so a deadlock shows up as a failing test instead of a hanging build.
- `RequirementsAcceptanceTest` walks through each requirement of the brief using only the public service API.
- The console was also exercised by hand with its `demo` command.

## Limitations
- No persistence. State is lost when the process stops.
- Single currency, no exchange rates, no fees, no overdraft limits.
- No idempotency keys, so a retried operation is applied twice.
- Concurrency control works inside one JVM only. Multiple service instances would need database transactions or distributed locks.
- Reading two balances is not an atomic snapshot: a caller who reads account A then B can see a transfer half-way between the two reads.
- `Account` is mutable and protected by locks. This is simple and fits the current scope, but an immutable design with explicit state transitions could be easier to reason about.
- Accounts are never removed, so accounts and ledgers grow for the life of the process.
- The console has minimal input handling: it reports errors but does not offer editing, quoting or autocompletion.
- No authentication, and no pagination on the transactions endpoint.
- Spring Boot 3.5.x was chosen for a stable, well-known test setup; moving to Spring Boot 4.x is a planned upgrade.

## What I would do next
1. A thin REST layer over `BankingService`.
2. Idempotency keys, so a retried request is not applied twice.
3. Pagination and date-range filters for history queries.
4. Persistence behind `AccountRepository`, with database transactions replacing the in-process locks.
5. An atomic multi-account balance snapshot, if reporting needs one.