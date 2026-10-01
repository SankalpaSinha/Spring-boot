# PointsCore

[![CI](https://github.com/SankalpaSinha/Spring-boot/actions/workflows/ci.yml/badge.svg)](https://github.com/SankalpaSinha/Spring-boot/actions/workflows/ci.yml)

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
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=change-me-please ./mvnw spring-boot:run
```

`ADMIN_EMAIL` and `ADMIN_PASSWORD` create the first admin at startup; admins
are not enrolled through the public API. Set `JWT_SECRET` (32+ bytes) too for
anything beyond a laptop, or tokens stop working on every restart.

Then open http://localhost:8080/swagger-ui.html.

```bash
./mvnw test               # 53 tests, against a real Postgres
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

## Deploying

The app is a single container (see `Dockerfile`) that reads its database and
secrets from the environment, so it runs anywhere that can run Docker and
reach a Postgres. `render.yaml` is a Render blueprint for the free tier:

1. Create a free Postgres on [Neon](https://neon.tech) and copy the host,
   database, user and password.
2. On [Render](https://render.com), choose **New > Blueprint**, pick this repo,
   and fill in the prompted values: `DB_URL` as
   `jdbc:postgresql://HOST/DBNAME?sslmode=require`, `DB_USER`, `DB_PASSWORD`,
   `ADMIN_EMAIL` and `ADMIN_PASSWORD`. `JWT_SECRET` is generated for you.
3. Deploy. Flyway creates the schema on first boot and the admin account is
   created from the two variables. Swagger is at `/swagger-ui.html`.

The free instance sleeps after fifteen idle minutes and takes about a minute
to wake, which is fine for a demo and wrong for a till.

To run the container locally against the compose database:

```bash
docker build -t pointscore .
docker run -p 8080:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/pointscore \
  -e DB_USER=pointscore -e DB_PASSWORD=pointscore \
  -e ADMIN_EMAIL=admin@example.com -e ADMIN_PASSWORD=change-me-please pointscore
```

## Trying it out

```bash
# sign up -- enrols the member and creates their login
curl -X POST localhost:8080/api/members \
  -H 'Content-Type: application/json' \
  -d '{"name":"Asha Rao","email":"asha@example.com","password":"a-long-passphrase"}'

# purchases come from the till, so they need an admin token
ADMIN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"change-me-please"}' | jq -r .token)

# a weekend coffee purchase: 10 base points x (2x weekend + 3x coffee) = 40
curl -X POST localhost:8080/api/members/1/transactions \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"externalRef":"RCPT-001","amount":1000.00,"category":"COFFEE","occurredAt":"2026-01-03T06:30:00Z"}'

# send the same receipt again -- returns 200 and the original, not a second award
curl -X POST localhost:8080/api/members/1/transactions \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"externalRef":"RCPT-001","amount":1000.00,"category":"COFFEE","occurredAt":"2026-01-03T06:30:00Z"}'

# the member reads their own balance with their own token
ASHA=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","password":"a-long-passphrase"}' | jq -r .token)

curl -H "Authorization: Bearer $ASHA" localhost:8080/api/members/1/balance
curl -H "Authorization: Bearer $ASHA" localhost:8080/api/members/1/ledger
```

## API

| Method | Path | | Who |
|---|---|---|---|
| POST | `/api/members` | sign up: enrol a member and create their login | anyone |
| POST | `/api/auth/login` | exchange email and password for a token | anyone |
| GET | `/api/auth/me` | what the token says about you | any token |
| GET | `/api/members/{id}` | fetch a member | self, admin |
| POST | `/api/members/{id}/transactions` | ingest a purchase and award points | admin |
| GET | `/api/members/{id}/transactions` | purchase history | self, admin |
| GET | `/api/members/{id}/balance` | balance, expiring-soon, next expiry | self, admin |
| GET | `/api/members/{id}/ledger` | paginated points history | self, admin |
| GET | `/api/members/{id}/tier/history` | every tier movement and why | self, admin |
| POST | `/api/members/{id}/redemptions` | redeem a reward | self, admin |
| GET | `/api/members/{id}/redemptions` | redemption history | self, admin |
| GET | `/api/rewards` | reward catalogue | anyone |
| GET/POST/PATCH | `/api/admin/rewards` | manage rewards | admin |

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

## Expiry and tiers

Two nightly jobs, in the programme's timezone. Expiry runs at 02:00 and drains
every lot past its date, writing a negative `EXPIRE` ledger row per lot in the
same transaction so the ledger/lot invariant survives. Draining the lot is what
makes a second run a no-op: the query only returns lots with points left.

Tier review runs at 03:00. Status is judged on points **earned** in a rolling
twelve months, never on balance, so redeeming a reward can never cost anyone
their tier -- a programme that demoted members for spending would punish the
exact behaviour it exists to encourage. A purchase recalculates the buyer's
tier on the spot, so upgrades are immediate. The nightly review is what makes
the window roll for members who stop buying. Every movement is recorded in
`tier_changes` with the qualifying figure it was based on, because "why am I no
longer Gold?" is not answerable from a single column.

Both jobs are off under the `test` profile so they cannot fire mid-test; the
services behind them are tested directly.

## Authentication

Stateless bearer tokens: `POST /api/auth/login` returns an HS256-signed JWT
carrying the account's role and, for members, their `memberId`. Every other
request sends it as `Authorization: Bearer <token>`.

Logins live in `user_accounts`, apart from `members`, because a member is a
loyalty customer and an account is a way in: staff need the second without the
first. A CHECK constraint ties the two together -- a `MEMBER` account must
point at a member and an `ADMIN` must not -- so the authorisation code can
assume it.

The rules sit in one place, `SecurityConfig`, in order. Members may only touch
URLs under their own `/api/members/{memberId}`; the whole `/api/admin/**` tree
is admin-only; and ingesting a purchase is admin-only even on your own account,
since a member who can post their own receipts can award themselves points.
Grouping member-scoped URLs under one prefix is what lets a single rule cover
them, rather than an annotation per endpoint where a missed one is a hole
nobody sees.

Failed logins take the same time whether the address exists or not, and say
the same thing, so the login endpoint cannot be used to enumerate members.
Sign-up still answers 201 or 409, which is an oracle by nature; that is the
accepted trade-off of a public sign-up. Security failures render as the same
RFC 9457 problem responses as everything else, with codes `UNAUTHENTICATED`
(401), `INVALID_CREDENTIALS` (401), `FORBIDDEN` (403) and `PASSWORD_TOO_LONG`
(400, bcrypt's 72-byte limit measured in bytes, not characters).

## Known limitations

- Tokens live twelve hours and there is no refresh or revocation. Logging out
  is forgetting the token.
- A member created before the accounts table existed, or enrolled through
  `MemberService` directly, has no login and no way to claim one. A greenfield
  deployment never hits this; a migration of an existing programme would need
  an admin "create login for member" step.
- The nightly jobs assume one running instance. A second copy does redundant
  work but nothing incorrect; a real deployment would add a locking scheduler.
- The bootstrap admin's address is not validated at startup beyond being
  non-blank, so a value the login endpoint's `@Email` check rejects would be
  created and then unable to log in.

## Status

- [x] Schema, migrations, member enrolment
- [x] Earn rule engine
- [x] Purchase ingestion, balance, ledger, reward catalogue
- [x] Redemption with row locking and idempotency keys
- [x] Points expiry job and tier recalculation
- [x] JWT authentication and the admin/member split
- [x] Tier review job, tier history endpoint, CI
