# My Receipts (myR)

Personal Android app that captures grocery/retail receipt SMS, stores the receipt links on the device, downloads each receipt as a PDF, and (planned) extracts line items with Gemini. Owner: Doron Sinai. Sideloaded, never published to Play Store.

**Read `docs/HANDOVER.md` first.** It has current status, known problems, decisions and next steps. `README.md` has machine setup. The source of truth for intent is `docs/adr/ADR 001 Android SMS Receipt Capture.pdf`, then `docs/superpowers/specs/`.

## Layout

- `app/` Android app, Kotlin, Compose + Material 3, Room, WorkManager. Package `com.neonsheep.myreceipts`. minSdk 26, target/compile 37.
- Sign-in and receipt PDFs come from **doron-backend** (separate repo, `../doron-backend`): Google sign-in via Credential Manager, then `POST /api/receipts/pdf`. Code in `auth/` and `net/`. See README "Sign-in and the backend".
- `backend/` the original NestJS service (Puppeteer + Gemini, local only). **The app does not call it**; its PDF rendering moved to doron-backend's receipts app.
- `docs/` ADR, design spec, implementation plans, handover notes.

## Commands

```bash
# Android (from repo root). JAVA_HOME = Android Studio JBR. Use system gradle if ./gradlew fails with an SSL/PKIX error.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
gradle assembleDebug                      # build debug APK
gradle :app:testDebugUnitTest             # 48 JVM unit tests
gradle :app:connectedDebugAndroidTest     # 13 instrumented tests per device (ANDROID_SERIAL picks one)
gradle :app:installDebug                  # install on connected device/emulator

# Backend (from backend/)
npm ci && npm run build && npm run start:prod   # needs .env, see backend/README.md
```

## Rules that matter

- **Never store the raw SMS body.** Only extracted links, sender label, timestamp, client id.
- The filter is the privacy gate: OTPs, bank alerts and personal SMS must never be stored. `ReceiptFilter` is an interface (domain allowlist + heuristic, composed).
- Sender label is a routing hint, not a trust signal. Alphanumeric sender IDs are spoofable.
- Receiver work stays minimal (`goAsync()` plus a quick insert). Network and WebView work goes in WorkManager.
- The app uses doron-backend on staging (`apps.neonsheep.dev`) by default. Backend work happens in doron-backend; staging changes need the owner's go-ahead.
- Never log a receipt link, a token or a PDF.
- Do not commit `backend/.env`, `local.properties`, APKs or `receipts-data/`. They are secrets or machine-specific.
- The repo is public (MIT). Never commit real receipt links, SMS bodies, device databases or keys.

## Working style the owner expects

- Test on the Pixel 10 (physical) or emulator and report what actually happened, not what should happen.
- Ask only when truly blocked. Prefer small, verified steps; the owner reviews by looking at screenshots from the device.
