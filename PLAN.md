# Blaise Agent — implementation plan

## Product goal

Build a local-first desktop financial assistant: describe an operation in chat,
let a local model collect the necessary information, and execute the operation
through a typed gateway integration. Begin with Woovi sandbox Pix charges in BRL.

### Confirmed direction

- App name: **Blaise Agent**; GitHub repository: **blaise-agent**.
- Public GitHub repository owned by `iagxferreira`.
- Kotlin, Compose Desktop, LangChain4j, and Ollama.
- Check Ollama availability when the app launches.
- Load installed models dynamically from Ollama and allow switching in the UI.
- Provide a Settings screen that stores Woovi API keys in the OS credential store.
- Woovi is the first test platform; additional gateways follow the same interface.
- Atomic changes and semantic commit messages throughout development.

### Environment observed during planning

On September 22, 2026, the development machine reported Ollama `0.33.3`, with
`qwen2.5-coder:3b` installed and a responding service. This is a local observation,
not a pinned runtime requirement or evidence that this model reliably calls tools.
Verify model suitability during the Ollama milestone.

## MVP boundary

Deliver conversational creation of a Woovi sandbox Pix charge, display the returned
payment link, and retrieve its status in the same conversation. Include local
conversation history and enough settings/diagnostics to run the app independently.

Production operations, outbound transfers, refunds, recurring payments, additional
gateways, hosted multi-user deployments, and webhook infrastructure are follow-ups.
Use status lookup for the first desktop flow; public webhook hosting is unnecessary
for that slice.

## Architecture

Use one Gradle application in `desktop/`, with the proposed base package
`dev.blaiseagent`. Pin compatible dependency versions when scaffolding.

| Package | Responsibility |
| --- | --- |
| `ui` | Compose screens, components, theme, rendering, and user input |
| `state` | Chat/settings state, lifecycle, and orchestration |
| `agent` | LangChain4j service, Ollama integration, chat memory, tool adapters |
| `payments` | Exact money types, operation rules, gateway interface, Woovi adapter |
| `config` | Endpoint/model settings and credential loading |
| `storage` | Local conversations and operation records |

Keep gateway calls, validation, and persistence out of composables. Agent tools and
UI actions must use the same payment operations. Carry structured tool results to
the UI, rather than parsing generated prose for links or charge identifiers.

## Milestones

### 0. Public project foundation

- [x] Publish the public GitHub repository: https://github.com/iagxferreira/blaise-agent.
- [x] Write README, implementation plan, and agent/contributor guidelines.
- [x] Define atomic Conventional Commit workflow and local-data exclusions.
- [ ] Select a license with the maintainer before distributing application releases.

**Acceptance:** Public repository contains accurate planning-stage documentation,
with scoped commits and no credentials or generated artifacts.

### 1. Runnable Compose Desktop foundation — implementation delivered; visual review pending

- [x] Add Gradle wrapper, Kotlin/JVM, Java 21, Compose Desktop, and Material 3.
- [x] Use MindGraph's desktop patterns as a reference for window, theme, and state.
- [x] Build sidebar, empty conversation, message list, composer, and settings surface.
- [x] Introduce chat domain/state with a replaceable agent interface.
- [ ] Add build and unit-test automation after the desktop foundation is stable.
- [ ] Manually review the running UI, including keyboard input and window resizing.

The initial slice has a runnable shell and eight passing offline conversation-state
tests. The default agent is unavailable, so sending is disabled and drafts are
preserved. Settings is informational; it does not accept or persist credentials.
Ollama/LangChain4j are the next integration. No gateway or model calls are made.

Local build/test and process startup were verified with JDK 21. Automated screen
capture was denied by the desktop session, so appearance and interactive behavior
have not been visually verified. This milestone is not marked complete yet.

**Acceptance:** A clean checkout builds and tests with the wrapper. The app opens
locally, input works, and the UI remains responsive. Temporary demo behavior is
explicitly labeled until connected to the agent.

### 2. Ollama startup and conversational agent — initial connection delivered

