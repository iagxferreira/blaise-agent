# Blaise Agent

**Financial operations through conversation, powered by local AI.**

Blaise Agent is an early Kotlin desktop application being built to connect a
ChatGPT-style chat to payment gateways through LangChain4j and Ollama. The first
planned integration is **Woovi's sandbox**, focused on creating Pix charges,
returning payment links, and checking charge status.

The name honors Blaise Pascal, who invented the Pascaline to help his father with
tax calculations.

## Project status

**Desktop foundation.** The app now launches with a dark chat workspace, new/switch
conversation controls, editable drafts, example prompts, and a Settings screen.
Conversation orchestration has offline tests for streaming, cancellation, isolation,
and failure handling behind a replaceable agent interface.

Ollama, LangChain4j, Woovi, credential storage, and conversation persistence are
**not connected yet**. Sending is disabled; example prompts only fill your draft.
Settings shows the planned integrations and does not accept API keys. All drafts
and conversations currently live in memory and disappear when the app closes.

See [PLAN.md](PLAN.md) for milestones and acceptance criteria and [AGENTS.md](AGENTS.md)
for development conventions.

## The first experience

> **You:** Create a Pix payment link for R$150 for consulting.
>
> **Blaise:** Collects any missing required details, creates a charge through Woovi,
> and displays the gateway's payment link and charge status.
>
> **You:** Has it been paid?
>
> **Blaise:** Looks up the charge and reports the status returned by Woovi.

This is the intended experience, not an example of a currently working feature.

### Planned desktop interface

- Conversation sidebar with new-chat and history controls.
- Message thread, streaming responses, and a multiline composer.
- Visible tool activity and structured payment-result cards.
- Copy/open payment-link actions and charge-status refresh.
- Settings screen with secure Woovi credential management and Ollama configuration.
- Model picker populated dynamically from Ollama, with refresh and switching
  between installed local models.

## Technology and architecture

- **Kotlin/JVM and Java 21** for the application.
- **Compose Desktop and Material 3** for the interface.
- **LangChain4j** for the planned AI service, conversation context, and tool calling.
- **Ollama** for planned locally hosted inference.
- **Woovi sandbox** as the planned first gateway.

Target integration architecture:

```text
Compose Desktop → ChatViewModel → LangChain4j ↔ Ollama
                                       ↓ tools
                               Payment operations
                                       ↓
                               PaymentGateway
                                       ↓
                                Woovi sandbox
```

The model interprets requests; Kotlin validates arguments and executes operations.
Payment links and statuses come from gateway responses. Gateway credentials stay
in the adapter configuration, outside model context. Local inference still requires
network access to Woovi for payment operations.

The project uses one Gradle application under `desktop/`. The scaffold contains
`ui`, `state`, and the `agent` interface; `payments`, `config`, and `storage` will
arrive with their implementations. The Compose Desktop structure
and restrained dark theme of [MindGraph](https://github.com/iagxferreira/mindgraph)
are references for the desktop experience.

## Planned startup behavior

1. Open the desktop shell and check the configured Ollama endpoint in the background
   (default: `http://127.0.0.1:11434`).
2. Reuse a responding service; do not start another instance.
3. If a local service is unavailable, offer a start action using the installed
   `ollama serve` command, plus diagnostics and retry. Remote endpoints receive
   connection diagnostics rather than local process management.
4. Discover installed models, restore a valid selection, and check tool-call
   suitability. Offer explicit model selection/download if needed.
5. Show Woovi configuration separately, so ordinary chat can work without gateway
   credentials.

## Planned settings and secure storage

The Settings screen will offer a masked Woovi API-key field with save, replace,
remove, and test-connection actions. Saved keys belong in the OS credential store:
Linux Secret Service, macOS Keychain, or Windows Credential Manager. If the secure
store is unavailable or locked, the app will offer session-only use rather than
silently saving plaintext. Keys must never enter chat history, model context,
ordinary preferences, exports, or logs.

Ollama settings will load installed models from `GET /api/tags`, display model
details, and support refresh and saved selection. Switching applies between chat
requests; payment tools require verified model support. Missing models and
unreachable endpoints appear as actionable states rather than hard-coded choices.

## Build and run

Install **JDK 21** and point `JAVA_HOME` to it. The Gradle wrapper is checked in;
no separate Gradle installation is required. Use JDK 21 to run Gradle as well as
compile the app; the pinned Gradle 8.11.1 wrapper does not support running on JDK 25.

From the repository root:

```sh
./desktop/gradlew -p desktop build
./desktop/gradlew -p desktop test
./desktop/gradlew -p desktop run
```

Or, from `desktop/`, use `./gradlew build`, `./gradlew test`, and `./gradlew run`.
The first build downloads Gradle and Maven dependencies. Running the UI requires a
graphical desktop session. The current app and tests need neither Ollama nor Woovi
credentials and do not call external APIs.

Test reports are generated in `desktop/build/reports/tests/test/index.html`.

For future live-model checks, use an Ollama model verified with the project's
tool-calling smoke test once that integration lands.

For gateway testing, create a separate account at
[Woovi sandbox](https://app.woovi-sandbox.com/) and obtain sandbox API credentials.
Production credentials do not work in that environment. Never commit credentials,
real customer data, or local conversation history.

## Development approach

Work in small, independently verifiable changes using Conventional Commits, such as
`feat(ollama): discover local models` and `feat(woovi): create sandbox charges`.
Keep documentation aligned with delivered behavior. Detailed rules live in
[AGENTS.md](AGENTS.md).

## References

- [Implementation plan](PLAN.md)
- [Woovi documentation](https://developers.woovi.com/en/docs/intro/getting-started)
- [Woovi sandbox setup](https://developers.woovi.com/en/docs/intro/test-environment)
- [Woovi API reference](https://developers.woovi.com/en/api)
- [LangChain4j](https://docs.langchain4j.dev/)
- [Ollama](https://docs.ollama.com/)
