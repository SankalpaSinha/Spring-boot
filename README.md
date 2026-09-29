# PointsCore

A loyalty points and rewards engine. Members earn points on purchases according
to rules held in the database, climb tiers, and redeem rewards. Points are held
in dated lots and expire twelve months after they are earned.

Built with Spring Boot 4.1 (Java 21), PostgreSQL 17, Flyway and Testcontainers.

## Two ideas the design rests on

**Balances are derived, never stored.** There is no `balance` column anywhere.
Every earn, redemption and expiry is an immutable row in `ledger_entries`, and a
balance is `SUM(points)` over those rows. This is slower than reading an integer
and it is the right trade: a wrong balance cannot be caused by one bad write,
and "why is my balance 4,200?" is answerable by replaying history. A Postgres
trigger rejects any `UPDATE` or `DELETE` on that table, so the guarantee holds
against raw SQL and not merely against well-behaved application code.

**Points live in dated lots, not one pool.** Expiry is per-batch: points earned
in January die in January whatever has been earned since, so a single pooled
number cannot express which points expire when. Redemption and expiry both
consume lots soonest-expiry-first, which is the member-friendly order since
those points were about to be lost anyway.

The consequence is an invariant the whole system depends on: for any member,
`SUM(ledger_entries.points)` must equal `SUM(point_lots.points_remaining)`.
Every lot mutation therefore writes its ledger row in the same database
transaction.

## Running it

Requires JDK 21 and a Docker daemon.

```bash
docker compose up -d      # Postgres 17 on :5432
./mvnw spring-boot:run
```

Then open http://localhost:8080/swagger-ui.html.

```bash
./mvnw test               # 23 tests, against a real Postgres
```

### Docker on macOS with Colima

Colima is a lighter alternative to Docker Desktop:

```bash
brew install colima docker docker-compose
mkdir -p ~/.docker/cli-plugins
ln -sfn /opt/homebrew/opt/docker-compose/bin/docker-compose ~/.docker/cli-plugins/docker-compose
colima start --cpu 2 --memory 4 --disk 20
```

Testcontainers does not read Docker CLI contexts, so it must be told where
Colima's socket is. Create `~/.testcontainers.properties`:

```properties
docker.host=unix:///Users/YOUR_USERNAME/.colima/default/docker.sock
testcontainers.reuse.enable=true
```

Without it, tests fail with *Could not find a valid Docker environment*.

Note that `reuse.enable` keeps the container between runs, so tests must not
assume an empty database.

## Trying it out

```bash
# enrol
curl -X POST localhost:8080/api/members \
  -H 'Content-Type: application/json' \
  -d '{"name":"Asha Rao","email":"asha@example.com"}'

# a weekend coffee purchase: 10 base points x (2x weekend + 3x coffee) = 40
curl -X POST localhost:8080/api/members/1/transactions \
  -H 'Content-Type: application/json' \
  -d '{"externalRef":"RCPT-001","amount":1000.00,"category":"COFFEE","occurredAt":"2026-01-03T06:30:00Z"}'

# send the same receipt again -- returns 200 and the original, not a second award
curl -X POST localhost:8080/api/members/1/transactions \
  -H 'Content-Type: application/json' \
  -d '{"externalRef":"RCPT-001","amount":1000.00,"category":"COFFEE","occurredAt":"2026-01-03T06:30:00Z"}'

curl localhost:8080/api/members/1/balance
curl localhost:8080/api/members/1/ledger
```

## API

| Method | Path | |
|---|---|---|
| POST | `/api/members` | enrol a member |
| GET | `/api/members/{id}` | fetch a member |
| POST | `/api/members/{id}/transactions` | ingest a purchase and award points |
| GET | `/api/members/{id}/transactions` | purchase history |
| GET | `/api/members/{id}/balance` | balance, expiring-soon, next expiry |
| GET | `/api/members/{id}/ledger` | paginated points history |
| GET | `/api/rewards` | reward catalogue |
| GET/POST/PATCH | `/api/admin/rewards` | manage rewards |

Errors are RFC 9457 problem responses carrying a stable `code` field.

## Earning rules

Rules live in the `earn_rules` table rather than in code, so a campaign is an
INSERT and not a redeploy.

A `BASE` rule converts money to points (`1 point per 100 spent`). Promotions
then contribute a bonus: `CATEGORY`, `DAY_OF_WEEK` and `MIN_AMOUNT`, each
carrying its parameters as jsonb.

Promotions stack **additively**: `effectiveMultiplier = 1 + Σ(multiplier − 1)`.
So a 2x and a 3x together give 4x, not 6x. Multiplying compounds -- four
independent 2x campaigns would silently become 16x and blow through whatever
the programme budgeted. The tier bonus applies on top, and the result is
floored exactly once at the end; rounding at each step loses a fraction every
time, always downward.

Rules are evaluated as of the purchase's `occurredAt`, never as of now, so a
till that batches its uploads overnight does not cost the customer a promotion
that was running when they paid. Day-of-week is resolved in the programme's
timezone (`pointscore.zone`), because an Instant has no day-of-week until you
pick one -- 04:00 Saturday in Hyderabad is still Friday in UTC.

## Redemption and concurrency

Redemption is the one place where two requests can destroy each other. Two taps
on the same button, arriving milliseconds apart, both read a balance of 300,
both decide a 200-point reward is affordable, and the member finishes at −100
holding two rewards. Neither request did anything wrong; they interleaved.

`PointLotRepository.lockLiveLotsForMember` is annotated
`@Lock(PESSIMISTIC_WRITE)`, so Hibernate emits `SELECT ... FOR UPDATE` and
Postgres holds those rows until the transaction commits. The second request
blocks rather than reading a stale balance, then re-reads the truth and is
correctly refused.

A Java `synchronized` block would not do: it guards one JVM, while the
contention is over database rows that a second instance, a script or a psql
session can all reach.

`RedemptionServiceTest.concurrentRedemptionsNeverOverdraw` fires twenty
simultaneous redemptions at a 300-point balance and asserts exactly three
succeed, the balance lands on zero, and reward stock falls by exactly three.
Swapping the locked query for the identical unlocked one makes that test fail
with a balance of −1500.

Retries are a separate problem with a separate fix: the caller sends an
`Idempotency-Key`, and a UNIQUE constraint on that column is what actually
prevents a double spend. The pre-flight lookup is only a fast path, since two
concurrent retries can both read "not seen".

## Status

- [x] Schema, migrations, member enrolment
- [x] Earn rule engine
- [x] Purchase ingestion, balance, ledger, reward catalogue
- [x] Redemption with row locking and idempotency keys
- [ ] Points expiry job and tier recalculation
- [ ] JWT authentication and the admin/member split
