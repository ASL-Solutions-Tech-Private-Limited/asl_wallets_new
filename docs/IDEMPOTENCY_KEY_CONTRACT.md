# Idempotency-Key — app → backend contract

How the **retailer app** generates and sends the idempotency key on money-moving requests
(AEPS, Payout, Add money, Recharge, BBPS, DMT, Micro ATM). Share this with the backend team so
both sides agree on the contract.

---

## 1. Who generates it
The **app generates the key** and sends it. The backend never generates it — it only reads,
validates, and de-duplicates on it.

## 2. How the app generates it
A random **UUID v4**:

```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

- Produced by `UUID.randomUUID().toString()`.
- 36 characters, lowercase hex + hyphens → already satisfies the backend's validation
  (8–128 chars, charset `[A-Za-z0-9_.:-]`).

## 3. Where it is sent
As an **HTTP request header** on the transaction call:

```
Idempotency-Key: <uuid>
```

(The backend already accepts this header case-insensitively, and also `x-idempotency-key`
or a body field `idempotencyKey` / `idempotency_key` — but the app always uses the header above.)

## 4. Lifecycle — the important part
The app follows standard idempotency semantics:

- **One key per transaction attempt.** Generated once when the retailer fills the form.
- **Same key on retry** of the *same* transaction (e.g. the retailer taps "Retry" after a timeout)
  → the backend must return the original result / block a double-charge, **not** start a new transaction.
- **New key for a genuinely new transaction** — the app regenerates the key whenever the inputs
  change (amount, mobile, recipient, type) or the retailer starts a fresh transaction.

So: **same key = same transaction** (de-duplicate); **different key = different transaction** (process).

## 5. What the backend should do (already implemented — for reference)
- Validate format; reject with `400` if it fails (8–128 chars, `[A-Za-z0-9_.:-]`).
- Scope the key per `user + transaction-domain` (e.g. `AEPS_TXN`, `PAYOUT_TXN`, `BBPS_BILL_PAYMENT`,
  and — when the mATM endpoints ship — `MATM_TXN`).
- Fingerprint the request payload (amount, recipient, etc.). If the **same key** arrives with a
  **different payload** → reject (`400` payload mismatch).
- If a transaction with that key is **in progress** → `409` conflict ("still processing").
- If it already **completed** (success/failed/reversed) → return the **cached original response**,
  do not re-execute.

## 6. Example request
```
POST /api/v1/merchant/payout/initiate
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{ "amount": "500", "account": "…", "ifsc": "…" }
```
Send the **exact same** `Idempotency-Key` if the app has to retry that same payout; use a **new**
UUID for any other payout.

## 7. Optional: enforcing it
The key is optional by default on the backend. To make it mandatory, set the env flags
`REQUIRE_TRANSACTION_IDEMPOTENCY_KEY=true` (and `REQUIRE_AEPS_IDEMPOTENCY_KEY=true` for AEPS).
The app always sends it, so enabling these is safe.
