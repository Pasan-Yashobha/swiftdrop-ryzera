# SwiftDrop Assistant Service

An AI chat assistant for customers, built on the Model Context Protocol (MCP). Java 21, Spring Boot 3.5.16,
Spring AI 1.1.8 (MCP server) and Google Gemini. Port 8088 locally, 8080 inside Docker.

## How it works

```
Chat widget  --POST /api/v1/assistant/chat (user's JWT)-->  ChatService
                                                              |  1. ask Gemini (with the tool list)
                                                              |  2. Gemini asks for a tool, e.g. estimate_fee
                                                              v  3. run it over MCP, with the user's own token
                                                         /mcp  (MCP server, Streamable HTTP, this same service)
                                                              |
                                                              v
                                          Pricing / Tracking / Parcel APIs (called with the user's token)
```

- **MCP server** (`/mcp`): exposes three tools. Any MCP client can use it, for example the MCP Inspector or Claude
  Desktop, as long as it sends a valid Identity JWT.
- **Chat loop**: asks the model, runs the tools the model requests through the real MCP endpoint, hands the
  results back, and repeats for at most 4 tool rounds.
- **The user's token**: the chat endpoint forwards the caller's `Authorization` header to MCP, and each tool
  forwards it to the real service. The assistant therefore never sees more than the user could see directly. The
  token comes from the HTTP header, never from the model, and is never returned to the page.

## Tools

| Tool | What it does | Backed by |
|---|---|---|
| `estimate_fee` | Fee in LKR between two zones for a weight, size, delivery type and optional promo | Pricing `POST /api/v1/pricing/estimate` (real) |
| `track_parcel` | Status and history of a parcel by tracking number | Tracking service (sample data until connected) |
| `get_history` | The signed-in user's recent parcels | Parcel service (sample data until connected) |

Tools never throw. A bad input or a failing service comes back as `{"error": "..."}` with a sentence the model
relays; for an unknown zone, `estimate_fee` lists the real zone codes so the model can correct itself.

**Sample data.** While `ASSISTANT_TRACKING_URL` or `ASSISTANT_PARCEL_URL` is blank, those two tools answer with
sample data marked `"demo": true`, and the assistant is told to say so. Set the URL (and, if needed,
`assistant.downstream.tracking-path` / `history-path`) to switch to the real service. The answer is passed to the
model as it is, limited to 4000 characters, so no response shape had to be agreed to connect it.

## API

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/assistant/chat` | Customer or Merchant |
| DELETE | `/api/v1/assistant/conversations/{id}` | Customer or Merchant ("new chat") |
| any | `/mcp` | Customer or Merchant (MCP Streamable HTTP) |
| GET | `/actuator/health` | Public |

```json
POST /api/v1/assistant/chat
{ "message": "How much to send 2 kg express from Colombo to Kandy?", "conversationId": "optional" }

200
{ "conversationId": "...", "reply": "...", "toolCalls": [ { "tool": "estimate_fee", "arguments": {...}, "failed": false } ] }
```

`toolCalls` lets the page show how an answer was produced. Errors are JSON problem details whose `detail` is a
safe sentence: 400 (empty or over-long message), 401/403 (token or role), 429 (more than 10 messages a minute per
user), 503 (model not configured or its usage limit reached; tools unavailable), 502 or 504 (model problem).

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | none | Google AI Studio key. Keep it in your local `.env`, never in Git |
| `GEMINI_MODEL` | none | A current Gemini model name from AI Studio. Not hard-coded because names change. `gemini-flash-lite-latest` worked well in testing (about 2 s, correct tool calls); the larger Flash models were overloaded at the time, and `gemini-2.5-flash` is already retired |
| `PRICING_SERVICE_URL` | `http://localhost:8086` | Pricing service |
| `ASSISTANT_TRACKING_URL`, `ASSISTANT_PARCEL_URL` | blank | Tracking and Parcel services; blank means sample data |
| `IDENTITY_JWKS_URI` | none | Identity public keys. Required unless using the `dev` profile |
| `DEV_JWT_HMAC_SECRET` | dev default | Dev profile only; the same secret as Pricing's dev tokens |
| `SERVER_PORT` | 8088 | HTTP port |

Without both `GEMINI_API_KEY` and `GEMINI_MODEL`, chat answers 503 "The AI model is not configured on this server
yet", and the MCP tools still work. The model is chosen in `LlmConfig`: another model is another `LlmClient`.

## Run locally

From the repository root, in PowerShell, with Pricing running on 8086:

```powershell
$env:GEMINI_API_KEY = "<your key>"
$env:GEMINI_MODEL = "<model name from AI Studio>"
mvn -f services/assistant/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Get a token from Pricing's dev endpoint (the same HS256 secret) and chat:

```powershell
$t = (Invoke-RestMethod -Method Post http://localhost:8086/api/v1/dev/tokens -ContentType application/json `
  -Body '{"sub":"pricing-demo-customer","role":"CUSTOMER"}').accessToken
Invoke-RestMethod -Method Post http://localhost:8088/api/v1/assistant/chat -ContentType application/json `
  -Headers @{ Authorization = "Bearer $t" } -Body '{"message":"How much to send 2 kg from Colombo to Kandy?"}'
```

## Tests

```powershell
mvn -f services/assistant/pom.xml verify
```

No API key and no Docker are needed. The language model is replaced by a scripted fake, so results are always the
same. Downstream services are faked with a small in-process HTTP server. The tests cover: the three tools called by
a real MCP client over HTTP (including that the user's token reaches the downstream service); the chat loop (tool
rounds, the round limit, memory, trimming, expiry, isolation between users, the rate limit); the Gemini request and
response mapping and its error handling; and the whole service end to end over HTTP, including roles. `verify`
also enforces 70% line coverage.

## Security and privacy

- Every endpoint needs a valid Identity JWT; only Customer and Merchant roles are allowed. Everything else is denied.
- Tool results are treated as data, not instructions (stated in the system prompt), and are length-limited.
- The Gemini key is sent in a header, never in a URL, and message text and keys are never logged.
- Provider error bodies are never passed to the user; they get a fixed sentence.
- On the free Gemini tier Google may use submitted content to improve its products (check the current terms), so
  use demo accounts and never real personal data.

## Not done yet

- Conversations live in memory only (30-minute expiry, last 10 turns, 5 per user). Saving them to a database needs
  an `assistant_db`, which `infra/postgres/init/01-init-databases.sh` does not create yet.
- Metrics (`ai_tool_calls_total` and others from the design) and a Prometheus endpoint.
- The chat widget in the Angular app.
- Real Tracking and Parcel connections: they wait for those services.
