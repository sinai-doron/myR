# My Receipts backend

NestJS 11 service. Takes a receipt URL, produces a PDF, extracts structured data with Gemini, stores the PDF on local disk. **Local use only.** Nothing is deployed to GCP. The Android app does not call it yet.

## Flow

`POST /receipts/process { url, clientId }`
1. `RendererService` opens the URL in headless Chromium (Puppeteer). If it finds a download button (`button.iconDownlond`, `[class*="download"]`, `a[download]`) it clicks it and uses the vendor PDF. Otherwise it prints the page to PDF.
2. `ExtractorService` sends the PDF to Gemini (`gemini-2.0-flash`, Vertex AI, location `me-west1`) with a JSON response schema: merchant, date, items (name, quantity, unit price, total), subtotal, tax, total, currency, store location, payment method.
3. `StorageService` writes `<STORAGE_PATH or ./receipts-data>/<clientId>.pdf`.

`GET /receipts/:clientId/pdf` returns the stored PDF. Both endpoints require header `x-api-key: <API_KEY>`.

## Setup

```bash
cp .env.example .env     # then edit
npm ci
npm run build
npm run start:prod       # listens on PORT, default 8080
```

`.env` keys:

| Key | Required | Meaning |
|---|---|---|
| `GCP_PROJECT_ID` | yes | project used for Vertex AI calls |
| `API_KEY` | yes | shared secret for the `x-api-key` header |
| `STORAGE_PATH` | no | folder for PDFs, default `./receipts-data` |
| `PORT` | no | default 8080 |

`GCS_BUCKET` in `.env.example` is left over from the original cloud design and is unused.

Gemini calls need Google Cloud credentials on the machine, for example `gcloud auth application-default login` with access to the project. This was not re-verified at handover.

Puppeteer downloads its own Chromium on `npm ci`. The Dockerfile instead uses system Chromium and expects a prebuilt `dist/`.

## Test it

```bash
curl -X POST localhost:8080/receipts/process \
  -H 'content-type: application/json' -H "x-api-key: $API_KEY" \
  -d '{"url":"<receipt link>","clientId":"test-1"}'
```

## Known gaps

- No automated tests (`npm test` finds none).
- `@google-cloud/vertexai` is deprecated; migrate to `@google/genai`.
- Shufersal links redirect via JavaScript and can fail intermittently in headless Chromium too.
- `receipts-data/` holds real receipts and is gitignored.
