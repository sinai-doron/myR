# My Receipts

Android app that captures receipt SMS, keeps the links on the device, downloads each receipt as a PDF, and shows them grouped by vendor. A NestJS backend (local only) can render a receipt URL to PDF and extract line items with Gemini. See `docs/HANDOVER.md` for status and `CLAUDE.md` for the short orientation.

## Prerequisites (macOS shown; any OS works)

| Tool | Version used | Notes |
|---|---|---|
| JDK | 21+ (Android Studio's bundled JBR 25 is used) | `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` on macOS. Bytecode targets Java 17. |
| Android SDK | platforms 35 and 36, build-tools 34/35, platform-tools | command-line tools are enough, no Android Studio needed |
| Gradle | 9.8.2 | wrapper is configured for 9.8.2; system Gradle also works |
| Node | 22+ (24 was used) | backend only |
| adb | from platform-tools | device and emulator control |

Android SDK from Android Studio lives at `~/Library/Android/sdk` on macOS.

## First run on a new machine

1. Clone the repo and `cd` into it.
2. Create `local.properties` in the repo root (it is gitignored and machine-specific):
   ```
   sdk.dir=/path/to/your/android/sdk
   # optional: one fixed debug key, so installs from any computer update the app in place
   # instead of needing an uninstall (which wipes captured receipts)
   signing.storeFile=/path/to/debug.keystore
   ```
3. Install SDK pieces if missing:
   ```bash
   sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
   ```
4. Build and test:
   ```bash
   ./gradlew assembleDebug :app:testDebugUnitTest
   ```
   If the wrapper fails with a PKIX/SSL error (e.g. behind a TLS-inspecting corporate proxy), use system Gradle 9.8.2: `gradle assembleDebug :app:testDebugUnitTest`.
5. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The app asks for SMS permission on first launch, then backfills receipts from the SMS inbox.

## Devices used

- Physical: Pixel 10, paired over wireless debugging (Developer options → Wireless debugging → Pair device with pairing code, then `adb pair <ip>:<port>` and `adb connect <ip>:<port>`). Find yours with `adb devices -l`.
- Emulator: a Pixel 10 AVD on API 37 (e.g. `gym_pixel10`). `emu sms send` puts messages in the inbox; the inbox backfill picks them up on launch, but the inbox backfill works on the emulator.

## Sign-in and the backend

The app signs in with Google through **doron-backend** (the shared sign-in service at
`apps.neonsheep.dev`, a separate repo) and asks it to turn receipt links into PDFs
(`POST /api/receipts/pdf`). Signed out, or when the backend can't make a PDF, the phone falls
back to downloading the PDF itself.

- **Which backend:** staging, `https://apps.neonsheep.dev`, by default. To use a doron-backend
  running on this computer, set `backend.url=http://localhost:7300` in `local.properties` and run
  `adb reverse tcp:7300 tcp:7300`. Plain HTTP is allowed only to the phone's own loopback.
- **Google:** the Android OAuth client is `com.neonsheep.myreceipts` with the fixed debug key's
  SHA-1 (see Signing). The ID token's audience is doron-backend's web client, set in
  `app/build.gradle.kts`. doron-backend must list the Android client ID in
  `GOOGLE_OTHER_CLIENT_IDS`, and your Google account must be on its allowlist
  (`npm run cli -- users add <email>` there).
- **Tokens** follow doron-backend's auth design §6: the access token stays in memory, the
  refresh token is encrypted with Tink under an Android Keystore key, refreshes run one at a
  time, and the session is excluded from backups.

## The old local backend (`backend/`)

See `backend/README.md`. Short version: copy `backend/.env.example` to `backend/.env`, fill `GCP_PROJECT_ID` and `API_KEY`, then `npm ci && npm run build && npm run start:prod`. Gemini extraction needs Google Cloud credentials on the machine.

## Continuing with Claude Code

Open Claude Code in this folder. `CLAUDE.md` loads automatically. Start with: "Read docs/HANDOVER.md and continue from the next steps." Previous conversation transcripts are not included, so the docs are the full record.

## License

MIT. See `LICENSE`.
