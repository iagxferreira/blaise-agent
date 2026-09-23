# Blaise Agent

**Financial operations through conversation, powered by local AI.**

Blaise Agent is a planned Kotlin desktop application that connects a ChatGPT-style
chat to payment gateways through LangChain4j and Ollama. The first integration is
**Woovi's sandbox**, focused on creating Pix charges, returning payment links, and
checking charge status.

The name honors Blaise Pascal, who invented the Pascaline to help his father with
tax calculations.

## Project status

**Planning stage.** This repository currently contains the project direction and
contribution guidelines. The desktop application and gateway integration are not
implemented yet. See [PLAN.md](PLAN.md) for milestones and acceptance criteria and
[AGENTS.md](AGENTS.md) for development conventions.

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
- **LangChain4j** for the AI service, conversation context, and tool calling.
- **Ollama** for locally hosted inference.
- **Woovi sandbox** as the first gateway.

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

Start with one Gradle application under `desktop/`, with clear `ui`, `state`,
`agent`, `payments`, `config`, and `storage` packages. The Compose Desktop structure
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

## Development prerequisites

Once the application scaffold lands, development will require Java 21, the checked-in
Gradle wrapper, Ollama, and a model verified with the project's tool-calling smoke
test. Build/run commands will be documented when they exist.

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
