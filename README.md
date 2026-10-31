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

## Backend

See `backend/README.md`. Short version: copy `backend/.env.example` to `backend/.env`, fill `GCP_PROJECT_ID` and `API_KEY`, then `npm ci && npm run build && npm run start:prod`. Gemini extraction needs Google Cloud credentials on the machine.

## Continuing with Claude Code

Open Claude Code in this folder. `CLAUDE.md` loads automatically. Start with: "Read docs/HANDOVER.md and continue from the next steps." Previous conversation transcripts are not included, so the docs are the full record.

## License

MIT. See `LICENSE`.
