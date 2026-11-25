# Micro ATM (mATM) — implementation plan

Written for: the ASL Wallet dev/review team.
Status: **IMPLEMENTED** (2026-11-24) · module `feature/matm` builds, unit-tested, installed · Retailer app.
Remaining: the backend `merchant/matm/*` endpoints must go live, then test on the real Morefun reader.
Reference: old app `ASL Projects/asl_into_new` (pkg `com.tech.fin`, `ui/matm/*`) — full working mATM.
Closest v2 analog: `feature/aeps` (device-driven cash txn, server initiate → device → server confirm).

mATM = customer dips/swipes/taps a **debit card** on a connected **mPOS card reader**; the retailer
does a **Balance Enquiry** or **Cash Withdrawal** and hands over cash on success, earning commission.

---

## 1. The device & SDK (confirmed from the old app)

Vendor = **FingPay**, card reader = **Morefun mPOS** (Bluetooth). Two binaries, both bundled in the old app's `app/libs/`:
- `fingpaymicroatm-release_V5.0.aar` — FingPay mATM SDK (the `MicroAtmLoginScreen` activity + `Constants`).
- `morefun_mpos_sdk_v3.0.20240827.jar` — Morefun hardware SDK.

The SDK is **Activity-based**: the app launches `com.fingpay.microatmsdk.MicroAtmLoginScreen` with
`Constants.*` intent extras and reads the result from the returned `Intent`. The SDK itself handles
Bluetooth pairing, card read, PIN entry and the bank round-trip. **Requires the physical reader** —
cannot be exercised without hardware.

