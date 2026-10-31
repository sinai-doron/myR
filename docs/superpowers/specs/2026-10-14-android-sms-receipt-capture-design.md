# Android SMS Receipt Capture — Phase 1 Design Spec

**Source:** ADR 001: Android SMS Receipt Capture (Oct 13 2026, @Doron Sinai)
**Status:** Approved for build
**Target device:** Pixel 10 (Android 16 / API 36)

## Scope

A native Android app (Kotlin) that captures inbound SMS, filters for grocery receipt messages, extracts the receipt link, and stores it locally. No network, no backend, no accounts. Nothing leaves the device.

Part of the Personal Grocery Price Intelligence system. Forwarding to GCP is designed but deferred to phase 2.

## Architecture

Single-module Kotlin app. Gradle with Kotlin DSL. Sideloaded, not published to Play Store.

```
Inbound SMS
  -> SmsReceiver (BroadcastReceiver, RECEIVE_SMS)
     -> ReceiptFilter (pure function: body -> list of on-list links + sender hint)
        -> match: ReceiptStore (Room) writes record
        -> no match: ignored, never stored
```

### Components

**SmsReceiver** — `BroadcastReceiver` registered for `SMS_RECEIVED`. Reassembles multipart messages (including UCS-2/Hebrew), runs the filter, writes the record. No network, no heavy work.

**ReceiptFilter** — Pure function. Takes message body, returns list of on-list links plus sender label as a routing hint. The sender label is informational, not a gate (alphanumeric sender IDs are spoofable). Only links whose host matches the merchant-domain list pass through.

**ReceiptStore** — Room database. Persists captured receipt records on device. Schema shaped for the future ingest payload so phase 2 just adds a drain job.

**MerchantConfig** — Bundled JSON file in assets. Contains sender hints and domain allowlist. Initial merchants:
- Shufersal: domain `shufersal.co.il`
- Pairzon: domain `pairzon.com`

**CapturedListScreen** — Minimal Jetpack Compose screen listing captured receipts. For development verification. Shows link, sender, timestamp, sync status placeholder.

**ForwardReceiptWorker** — Deferred to phase 2. WorkManager job that drains ReceiptStore to the GCP ingest endpoint.

## Data Model

One Room entity, one row per on-list link extracted:

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long (auto) | Room primary key |
| `link` | String | Extracted receipt URL |
| `senderLabel` | String | Alphanumeric sender ID (e.g. "Shufersal") |
| `timestamp` | Long | Message timestamp, epoch milliseconds |
| `clientId` | String | UUID v5 from link+timestamp, for idempotency |
| `capturedAt` | Long | When the app stored the record, epoch ms |

The raw message body is never stored.

**Mixed links (the Pairzon case):** When a message contains both on-list and off-list links, store only the on-list links. The server makes the trust decision in phase 2.

**Retention:** Keep all records, no cap. Receipt records are tiny (~200 bytes each).

## Detection Rules

A message is a receipt candidate only if its body contains a URL whose host matches a domain in MerchantConfig. That single rule is the gate.

- No link -> ignored
- Link to unknown host -> ignored
- Link to on-list host -> receipt candidate, extract and store

## Manifest & Permissions

- `RECEIVE_SMS` only (no `INTERNET` until phase 2)
- Receiver exported with `android:permission="android.permission.BROADCAST_SMS"`
- `BOOT_COMPLETED` receiver to survive reboots (re-registration)

## Build Configuration

- `minSdk 26` (emulator testing flexibility)
- `targetSdk 36` (Pixel 10 / Android 16)
- Local debug signing for sideload
- No Play Store configuration

## Dependencies

- Room (local persistence)
- kotlinx-serialization (record shape / merchant config parsing)
- Jetpack Compose + Material 3 (captured-list screen)
- JUnit 5 + Truth (unit tests)
- Robolectric or AndroidX Test (instrumented tests for the receiver)

Not included in phase 1: WorkManager, HTTP client, any networking library.

## Testing Strategy

**Unit tests:**
- `ReceiptFilter` against real-world example messages:
  - Shufersal: single on-list link -> extracted
  - Pairzon (OSHERAD): two links including a bit.ly decoy -> only on-list link extracted
  - OTP message -> no links, ignored
  - Bank alert -> no links, ignored
  - Hebrew multipart message -> correct reassembly and link extraction
- Record shape: `clientId` (UUID v5) stability — same input always produces the same id
- MerchantConfig parsing from JSON

**Instrumented tests:**
- Feed a crafted `SMS_RECEIVED` intent to `SmsReceiver`
  - Receipt message -> record written to Room
  - Non-receipt message -> no record written

## Future: Unknown Vendor Detection

Phase 1 uses a conservative domain allowlist. A later phase will add heuristic detection for receipts from unknown vendors — messages that look like receipts but whose domain isn't on the list. Likely signals: any URL + receipt-like body patterns (currency symbols, "total"/"receipt"/"order"/"הזמנה", merchant-sounding sender label).

This is why `ReceiptFilter` is designed as a clean interface returning a result object (matched links + sender hint + match confidence), not a hardwired domain check. The domain allowlist becomes one strategy behind that interface; a heuristic layer slots in alongside it later without touching `SmsReceiver` or `ReceiptStore`.

For phase 1: domain match only, but the seam is there.

## Scope Boundaries (NOT in this build)

- No receipt page rendering or screenshotting
- No receipt content or line-item parsing
- No price data storage or querying
- No barcode scanning or in-aisle lookup
- No backend communication or forwarding
- No accounts, login, or user identity
- No authoritative allowlist decision (server-side in phase 2)
