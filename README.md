# My Receipts

Android app that captures receipt SMS, keeps the links on the device, downloads each receipt as a PDF, and shows them grouped by vendor. A NestJS backend (local only) can render a receipt URL to PDF and extract line items with Gemini. See `docs/HANDOVER.md` for status and `CLAUDE.md` for the short orientation.

## Prerequisites (macOS shown; any OS works)

| Tool | Version used | Notes |
|---|---|---|
| JDK | 17 | `JAVA_HOME` must point to it |
| Android SDK | platforms 35 and 36, build-tools 34/35, platform-tools | command-line tools are enough, no Android Studio needed |
| Gradle | 9.8.1 | wrapper is configured for 9.8.1; system Gradle also works |
| Node | 22+ (24 was used) | backend only |
| adb | from platform-tools | device and emulator control |

On macOS with Homebrew: `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`, JDK from `openjdk@17`.

## First run on a new machine

1. Clone the repo and `cd` into it.
2. Create `local.properties` in the repo root (it is gitignored and machine-specific):
   ```
   sdk.dir=/path/to/your/android/sdk
   ```
3. Install SDK pieces if missing:
   ```bash
   sdkmanager "platforms;android-36" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
   ```
4. Build and test:
   ```bash
   ./gradlew assembleDebug :app:testDebugUnitTest
   ```
   If the wrapper fails with a PKIX/SSL error (e.g. behind a TLS-inspecting corporate proxy), use system Gradle 9.8.1: `gradle assembleDebug :app:testDebugUnitTest`.
5. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The app asks for SMS permission on first launch, then backfills receipts from the SMS inbox.

## Devices used

- Physical: Pixel 10 (Android 16, API 36), paired over wireless debugging. Find yours with `adb devices -l`.
- Emulator AVDs on the original machine: `Pixel_10_API36`, `Pixel_10_API35`, `Pixel_8_API35`. `emu sms send` does not trigger the app's receiver, but the inbox backfill works on the emulator.

## Backend

See `backend/README.md`. Short version: copy `backend/.env.example` to `backend/.env`, fill `GCP_PROJECT_ID` and `API_KEY`, then `npm ci && npm run build && npm run start:prod`. Gemini extraction needs Google Cloud credentials on the machine.

## Continuing with Claude Code

Open Claude Code in this folder. `CLAUDE.md` loads automatically. Start with: "Read docs/HANDOVER.md and continue from the next steps." Previous conversation transcripts are not included, so the docs are the full record.

## License

MIT. See `LICENSE`.
