# NovaBrowser — System Architecture

Companion docs: `PRD.md` (why) / `PLAN.txt` (when) / `DESIGN.md` (data + UI) / `SECURITY.md` (threat model)

---

## 1. Design Philosophy

> Do not ask: "How do we put a giant AI inside a browser?"
> Ask: "How little AI do we need to make the browser feel intelligent?"

Division of responsibility:

```
Browser engine     -> render the web            (WebView / Chromium)
Security core      -> allow/block decisions      (deterministic code)
Retrieval engine    -> find the user's data      (SQLite + lexical/embedding search)
Tiny local LLM      -> understand/summarize/explain/choose (narrow, tool-scoped)
Browser controller  -> execute validated actions (permissioned API)
Local database       -> remember browser state    (SQLite)
Sync layer            -> optional encrypted state movement between devices
```

**Non-negotiable rule:** AI is never the final security authority.

```
BAD:      URL -> LLM -> "looks safe" -> open
CORRECT:  URL -> deterministic Security Gate -> allow / block
```

---

## 2. High-Level Architecture

```
                         +-------------------------+
                         |       BROWSER UI        |
                         | Tabs / URL / History     |
                         +------------+------------+
                                      |
                              Navigation Request
                                      |
                                      v
                    +--------------------------------------+
                    |             SECURITY GATE             |
                    |  1. URL normalization                |
                    |  2. Local blocklist lookup            |
                    |  3. Threat/reputation feed            |
                    |  4. Typosquat heuristics               |
                    |  5. Suspicious TLD / pattern checks    |
                    |  6. Redirect policy                    |
                    |  7. Download policy                    |
                    +----------------+---------------------+
                                     |
                         +-----------+-----------+
                         |                       |
                       BLOCK                   ALLOW
                         |                       |
                         v                       v
                   Warning Page             WEB ENGINE
                                             |
                                     +-------+-------+
                                     |               |
                                  Android          Desktop
                                  WebView          Electron
                                     |               |
                                     +-------+-------+

                +------------------+
                |    LOCAL AI      |
                |   AI Assistant   |
                +---------+--------+
                          |
                 +--------+---------+
                 |                  |
              Tiny LLM          Retrieval Engine
                 |                  |
                 +--------+---------+
                          |
                       SQLite
                          |
              +-----------+-----------+
              |                       |
           History                AI Index
           Bookmarks              Embeddings
           Sessions               Page summaries
           Metadata               Semantic metadata
```

The AI layer sits **parallel to**, never **inside**, the navigation decision path.

---

## 3. Component Breakdown

| Component | Responsibility | Depends on AI? |
|---|---|---|
| Browser UI | Tabs, address bar, "Ask Browser" input | No |
| Security Gate | URL/redirect/download allow-block decisions | **No — must never depend on AI** |
| Web Engine | Render pages (WebView / Chromium via Electron) | No |
| Retrieval Engine | Lexical + optional semantic search over local data | No (works without LLM) |
| Tiny Local LLM | Intent parsing, summarization, explanation, formatting | Is the AI |
| Browser Controller | Executes validated, permissioned actions | No — validates, doesn't trust LLM output blindly |
| Local DB (SQLite) | Security rules, history/bookmarks, AI index | No |
| Sync Layer (Phase 6) | Optional encrypted cross-device state | No |

---

## 4. Security Core (detail)

Layered, in order:

1. **Threat/reputation snapshot** — local copy of blocklists (URLhaus for malware, EasyList/EasyPrivacy for trackers/ads), updated opportunistically when online, usable fully offline from the last snapshot.
2. **URL canonicalization** — normalize URL/host, handle encoding/obfuscation, compare canonical forms before any lookup.
3. **Blocklist/allowlist lookup** — start with a simple indexed/hashed lookup in SQLite; move to a Bloom filter only if measured lookup latency requires it (avoid premature optimization / added complexity).
4. **Heuristic suspicious-link detection** — typosquatting, suspicious TLD/subdomain patterns, obfuscated URLs, brand-impersonation indicators. **Outputs a risk score/warning, not a false claim of certainty.**
5. **Redirect protection** — track navigation chains (site A -> B -> C -> download), apply allow/warn/block policy on the chain, not just the final URL.
6. **Download protection** — classify safe vs. risky (executables/scripts get stronger scrutiny); quarantine risky downloads rather than silently allowing or silently deleting.
7. **Web isolation** — rely on the underlying engine's sandbox (WebView/Chromium); do not attempt custom isolation in v1.

