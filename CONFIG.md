# API Keys and Configuration

## Application Config

`application.yml` is not committed (it may contain project-specific values). Copy the example to create your own:

```bash
cp backend/src/main/resources/application.yml.example backend/src/main/resources/application.yml
```

Then edit `application.yml` with your settings.

---

## Summary

| What | Key/Credential | Where to Put |
|------|----------------|--------------|
| **ADK / Gemini** (Approach B, general Q&A) | `GOOGLE_API_KEY` | Environment variable |
| **GCP Retail / Conversational Commerce** | Service account JSON or Application Default Credentials | `GOOGLE_APPLICATION_CREDENTIALS` or `gcloud auth` |
| **GCP project settings** | `GCP_PROJECT_ID`, `GCP_PLACEMENT`, `GCP_BRANCH` | Environment variables or `application.yml` |

---

## 1. ADK / Gemini (for Approach B and General Q&A specialist)

**Used by:** ADK orchestrator agent, General Question specialist (Approach A)

**Get a key:**
1. Go to [Google AI Studio](https://aistudio.google.com/)
2. Click **Get API key** → Create API key
3. Copy the key

**Where to put it:**

```bash
export GOOGLE_API_KEY="your-api-key-here"
```

To make it permanent, add to `~/.zshrc` or `~/.bash_profile`:
```bash
echo 'export GOOGLE_API_KEY="your-api-key-here"' >> ~/.zshrc
source ~/.zshrc
```

Or create a `.env` file in the project root and load it before running:
```bash
# .env (add to .gitignore - do not commit)
GOOGLE_API_KEY=your-api-key-here
```

Then run: `source .env && ./run-app.sh`

---

## 2. GCP Retail / Conversational Commerce API

**Used by:** Approach A (Convo Commerce as orchestrator), and the product search tool in Approach B

**Prerequisites:**
- GCP project with [Vertex AI Search for commerce](https://cloud.google.com/retail/docs/conversational-commerce-dev-guide) enabled
- Retail catalog and placement configured

**Option A: Application Default Credentials (recommended for local dev)**

```bash
gcloud auth application-default login
```

This uses your user credentials. No separate key file.

**Option B: Service account JSON**

1. In [GCP Console](https://console.cloud.google.com/) → IAM & Admin → Service Accounts
2. Create a service account with **Vertex AI User** and **Retail Admin** (or equivalent) roles
3. Create a key (JSON) and download it
4. Set the path:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your-service-account-key.json"
```

---

## 3. GCP Project Configuration

**Environment variables:**

```bash
export GCP_PROJECT_ID="your-gcp-project-id"
export GCP_PLACEMENT="projects/your-project-id/locations/global/catalogs/default_catalog/placements/default_search"
export GCP_BRANCH="projects/your-project-id/locations/global/catalogs/default_catalog/branches/default_branch"
```

**Or** set in `backend/src/main/resources/application.yml` (avoid putting secrets here):

```yaml
conversational-commerce:
  enabled: true
  project-id: your-project-id
  placement: projects/your-project-id/locations/global/catalogs/default_catalog/placements/default_search
  branch: projects/your-project-id/locations/global/catalogs/default_catalog/branches/default_branch
```

---

## 4. Enable Conversational Commerce

Set this to turn on the real GCP Retail API (otherwise stub responses are used):

```bash
export CONVERSATIONAL_COMMERCE_ENABLED=true
```

Or in `application.yml`:
```yaml
conversational-commerce:
  enabled: true
```

---

## 5. Quick Setup Checklist

**Minimum (stub mode, no GCP):**
- `GOOGLE_API_KEY` – for ADK/Gemini (Approach B and general Q&A)

**Full setup (real product search):**
- `GOOGLE_API_KEY` – for ADK/Gemini
- `GOOGLE_APPLICATION_CREDENTIALS` or `gcloud auth application-default login` – for Retail API
- `GCP_PROJECT_ID`, `GCP_PLACEMENT`, `GCP_BRANCH` – project config
- `conversational-commerce.enabled=true` – enable real Retail API

---

## 6. Example `.env` file (add to .gitignore)

```bash
# .env - DO NOT COMMIT
GOOGLE_API_KEY=AIza...
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
GCP_PROJECT_ID=my-project-123
GCP_PLACEMENT=projects/my-project-123/locations/global/catalogs/default_catalog/placements/default_search
GCP_BRANCH=projects/my-project-123/locations/global/catalogs/default_catalog/branches/default_branch
CONVERSATIONAL_COMMERCE_ENABLED=true
```

Load before running: `set -a && source .env && set +a && ./run-app.sh`
