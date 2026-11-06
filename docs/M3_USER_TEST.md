# Module 3 · Profile, Settings and AEPS: user test checklist

**APK:** `dist/ASL-Wallet-UAT-M3-Profile-AEPS.apk` (UAT · `com.asl.wallet.app.uat`). Install over the M2 build.
**You need:** a real phone with a screen lock, the **Mantra L1** scanner with its **Mantra RD Service** app installed, OTG cable, location on, and a test customer with an Aadhaar-linked bank account.
**Bank:** test on **Kotak Bank**. Yes Bank isn't finished on the new backend yet, so it should show "not available yet".

Mark ✅ / ❌ with a note.

## Home
- [ ] No "Complete your KYC" banner and no locks on service tiles
- [ ] No Aadhaar Pay anywhere
- [ ] Tap **AEPS**: the "Choose AEPS bank" sheet opens with Kotak Bank and Yes Bank, each with a status (Ready / Login due / Set-up needed)
- [ ] Your last choice is marked "Last used" the next time

## Profile tab
- [ ] Shows your name, shop, `R-<reg no>`, and Retailer
- [ ] **AEPS set-up** row shows each bank's state. Tapping it opens the bank chooser
- [ ] **Profile details**: mobile, email and PAN are masked. The address is on one line. Nothing can be edited
- [ ] **My commission**: Banking and Recharge tabs. (UAT currently returns an error here: it should show "Couldn't load" and **Try again**, not crash)
- [ ] **Active services**: list with Active / Off (UAT currently returns an error: same "Couldn't load" state)
- [ ] **Change password**: weak, same-as-current and mismatched passwords keep **Send OTP** disabled. With a valid one, a **6-digit** code goes to your email (shown masked). A wrong code shows tries left. The right code shows "Password changed"
- [ ] **Settings**: language switches instantly (English / हिन्दी / বাংলা), theme Light / Dark / System, app lock shown as always on
- [ ] **Help and support**: **Call ASL** opens the dialler with 9733815791, **WhatsApp** opens WhatsApp. FAQs expand
- [ ] **Raise a complaint**: needs a service and 10+ characters. Add a photo. **Submit** shows "Complaints open soon" with Call/WhatsApp, and your text is still there after closing it
- [ ] **My complaints** and **Rate ASL Wallet**: the screens work. Submitting says the feature opens soon
- [ ] **Log out** asks to confirm, then shows Login

## AEPS: set-up (only if your account isn't set up for Kotak yet)
- [ ] Location off or denied: "Turn on location". AEPS does not continue without it
- [ ] Onboarding start screen lists the 4 steps and what to keep ready
- [ ] Details: prefilled where the bank has data. Bad Aadhaar, PAN, IFSC, pincode or an under-18 date are flagged
- [ ] Documents: the camera opens for each photo. Photos don't appear in the phone's gallery
- [ ] OTP from the bank (6 digits), resend after 60 s
- [ ] Fingerprint with Mantra L1: success → "AEPS set up" → continues to bank verification
- [ ] Bank verification (Kotak): OTP, then your fingerprint
- [ ] Daily login: one fingerprint scan, then AEPS opens

## AEPS: transactions (Kotak)
- [ ] Scanner chip shows "Mantra L1 ready". Tap it to pick another scanner. Uninstalled scanners show **Install**
- [ ] Withdraw: customer Aadhaar is hidden as you type (eye to show), mobile, bank (SBI/PNB/BoB chips or **Search…**), amount ₹100–₹10,000 with the amount in words
- [ ] Scan the customer's finger → "in progress" → receipt with masked Aadhaar, bank, balance, bank ref
- [ ] Receipt: **Share** (text), **Save PDF**, **Print** (choose thermal 58 mm or A4), **Same customer again** keeps the Aadhaar, mobile and bank
- [ ] Balance enquiry shows the balance large, with buttons for Statement and Withdraw
- [ ] Mini statement shows the passbook table (Date | Narration | Cr/Dr | Amount); account numbers in narrations are masked
- [ ] Above ₹5,000: the app asks for the customer's OTP first. (The backend currently doesn't return the OTP reference, so expect the message "The bank didn't return an OTP reference…". That's a backend fix.)
- [ ] Wrong finger: "Transaction failed" with the bank's reason and "not debited"
- [ ] Turn off internet right after scanning: "Waiting for the bank · Don't repeat this transaction", not "Failed"
- [ ] Yes Bank: choose it and try a transaction: "Yes Bank AEPS is not available yet" with **Use Kotak Bank**

## Security spot checks
- [ ] Screenshots are blocked on AEPS, receipts, profile details and change password
- [ ] Log out and sign in again: no customer Aadhaar is suggested anywhere

## API log (Chucker), UAT test build only
Every API call the app makes is recorded on the phone for **1 day**, so you can share it if something goes wrong.
- **Open it:** pull down the notification shade and tap **"Recording HTTP activity"** (allow notifications for ASL Wallet UAT if asked). Or long-press the app icon and choose **"Chucker"**.
- **Share one call:** open the call → tap **Share** (top right) → *Share as text* → send it to the developer (WhatsApp/email). Or tap **Save** to save it as a file.
- **Share everything:** in the list, menu (⋮) → *Export* (text or HAR).
- **What's hidden before anything is saved:** passwords, OTPs, tokens and fingerprint (PID) data show as `[redacted]`. Aadhaar, account numbers, PAN, mobile and email are masked (e.g. `XXXX XXXX 0009`). Amounts, bank IIN, status codes, error messages and the `X-Request-Id` stay visible so problems can be traced on the server.
- **Clear it:** menu (⋮) → *Clear*.
- The Play Store/live app and any release build contain **no** Chucker: it exists only in this UAT test build.
