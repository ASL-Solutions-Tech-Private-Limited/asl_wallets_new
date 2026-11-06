# Module 1 · Auth: user test checklist

**APK:** `dist/ASL-Wallet-UAT-M1-Auth.apk` (UAT · `com.asl.wallet.app.uat`). Uninstall the Module 0 test build first, or install over it.
**Account:** the demo retailer login ASL shared (email). It works with mobile number too, if that account has one.
Use a real phone **with a screen lock** (fingerprint/PIN/pattern).

Mark ✅ / ❌ with a note.

## First launch
- [ ] Splash (logo on night-sky background, soft moving lights), then **Choose language**
- [ ] Pick हिन्दी or বাংলা: the screens switch language immediately. Switch back to English and tap Continue
- [ ] The language screen does **not** appear again on the next launch

## Sign in
- [ ] Login: one field "Mobile number or email". Typing `12345` and leaving the field shows a clear error
- [ ] **Sign in** stays dimmed until both fields are filled
- [ ] Try a screenshot on Login: it's **blocked or black**
- [ ] Sign in with the demo account: no crash, and the button shows a spinner while waiting
- [ ] (Only if ASL turns on OTP for the account) a 4-digit code screen appears, SMS code works, wrong code shows "tries left", resend is available after 60 s
- [ ] **Protect your app** screen: tap **Turn on app lock**, confirm with your fingerprint/PIN, and you reach Home
- [ ] Home shows your name, shop, masked mobile (98XXX XX123 style) and KYC status from the server

## App lock
- [ ] Press Home, wait **more than 30 s**, reopen: the phone's unlock prompt appears. Under 30 s: no prompt
- [ ] Leave the app open for **5 min without touching**: it locks
- [ ] Swipe the app away (kill it) and open it again: it asks to unlock **before** showing anything
- [ ] Cancel the prompt: the "ASL Wallet is locked" screen stays, and **Unlock** opens the prompt again

## Log out and forgot password
- [ ] **Log out** returns to Login with your email/mobile prefilled and the password empty
- [ ] Sign in again: no app-lock setup this time (already done on this phone)
- [ ] **Forgot password?** opens with your mobile prefilled (if you signed in by mobile)
- [ ] Enter the mobile, then **Send code**: step 2 shows code boxes, a resend timer, and new/confirm password
- [ ] A weak password (e.g. `password`) keeps **Save** disabled; mismatched confirm shows "Passwords don't match"
- [ ] (Optional, changes your password) complete a reset, see **Password changed**, then sign in with the new password

## Errors
- [ ] Airplane mode, then Sign in: "No internet…" message, no crash
- [ ] One wrong password: the server's message shows (e.g. "Invalid credentials. N attempt(s) remaining") and the password field clears. **Don't try 5 times, or the account locks**

## Language and size
- [ ] In বাংলা, Login, OTP and Reset texts fit without cutting off
- [ ] Phone font size **Largest**: Login and Reset are still usable (scroll if needed)
