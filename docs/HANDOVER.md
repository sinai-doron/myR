# Handover: My Receipts

Prepared 2026-10-31 from the repo state and the saved Claude session transcripts (last working session ended 2026-10-18). Written so a fresh Claude Code on another machine can continue without the old conversation.

## 1. What this project is

A personal "grocery price intelligence" system. Phase 1 (this repo) is an Android app that:

1. Receives inbound SMS (`RECEIVE_SMS`), reassembles multipart/Hebrew messages.
2. Filters for receipts, extracts the receipt link, stores link + sender + timestamp + client id in Room. Raw body is never stored.
3. Backfills from the SMS inbox on first launch.
4. Downloads each receipt as a PDF in the background (WebView + WorkManager) so it can open offline.
5. Shows receipts grouped by vendor, with date, amount placeholder, and a details screen.

Later phases (not built): send the PDF to Gemini for line-item extraction, price database, in-aisle lookup.

Authoritative design: `docs/adr/ADR 001 Android SMS Receipt Capture.pdf` (Oct 13 2026), `docs/superpowers/specs/2026-10-14-android-sms-receipt-capture-design.md`.

## 2. State at handover

| Item | State |
|---|---|
| Git | Fresh public history on `main` (earlier private history not published). |
| Android build | `assembleDebug` succeeds after the 2026-10-31 dependency upgrade; app verified on a Pixel 10 API 37 emulator. |
| Unit tests | 48 JVM tests pass: ReceiptFilter 12, HeuristicReceiptFilter 14, CompositeReceiptFilter 3, ClientId 4, MerchantConfig 3, AuthRepository 12. |
| Instrumented tests | 13 pass on a Pixel 10 API 37 emulator (receiver integration, store, list screen, sign-in and PDF screens). |
| Backend | Builds and starts locally per earlier sessions. **No tests.** Not verified at handover. |
| App to backend link | Google sign-in and receipt PDFs through doron-backend (2026-10-31), local first. See README "Sign-in and the backend". |

## 3. Architecture

```
SMS arrives
 -> SmsReceiver (BroadcastReceiver, goAsync) reassembles parts
    -> CompositeReceiptFilter = DomainMatchFilter (merchants.json) + HeuristicReceiptFilter
       -> match: ReceiptStore (Room, unique clientId) -> enqueue ReceiptDownloadWorker (if <= 30 days old)
       -> no match: dropped, never stored
MainActivity observes Room Flow -> CapturedListScreen (vendors) -> VendorReceiptsScreen -> ReceiptDetailScreen
ReceiptDownloadWorker (WorkManager chain, one at a time) -> ReceiptPdfDownloader (WebView)
    1. resolve redirects, load page, poll for a download button (e.g. button.iconDownlond)
    2. click it, intercept the file via DownloadListener / shouldInterceptRequest
    3. fallback: printToPdf of the rendered page (rejects captures shorter than 800 px as "loading page")
    -> file saved, pdfPath written to Room, UI shows a check mark
```

Key files under `app/src/main/java/com/neonsheep/myreceipts/`:

- `receiver/SmsReceiver.kt` receiver plus `buildFilter` / `buildStrictFilter`.
- `data/ReceiptFilter.kt`, `MerchantConfig.kt`, `ReceiptStore.kt` (entity, DAO, DB), `SmsBackfill.kt`, `ClientId.kt`.
- `worker/ReceiptDownloadWorker.kt`, `worker/ReceiptPdfDownloader.kt`.
- `ui/screens/` list, vendor list, detail. `ui/theme/` blue theme matching the new logo.
- `app/src/main/assets/merchants.json` domain allowlist, sender hints, and known receipt-platform domains (wee.ai, greeninvoice.co.il, invoice4u.co.il, icount.co.il).

Data model (`captured_receipts`, Room DB version 3, `fallbackToDestructiveMigration`, so schema changes wipe local data): `id`, `link`, `senderLabel`, `timestamp`, `clientId` (deterministic UUID from link+timestamp, unique index), `capturedAt`, `totalAmount?`, `pdfPath?`.

Stack (upgraded 2026-10-31): AGP 9.5.0 (built-in Kotlin, no `kotlin-android` plugin), Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00 (+ `material-icons-core`, no longer pulled in by Material 3), Room 2.8.5, WorkManager 2.12.0, kotlinx-serialization 1.12.0, Gradle 9.8.2, compileSdk/targetSdk 37, Java 17 bytecode, builds on JDK 25 (Android Studio JBR).

## 4. Deviations from the ADR (intentional, made during the build)

The ADR says "no network, INTERNET not requested". The app has since grown:

- Manifest now has `INTERNET` and `READ_SMS` (for WebView PDF download and inbox backfill).
- A heuristic filter detects unknown vendors (any link plus receipt-like text, including the Hebrew `פתקית` keyword), in addition to the domain allowlist.
- The 30-day rule: receipts older than 30 days are stored but never auto-downloaded. UI shows "Not available" for them.
- UI redesign: vendor list, per-receipt rows with date and download indicator, details screen with original link, id, and download status.
- Default tap opens the downloaded PDF through a FileProvider; falls back to the browser when no PDF exists.

The ADR and spec were not updated for these. If the ADR matters, amend it.

## 5. Open problems, most important first