**Android JS bridge boundary (hard rule):**

```
UNTRUSTED WEB
     |
     X   <-- no path here
     |
privileged Android APIs

Browser-owned/trusted UI
     |
     v
Browser controller
```

Never expose a JavaScript bridge that gives arbitrary web content access to privileged native functionality. Full threat model in `SECURITY.md`.

---

## 5. Local AI Strategy

- Not "an LLM wrapped around a browser." Ordinary algorithms handle anything that doesn't need language understanding (see table).
- LLM size target: **sub-2B to ~3B class**, quantized GGUF, run via `llama.cpp` (Android NDK bindings; desktop via `llama.cpp` directly or an optional local server such as Ollama on localhost).

| Task | Best tool |
|---|---|
| Find exact text | Search algorithm |
| Extract page/article content | DOM parser |
| Language detection | Small classifier/rules |
| Summarize | Tiny LLM |
| Rewrite text | Tiny LLM |
| Page Q&A | Retrieval + tiny LLM |
| Autofill | Rules / controlled flows |
| Tab grouping | Classifier/embeddings |
| Threat decision | Deterministic security engine — **never the LLM** |
| Read aloud | Platform TTS |
| Voice command input | Local STT where available |

### Operating modes
- **LOCAL** — AI runs entirely on-device (history search, page search, summarization, rewriting, commands).
- **HYBRID** — local model handles routine work; a larger/cloud model is optional and explicitly user-enabled for tasks beyond local capacity.
- **CLOUD OFF** — no cloud AI at all; privacy-maximized mode.

Security intelligence (blocklists) is a separate concern from AI: it cannot be perfectly current while offline, but the browser uses the last downloaded snapshot and updates opportunistically — this is independent of whether the AI mode is LOCAL/HYBRID/CLOUD OFF.

---

## 6. LOW-MEMORY / NO-BLOAT STRATEGY (critical requirement)

This is a first-class architectural constraint, not an afterthought.

### 6.1 Device tiering (checked on first launch, overridable in Settings)

| Tier | RAM | AI behavior |
|---|---|---|
| Minimal | ≤ 2GB | **No LLM loaded.** Lexical-only history/bookmark search (SQL `LIKE`/FTS). Page "summarization" falls back to extractive (first N sentences / heading extraction) — no generation. |
| Light | ~3–4GB | 0.5B–1.5B quantized model, loaded on demand, unloaded after idle timeout (e.g. 2 min). |
| Standard | ~6GB+ | Up to ~3B quantized model, still lazy-loaded/unloaded, embeddings enabled for semantic search. |

The app must be **fully functional** (browser + full security gate) at every tier with AI **off** — AI is additive, never load-bearing for core browsing.

### 6.2 Concrete techniques
- **Lazy load / unload**: the model is not resident at app start. Load on first AI interaction; unload after an idle timeout. A browsing session that never touches "Ask Browser" should carry near-zero AI memory cost.
- **mmap-backed GGUF loading** via llama.cpp rather than fully materializing the model in heap where the runtime supports it.
- **No embeddings on Minimal tier** — embeddings + vector index are optional, added only where the tier supports the extra RAM/storage.
- **Retrieval-before-generation everywhere**: never feed a full page or full history into the model. Chunk, rank, retrieve top-N, only then generate (see §7).
- **No bundled Chromium on Android** — Android uses the OS-provided WebView (Chromium-based but system-shared, not app-bundled), which is why the APK stays small; Electron's Chromium bundling cost is a **desktop-only** cost, not paid on mobile.
- **Model files live outside the app bundle / outside the git repo**, downloaded post-install and stored in app-private storage — keeps the installable APK small (~target <40MB per PRD.md) and avoids shipping model weights that most low-end users would never load anyway.
- **No unnecessary background services**: security snapshot updates and any AI indexing run as bounded, user-visible or WorkManager-scheduled jobs — not persistent background processes.
- **Avoid heavy dependency bloat**: prefer platform-native APIs over large third-party UI/utility libraries; every new dependency should have a stated reason in code review, not be added "just in case."

### 6.3 What "no bloatware" means concretely here
- No pre-loaded, non-removable feature modules the user didn't ask for.
- No telemetry/analytics SDKs beyond what's strictly needed for crash diagnostics (and that should be disclosed, not silent).
- No dependency on cloud services for anything in the MVP feature list.
- Settings expose the AI tier and let a user manually force "Minimal" even on a capable device.