- [x] Check endpoint health with a bounded timeout off the UI thread.
- [x] Show a startup toast for reachable/unreachable Ollama and no-model states.
- [x] Discover models through `GET /api/tags` and use an available model for chat.
- [x] Refresh installed models and switch the active model between requests.
- [x] Connect LangChain4j's streaming Ollama adapter to the chat state.
- [ ] Distinguish checking, ready, unreachable, and incompatible model states in the
  persistent application state.
- Reuse running Ollama. For an unavailable local endpoint, provide an explicit
  start action, detect the binary, invoke `ollama serve` without a shell, and poll
  readiness with a deadline. Handle another process starting it concurrently.
- Track ownership of any launched process; never terminate a pre-existing service.
- Populate the Settings model picker dynamically using `GET /api/tags`; show name,
  size, and available metadata. Refresh on settings open, endpoint changes, and
  explicit refresh. Handle empty lists, errors, and a saved model being removed.
- Persist endpoint/model selection as non-secret preferences. Apply model changes
  between requests, never midway through generation or a payment tool operation.
  Snapshot the selected model per request and record it on assistant messages.
- Distinguish local models from cloud-backed entries, keeping the MVP local-only.
- Offer explicit download only if requested; do not hard-code installed models.
- Verify model metadata and a harmless tool-call smoke test before payment tools
  are enabled; an installed model alone is insufficient evidence of compatibility.
- Connect LangChain4j, stream chat responses, maintain per-conversation memory,
  expose cancellation, and surface connection/model errors.

The initial connection slice uses Retrofit with Kotlin serialization for typed HTTP
interfaces. This is the HTTP pattern intended for Woovi and future gateways; it
keeps external API boundaries declarative and testable without adding Spring to the
desktop application. LangChain4j owns the Ollama chat stream.

**Acceptance:** Existing local Ollama is reused. Offline/missing-model scenarios
show actionable UI states. The picker reflects API results and refreshes after
external model installation/removal. Selecting another compatible model changes
the next request without losing chat context or interrupting in-flight operations.
A compatible local model answers in chat and completes
a deterministic dummy-tool smoke test. Automated tests fake Ollama; a separate
manual integration check records the tested model and version.

### 3. Payment domain and Woovi sandbox adapter

- Define `PaymentGateway` around create-charge and get-charge operations.
- Represent BRL using exact minor units; validate positive amounts, bounds, and
  required inputs before issuing a request. Reject ambiguous amounts for clarification.
- Confirm current Woovi sandbox API base URL, authentication scheme, charge request
  fields, payment-link response field, status mapping, and correlation-ID semantics
  against the official API reference. Record these in adapter documentation/tests.
- Add a Settings screen with a masked Woovi key field and save, replace, remove,
  and test-connection actions. Connection testing uses a verified non-mutating
  provider endpoint, never charge creation. Show sanitized results.
- Introduce a `CredentialStore` abstraction backed by Linux Secret Service first;
  use macOS Keychain and Windows Credential Manager when those platforms ship.
  Scope entries to the app, gateway, and environment. Select and validate a JVM
  integration library during implementation, including packaging requirements.
- Persist only credential references/non-secret settings in ordinary preferences.
  If the store is locked or unavailable, explain the issue and offer session-only
  use; never silently fall back to plaintext or a bundled encryption key.
- Retrieve keys only in the gateway layer. Never include secrets in model context,
  saved UI state, conversation storage, exports, HTTP traces, or error messages.
  Clear input/session references when no longer needed; JVM memory cannot provide
  guaranteed zeroization. The OS store protects saved secrets, not a compromised
  logged-in user session.
- Allow environment credentials for explicit development/test use; label their
  source in Settings and define precedence over saved keys. Session-only input
  takes precedence for that session; removal of a saved key does not unset an
  environment variable. Test and document these cases.
- Implement timeouts and typed errors for validation, authorization, rate limiting,
  network failures, and unavailable services.
- Persist a stable operation/correlation identifier before create calls. Verify
  provider duplicate semantics and reconcile ambiguous timeouts by lookup before
  considering a retry; do not blindly retry charge creation.
- Test the adapter with synthetic fixtures and a mock HTTP server. Keep actual
  sandbox tests opt-in and out of default CI.

