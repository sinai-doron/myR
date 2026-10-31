# My Receipts (myR)

Personal Android app that captures grocery/retail receipt SMS, stores the receipt links on the device, downloads each receipt as a PDF, and (planned) extracts line items with Gemini. Owner: Doron Sinai. Sideloaded, never published to Play Store.

**Read `docs/HANDOVER.md` first.** It has current status, known problems, decisions and next steps. `README.md` has machine setup. The source of truth for intent is `docs/adr/ADR 001 Android SMS Receipt Capture.pdf`, then `docs/superpowers/specs/`.

## Layout

- `app/` Android app, Kotlin, Compose + Material 3, Room, WorkManager. Package `com.neonsheep.myreceipts`. minSdk 26, target/compile 37.
- `backend/` NestJS service: renders a receipt URL to PDF (Puppeteer), extracts data with Gemini on Vertex AI, saves PDFs to local disk. Runs locally only. **The app does not call it yet.**
- `docs/` ADR, design spec, implementation plans, handover notes.

## Commands

```bash
# Android (from repo root). JAVA_HOME = Android Studio JBR. Use system gradle if ./gradlew fails with an SSL/PKIX error.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
gradle assembleDebug                      # build debug APK
gradle :app:testDebugUnitTest             # 36 JVM unit tests
gradle :app:connectedDebugAndroidTest     # 18 instrumented tests (device/emulator)
gradle :app:installDebug                  # install on connected device/emulator

# Backend (from backend/)
npm ci && npm run build && npm run start:prod   # needs .env, see backend/README.md
```

## Rules that matter

- **Never store the raw SMS body.** Only extracted links, sender label, timestamp, client id.
- The filter is the privacy gate: OTPs, bank alerts and personal SMS must never be stored. `ReceiptFilter` is an interface (domain allowlist + heuristic, composed).
- Sender label is a routing hint, not a trust signal. Alphanumeric sender IDs are spoofable.
- Receiver work stays minimal (`goAsync()` plus a quick insert). Network and WebView work goes in WorkManager.
- The user decided: **run everything locally, no GCP deployment** for now.
- Do not commit `backend/.env`, `local.properties`, APKs or `receipts-data/`. They are secrets or machine-specific.
- The repo is public (MIT). Never commit real receipt links, SMS bodies, device databases or keys.

## Working style the owner expects

- Test on the Pixel 10 (physical) or emulator and report what actually happened, not what should happen.
- Ask only when truly blocked. Prefer small, verified steps; the owner reviews by looking at screenshots from the device.