Permissions needed: `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (txn is geo-tagged), Bluetooth
(for the reader; the SDK declares its own, confirm at integration).

v2 wrap: a `MatmDevice` abstraction launched via `ActivityResultContracts.StartActivityForResult`
(same pattern as AEPS `rememberRdCapture`). A real `FingPayMatmDevice` builds the intent; result
codes map to Ok / Cancelled.

---

## 2. API (new backend — from the Postman collection)

Base `{base_url}merchant/`. (The **old** app used `api/apiforapp/v1/matm-transaction[-status-update]`;
v2 uses the new `merchant/matm/*` paths below.)

### 2.1 `POST merchant/matm/initiate` — before the card read
Request:
```json
{ "amount": "10", "customer_mobile": "8709305218", "txn_type": "CW" }   // txn_type: "BE" | "CW"
```
Response (old-backend shape `InitiateMATMResponse` — **confirm new backend matches, §7**):
```
status, message, error,
superMerchantId, merchantUserId, merchantPassword,   // SDK credentials
mobileNumber, amount, remarks, txnId, imei,
amountEditable (bool),
type (Int): 2 = Cash Withdrawal, 4 = Balance Enquiry
```
These feed the SDK intent extras 1:1.

### 2.2 `POST merchant/matm/update-status` — after the card read
Body = the device result **verbatim** (`MicroAtmResult`):
```
status(bool), message, transAmount, balanceAmount, bankRrn, transType, type,
cardNum, bankName, cardType, terminalId, txnId, mobileNumber, fpId, errorCode
```
Response = same `MicroAtmResult` shape **plus an `error` field**: `error` empty → show receipt;
`error` non-empty → "contact support for status" dialog.

---

## 3. SDK launch (extras map, from old app)

`MicroAtmLoginScreen` extras via `Constants`:
`SUPER_MERCHANTID, MERCHANT_USERID, MERCHANT_PASSWORD, AMOUNT, REMARKS, MOBILE_NUMBER,
AMOUNT_EDITABLE, TXN_ID, IMEI, LATITUDE, LONGITUDE, TYPE` where
`TYPE = CASH_WITHDRAWAL` (init type 2) or `BALANCE_ENQUIRY` (init type 4).
Result extras read back → `MicroAtmResult` (TRANS_STATUS, MESSAGE, TRANS_AMOUNT, BALANCE_AMOUNT,
RRN, TRANS_TYPE, TYPE, CARD_NUM, BANK_NAME, CARD_TYPE, TERMINAL_ID, TXN_ID, MOBILE_NUMBER,
FP_TRANS_ID, RESPONSE_CODE).

---

## 4. Module structure (mirrors AEPS)
```
feature/matm/
  libs/     fingpaymicroatm-release_V5.0.aar · morefun_mpos_sdk_v3.0.20240827.jar   (copied from old app)
  data/     MatmApi.kt · MatmRepository.kt · MatmDevice.kt (+ FingPayMatmDevice) · MatmModule.kt
  domain/   Models.kt  (TxnType Balance/Withdraw; MatmInit; MatmResult)
  ui/       MatmViewModel.kt · MatmScreens.kt · MatmNavigation.kt
  res/      strings_matm.xml
  test/     MatmTest.kt   (initiate request + update-status mapping; device behind a fake)
```
Wiring: `settings.gradle.kts`, app DI, `ServiceKind.MicroAtm` (module id 7) opens it, nav in `AslWalletApp.kt`,
`ACCESS_FINE_LOCATION` + Bluetooth in the app manifest.

---

## 5. Flow
1. Open **Micro ATM** → enter customer **mobile**, pick **Balance** or **Withdraw**, (CW) enter **amount**.
2. Acquire location (needs permission) → `initiate` → get SDK credentials + `type`.
3. Launch FingPay `MicroAtmLoginScreen` → customer taps/dips card, enters PIN on the reader.
4. Result → `update-status`.
5. **Receipt** (v2 Slip): status band SUCCESS/FAILED, friendly type, amount, txnId, card ••1042, bank, RRN,
   terminal id, balance (for BE), message, retailer name/phone/address — reuses the new receipt system.
6. Cancelled / declined / fraud / timeout → clear message, no receipt, no cash.

Safeguards: FLAG_SECURE; never log `cardNum`/PIN; amount limits (TBD); idempotency header on `initiate`;
retailer-only (already enforced app-wide).

---

## 6. Receipt fields (old app, to match)
Status · Service(type) · Amount · Txn ID · Date · Card (XXXX+last4) · Bank RRN · Terminal ID ·
Message · Available Balance · Retailer Name/Address/Phone.

---

## 7. Decisions (confirmed) — all done
1. **Bundled** the FingPay AAR + Morefun jar into `feature/matm/libs/` and wired the real SDK.
2. **initiate** response: CONFIRMED against the live endpoint — the backend returns the SDK
   credentials / `status` / `message` **flat at the top level** (exactly like the old app's
   `InitiateMATMResponse`), NOT wrapped in `ApiEnvelope.data`. So the matm responses are typed as
   flat `MatmBody` models (`MatmInitResponse` / `MatmStatusResponse`) read by a dedicated
   `matmCall` helper, not `ApiEnvelope<T>` + `safeApiCall`. A nested `data` object is still accepted
   as a fallback. (Earlier the envelope assumption made a good response report a false "Empty
   response".) `update-status` likewise: a non-empty flat `error` = "could not confirm".
3. **Txn types**: Balance (BE) + Withdrawal (CW) only.

## 8. What shipped
- New module `:feature:matm` (registered in settings + app), `ServiceKind.MicroAtm` → module 7 is now
  a BUILT module and opens from Home / All Services.
- `MatmApi` (`initiate` + `update-status`), `MatmRepository`, `MatmViewModel`, `MatmScreens`
  (form → location → FingPay reader → `update-status` → `BillReceipt` with share/save/print), nav graph.
- Receipt reuses the app's standard slip (status band + retailer details) with: type, amount, balance,
  card ••1042, bank, RRN, terminal id, txn id, message; commission shown when the server returns it.
- Permissions added (location + Bluetooth); amounts/mobile validated; 4 repository unit tests green.
- Deps added to the version catalog: `bcprov-jdk15on` (card crypto) + `play-services-location`.

## 9. Gotcha (for future modules)
KSP/Hilt failed with "`MatmApi` could not be resolved" (no Kotlin error shown). Root cause was a
character in a KDoc/line comment inside `MatmApi.kt` that broke KSP's symbol resolution. Fix: keep
comments plain ASCII and avoid inline comments inside data-class parameter lists in files KSP reads.
