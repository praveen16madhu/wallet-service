# wallet-service

A small wallet API (accounts, deposit, withdraw, transfer) built to show how money code stays correct when
requests are repeated, run at the same time, interrupted, or when other systems are slow or down.

Java 21, Spring Boot 3.3, PostgreSQL, Spring Data JPA, Resilience4j, Apache Kafka.

## The five problems and the fix for each

| # | What goes wrong | Fix in this project | Where |
|---|---|---|---|
| 1 | The client times out and sends the same payment twice, so money moves twice | **Idempotency-Key**: the business change and the key are saved in one transaction, the key is a primary key, and a repeat replays the stored answer | `IdempotencyService` |
| 2 | The app crashes between the debit and the credit | **One ACID transaction** for both steps; a crash rolls the debit back | `WalletService.transfer` |
| 3 | Two requests read the same balance and overwrite each other (lost update) | **Pessimistic locking** (`SELECT ... FOR UPDATE`); locks are taken in id order so opposite transfers cannot deadlock | `AccountRepository`, `WalletService` |
| 4 | A downstream service is slow, flaky or down | **Explicit connect and read timeouts** plus **bounded retries** (Resilience4j); a refused connection and a read timeout are different failures | `NotificationService` |
| 5 | Kafka redelivers an event, or a malformed event arrives | **Idempotent consumer** (`INSERT ... ON CONFLICT DO NOTHING` on the transfer id) and a **dead-letter topic**; events are published only after the commit | `kafka/` |

## Run it

Requires Java 21, Maven and a local PostgreSQL.

```bash
createdb wallet
mvn spring-boot:run          # http://localhost:8085
```

Open http://localhost:8085 for the lessons page. Each button calls the real API and shows what happened.
Set `DB_USER`, `DB_PASSWORD` and `PORT` to override the defaults.

Kafka is off by default. To use it, start a broker and run with `WALLET_KAFKA_ENABLED=true`
(and `KAFKA_BOOTSTRAP` if it is not on `localhost:9092`).

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/accounts` | `{ "owner": "Asha", "initialBalance": 1000 }` |
| GET | `/api/accounts`, `/api/accounts/{id}`, `/api/accounts/{id}/transactions` | |
| POST | `/api/accounts/{id}/deposit` and `/withdraw` | `{ "amount": 100 }`, optional `Idempotency-Key` header |
| POST | `/api/transfers` | `{ "fromAccount", "toAccount", "amount" }`, optional `Idempotency-Key` header |

Errors: 400 invalid input, 404 unknown account, 409 insufficient funds, 422 key reused with a different body.

## Tests

```bash
createdb wallet_test
mvn test
```

23 tests run against a real PostgreSQL database (locking cannot be proven on an in-memory database) and an
embedded Kafka broker. Highlights:

- 50 parallel withdrawals leave exactly the right balance; with locking switched off, updates are lost.
- The balance never goes negative under 40 parallel withdrawals of 1 from an account holding 10.
- 200 transfers in opposite directions at once finish without deadlock and conserve the total.
- 10 identical requests with the same Idempotency-Key apply exactly once.
- A transfer that crashes after the debit leaves both balances unchanged.
- A duplicate Kafka event is stored once; a malformed event lands on the dead-letter topic and does not block the next one.
- Read timeout, connection refused and retry counts are asserted separately.

## A bug the tests caught

The first version stored the idempotency record with Spring Data `save()`. For an entity with a preset id that is a
*merge*: when another request had already committed the key, the merge became an UPDATE, nothing failed, and 8 of 10
identical requests moved money. The fix is a strict `persist()` (INSERT), so the unique key makes the losers fail,
roll back, and replay the winner's answer.

## Limits (deliberate, to keep it readable)

- Money is `numeric(19,2)` in one currency. No authentication.
- The idempotency table is never cleaned up; a real system would expire old keys.
- The Kafka publish happens after commit but is not an outbox, so a crash between commit and publish can drop an event.
  The next step would be a transactional outbox table.
