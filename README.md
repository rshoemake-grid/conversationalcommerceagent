# Conversational Commerce Agent

A full-stack application (Spring Boot + React) that provides a chat UI to interact with Google's GCP Conversational Commerce agent, with a pluggable agent architecture supporting two orchestration modes.

## Architecture

- **Approach A (Convo Commerce as Orchestrator)**: Conversational Commerce API is primary; routes general questions to an ADK specialist agent.
- **Approach B (ADK as Orchestrator)**: ADK LlmAgent orchestrates; uses Conversational Commerce as a tool for product search, plus loyalty/recommendation tools.

## Prerequisites

- Java 17+
- Node.js 18+
- Maven (or use `./mvnw` wrapper)
- For GCP integration: Google Cloud project with Vertex AI Search for commerce enabled

## Quick Start

### Run both (recommended)

```bash
./run-app.sh
```

Starts the backend (http://localhost:8080) and frontend (http://localhost:5173). Press Ctrl+C to stop.

### Or run separately

**Backend:**
```bash
cd backend
./mvnw spring-boot:run
```

The API runs at http://localhost:8080. By default, GCP is disabled and stub responses are returned.

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

The UI runs at http://localhost:5173 and proxies `/api` to the backend.

### API Keys & Configuration

See **[CONFIG.md](CONFIG.md)** for full details. Summary:

| Credential | Where | Purpose |
|------------|-------|---------|
| `GOOGLE_API_KEY` | Environment variable | ADK/Gemini (Approach B, general Q&A) |
| `GOOGLE_APPLICATION_CREDENTIALS` | Path to service account JSON | GCP Retail API |
| `GCP_PROJECT_ID`, `GCP_PLACEMENT`, `GCP_BRANCH` | Environment or `application.yml` | GCP project config |

**Minimum for Approach B:** Set `GOOGLE_API_KEY` (get from [Google AI Studio](https://aistudio.google.com/)).

**For real product search:** Also enable GCP Retail (see CONFIG.md).

## Running Tests

```bash
cd backend
./mvnw test
```

## API

POST `/api/chat`:

```json
{
  "mode": "convo_commerce" | "adk_orchestrator",
  "message": "user message",
  "conversationId": "optional for follow-up",
  "sessionId": "optional for visitor tracking"
}
```

Response:

```json
{
  "text": "assistant response",
  "conversationId": "session id",
  "refinedQuery": "optional search query",
  "products": []
}
```

## Project Structure

```
ConversationalCommerceAgent/
├── backend/           # Spring Boot
│   ├── agent/         # ConversationalAgent, adapters
│   ├── orchestration/ # ConvoCommerceOrchestrator, AdkOrchestrator
│   ├── tool/          # ADK tools (ConversationalCommerceTool, LoyaltyRecommendationTool)
│   └── web/           # ChatController
├── frontend/          # React + Vite
│   └── src/
│       ├── api/       # chatApi
│       └── components/ # ChatInterface, MessageList, ModeSelector
└── README.md
```
