# Credit Card Fraud Detection Simulator

A small Java simulator that models a bank running a set of pluggable fraud
checks against credit card transactions, driven entirely by a JSON config
file (no hardcoded scenarios).

## Structure

| File | Purpose |
|---|---|
| `Models.java` | Core data classes: `Customer`, `CreditCard`, `Transaction` |
| `Fraud.java` | Fraud check hierarchy (`FraudCheck` + 5 implementations), `FraudDetector`, `Bank`, `Report` |
| `JsonParser.java` | Minimal, dependency-free JSON parser (`JsonObject`, `JsonArray`) |
| `Main.java` | Entry point — loads `data.json` and runs the simulation |

## OOP Concepts Demonstrated

- **Abstraction** — `FraudCheck` is an abstract base class
- **Inheritance** — `HighAmountCheck`, `UnusualLocationCheck`, `NightTimeCheck`,
  `RapidTransactionCheck`, and `ForeignTransactionCheck` all extend `FraudCheck`
- **Polymorphism** — `FraudDetector` calls `check()` on each check without
  knowing its concrete type
- **Encapsulation** — private fields with public accessors throughout

## Fraud Checks

- **High Amount** — flags transactions above a configurable limit
- **Unusual Location** — flags transactions outside the cardholder's home city
- **Night Time** — flags transactions inside a configurable "suspicious hours" window
- **Rapid Transactions** — flags too many transactions within a short time window
- **Foreign Transaction** — flags international transactions

A card is automatically blocked once a single transaction trips 3 or more checks.

## Running

This project expects a `data.json` config file (not included here) describing
the bank, customers, cards, fraud check parameters, and transaction scenarios.

\`\`\`bash
javac *.java
java Main data.json
\`\`\`

If no path is given, it defaults to looking for `data.json` in the current directory.

## Output

The simulator prints each transaction as it's processed (approved / flagged /
blocked, with reasons), followed by a final summary report: totals, per-card
breakdown, and a list of all flagged/blocked transactions.