1. **PDF download for Shufersal is unreliable.** Shufersal links go through an Angular page (`DIShortcutURL`) that redirects with JavaScript, often failing intermittently ("Response is null"). The download button (`button.iconDownlond`) is found and clicked, but its download did not reach `DownloadListener` (probably a blob or fetch-based download). Injecting JS hooks broke the page, so they were removed. The current mitigation is the `printToPdf` fallback with a minimum-height check and an 8 second debounce for redirects. This was being tested on the Pixel 10 when the session ended. One SuperPharm PDF (from `superpharmstorage.blob.core.windows.net`) was downloaded successfully, so the mechanism works for standard downloads.
2. **Last test run showed no downloads starting.** On Oct 17 the app was relaunched with a restored DB copy and no downloads began. The suspected cause is the 30-day filter, because the restored receipts were older than 30 days. This was not confirmed. When testing, use receipts from the last 30 days or temporarily raise the cutoff (constants in `SmsBackfill.kt`, `VendorReceiptsScreen.kt`, `ReceiptDetailScreen.kt`; the cutoff is duplicated in three places and should become one constant).
3. **Other vendors need their own handling:** pairzon links often show an "expired" dialog; wee.ai/Weezmo and Delek need a "view invoice" click first.
4. **Backend link is local only.** Sign-in and PDFs go through doron-backend (2026-10-31). Staging needs the Android client ID added to `GOOGLE_OTHER_CLIENT_IDS` and a deploy with Chrome in the image. Gemini extraction is not wired yet.
5. **Vertex AI SDK is deprecated.** `@google-cloud/vertexai` should move to `@google/genai`.
6. **Extraction results have nowhere to land yet.** `CapturedReceiptEntity` needs columns (or a child table) for merchant, items, total, tax, currency. `totalAmount` exists but is not populated.
7. **Tidy-ups:** stale `GCS_BUCKET` in `backend/.env.example` (storage is local disk now), ADR not updated, no backend tests.

## 6. Decisions the owner made (do not relitigate)

- Android only. Going to Google Play (decided 2026-11-05): the SMS permission is declared under "SMS-based money management", so the budget comes first.
- No GCP deployment for now. Run the backend locally only.
- PDF strategy: 1) find the vendor's download button, 2) if found use that file, 3) if not, render our own PDF. Either way the PDF must be available inside the app. Later, feed the PDF to Gemini.
- Open the file by default; browser only as fallback. Show a download indicator beside the "i" icon and download status in the details screen.
- Do not auto-download receipts older than 30 days.
- Logo and theme: blue (`#1A3298`), from a Gemini-generated logo (phone with receipt going into a folder). App icon and splash were updated to match.
- Keep the Shufersal merchant logo for now.

## 7. Privacy and security notes

- The app can read all SMS. Keep it off shared devices. The filter is the gate; do not weaken it.
- `backend/.env` holds a dev `API_KEY` and `GCP_PROJECT_ID`. It is **not** in the zip. Recreate it from `backend/.env.example`.
- `local.properties` is machine-specific and not in the zip.
- Receipt URLs contain access tokens. Do not paste real links into issues or chats you do not control. Pulled phone databases must never be committed.
- The repository is public. Never commit real receipt links, SMS bodies, pulled databases, or keys.

## 8. Next steps (suggested order)

1. (Done) Work committed and published as a public GitHub repo.
2. Reproduce the Shufersal download problem on a device with recent receipts. Confirm whether the 30-day filter was the cause of "no downloads". Capture logcat with `adb logcat -s ReceiptPdfDownloader:* ReceiptDownload:*`.
3. Fix Shufersal: try intercepting the click's network calls (`shouldInterceptRequest` already logs them), or evaluate a fetch of the download endpoint, or accept the `printToPdf` fallback if the SPA redirect can be made reliable.
4. (Done 2026-10-31) Instrumented tests fixed and passing on a Pixel 10 API 37 emulator: `gradle :app:connectedDebugAndroidTest`.
5. Move the 30-day constant to one place.
6. (Done 2026-11-05, no AI) Amounts: doron-backend reads the amount paid from the receipt page and sends `X-Receipt-Total` with the PDF; without it the phone reads the PDF's text (Android 15+, `PdfText`/`ReceiptTotals`); else the user types it. `totalSource` says which (page, pdf, user, none); a typed amount is never replaced. (Done) Monthly spending and budget: `SpendingScreen` from the home "This month" card; categories per store (guessed from the sender name, `Category.guessFor`, changed by tapping a store); a monthly budget overall and per category, amber from 80%, red over. Next: a notification near the limit (needs POST_NOTIFICATIONS).
7. Migrate backend to `@google/genai`, add a couple of tests.
8. Update the ADR/spec to match section 4.

## 9. Running things

See `README.md` for setup. Useful commands:

```bash
gradle assembleDebug :app:testDebugUnitTest
gradle :app:installDebug
adb devices -l
adb -s <serial> logcat -d -s ReceiptPdfDownloader:* ReceiptDownload:*
adb -s <serial> shell run-as com.neonsheep.myreceipts cat databases/<db> > /tmp/receipts.db   # debug build only
sqlite3 /tmp/receipts.db "select senderLabel, datetime(timestamp/1000,'unixepoch'), pdfPath from captured_receipts order by timestamp desc"
```

The earlier sessions pulled the DB with `adb pull`/`run-as`. The Room file name is set in `ReceiptStore.kt`.

## 10. Where earlier thinking lives

- `docs/superpowers/specs/2026-10-14-android-sms-receipt-capture-design.md` approved design.
- `docs/superpowers/plans/2026-10-14-skeleton-app.md` and `2026-10-14-sms-capture-pipeline.md` task-by-task plans (written for the superpowers plugin; checkboxes are not all updated).
