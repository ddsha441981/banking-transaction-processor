# Banking Transaction Processor

A small, test-driven service that processes banking transactions across multiple accounts.

## Goals
- Accounts with unique IDs and balances
- Deposit, withdrawal and transfer
- Validation: no overdrafts, no invalid amounts
- Per-account ledger with timestamps
- Queries for balance and transaction history

## Assumptions
- Single currency
- In-memory storage only (no persistence)
- Money is modelled with `BigDecimal`, never `double`

## How to run
-