**Acceptance:** Adapter tests verify request/response mapping, money conversion,
error handling, and duplicate/uncertain outcomes. An opt-in sandbox check creates
a charge, obtains its real link, and retrieves its status. Settings can save,
replace, retrieve after restart, and remove a sandbox key via the Linux credential
store. Locked/missing-store tests verify session-only behavior and no plaintext
fallback. Fake-store tests cover UI state and credential-source precedence; manual
OS integration checks verify storage and secret redaction separately.

### 4. Chat-to-payment vertical slice

- Expose narrowly typed create-charge and get-charge tools through LangChain4j.
- Ask for missing information and execute complete user requests through the
  payment service; keep operation rules in Kotlin rather than only in prompts.
- Bind charge references to conversation/operation records for follow-up questions.
- Show tool activity and payment cards from structured gateway results.
- Add copy/open link and status refresh; preserve operation state when generation
  fails or is canceled after a gateway request has been sent.
- Bound tool execution loops and reuse the same operation identity for repeated
  attempts at an in-flight operation. A deliberate new charge gets a new identity.

**Acceptance:** “Create a Pix payment link for R$150 for consulting” produces one
Woovi sandbox charge and its actual payment link. “Has it been paid?” retrieves
that charge. Missing inputs, tool errors, and generation interruptions do not
produce invented success messages or accidental duplicate charges.

### 5. Durable desktop experience

- Persist conversations, messages, charge references, and operation outcomes locally
  with an explicit schema and migrations. Choose the store before this milestone;
  SQLite is a candidate for transactional operation records.
- Persist minimum operation records in milestone 3, then extend storage here.
- Support new/switch/delete conversation actions and bounded agent context.
- Restore history and model settings across restarts; do not repeat side effects
  when reconstructing chat memory.
- Treat cancellation of generation separately from the outcome of an already-sent
  gateway request. Reconcile unresolved operations after restart.
- Finish keyboard behavior, scrolling, empty/loading/error states, and settings.

**Acceptance:** Restart preserves history and operation identity. Switching chats
isolates context. Removing local chat history does not cancel a provider charge.
Document the actual local data path and deletion behavior.

### 6. First preview release

- Run the documented sandbox walkthrough and record the tested Ollama model.
- Add screenshots and accurate setup/troubleshooting instructions.
- Package and manually test Linux first; validate other operating systems before
  advertising support for their packages.
- Publish a versioned preview after selecting a license and validating packaging.

**Acceptance:** A new user can install the preview, connect local Ollama, configure
Woovi sandbox credentials, and complete the create-link/status-check walkthrough.

## Delivery and verification

Each milestone is split into small changes with one purpose. Commit subjects use
Conventional Commits, for example:

```text
build(desktop): bootstrap Compose application
feat(ollama): discover available local models
feat(agent): stream local model responses
feat(payments): define charge operations
feat(woovi): create sandbox Pix charges
feat(chat): display payment tool results
```

Each commit must leave the project coherent; after scaffolding, each must compile
and pass relevant checks independently. Include an interface and its required
call-site updates together. Test payment rules, operation identity/reconciliation,
adapter mapping, and meaningful state transitions. Validate Compose visually in a
running app and report that separately from compilation.

Default tests use fakes/mock servers and need neither Woovi credentials nor a local
model. Live Ollama and sandbox checks are explicit integration checks. Update the
README and milestone checkboxes as functionality is actually delivered.

## Decisions to resolve at implementation time

- License selection by the maintainer.
- Compatible pinned Kotlin/Compose/LangChain4j versions.
- Local model that passes the real tool-calling smoke test on available hardware.
- Verified Woovi sandbox API contract and test credentials supplied locally.
- Conversation storage choice and maintained JVM OS-credential-store integration.

## Sources

- [Woovi sandbox setup](https://developers.woovi.com/en/docs/intro/test-environment)
- [Woovi API reference](https://developers.woovi.com/en/api)
- [LangChain4j Ollama integration](https://docs.langchain4j.dev/integrations/language-models/ollama/)
- [Ollama documentation](https://docs.ollama.com/)
- [Ollama model-list API](https://docs.ollama.com/api/tags)
