# Payment Gateway — Project Approach & Design Notes

## MR Bank Access URLs

- Project base URL: `http://localhost:8000`
- Public auth base URL: `http://localhost:8000/api/v1/public/auth`
- Swagger UI: `http://localhost:8000/swagger-ui/index.html`
- OpenAPI docs JSON: `http://localhost:8000/v3/api-docs`

## Why This Project

The real motivation is to get hands-on with the parts of backend engineering that are hard to fake: **atomicity, concurrency, and service-to-service reliability**. Payment systems force you to deal with these properly — money can't be lost, duplicated, or left in an inconsistent state. That's a better story for interviews than "I wanted a job at this company," even if that's part of the honest motivation too. It's fine to mention interest in fintech companies (e.g. Paytm) as a bonus, framed as genuine interest in the domain — not as the headline reason.

No fancy frontend. This is an API-first, backend-focused build.

---

## The Two-Project Architecture

This isn't one monolith — it's **two separate Spring Boot projects** talking over REST:

### 1. Payment Gateway Service (the main app)
Owns users, KYC, linked bank accounts, and orchestrates payments.

### 2. Bank Service (a mock external bank)
A small standalone service simulating a real bank's API — it owns actual account balances and processes credit/debit.

**Why separate them instead of one project?**
- Mirrors reality — a payment gateway never owns the money; banks do. The gateway just orchestrates.
- Forces real practice with inter-service HTTP calls (WebClient), timeouts, and retries — not just CRUD.
- Lets you honestly say "I've worked with microservices concepts" (service-to-service comms, fault tolerance) without over-engineering with service discovery or message queues, which aren't needed at this scale.
- Creates real failure scenarios to reason about: what happens if the Bank Service is slow or down mid-transaction?

---

## Module Breakdown

### Payment Gateway modules
1. Auth (register / login)
2. KYC & profile completion
3. Add bank account(s) — verified via a call to the Bank Service
4. Check balance
5. Search account
6. Send payment
7. Transaction history (ledger)

### Bank Service modules
1. Simple account registration (no real KYC — mock bank)
2. Credit / Debit APIs
3. Check balance
4. Transaction history

---

## Data Model & Reasoning

- **User (auth) table** — email, hashed password (BCrypt), a `kycComplete` boolean for fast routing checks, and a `state` enum.
- **KYC Document table** (linked to user) — records *every* KYC attempt (unique document ID), with its own status enum (`pending` / `failed` / `completed`). This is the full audit trail; the boolean on the user table is just a cheap flag so you don't need a join for routing decisions.
- **Account table** — `id`, unique `accountNumber`, `balance`, `state` enum (`active` / `frozen` / `closed`).
- **Transaction (ledger) table — double-entry style.** Every transfer writes **two rows**, one per account:
    - `transactionId`, `accountId` (whose row this is), `counterpartyAccountId`, `amount`, `type` enum (`CREDIT`/`DEBIT`), `status`, `timestamp`, `state`.
    - **Why double-entry over a single row + OR query?** A single-row design (sender/receiver columns, queried with `account = ? OR counterparty = ?`) forces the application to flip the sign depending on which side the current user is on — messy and error-prone. Two rows means each account's history is already correct from that account's perspective; no flip logic, clean indexing on `accountId` alone. It also mirrors real accounting systems — a solid interview talking point.

**Every table has a `state` enum** instead of hard deletes — soft-delete pattern. Nothing is ever physically removed; accounts can be frozen/reactivated, users can be disabled, without losing history.

---

## Ledger vs. Logging — Important Distinction

- **Ledger** = strictly actual money movement (credit/debit). Lives in the main relational DB (Postgres). Append-only, source of truth for balance, and is exactly what powers the transaction history feature.
- **Everything else** (logins, balance checks, general activity) = logging/audit, *not* ledger. For this project's scope, SLF4J + Logback is enough. In production, this kind of high-volume, non-critical data typically ships to something like the **ELK stack** (Elasticsearch, Logstash, Kibana) for search/analytics, since it doesn't need the same consistency guarantees as financial data.

---

## Auth & Registration Flow

Multi-step, not one giant form — each step commits independently and is internally atomic (the *whole journey* isn't one big transaction; that would be fragile).

1. **Register** — email + password only. Password hashed with BCrypt.
2. **Send OTP.**
3. **Verify OTP** — on success, store `otp:verified:<email> = true` in **Redis** with a short TTL (~10–15 min). Never trust the client's claim that it verified — always re-check server-side.
4. **Final form submit** — check Redis first (fast, fail-fast on unverified), *then* check the DB for an existing email. Order matters: Redis before DB, since Redis lookups are cheaper and unverified attempts are the common case.
5. **KYC form** — name + document ID, written to the KYC Document table with its own status.
6. Once KYC is marked completed → **auto-generate the Account** (account number, balance = 0, state = active).
7. **Login** issues a JWT.

**Endpoint structure:**
`/api/v1/public/auth/*` → `register`, `authenticate`, `send-otp`, `verify-otp`. The `public` prefix is a simple convention so the security filter can skip token checks on these paths; everything else requires a valid JWT.

---

## Inter-Service Communication (Gateway ↔ Bank Service)

- **WebClient** (Spring WebFlux) — the current recommended approach; `RestTemplate` is being phased out.
- **Resilience4j** wrapped around those calls — retries, timeouts, circuit breaker — so the gateway handles the Bank Service being slow or unavailable gracefully instead of failing hard.

---

## Scope Control — What to Go Deep On vs. Skip

**Go deep on:**
- Atomic debit+credit as a single DB transaction
- Idempotency for payment requests
- Concurrency / race conditions on balance updates
- Double-entry ledger design
- Resilience for the Bank Service calls (timeouts, retries, circuit breaking)

**Keep light or skip for now:**
- Frontend
- QR codes, contact-list search
- Real identity verification (Aadhaar etc.) — just a document ID field for now
- Service discovery, message queues, full microservices tooling

---

## Build Order

**Phase 1 — Payment Gateway core**
1. Project skeleton
2. Auth (register/login, JWT, BCrypt)
3. OTP verification (Redis)
4. KYC flow
5. Auto account creation

**Phase 2 — Bank Service (separate project)**
1. Simple account registration
2. Credit / Debit APIs
3. Check balance
4. Transaction history

**Phase 3 — Integration & core payment features**
1. Add bank account (WebClient call to Bank Service + Resilience4j)
2. Check balance
3. Search account
4. Send payment (atomic debit/credit, double-entry ledger write)
5. Transaction history

---

## Interview Narrative

- Lead with: payment systems sit at the intersection of consistency, concurrency, and reliability — money can't be lost or duplicated — so this project was a way to get real experience with idempotency, safe concurrent request handling, and designing for failure.
- It's fine to add that this is part of why fintech companies specifically are appealing.
- Worth mentioning explicitly: the deliberate choice *not* to over-engineer (no premature microservices tooling, no unnecessary complexity) — that shows judgment, not just tool knowledge.
