# Blaise Agent — repository guidelines

## Purpose and current state

Build a Kotlin/Compose Desktop financial assistant using LangChain4j and local
Ollama inference. Woovi sandbox Pix charge creation, payment links, and charge-status
lookup form the first end-to-end flow.

Read `README.md` and `PLAN.md` before making changes. The repository currently has a
runnable desktop shell, Ollama/LangChain4j chat, native Woovi connection checks,
Linux Secret Service credentials, and tested session-only conversation context.
Payment creation, persistence, and global preferences are not implemented yet.
Follow the milestone acceptance criteria and keep status accurate.

## Intended structure

The application lives under `desktop/`, with package `dev.blaiseagent`. Packages
are introduced as their implementations land:

- `ui/`: composables and presentation only.
- `state/`: UI-facing state and orchestration.
- `agent/`: LangChain4j, Ollama, conversation context, and tool adapters.
- `payments/`: money/charge domain, shared operation rules, gateway contracts/adapters.
- `config/`: settings and credential loading.
- `storage/`: conversation and operation persistence.

Start with one Gradle application. Introduce modules only when concrete boundaries
justify them. Use the MindGraph desktop project as a reference, not as a runtime
dependency or a required local checkout.

## Engineering rules

- Keep native tool requests/results paired by call ID in conversation context.
  Text shaped like tool JSON is diagnostic data, never executable input.
- Keep prompt policy in the versioned `PromptComposer`; application notices must
  remain distinct from model text. Prompts do not replace Kotlin validation.
- Context currently uses whole-turn character budgeting, not token accounting.
  Timings use a monotonic clock and remain session-only UI metadata.

- Standard Kotlin style: four spaces, `camelCase` members, `PascalCase` types.
- Prefer explicit immutable models, constructor injection, and testable functions.
- Keep blocking I/O off the Compose UI thread; scope coroutines to owners and clean
  them up when those owners close.
- Keep payment rules in Kotlin services shared by UI and tools, not in composables
  or prompts. Use exact monetary representations, never floating-point money.
- Gateway URLs, statuses, and charge IDs must come from actual adapter results.
- Treat model tool arguments as inputs requiring validation. Missing or ambiguous
  required values need clarification rather than invented customer/payment details.
- Preserve operation identity across retries and restarts. Reconcile uncertain
  provider outcomes; do not blindly repeat a create request.
- Default the first integration to Woovi sandbox. Verify its current API contract
  before implementing it; do not guess authentication or retry semantics.
- Keep credentials outside source control, model context, ordinary preferences,
  fixtures, and logs. Use synthetic customer data in tests and documentation.
- Settings must save gateway API keys through an OS-backed `CredentialStore`.
  Support session-only use when unavailable; never silently fall back to plaintext.
  Test credential replacement/removal, precedence, and sanitized error paths.
- Check Ollama asynchronously at startup, reuse existing instances, and never stop
  a service the app does not own. Model downloads must be explicit user actions.
- Use typed declarative HTTP clients for external APIs. Retrofit with Kotlin
  serialization is the current desktop choice; keep service interfaces separate
  from gateway/domain mapping and sanitize transport errors.
- Populate model choices from Ollama's API, not a hard-coded list. Refresh choices,
  handle removed models, and apply switches only between requests. Verify tool-call
  suitability before enabling payment tools for a selected model.

## Atomic and semantic workflow

1. Inspect the working tree and relevant code/docs before editing. Preserve unrelated
   work and keep the change focused on one behavior or concern.
2. Implement a coherent slice, including required interface/call-site updates.
3. Run checks appropriate to the change and inspect the complete staged diff.
4. Commit only when requested by the maintainer. Use Conventional Commits with a
   meaningful scope: `feat(ollama):`, `feat(woovi):`, `fix(payments):`,
   `test(agent):`, `build(desktop):`, `ci(build):`, or `docs(plan):`.
5. Each commit must be independently coherent and, once code exists, buildable.
   Do not split a compiling change into dependency-breaking intermediate commits.
6. Push/create releases only when requested. Never force-push or rewrite shared
   history without explicit permission.

Atomic means one independently verifiable purpose, not one file per commit. Avoid
mixing formatting/refactors with feature work. Record the reason for non-obvious
decisions in the commit body or relevant documentation.

## Verification

Use JDK 21 for both Gradle and compilation (`JAVA_HOME` must point to JDK 21).
From the repository root, verified commands are:

```sh
./desktop/gradlew -p desktop build
./desktop/gradlew -p desktop test
./desktop/gradlew -p desktop run
```

The equivalent commands inside `desktop/` are `./gradlew build`, `./gradlew test`,
and `./gradlew run`. Gradle 8.11.1 is pinned with a distribution checksum. The UI
needs a graphical session; tests do not. Dependencies may download on a first build.

Use `kotlin.test` and coroutine test utilities. Default tests must run without
Ollama, gateway credentials, or external network APIs. When automation is
reintroduced, it should run `build` on Linux/JDK 21. Also check documentation
consistency and `git diff --check`.

Prioritize behavioral tests for money validation, tool dispatch, state transitions,
Woovi HTTP mapping/errors, persistence, and duplicate/uncertain operation handling.
Use mock servers and synthetic fixtures. Keep live-model and sandbox checks opt-in.
Manually inspect the running Compose app for UI changes; compilation is not visual
verification. Report exactly which checks ran and what remains unverified.

## Documentation follow-through

- `README.md`: public purpose, delivered behavior, and working setup instructions.
- `PLAN.md`: milestones, acceptance criteria, decisions, and completion status.
- `AGENTS.md`: architecture, development conventions, and verified commands.

Update documentation in the same change when behavior or workflow changes. Never
mark a milestone complete based only on intent or mocked integration results.
