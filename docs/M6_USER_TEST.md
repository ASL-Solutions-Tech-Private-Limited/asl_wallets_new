# Modules 4–6 · Wallet, Recharge, Money transfer, Reports and the slip receipt: user test checklist

**APK:** `dist/ASL-Wallet-UAT-M6-Services-Reports.apk` (UAT · `com.asl.wallet.app.uat` · version `2.0.0-m6-uat`).
It installs over the M3 build and keeps your sign-in. If the phone says "App not installed", uninstall the old UAT app first; the build is signed with a different debug key.
SHA-256: `7de9adf2ffc40c5daffb6bc864c7a781556266a8e4b53a6e8c8b45c3043bdb5e`

**You need:** a real phone with a screen lock and internet, and the demo retailer account.
**Server:** UAT. Recharges fail at the provider on UAT today (its IP isn't allowed), so a failed or pending recharge is expected there.
**Network log:** the UAT build includes Chucker. Pull down the notification shade → "Chucker" to see each request and response.

Mark each line ✅ / ❌ and add a screenshot for every ❌.

## Slip receipt (new)
- [ ] After a successful transaction, the slip feeds out of the printer slot in short steps with light vibration ticks, then the status is stamped, then Share · PDF · Print · Help slide up (about 2 s)
- [ ] Rotate the phone, or leave and come back: it doesn't replay
- [ ] Settings → Accessibility → "Remove animations" on: the slip shows at once
- [ ] The slip stays paper-coloured in dark theme; long UTRs and IDs wrap neatly; the largest font size still fits
- [ ] Share, PDF and Print still work and show the same details

## Module 6 · Reports
- [ ] The **Reports** tab shows the wallet ledger card with this month's money in / out, the service tiles, Fund requests and Statement PDF
- [ ] Home → Recent → **See all** opens the wallet ledger
- [ ] Ledger: entries grouped by day, + / − amounts, balance after each entry; All · Money in · Money out chips change the list
- [ ] Ledger: account numbers in descriptions show as ••1234, never in full
- [ ] Ledger: tap an entry → details (opening, amount, closing, reference); Share and Save PDF work
- [ ] Calendar icon → filter sheet: Today · Yesterday · 7 days · This month · Last month · Custom; more than 3 months is refused with a message
- [ ] Search icon → type 3+ characters → the list updates after you stop typing
- [ ] Scroll a long list: "Loading more…" appears only near the end, and no rows repeat
- [ ] Pull down to refresh; with internet off: "Couldn't refresh" banner, and the old list stays on screen
- [ ] Recharge report: status chips, totals, a row opens its slip (already printed, no animation)
- [ ] Payout report: account shows as ••1234
- [ ] Money transfer report: only **your** transfers (your ₹100 test on 3 Nov shows as Refunded)
- [ ] AePS / Micro ATM / PAN: empty-state screen with "Show this month" (no data on UAT)
- [ ] Complaint: only offered on a Success or Pending row; send one → "Complaint sent" and it shows on the row. ⚠ This sends a real complaint to UAT
- [ ] Statement PDF: pick Last month → Make PDF → the share sheet opens; the PDF has the shop header, opening and closing balance, and every entry (several pages if long)
- [ ] Fund requests tile opens Wallet → My requests

## Module 5B · Money transfer
- [ ] Recent customers list, search by number or name, Clear
- [ ] Register a customer; a wrong OTP shows an error and the OTP sheet stays open (only Cancel closes it)
- [ ] Add a recipient (with and without the ₹3 name check)
- [ ] Amounts under ₹10 are refused before the OTP
- [ ] A **successful** transfer with the real OTP from the registered phone, then the slip receipt

## Module 5A · Recharge
- [ ] Mobile: number → operator detected → plans by operator → review → result
- [ ] DTH: operator → subscriber ID (10+ characters) → Get details → result

## Module 4 · Wallet
- [ ] Home → **Payout** tile opens the Payout screen (send to your own bank account)
- [ ] Balance, Add money ("opens soon"), Fund request (submits), My requests (your last 10), Payout, Bank accounts

## Report back
For each ❌: the screen, what you did, what you expected, a screenshot, and (if it's about data) the Chucker entry.