---

## 7. Retrieval + Tool Use (why tiny LLMs can work here)

```
User request
    |
    v
Tiny LLM = intent interpretation
    |
    v
Search / retrieval engine
    +--> lexical search
    +--> semantic search (tier-dependent)
    +--> metadata filters (time, domain, etc.)
    |
    v
Top relevant results
    |
    v
Tiny LLM = explain / choose / format
    |
    v
Browser Controller (validates + executes)
```

The model interprets intent and formats output; **code** finds the actual data and **code** executes browser actions. The LLM is never given, and never needs, huge context.

### Page Q&A pipeline (for large pages)
```
Page loaded -> DOM extraction -> remove irrelevant material ->
chunk/segment -> rank/retrieve relevant sections ->
send only relevant context to tiny local LLM -> answer
```
A 40-page doc is never dumped wholesale into a 1–3B model; only the top relevant chunks (e.g. the "authentication" section for an auth-related question) are sent.

### Model router
```
Task -> can deterministic code solve it? --YES--> do it, no LLM
                     | NO
                     v
        can tiny local model solve it? --YES--> local inference
                     | NO
                     v
        optional larger/cloud model (only if user enabled it)
```

---

## 8. Data Layer

Logical separation (can physically live in one SQLite file, separated by table/module — full schema in `DESIGN.md`):

- **Security DB** — domain rules, URL rules, tracker rules, threat metadata (source-tagged: URLHAUS / EASYLIST / EASYPRIVACY / LOCAL_HEURISTIC).
- **Browser DB** — history, bookmarks, tabs, sessions, downloads.
- **AI Index** — embeddings (tier-dependent), page summaries, semantic metadata.

---

## 9. Platform Architecture

### Android (v1 primary target)
- Engine: Android WebView (Chromium-based, OS-shared).
- Navigation security: `WebViewClient`/navigation callback as the **first** gate; resource interception (`shouldInterceptRequest`) as a **second**, complementary layer — do not assume one callback covers the full threat surface.
- Local model: llama.cpp via NDK bindings, GGUF, tiered per §6.
- On newer/supported devices, platform on-device model options (e.g. Gemini Nano via Android's on-device AI stack) may be considered as an *alternative* path — backlog item, not MVP.

### Desktop (Phase 5)
- Engine: Electron (bundled Chromium) — chosen for consistent behavior across desktop OSes vs. maintaining per-OS system webviews.
- Request interception: `session`/`webRequest` before-request hooks wired to the same Security Gate logic used on Android.
- Local inference: llama.cpp directly, or an optional local server (Ollama) over localhost.

```
                    SHARED BROWSER CORE
                            |
                 +----------+----------+
                 |                     |
              Android               Desktop
               WebView               Electron
                 |                     |
                 +----------+----------+
                            |
                      Shared AI Core
                            |
                   Shared local data model
                            |
                History / semantic retrieval
```

---

## 10. Storage / Resource Expectations (dev environment, not the shipped app)

| Component | Approx storage |
|---|---|
| Android Studio + SDK + build tools | 15–30 GB |
| Project source + Gradle caches | 2–8 GB |
| Emulator/system images | 5–15+ GB |
| Web engine/dependencies | ~1–5 GB |
| Small local LLMs | 0.5–4 GB each |
| Build outputs / APKs / logs | 1–5 GB |
| Git history / misc cache | 2–10 GB |

Practical dev machine free space: minimum ~50GB, comfortable ~80–100GB.

Recommended layout — **keep model weights out of the git repo**:
```
D:\
 ├── NovaBrowser\
 │    ├── app\
 │    ├── browser-core\
 │    ├── ai\
 │    └── ...
 └── LocalModels\
      ├── 0.5B\
      ├── 1.5B\
      └── 3B\
```
This dev-environment footprint is unrelated to the shipped app size — the shipped APK stays small (§6.2); models, SDKs, and caches are development-time costs only.

---

## 11. Future: Agent Architecture (Phase 6, not MVP)

```
User request -> Intent/Plan -> Retrieve context -> Structured browser tools
   (search_history, search_page, open_url, switch_tab, compare_pages, bookmark)
   -> Browser Controller -> Action -> Result -> Local LLM explains next step
```
Agent actions always pass through the Security Gate — the agent never bypasses it.
