# Module 2 · Home & navigation: user test checklist

**APK:** `dist/ASL-Wallet-UAT-M2-Home.apk` (UAT · `com.asl.wallet.app.uat`). Install over the M1 build.
**Account:** the demo retailer login ASL shared. Use a real phone **with a screen lock**.

Mark ✅ / ❌ with a note.

## Home
- [ ] After sign-in (or unlock) Home shows **Good morning / afternoon / evening, <first name>**, the shop name and `R-<reg no>`
- [ ] Wallet card shows your **real balance** and "Updated just now". It changes to "Updated N min ago" over time
- [ ] The balance **hides itself after 10 s**. Tap **Show** to see it again, and it hides again after 10 s
- [ ] If you have earned commission, a gold **"₹… commission"** chip shows on the card
- [ ] **Add money** and **Passbook** say "coming soon" (they arrive in Module 4)
- [ ] KYC pending account: the **Complete your KYC** banner shows, and money services (AEPS, Micro ATM, Money transfer, UPI, Card to cash) show a **lock**
- [ ] Recent transactions show the last 3 ledger rows. Credits are green with **+**, debits grey with **−**
- [ ] Any account or Aadhaar number inside a transaction name shows as **••••1234**, never in full
- [ ] Pull down to refresh: a spinner shows and the data reloads

## Services
- [ ] 8 tiles: 7 services and **All services**
- [ ] Tap a **locked** service: "KYC needed for …" sheet
- [ ] Tap an unlocked service that isn't built yet: "… is coming soon" sheet (the tile has a **Soon** tag)
- [ ] **All services**: grouped into Banking / Recharge and bills / Documents, travel, gifts
- [ ] Search "rech" shows only recharge services. Search "zzz" shows "No service matches"
- [ ] Leave All services with the **phone's Back button** and open it again: the search box is empty
- [ ] (When the server returns partner links) a **Web** tile opens a sheet with the site name. **Open** opens it in a browser tab with the address bar visible

## Edit Home services
- [ ] Tap **Edit**: your 7 services are numbered 1–7 in order
- [ ] With 7 picked, the others are dimmed and can't be added. Untick one, then tick another: it becomes number 7
- [ ] **Save**: Home shows the new set in that order. **Reset to default** restores the original 7
- [ ] Log out and sign in again: your choice is **kept**

## Offline
- [ ] Turn on airplane mode and pull to refresh: the last balance and transactions stay, with a **"You're offline… Payments are paused"** banner
- [ ] Turn internet back on and pull to refresh: the banner goes away

## Tabs, notifications, logout
- [ ] Bell → Notifications: "No notifications yet" (the API isn't ready yet)
- [ ] Reports / Earnings / Profile tabs say "coming soon"
- [ ] Profile → **Log out** asks to confirm, then shows Login with your email filled in
- [ ] After logout, open the app again: no balance is shown before sign-in

## Language and theme
- [ ] Hindi and Bengali: greeting, wallet card, banners, sections, sheets and tabs are translated. Service names stay as the server sends them
- [ ] Dark theme (UAT: Profile → Foundation showcase → Settings): Home is readable, and the cards and tiles keep their colours

## Security spot checks
- [ ] Screenshot on Home is **blocked or black**
- [ ] In the recent-apps view the app preview is **blank**
