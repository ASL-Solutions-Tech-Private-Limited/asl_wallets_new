# Module 0 · Foundation: user test checklist

**APK:** `dist/ASL-Wallet-UAT-M0-Foundation.apk` (UAT debug build · app ID `com.asl.wallet.app.uat`; it installs next to the old app and doesn't replace it)

Install: copy the APK to the phone and open it, or run `adb install -r dist/ASL-Wallet-UAT-M0-Foundation.apk`.
Use a **real phone** if you can (Android 7 or newer), ideally a budget one, with a screen lock set.

Mark each line ✅ / ❌ and add a note for anything that looks wrong.

## 1. Look and feel
- [ ] Home opens in under 2 s, with the wallet card (moving gradient and light glint), the gold "earned" chip, and 4 test sections
- [ ] Tap **Hide** on the wallet card: the balance becomes ₹ •••••• and the label changes to **Show**
- [ ] Font sizes feel right on your phone (not too big, not too small)
- [ ] Phone Settings → Display → Font size → **Largest**, then reopen the app: nothing is cut off or overlapping

## 2. Components
- [ ] **Continue** shows a spinner and can't be tapped twice; **Stop loading** resets it
- [ ] Mobile field: typing `5876543210` shows a red error, `9876543210` shows a green ✓
- [ ] Password eye icon shows and hides the password
- [ ] OTP boxes: typing 4 digits shows "OTP entered"; the keyboard is numeric
- [ ] Amount: type `12345` and the words read "Twelve thousand three hundred forty-five rupees"; the quick chips change the amount
- [ ] **Slide to send**: releasing halfway springs back; sliding to the end shows "Confirmed exactly once" (only once); **Reset slider** works
- [ ] Receipt, action bar (WhatsApp, PDF, Print, Help), help card and the two passbook tables look right (actions are placeholders until Module 5)

## 3. System states
- [ ] Each chip (Loading, Empty, Offline, Session, Update, Location, Hours) shows a clear screen with one action

## 4. Security
- [ ] **Security checks** screen: try a screenshot. It must be **blocked or black**. Open recent apps: the preview is blank
- [ ] **Phone safety** says "No root, tampering or debugger found" on a normal phone
- [ ] **Check secure connection** shows "Secure · certificate pin matched" (needs internet)
- [ ] Turn on **Simulate a signed-in session**: a green pill "Encrypted with an Android Keystore key · read back OK" appears
- [ ] Tap **Lock now**: the lock screen and phone unlock prompt appear; fingerprint or phone PIN unlocks it
- [ ] With the session on, press Home, wait **more than 30 s**, then reopen: the app asks to unlock. Under 30 s: no lock
- [ ] With the session on, leave the app open **5 min without touching**: it locks
- [ ] On a phone **with no screen lock**: the lock screen asks you to set one and opens phone settings

## 5. Appearance and language
- [ ] Theme Light, Dark and System all switch instantly and everything stays readable
- [ ] Language हिन्दी and বাংলা switch the test labels (only Module 0 labels are translated for now)
- [ ] Close and reopen the app: theme and language are remembered

## 6. Stability
- [ ] Rotate the phone, switch apps and come back: no crash and no lost state
- [ ] Turn on airplane mode, then **Check secure connection**: it shows "No internet" with no crash

---
Already tested on our side (3 Nov 2026): 36 unit tests pass; emulator smoke test passed (launch, home render, components render, real UAT connection with pin matched in 700 ms, screenshot blocked); release APK minified to 2.5 MB with logs stripped; Live build is refused until the Live URL and pins are set.
