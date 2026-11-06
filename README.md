# ASL Wallet v2 (Android · Jetpack Compose)

The new retailer app, rebuilt from scratch. **Project plan, decisions, reviews and the module tracker** live in
`/Users/injamamul/Desktop/Projects/asl_new_app/PROJECT_REVIEW.md` (read it first). The old app in `asl_new_app` is reference only.

## Build
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew testUatDebugUnitTest testDebugUnitTest   # all unit tests
./gradlew :app:assembleUatDebug                     # test APK (UAT server)
./gradlew :app:assembleUatRelease                   # minified release (unsigned)
./gradlew :app:assembleLiveRelease                  # fails on purpose until the Live URL + pins are set
```
Toolchain: AGP 9.2.1 (built-in Kotlin) · Kotlin 2.3.20 · Gradle 9.4.1 · compileSdk 37 · targetSdk 36 · minSdk 24 · JDK 17 bytecode.

## Modules
| Module | What it holds |
|---|---|
| `app` | Activity, navigation, flavors `uat`/`live`, Hilt wiring, lock and integrity gates, feature screens |
| `core:common` | Money formatting (Indian grouping), amount in words, validators (Aadhaar Verhoeff, IFSC, PAN, UPI, mobile), masking, dispatchers, `TokenStore`, `IdempotencyKey` |
| `core:designsystem` | Theme tokens (colours, Noto Sans type scale, gradients, shapes, motion), icon set, all UI components |
| `core:network` | OkHttp/Retrofit with TLS 1.2+, certificate pinning, auth + refresh-token authenticator, request IDs, `safeApiCall` → `NetworkResult` |
| `core:security` | Tink + Android Keystore encrypted token store, app lock (30 s background / 5 min idle), system unlock prompt, root/emulator checks, `SecureScreen` (FLAG_SECURE) |
| `core:testing` | `MainDispatcherRule`, `InMemoryTokenStore` |

## Rules (from PROJECT_REVIEW.md)
- Every module: Design → Code → VAPT → Test → User test → Confirmation. Never skip ahead.
- UI changes: design first and get approval, then code.
- Screens use design-system tokens and components only. No raw colours or font sizes.
- Every money POST carries an `Idempotency-Key`; OkHttp never auto-retries.
- No logging of tokens, OTPs, PID or Aadhaar data. Release builds strip `android.util.Log`.
- The commission is never printed on customer receipts.
